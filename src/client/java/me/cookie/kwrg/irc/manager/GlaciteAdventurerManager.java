package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.module.modules.cheat.GlaciteAdventurerModule;
import me.cookie.kwrg.render.WaypointRenderer;
import me.cookie.kwrg.util.skyblock.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GlaciteAdventurerManager {
    private static final String MINESHAFT_MODE = "mineshaft";
    private static final long ENTRY_DELAY = TimeUnit.SECONDS.toNanos(1);
    private static final long SCAN_INTERVAL = TimeUnit.MILLISECONDS.toNanos(250);
    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD);
    private static final Map<BlockPos, Integer> WAYPOINTS = new HashMap<>();

    private static ClientLevel level;
    private static String server = "";
    private static long nextScan;

    private GlaciteAdventurerManager() {}

    public static void onTick(Minecraft client) {
        if (!GlaciteAdventurerModule.isEnabled() || client.level == null || client.player == null
                || !MINESHAFT_MODE.equals(SkyblockLocation.island())) {
            level = null;
            WAYPOINTS.clear();
            return;
        }

        long now = System.nanoTime();
        String currentServer = SkyblockLocation.server();
        if (level != client.level || !server.equals(currentServer)) {
            level = client.level;
            server = currentServer;
            nextScan = now + ENTRY_DELAY;
            WAYPOINTS.clear();
        }

        if (now - nextScan < 0) return;
        nextScan = now + SCAN_INTERVAL;

        WAYPOINTS.clear();
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                var armor = stand.getItemBySlot(slot);
                if (!armor.is(Items.LEATHER_CHESTPLATE) && !armor.is(Items.LEATHER_LEGGINGS)
                        && !armor.is(Items.LEATHER_BOOTS) && !armor.is(Items.LEATHER_HELMET)) continue;
                var dye = armor.get(DataComponents.DYED_COLOR);
                if (dye == null) continue;
                WAYPOINTS.put(stand.blockPosition(), 0xFF000000 | dye.rgb());
                break;
            }
        }
    }

    static void render(ClientLevel currentLevel, WaypointRenderer renderer) {
        if (level != currentLevel || !GlaciteAdventurerModule.isEnabled()
                || !MINESHAFT_MODE.equals(SkyblockLocation.island())
                || !server.equals(SkyblockLocation.server())) return;

        WAYPOINTS.forEach((pos, color) -> {
            Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(color, 1.0f, 0x40000000 | (color & 0xFFFFFF)))
                    .setAlwaysOnTop();
            Gizmos.billboardTextOverBlock("冒险家", pos, 0, color, 1.0f);
            renderer.waypoint(pos, color, false, true);
        });
    }
}
