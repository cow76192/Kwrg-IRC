package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.util.WaypointFiles;
import me.cookie.kwrg.util.SkyblockLocation;
import me.cookie.kwrg.render.WaypointRenderer;

import me.cookie.kwrg.irc.config.IrcConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaypointManager {

    public static final int MAX_WAYPOINTS = 64;
    public static final int MAX_NAME_LENGTH = 32;
    public static final String EMPTY_NAME = "\u200B";
    private static final double MAX_COORD = 30_000_000.0;

    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF00FF88, 1.0f, 0x8000FF88);
    private static final int TEXT_COLOR = 0xFF00FF88;

    private static final Map<String, IrcConfig.WaypointEntry> SAVED_WAYPOINTS = new LinkedHashMap<>();

    private WaypointManager() {
    }

    public static void reloadFromConfig() {
        IrcConfig config = IrcConfig.get();
        Map<String, IrcConfig.WaypointEntry> normalized = normalize(config.waypoints);
        SAVED_WAYPOINTS.clear();
        SAVED_WAYPOINTS.putAll(normalized);
        if (WaypointFiles.initialize(normalized)) config.waypoints = new LinkedHashMap<>();
    }

    public static void replaceSavedEntries(List<PointEntry> points) {
        SAVED_WAYPOINTS.clear();
        for (PointEntry point : points) {
            if (!point.valid()) continue;
            IrcConfig.WaypointEntry entry = new IrcConfig.WaypointEntry(sanitizeName(point.name), Double.parseDouble(point.x), Double.parseDouble(point.y), Double.parseDouble(point.z));
            entry.enabled = point.visible;
            SAVED_WAYPOINTS.put(point.id, entry);
        }
    }

    private static Map<String, IrcConfig.WaypointEntry> normalize(Map<String, IrcConfig.WaypointEntry> input) {
        Map<String, IrcConfig.WaypointEntry> result = new LinkedHashMap<>();
        if (input == null) return result;

        for (Map.Entry<String, IrcConfig.WaypointEntry> item : input.entrySet()) {
            IrcConfig.WaypointEntry entry = item.getValue();
            if (entry == null || !isValidCoord(entry.x, entry.y, entry.z)) continue;
            entry.name = sanitizeName(entry.name);
            result.put(item.getKey(), entry);
            if (result.size() >= MAX_WAYPOINTS) break;
        }
        return result;
    }

    private static String sanitizeName(String value) {
        if (value == null) return EMPTY_NAME;
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (clean.isEmpty()) return EMPTY_NAME;
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    public static void init() {
        SkyblockLocation.init();
        LevelRenderEvents.BEFORE_GIZMOS.register(WaypointManager::renderWaypoints);
    }

    public static List<IrcConfig.WaypointEntry> savedEntries() {
        return new ArrayList<>(SAVED_WAYPOINTS.values());
    }

    public static boolean saveEntry(int editIndex, String name, String[] values) {
        String cleanName = name != null ? name.trim() : "";
        if (cleanName.isEmpty()) cleanName = EMPTY_NAME;
        if (cleanName.length() > MAX_NAME_LENGTH
                || values == null || values.length < 3
                || values[0] == null || values[1] == null || values[2] == null) return false;

        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(values[0].trim());
            y = Double.parseDouble(values[1].trim());
            z = Double.parseDouble(values[2].trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (!isValidCoord(x, y, z)) return false;

        List<String> keys = new ArrayList<>(SAVED_WAYPOINTS.keySet());
        if (editIndex >= 0 && editIndex < keys.size()) {
            IrcConfig.WaypointEntry entry = SAVED_WAYPOINTS.get(keys.get(editIndex));
            entry.name = cleanName;
            entry.x = x;
            entry.y = y;
            entry.z = z;
        } else {
            if (SAVED_WAYPOINTS.size() >= MAX_WAYPOINTS) return false;
            SAVED_WAYPOINTS.put(UUID.randomUUID().toString(), new IrcConfig.WaypointEntry(cleanName, x, y, z));
        }

        return WaypointFiles.saveDefault(SAVED_WAYPOINTS);
    }

    public static boolean removeAt(int index) {
        List<String> keys = new ArrayList<>(SAVED_WAYPOINTS.keySet());
        if (index < 0 || index >= keys.size()) return false;
        SAVED_WAYPOINTS.remove(keys.get(index));
        return WaypointFiles.saveDefault(SAVED_WAYPOINTS);
    }

    public static boolean toggleAt(int index) {
        List<String> keys = new ArrayList<>(SAVED_WAYPOINTS.keySet());
        if (index < 0 || index >= keys.size()) return false;
        IrcConfig.WaypointEntry entry = SAVED_WAYPOINTS.get(keys.get(index));
        if (entry == null) return false;
        entry.enabled = !entry.enabled;
        return WaypointFiles.saveDefault(SAVED_WAYPOINTS);
    }

    public static String formatCoordMessage(double x, double y, double z) {
        return "x: " + formatCoord(x) + " y: " + formatCoord(y) + " z: " + formatCoord(z);
    }

    public static String formatCoord(double value) {
        if (!Double.isFinite(value)) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public static boolean isValidCoord(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= MAX_COORD && Math.abs(y) <= MAX_COORD && Math.abs(z) <= MAX_COORD;
    }

    private static void renderWaypoints(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !context.levelState().cameraRenderState.initialized) return;
        float animationTime = Math.floorMod(mc.level.getGameTime(), 40)
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        WaypointRenderer renderer = new WaypointRenderer(context, animationTime);

        for (WaypointFiles.Collection file : WaypointFiles.collections()) {
            if (!file.activeIn(SkyblockLocation.island())) continue;
            for (PointEntry entry : file.points) {
                if (!entry.visible || !entry.valid()) continue;
                BlockPos pos = entry.blockPos();
                Gizmos.cuboid(pos, BLOCK_STYLE).setAlwaysOnTop();
                renderer.waypoint(pos, TEXT_COLOR, false, file.showTracer);
                if (entry.name != null && !entry.name.isBlank() && !EMPTY_NAME.equals(entry.name)) {
                    Gizmos.billboardTextOverBlock(entry.name, pos, 0, TEXT_COLOR, 1.0f);
                }
            }
        }

        renderer.draw();
    }
}
