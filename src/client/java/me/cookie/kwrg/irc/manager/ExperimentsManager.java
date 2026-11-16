package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.modules.cheat.ExperimentsModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ClickUtil;
import me.cookie.kwrg.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExperimentsManager {

    private enum GameType {
        NONE, CHRONOMATRON, ULTRASEQUENCER
    }

    private enum HelperPhase {
        READ, REPLICATE
    }

    private static final int ROUND_STATUS_SLOT = 4;
    private static final int PHASE_STATUS_SLOT = 49;
    private static final int DEFAULT_CENTER_SLOT = 22;

    private static final int CHRONOMATRON_SAFE_MAX_ROUND = 13;
    private static final int ULTRASEQUENCER_SAFE_MAX_ROUND = 10;

    private static final Pattern ROUND_PATTERN = Pattern.compile("Round:\\s*(\\d+)");
    private static final Pattern TIMER_PATTERN = Pattern.compile("Timer:\\s*\\d+s");

    private static final List<String> chronomatronSequence = new ArrayList<>();
    private static final List<Integer> ultrasequencerSequence = new ArrayList<>();

    private static int activeContainerId = -1;
    private static GameType activeGameType = GameType.NONE;

    private static boolean chronHasBeenEmpty = true;
    private static int chronomatronSequenceIndex = 0;
    private static int currentChronomatronRound = 0;
    private static int currentUltraSequencerRound = 0;

    private static HelperPhase currentPhase = null;
    private static int autoClickIndex = 0;
    private static int lastClickedSlot = -1;
    private static long nextClickTimeMs = 0L;

    public static boolean isEnabled() {
        return ExperimentsModule.CONFIG.get().experimentsEnabled;
    }

    public static void toggle() {
        boolean nextState = !isEnabled();
        ExperimentsModule.CONFIG.get().experimentsEnabled = nextState;
        resetState();
        ChatNotifier.send(
                nextState ? "§a[Experiments] 自动实验桌小游戏已开启" : "§c[Experiments] 自动实验桌小游戏已关闭",
                nextState ? NotificationType.SUCCESS : NotificationType.INFO
        );
    }

    public static int getBaseDelay() {
        return ExperimentsModule.CONFIG.get().experimentsBaseDelay;
    }

    public static int getRandomDelay() {
        return ExperimentsModule.CONFIG.get().experimentsRandomDelay;
    }

    public static void resetState() {
        chronomatronSequence.clear();
        ultrasequencerSequence.clear();
        activeContainerId = -1;
        activeGameType = GameType.NONE;
        chronHasBeenEmpty = true;
        chronomatronSequenceIndex = 0;
        currentChronomatronRound = 0;
        currentUltraSequencerRound = 0;
        currentPhase = null;
        autoClickIndex = 0;
        lastClickedSlot = -1;
        nextClickTimeMs = 0L;
    }

    private static boolean hasCachedState() {
        return activeContainerId != -1
                || activeGameType != GameType.NONE
                || currentPhase != null
                || !chronomatronSequence.isEmpty()
                || !ultrasequencerSequence.isEmpty();
    }

    public static void onTick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        // 离开容器界面时清空状态
        AbstractContainerScreen<?> screen = InventoryUtil.getOpenContainerScreen(mc);
        if (screen == null) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        String title = InventoryUtil.getCleanTitle(screen);
        GameType gameType = getGameType(title);
        if (gameType == GameType.NONE) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId != activeContainerId || gameType != activeGameType) {
            resetState();
            activeContainerId = menu.containerId;
            activeGameType = gameType;
        }

        // 如果玩家鼠标光标上意外拿起了物品，暂停自动点击以防 BadPackets
        if (InventoryUtil.hasCarriedItem(menu)) {
            return;
        }

        HelperPhase oldPhase = currentPhase;
        currentPhase = readPhase(menu);

        if (currentPhase == null) return;

        if (gameType == GameType.CHRONOMATRON) {
            processChronomatron(menu, oldPhase);
            if (currentPhase == HelperPhase.REPLICATE && oldPhase != HelperPhase.REPLICATE) {
                prepareReplicatePhase(menu, GameType.CHRONOMATRON);
            }
            if (currentPhase == HelperPhase.REPLICATE && currentChronomatronRound <= CHRONOMATRON_SAFE_MAX_ROUND) {
                autoClickChronomatron(mc, menu);
            }
        } else if (gameType == GameType.ULTRASEQUENCER) {
            processUltrasequencer(menu);
            if (currentPhase == HelperPhase.REPLICATE && oldPhase != HelperPhase.REPLICATE) {
                prepareReplicatePhase(menu, GameType.ULTRASEQUENCER);
            }
            if (currentPhase == HelperPhase.REPLICATE && currentUltraSequencerRound <= ULTRASEQUENCER_SAFE_MAX_ROUND) {
                autoClickUltrasequencer(mc, menu);
            }
        }
    }

    private static void prepareReplicatePhase(AbstractContainerMenu menu, GameType type) {
        autoClickIndex = 0;
        int fromSlot = (lastClickedSlot != -1) ? lastClickedSlot : DEFAULT_CENTER_SLOT;
        int firstTargetSlot = resolveTargetSlot(menu, type, 0, fromSlot);
        scheduleNextClick(fromSlot, firstTargetSlot != -1 ? firstTargetSlot : DEFAULT_CENTER_SLOT, true);
    }

    private static GameType getGameType(String cleanTitle) {
        if (cleanTitle.startsWith("Chronomatron (")) return GameType.CHRONOMATRON;
        if (cleanTitle.startsWith("Ultrasequencer (")) return GameType.ULTRASEQUENCER;
        return GameType.NONE;
    }

    private static HelperPhase readPhase(AbstractContainerMenu menu) {
        String name = InventoryUtil.getSlotCleanName(menu, PHASE_STATUS_SLOT);
        if (name.isEmpty()) return null;
        if (TIMER_PATTERN.matcher(name).find()) {
            return HelperPhase.REPLICATE;
        }
        if (name.contains("Remember the pattern!")) {
            return HelperPhase.READ;
        }
        return null;
    }

    private static void processChronomatron(AbstractContainerMenu menu, HelperPhase oldPhase) {
        Integer round = readRound(menu);
        if (round == null) return;

        // 如果回合数比已记录的回合数更小（例如重新开始了第 1 轮），重置序列
        if (round < currentChronomatronRound) {
            chronomatronSequence.clear();
            chronomatronSequenceIndex = 0;
            chronHasBeenEmpty = true;
            autoClickIndex = 0;
            lastClickedSlot = -1;
        }
        currentChronomatronRound = round;

        int hypixelSizeNow = chronomatronSequence.size();

        List<String> activeColors = new ArrayList<>();
        for (Slot slot : InventoryUtil.getContainerSlots(menu)) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String itemId = InventoryUtil.getItemIdPath(stack);
            if (itemId.endsWith("_terracotta") || itemId.equals("terracotta") || itemId.contains("stained_hardened_clay")) {
                String cleanName = InventoryUtil.getCleanHoverName(stack);
                if (!cleanName.isEmpty() && !activeColors.contains(cleanName)) {
                    activeColors.add(cleanName);
                }
            }
        }

        if (activeColors.isEmpty()) {
            chronHasBeenEmpty = true;
            return;
        } else if (!chronHasBeenEmpty) {
            return;
        } else {
            chronHasBeenEmpty = false;
        }

        String clickedColor = null;
        for (String color : activeColors) {
            String expected = chronomatronSequenceIndex < chronomatronSequence.size()
                    ? chronomatronSequence.get(chronomatronSequenceIndex)
                    : null;
            if (expected == null || color.equalsIgnoreCase(expected)) {
                clickedColor = color;
                break;
            }
        }
        if (clickedColor == null) return;

        boolean shouldReadLastReplicate = (oldPhase == HelperPhase.READ) || (hypixelSizeNow < currentChronomatronRound);
        boolean isReadingReady = (oldPhase == null) || (oldPhase == HelperPhase.READ);

        boolean shouldNotReadYet = switch (currentPhase) {
            case REPLICATE -> !shouldReadLastReplicate;
            case READ -> !isReadingReady;
        };
        if (shouldNotReadYet) return;

        if (chronomatronSequenceIndex == hypixelSizeNow) {
            chronomatronSequence.add(clickedColor);
            chronomatronSequenceIndex = 0;
            autoClickIndex = 0;
            int fromSlot = (lastClickedSlot != -1) ? lastClickedSlot : DEFAULT_CENTER_SLOT;
            int firstTarget = findNearestChronomatronSlot(menu, chronomatronSequence.getFirst(), fromSlot);
            scheduleNextClick(fromSlot, firstTarget != -1 ? firstTarget : DEFAULT_CENTER_SLOT, true);
        } else {
            chronomatronSequenceIndex++;
        }
    }

    private static void autoClickChronomatron(Minecraft mc, AbstractContainerMenu menu) {
        if (autoClickIndex >= chronomatronSequence.size()) return;
        if (System.currentTimeMillis() < nextClickTimeMs) return;

        int fromSlot = (lastClickedSlot != -1) ? lastClickedSlot : DEFAULT_CENTER_SLOT;
        String targetColor = chronomatronSequence.get(autoClickIndex);

        int targetSlot = findNearestChronomatronSlot(menu, targetColor, fromSlot);
        if (targetSlot == -1) return;

        ClickUtil.clickSlotPickblock(mc, menu.containerId, targetSlot);
        lastClickedSlot = targetSlot;
        autoClickIndex++;

        if (autoClickIndex < chronomatronSequence.size()) {
            String nextColor = chronomatronSequence.get(autoClickIndex);
            int nextSlot = findNearestChronomatronSlot(menu, nextColor, targetSlot);
            scheduleNextClick(targetSlot, nextSlot != -1 ? nextSlot : targetSlot, false);
        }
    }

    private static int findNearestChronomatronSlot(AbstractContainerMenu menu, String targetColor, int fromSlot) {
        return InventoryUtil.findNearestContainerSlot(menu, fromSlot, 2, slot -> {
            if (slot.index == ROUND_STATUS_SLOT || slot.index == PHASE_STATUS_SLOT) return false;
            String name = InventoryUtil.getCleanHoverName(slot.getItem());
            return name.equalsIgnoreCase(targetColor);
        });
    }

    private record UltraSlot(int number, int slotIndex) {}

    private static void processUltrasequencer(AbstractContainerMenu menu) {
        if (currentPhase != HelperPhase.READ) return;

        List<UltraSlot> orderedSlots = new ArrayList<>();
        int maxNumberInMenu = 0;

        for (Slot slot : InventoryUtil.getContainerSlots(menu)) {
            if (slot.index == ROUND_STATUS_SLOT || slot.index == PHASE_STATUS_SLOT) continue;

            String cleanName = InventoryUtil.getCleanHoverName(slot.getItem());
            if (cleanName.isEmpty()) continue;

            try {
                int seqNum = Integer.parseInt(cleanName);
                maxNumberInMenu = Math.max(maxNumberInMenu, seqNum);
                orderedSlots.add(new UltraSlot(seqNum, slot.index));
            } catch (NumberFormatException ignored) {
            }
        }

        if (orderedSlots.isEmpty()) return;

        // 如果当前桌面只有 1 个数字 (第 1 轮)，而之前缓存的回合数 > 1，说明开始了新的一局
        if (maxNumberInMenu == 1 && orderedSlots.size() == 1 && currentUltraSequencerRound > 1) {
            ultrasequencerSequence.clear();
            currentUltraSequencerRound = 1;
            lastClickedSlot = -1;
        } else {
            currentUltraSequencerRound = Math.max(currentUltraSequencerRound, maxNumberInMenu);
        }

        orderedSlots.sort(Comparator.comparingInt(UltraSlot::number));

        boolean isOld = currentUltraSequencerRound != orderedSlots.size();
        boolean alreadyKnown = ultrasequencerSequence.size() == orderedSlots.size();
        if (isOld || alreadyKnown) return;

        ultrasequencerSequence.clear();
        for (UltraSlot us : orderedSlots) {
            ultrasequencerSequence.add(us.slotIndex);
        }
        autoClickIndex = 0;
    }

    private static void autoClickUltrasequencer(Minecraft mc, AbstractContainerMenu menu) {
        if (autoClickIndex >= ultrasequencerSequence.size()) return;
        if (System.currentTimeMillis() < nextClickTimeMs) return;

        int targetSlot = ultrasequencerSequence.get(autoClickIndex);
        ClickUtil.clickSlotPickblock(mc, menu.containerId, targetSlot);
        lastClickedSlot = targetSlot;
        autoClickIndex++;

        if (autoClickIndex < ultrasequencerSequence.size()) {
            int nextSlot = ultrasequencerSequence.get(autoClickIndex);
            scheduleNextClick(targetSlot, nextSlot, false);
        }
    }

    private static int resolveTargetSlot(AbstractContainerMenu menu, GameType type, int seqIndex, int fromSlot) {
        if (type == GameType.CHRONOMATRON) {
            if (seqIndex >= chronomatronSequence.size()) return -1;
            return findNearestChronomatronSlot(menu, chronomatronSequence.get(seqIndex), fromSlot);
        } else if (type == GameType.ULTRASEQUENCER) {
            if (seqIndex >= ultrasequencerSequence.size()) return -1;
            return ultrasequencerSequence.get(seqIndex);
        }
        return -1;
    }

    private static void scheduleNextClick(int fromSlot, int toSlot, boolean isFirstClickInRound) {
        nextClickTimeMs = ClickUtil.scheduleNextClickTimeMs(
                fromSlot,
                toSlot,
                getBaseDelay(),
                getRandomDelay(),
                isFirstClickInRound
        );
    }

    private static Integer readRound(AbstractContainerMenu menu) {
        String name = InventoryUtil.getSlotCleanName(menu, ROUND_STATUS_SLOT);
        if (name.isEmpty()) return null;
        Matcher matcher = ROUND_PATTERN.matcher(name);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}