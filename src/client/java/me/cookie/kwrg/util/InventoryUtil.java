package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    /**
     * 获取当前打开的容器界面，若未打开容器则返回 null
     */
    public static AbstractContainerScreen<?> getOpenContainerScreen(Minecraft mc) {
        if (mc == null) return null;
        return mc.screen instanceof AbstractContainerScreen<?> screen ? screen : null;
    }

    /**
     * 获取当前打开的容器标题（去除颜色代码）
     */
    public static String getOpenContainerCleanTitle(Minecraft mc) {
        AbstractContainerScreen<?> screen = getOpenContainerScreen(mc);
        return screen != null ? getCleanTitle(screen) : "";
    }

    public static String getCleanTitle(AbstractContainerScreen<?> screen) {
        if (screen == null) return "";
        return stripColor(screen.getTitle().getString());
    }

    /**
     * 判断玩家鼠标光标上是否正抓着物品（Carried Item）
     */
    public static boolean hasCarriedItem(AbstractContainerMenu menu) {
        return menu != null && !menu.getCarried().isEmpty();
    }

    /**
     * 安全获取指定槽位索引的物品
     */
    public static ItemStack getSlotItem(AbstractContainerMenu menu, int slotIndex) {
        if (menu == null || slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return menu.slots.get(slotIndex).getItem();
    }

    /**
     * 获取指定槽位物品去除颜色代码后的显示名称
     */
    public static String getSlotCleanName(AbstractContainerMenu menu, int slotIndex) {
        return getCleanHoverName(getSlotItem(menu, slotIndex));
    }

    public static String getCleanHoverName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return stripColor(stack.getHoverName().getString()).trim();
    }

    /**
     * 获取物品注册表路径名（例如 "orange_terracotta"）
     */
    public static String getItemIdPath(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /**
     * 判断该槽位是否属于玩家自身背包（而非上部箱子容器）
     */
    public static boolean isPlayerInventorySlot(Slot slot) {
        return slot != null && slot.container instanceof Inventory;
    }

    /**
     * 判断该槽位是否属于上部箱子容器
     */
    public static boolean isContainerSlot(Slot slot) {
        return slot != null && !(slot.container instanceof Inventory);
    }

    /**
     * 获取上部箱子容器的所有槽位（排除玩家自身背包槽位）
     */
    public static List<Slot> getContainerSlots(AbstractContainerMenu menu) {
        if (menu == null) return List.of();
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isContainerSlot(slot)) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * 获取当前窗口中属于玩家自身背包的所有槽位
     */
    public static List<Slot> getPlayerInventorySlots(AbstractContainerMenu menu) {
        if (menu == null) return List.of();
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isPlayerInventorySlot(slot)) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * 计算两个槽位在 9 列网格中的欧几里得距离
     */
    public static double getSlotDistance(int slotA, int slotB, double fallbackDistance) {
        if (slotA < 0 || slotB < 0) return fallbackDistance;
        int colA = slotA % 9;
        int rowA = slotA / 9;
        int colB = slotB % 9;
        int rowB = slotB / 9;
        int dx = colA - colB;
        int dy = rowA - rowB;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 在上部箱子容器中查找满足条件且距离 fromSlot 最近的槽位索引。
     * 距离相等时，优先选择更靠近 preferredRow (例如中间行 row = 2) 的槽位。
     */
    public static int findNearestContainerSlot(
            AbstractContainerMenu menu,
            int fromSlot,
            int preferredRow,
            Predicate<Slot> filter
    ) {
        if (menu == null || filter == null) return -1;
        int bestSlot = -1;
        double minDistance = Double.MAX_VALUE;

        for (Slot slot : menu.slots) {
            if (!isContainerSlot(slot) || !filter.test(slot)) continue;

            double dist = getSlotDistance(fromSlot, slot.index, Double.MAX_VALUE);
            if (dist < minDistance - 1e-4) {
                minDistance = dist;
                bestSlot = slot.index;
            } else if (Math.abs(dist - minDistance) <= 1e-4 && bestSlot != -1) {
                int currentRowDiff = Math.abs((slot.index / 9) - preferredRow);
                int bestRowDiff = Math.abs((bestSlot / 9) - preferredRow);
                if (currentRowDiff < bestRowDiff) {
                    bestSlot = slot.index;
                }
            }
        }
        return bestSlot;
    }

    /**
     * 在玩家快捷栏 (0~8) 中查找匹配的槽位索引，未找到返回 -1
     */
    public static int findHotbarSlot(Minecraft mc, Predicate<ItemStack> predicate) {
        if (mc == null || mc.player == null || predicate == null) return -1;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 去除字符串中的 Minecraft 颜色/格式代码 (§x)
     */
    public static String stripColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }
}