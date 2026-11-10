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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class HarpManager {

    private static final int FIRST_NOTE_COL = 1;
    private static final int LAST_NOTE_COL = 7;

    private static final int TERRACOTTA_ROW = 4;
    private static final int FIRST_ABOVE_ROW = 3;

    // 同一列连续两次触发的最小防抖间隔 (ms)
    private static final long MIN_SAME_COL_TRIGGER_INTERVAL_MS = 100L;
    // 全局两次点击发包之间的最小安全间隔 (ms)，防止同一毫秒/同一Tick连发多个点击包
    private static final long MIN_GLOBAL_CLICK_INTERVAL_MS = 55L;

    private record ScheduledClick(int containerId, int slotIndex, long executeTimeMs) {}

    private static int activeContainerId = -1;
    private static final int[] lastWoolChain = new int[9];
    private static final long[] lastTriggerTimeMs = new long[9];
    private static final Deque<ScheduledClick> pendingClicks = new ArrayDeque<>();
    private static long lastGlobalClickTimeMs = 0L;

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
        Arrays.fill(lastTriggerTimeMs, 0L);
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
            int currentChain = getWoolChainAboveTerracotta(menu, col);
            int prevChain = lastWoolChain[col];

            boolean shouldTrigger = false;

            if (prevChain == 0 && currentChain > 0) {
                shouldTrigger = true;
            } else if (currentChain > 0 && currentChain < prevChain) {
                if (now - lastTriggerTimeMs[col] >= MIN_SAME_COL_TRIGGER_INTERVAL_MS) {
                    shouldTrigger = true;
                }
            }

            if (shouldTrigger) {
                int terracottaSlot = TERRACOTTA_ROW * 9 + col;
                long clickTime = ClickUtil.scheduleJitteredClickTimeMs(getBaseDelay(), getJitterDelay());
                pendingClicks.addLast(new ScheduledClick(menu.containerId, terracottaSlot, clickTime));
                lastTriggerTimeMs[col] = now;
            }

            lastWoolChain[col] = currentChain;
        }
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

    /**
     * 每次调用最多执行 1 次点击，并强制保证两次点击间隔 >= MIN_GLOBAL_CLICK_INTERVAL_MS，
     * 杜绝同一 Tick 内并发多个容器点击包。
     */
    private static void processPendingClicks(Minecraft mc, AbstractContainerMenu menu, long now) {
        if (pendingClicks.isEmpty()) return;
        if (now - lastGlobalClickTimeMs < MIN_GLOBAL_CLICK_INTERVAL_MS) return;

        ScheduledClick head = pendingClicks.peekFirst();
        if (head == null) return;

        // 丢弃已过期的旧窗口点击任务
        if (head.containerId() != menu.containerId) {
            pendingClicks.pollFirst();
            return;
        }

        // 预留 25ms (半个 Tick) 的提前量补偿，防止正抖动 (+2ms~+10ms) 因为跨 Tick 被强行推迟 50ms
        if (now + 25L >= head.executeTimeMs()) {
            pendingClicks.pollFirst();
            ClickUtil.clickSlotPickblock(mc, menu.containerId, head.slotIndex());
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