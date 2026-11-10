package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.concurrent.ThreadLocalRandom;

public final class ClickUtil {

    public static final double DEFAULT_REFERENCE_DISTANCE = 3.0;
    public static final double DEFAULT_DISTANCE_FACTOR = 0.14;
    public static final long MIN_SAFE_CLICK_DELAY_MS = 90L;

    private ClickUtil() {
    }

    /**
     * 底层通用容器槽位点击方法
     */
    public static void clickSlotRaw(Minecraft mc, int containerId, int slotId, int button, ContainerInput mode) {
        if (mc == null || mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(containerId, slotId, button, mode, mc.player);
    }

    /**
     * 中键点击 (CLONE / Pickblock, button = 2)
     * 适用于 Hypixel GUI 小游戏与菜单点击，防止把物品抓在鼠标光标上
     */
    public static void clickSlotPickblock(Minecraft mc, int containerId, int slotId) {
        clickSlotRaw(mc, containerId, slotId, 2, ContainerInput.CLONE);
    }

    public static void clickSlotPickblock(Minecraft mc, AbstractContainerMenu menu, int slotId) {
        if (menu == null) return;
        clickSlotPickblock(mc, menu.containerId, slotId);
    }

    /**
     * 普通左键点击 (PICKUP, button = 0)
     */
    public static void clickSlotLeft(Minecraft mc, int containerId, int slotId) {
        clickSlotRaw(mc, containerId, slotId, 0, ContainerInput.PICKUP);
    }

    /**
     * 普通右键点击 (PICKUP, button = 1)
     */
    public static void clickSlotRight(Minecraft mc, int containerId, int slotId) {
        clickSlotRaw(mc, containerId, slotId, 1, ContainerInput.PICKUP);
    }

    /**
     * Shift + 左键快速移动物品 (QUICK_MOVE, button = 0)
     */
    public static void shiftClickSlot(Minecraft mc, int containerId, int slotId) {
        clickSlotRaw(mc, containerId, slotId, 0, ContainerInput.QUICK_MOVE);
    }

    /**
     * 丢弃槽位物品 (THROW, button = 0 丢一个, button = 1 丢一组)
     */
    public static void dropSlot(Minecraft mc, int containerId, int slotId, boolean fullStack) {
        clickSlotRaw(mc, containerId, slotId, fullStack ? 1 : 0, ContainerInput.THROW);
    }

    /**
     * 根据两个槽位的距离、基准延迟、随机波动计算下一次点击的时间戳 (毫秒)
     */
    public static long scheduleNextClickTimeMs(
            int fromSlot,
            int toSlot,
            int baseDelayMs,
            int randomDelayMs,
            boolean isFirstClickInRound
    ) {
        return System.currentTimeMillis() + computeDynamicDelayMs(
                fromSlot, toSlot, baseDelayMs, randomDelayMs, isFirstClickInRound
        );
    }

    /**
     * 菲茨定律动态延迟计算：根据上一个槽位与目标槽位的网格距离动态增减延迟
     */
    public static long computeDynamicDelayMs(
            int fromSlot,
            int toSlot,
            int baseDelayMs,
            int randomDelayMs,
            boolean isFirstClickInRound
    ) {
        double distance = InventoryUtil.getSlotDistance(fromSlot, toSlot, DEFAULT_REFERENCE_DISTANCE);
        double multiplier = 1.0 + (distance - DEFAULT_REFERENCE_DISTANCE) * DEFAULT_DISTANCE_FACTOR;
        multiplier = Math.clamp(multiplier, 0.62, 1.65);

        double dynamicBase = baseDelayMs * multiplier;

        long jitter = 0L;
        if (randomDelayMs > 0) {
            int minJitter = -randomDelayMs / 3;
            int maxJitter = randomDelayMs;
            jitter = ThreadLocalRandom.current().nextInt(minJitter, maxJitter + 1);
        }

        // 回合刚变为可点击状态时的视觉反应补偿 (180ms ~ 320ms)
        long reactionBonus = isFirstClickInRound ? ThreadLocalRandom.current().nextLong(180L, 321L) : 0L;

        return Math.max(MIN_SAFE_CLICK_DELAY_MS, Math.round(dynamicBase) + jitter + reactionBonus);
    }
}