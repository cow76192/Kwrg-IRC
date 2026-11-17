package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.modules.functions.RefillModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.KeyInputs;
import me.cookie.kwrg.util.skyblock.SkyblockItemHelper;
import me.cookie.kwrg.util.skyblock.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RefillManager {

    private static final int REQUEST_TIMEOUT_TICKS = 48;
    private static final int INVENTORY_SLOT_COUNT = 36;
    private static final int MAX_TARGET = 2240;
    private static final int MAX_INTERVAL_SECONDS = 600;

    private static final Map<String, Long> PENDING = new HashMap<>();
    private static final Map<RefillModule.Entry, Long> NEXT_DUE = new IdentityHashMap<>();
    private static final Map<String, String> ITEM_ALIASES = Map.of(
            "PEARL", "ENDER_PEARL",
            "EPEARL", "ENDER_PEARL"
    );
    private static long ticks;
    private static boolean autoGateWarned;

    private RefillManager() {
    }

    public static void onTick(Minecraft mc) {
        ticks++;
        KeyInputs.beginTick();
        if (mc == null) return;
        pollHotkeys(mc);
        runAuto(mc);
    }

    public static void reset() {
        PENDING.clear();
        NEXT_DUE.clear();
        autoGateWarned = false;
        KeyInputs.reset();
    }

    private static void pollHotkeys(Minecraft mc) {
        RefillModule.Settings settings = RefillModule.CONFIG.get();
        if (settings == null || settings.refillEntries == null || settings.refillEntries.isEmpty()) return;

        Set<String> fired = new HashSet<>();

        for (RefillModule.Entry entry : settings.refillEntries) {
            if (entry == null || entry.auto) continue;
            String name = entry.key;
            if (name == null || name.isBlank()) continue;
            if (!KeyInputs.justPressed(name)) continue;
            if (!fired.add(name)) continue;
            trigger(entry, mc);
        }
    }

    private static int trigger(RefillModule.Entry entry, Minecraft mc) {
        int requested = refill(mc, entry, false);
        if (requested > 0) {
            ChatNotifier.send("§a[IRC] 已请求补充 §e" + normalize(entry.item), NotificationType.SUCCESS);
        }
        return requested;
    }

    private static void runAuto(Minecraft mc) {
        RefillModule.Settings settings = RefillModule.CONFIG.get();
        if (settings == null || settings.refillEntries == null || settings.refillEntries.isEmpty()) return;
        if (!SkyblockItemHelper.isIndexed()) {
            if (SkyblockItemHelper.isRepoMissing() && canRun(mc) && !autoGateWarned) {
                autoGateWarned = true;
                ChatNotifier.send("§e[IRC] 未找到 skyblock-repo-cache，自动补货已停用；手动快捷键仍可用", NotificationType.WARN);
            }
            return;
        }
        if (!canRun(mc)) return;

        for (RefillModule.Entry entry : settings.refillEntries) {
            if (entry == null || !entry.auto || entry.autoSeconds <= 0) continue;
            String id = normalize(entry.item);
            if (id.isEmpty()) continue;

            long due = NEXT_DUE.getOrDefault(entry, 0L);
            if (ticks < due) continue;

            NEXT_DUE.put(entry, ticks + Math.max(1, entry.autoSeconds) * 20L);
            refill(mc, entry, true);
        }
    }

    private static int refill(Minecraft mc, RefillModule.Entry entry, boolean auto) {
        String id = normalize(entry.item);
        if (id.isEmpty()) return 0;
        if (!islandAllows(entry)) {
            if (!auto) ChatNotifier.send("§e[IRC] 当前岛屿不在 §f" + entry.item + " §e的生效范围内", NotificationType.WARN);
            return 0;
        }
        if (!canRun(mc)) return 0;

        int target = clampTarget(entry.target);
        long release = pendingUntil(mc, id, target);
        if (release >= 0) {
            if (auto) {
                NEXT_DUE.put(entry, Math.max(ticks + 1, release));
            } else {
                ChatNotifier.send("§e[IRC] 正在等待 §f" + id + " §e的补充结果", NotificationType.WARN);
            }
            return 0;
        }

        int current = count(mc, id);
        int delta = target - current;
        if (delta <= 0) {
            if (!auto) ChatNotifier.send("§a[IRC] " + id + " 已满足 §7(" + current + "/" + target + ")", NotificationType.INFO);
            return 0;
        }

        mc.player.connection.sendCommand("gfs " + id + " " + delta);
        PENDING.put(id, ticks + REQUEST_TIMEOUT_TICKS);
        return 1;
    }

    private static long pendingUntil(Minecraft mc, String id, int target) {
        Long until = PENDING.get(id);
        if (until == null) return -1;
        if (ticks >= until || count(mc, id) >= target) {
            PENDING.remove(id);
            return -1;
        }
        return until;
    }

    private static int count(Minecraft mc, String id) {
        Inventory inventory = mc.player.getInventory();
        int total = 0;
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (matches(stack, id)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean matches(ItemStack stack, String id) {
        String stackId = SkyblockItemHelper.skyblockId(stack);
        if (!stackId.isEmpty()) return id.equals(stackId);
        return id.equalsIgnoreCase(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    private static boolean canRun(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null || mc.level == null || mc.screen != null) return false;
        return isInSkyblock(mc);
    }

    private static boolean isInSkyblock(Minecraft mc) {
        if (!SkyblockLocation.island().isEmpty()) return true;
        if (mc.level == null) return false;

        var objective = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;
        return objective.getDisplayName().getString().replaceAll("§.", "").toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }

    private static boolean islandAllows(RefillModule.Entry entry) {
        List<String> islands = entry.islands;
        if (islands == null || islands.isEmpty()) return true;
        if (islands.contains("all")) return true;
        return islands.contains(SkyblockLocation.island());
    }

    public static int clampTarget(int target) {
        return Math.max(1, Math.min(MAX_TARGET, target));
    }

    public static int clampInterval(int seconds, boolean auto) {
        return Math.max(auto ? 1 : 0, Math.min(MAX_INTERVAL_SECONDS, seconds));
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String id = raw.trim();
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        id = id.toUpperCase(Locale.ROOT);
        return ITEM_ALIASES.getOrDefault(id, id);
    }
}
