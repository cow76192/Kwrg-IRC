package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.modules.cheat.HarpModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ClickUtil;
import me.cookie.kwrg.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

public class HarpManager {

    private static final int FIRST_NOTE_COL = 1;
    private static final int LAST_NOTE_COL = 7;

    private static final int TERRACOTTA_ROW = 4;
    private static final int FIRST_ABOVE_ROW = 3;
    private static final int MAX_ABOVE_CHAIN = FIRST_ABOVE_ROW + 1; // 陶瓦上方共 4 行
    private static final int TOTAL_HARP_ROWS = 6;

    // 同一列连续两次触发的最小防抖间隔 (ms)
    private static final long MIN_SAME_COL_TRIGGER_INTERVAL_MS = 100L;
    private static final long MIN_GLOBAL_CLICK_INTERVAL_MS = 35L;

    private record ScheduledClick(int containerId, int slotIndex, long executeTimeMs) {}

    private static int activeContainerId = -1;
    private static final int[] lastWoolChain = new int[9];
    private static final int[] lastLowestWoolRow = new int[9];
    private static final long[] lastLowestAdvanceTimeMs = new long[9];
    private static final long[] measuredBeatIntervalMs = new long[9];
    private static final long[] lastTriggerTimeMs = new long[9];
    private static final ItemStack[][] lastColumnStacks = new ItemStack[9][TOTAL_HARP_ROWS];

    // 按计划执行时间升序排列的优先队列
    private static final PriorityQueue<ScheduledClick> pendingClicks =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledClick::executeTimeMs));
    private static long lastGlobalClickTimeMs = 0L;

    static {
        resetState();
    }

    public static boolean isEnabled() {
        return HarpModule.CONFIG.get().harpEnabled;
    }

    public static void toggle() {
        boolean nextState = !isEnabled();
        HarpModule.CONFIG.get().harpEnabled = nextState;
        resetState();
        ChatNotifier.send(
                nextState ? "§a[Harp] 自动竖琴已开启" : "§c[Harp] 自动竖琴已关闭",
                nextState ? NotificationType.SUCCESS : NotificationType.INFO
        );
    }

    public static int getBaseDelay() {
        return HarpModule.CONFIG.get().harpBaseDelay;
    }

    public static int getJitterDelay() {
        return HarpModule.CONFIG.get().harpJitterDelay;
    }

    public static void resetState() {
        activeContainerId = -1;
        Arrays.fill(lastWoolChain, 0);
        Arrays.fill(lastLowestWoolRow, -1);
        Arrays.fill(lastLowestAdvanceTimeMs, 0L);
        Arrays.fill(measuredBeatIntervalMs, 0L);
        Arrays.fill(lastTriggerTimeMs, 0L);
        for (int col = 0; col < lastColumnStacks.length; col++) {
            Arrays.fill(lastColumnStacks[col], ItemStack.EMPTY);
        }
        pendingClicks.clear();
        lastGlobalClickTimeMs = 0L;
    }

    private static boolean hasCachedState() {
        return activeContainerId != -1 || !pendingClicks.isEmpty();
    }

    public static void onTick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        AbstractContainerScreen<?> screen = InventoryUtil.getOpenContainerScreen(mc);
        if (screen == null) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        String title = InventoryUtil.getCleanTitle(screen);
        if (!isHarpScreen(title)) {
            if (hasCachedState()) {
                resetState();
            }
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId != activeContainerId) {
            resetState();
            activeContainerId = menu.containerId;
        }

        if (InventoryUtil.hasCarriedItem(menu)) {
            return;
        }

        long now = System.currentTimeMillis();
        scanColumnsAndSchedule(menu, now);
        processPendingClicks(mc, menu, now);
    }

    private static boolean isHarpScreen(String cleanTitle) {
        return cleanTitle.startsWith("Harp - ");
    }

    private static void scanColumnsAndSchedule(AbstractContainerMenu menu, long now) {
        for (int col = FIRST_NOTE_COL; col <= LAST_NOTE_COL; col++) {
            updateColumnBeatCadence(menu, col, now);

            int currentChain = getWoolChainAboveTerracotta(menu, col);
            int prevChain = lastWoolChain[col];
            boolean columnSlotsUpdated = updateAndCheckColumnStacks(menu, col);

            boolean shouldTrigger = false;

            if (prevChain == 0 && currentChain > 0) {
                // 单个羊毛或连续羊毛串的第一个音符刚到达陶瓦正上方
                shouldTrigger = true;
            } else if (currentChain > 0 && currentChain < prevChain) {
                // 陶瓦上方的连续羊毛串下移并缩短
                if (now - lastTriggerTimeMs[col] >= MIN_SAME_COL_TRIGGER_INTERVAL_MS) {
                    shouldTrigger = true;
                }
            } else if (currentChain == MAX_ABOVE_CHAIN && prevChain == MAX_ABOVE_CHAIN) {
                // 超过 4 个连续羊毛时，上方 4 行窗口处于饱和状态
                long elapsed = now - lastTriggerTimeMs[col];
                if (elapsed >= MIN_SAME_COL_TRIGGER_INTERVAL_MS) {
                    long expectedBeatMs = measuredBeatIntervalMs[col] > 0L
                            ? measuredBeatIntervalMs[col] + 25L
                            : MIN_SAME_COL_TRIGGER_INTERVAL_MS;
                    if (columnSlotsUpdated || elapsed >= expectedBeatMs) {
                        shouldTrigger = true;
                    }
                }
            }

            if (shouldTrigger) {
                int terracottaSlot = TERRACOTTA_ROW * 9 + col;
                long clickTime = ClickUtil.scheduleJitteredClickTimeMs(getBaseDelay(), getJitterDelay());
                pendingClicks.offer(new ScheduledClick(menu.containerId, terracottaSlot, clickTime));
                lastTriggerTimeMs[col] = now;
            }

            lastWoolChain[col] = currentChain;
        }
    }

    /**
     * 追踪羊毛从 Row 0 -> Row 1 -> Row 2 -> Row 3 下落时的节拍间隔
     */
    private static void updateColumnBeatCadence(AbstractContainerMenu menu, int col, long now) {
        int lowestRow = getLowestWoolRowAboveTerracotta(menu, col);
        int prevLowestRow = lastLowestWoolRow[col];

        if (lowestRow == -1) {
            lastLowestAdvanceTimeMs[col] = 0L;
            measuredBeatIntervalMs[col] = 0L;
        } else if (prevLowestRow >= 0 && lowestRow == prevLowestRow + 1) {
            if (lastLowestAdvanceTimeMs[col] > 0L) {
                long stepMs = now - lastLowestAdvanceTimeMs[col];
                if (stepMs >= MIN_SAME_COL_TRIGGER_INTERVAL_MS && stepMs <= 1000L) {
                    measuredBeatIntervalMs[col] = stepMs;
                }
            }
            lastLowestAdvanceTimeMs[col] = now;
        } else if (prevLowestRow == -1 && lowestRow == 0) {
            lastLowestAdvanceTimeMs[col] = now;
        }

        lastLowestWoolRow[col] = lowestRow;
    }

    private static int getLowestWoolRowAboveTerracotta(AbstractContainerMenu menu, int col) {
        for (int row = FIRST_ABOVE_ROW; row >= 0; row--) {
            if (isWool(InventoryUtil.getSlotItem(menu, row * 9 + col))) {
                return row;
            }
        }
        return -1;
    }

    /**
     * 记录并检查当前列是否有槽位的 ItemStack 实例被服务端更新
     */
    private static boolean updateAndCheckColumnStacks(AbstractContainerMenu menu, int col) {
        boolean updated = false;
        for (int row = 0; row < TOTAL_HARP_ROWS; row++) {
            ItemStack current = InventoryUtil.getSlotItem(menu, row * 9 + col);
            if (current != lastColumnStacks[col][row]) {
                updated = true;
                lastColumnStacks[col][row] = current;
            }
        }
        return updated;
    }

    private static int getWoolChainAboveTerracotta(AbstractContainerMenu menu, int col) {
        int chainCount = 0;
        for (int row = FIRST_ABOVE_ROW; row >= 0; row--) {
            int slotIndex = row * 9 + col;
            ItemStack stack = InventoryUtil.getSlotItem(menu, slotIndex);
            if (isWool(stack)) {
                chainCount++;
            } else {
                break;
            }
        }
        return chainCount;
    }

    private static void processPendingClicks(Minecraft mc, AbstractContainerMenu menu, long now) {
        if (pendingClicks.isEmpty()) return;
        if (now - lastGlobalClickTimeMs < MIN_GLOBAL_CLICK_INTERVAL_MS) return;

        while (!pendingClicks.isEmpty() && pendingClicks.peek().containerId() != menu.containerId) {
            pendingClicks.poll();
        }

        ScheduledClick earliest = pendingClicks.peek();
        if (earliest == null) return;

        // 预留 25ms (半个 Tick) 的提前量补偿，防止正抖动 (+2ms~+10ms) 因为跨 Tick 被强行推迟 50ms
        if (now + 25L >= earliest.executeTimeMs()) {
            pendingClicks.poll();
            ClickUtil.clickSlotPickblock(mc, menu.containerId, earliest.slotIndex());
            lastGlobalClickTimeMs = now;
        }
    }

    private static boolean isWool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ItemTags.WOOL)) return true;
        String id = InventoryUtil.getItemIdPath(stack);
        return id.endsWith("_wool") || id.equals("wool");
    }
}