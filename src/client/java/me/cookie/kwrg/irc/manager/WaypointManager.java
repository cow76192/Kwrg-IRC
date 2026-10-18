package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WaypointManager {

    public static final int MAX_WAYPOINTS = 64;
    public static final int MAX_NAME_LENGTH = 32;
    public static final String EMPTY_NAME = "\u200B";
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final double MAX_COORD = 30_000_000.0;

    private static final String NUMBER = "-?\\d{1,9}(?:\\.\\d{1,14})?";
    private static final Pattern LABELED_PATTERN = Pattern.compile(
            "(?<![\\w:])[xX]\\s*[:：]\\s*(?<x>" + NUMBER + ")\\s*[,，]?\\s*[yY]\\s*[:：]\\s*(?<y>" + NUMBER + ")\\s*[,，]?\\s*[zZ]\\s*[:：]\\s*(?<z>" + NUMBER + ")(?![\\d.])"
    );
    private static final Pattern BARE_PATTERN = Pattern.compile(
            "(?<![\\d.])(" + NUMBER + ")\\s+(" + NUMBER + ")\\s+(" + NUMBER + ")(?![\\d.])"
    );
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private static final GizmoStyle FILL_STYLE = GizmoStyle.fill(0x8000FF88);
    private static final int TEXT_COLOR = 0xFF00FF88;
    private static final int BEAM_COLOR = 0xFF00FF88;

    private static ChatWaypoint chatWaypoint = null;
    private static long nearSince = 0L;

    private WaypointManager() {
    }

    public static void reloadFromConfig() {
        IrcConfig config = IrcConfig.get();
        config.waypoints = normalize(config.waypoints);
    }

    private static List<IrcConfig.WaypointEntry> normalize(List<IrcConfig.WaypointEntry> input) {
        List<IrcConfig.WaypointEntry> result = new ArrayList<>();
        if (input == null) return result;

        for (IrcConfig.WaypointEntry entry : input) {
            if (entry == null || !isValidCoord(entry.x, entry.y, entry.z)) continue;
            entry.name = sanitizeName(entry.name);
            entry.source = sanitizeSource(entry.source);
            result.add(entry);
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

    private static String sanitizeSource(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\r\\n\\t]", " ").trim();
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderWaypoints());
        LevelRenderEvents.END_EXTRACTION.register(WaypointManager::extractBeam);
        ClientTickEvents.END_CLIENT_TICK.register(WaypointManager::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            chatWaypoint = null;
            nearSince = 0L;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            chatWaypoint = null;
            nearSince = 0L;
        });
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

        IrcConfig config = IrcConfig.get();
        List<IrcConfig.WaypointEntry> list = config.waypoints;
        if (list == null) {
            list = new ArrayList<>();
            config.waypoints = list;
        }

        if (editIndex >= 0 && editIndex < list.size()) {
            IrcConfig.WaypointEntry entry = list.get(editIndex);
            entry.name = cleanName;
            entry.x = x;
            entry.y = y;
            entry.z = z;
        } else {
            if (list.size() >= MAX_WAYPOINTS) return false;
            list.add(new IrcConfig.WaypointEntry(cleanName, x, y, z));
        }

        IrcConfig.save();
        return true;
    }

    public static boolean removeAt(int index) {
        List<IrcConfig.WaypointEntry> list = IrcConfig.get().waypoints;
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        IrcConfig.save();
        return true;
    }

    public static boolean toggleAt(int index) {
        List<IrcConfig.WaypointEntry> list = IrcConfig.get().waypoints;
        if (list == null || index < 0 || index >= list.size()) return false;
        IrcConfig.WaypointEntry entry = list.get(index);
        if (entry == null) return false;
        entry.enabled = !entry.enabled;
        IrcConfig.save();
        return true;
    }

    public static int removeBySource(String source) {
        if (source == null || source.isBlank()) return 0;
        List<IrcConfig.WaypointEntry> list = IrcConfig.get().waypoints;
        if (list == null || list.isEmpty()) return 0;
        int before = list.size();
        list.removeIf(entry -> entry != null && source.equals(entry.source));
        int removed = before - list.size();
        if (removed > 0) IrcConfig.save();
        return removed;
    }

    public static int importFromRoute(String source, List<RouteManager.Entry> routeEntries) {
        if (source == null || source.isBlank() || routeEntries == null) return 0;

        IrcConfig config = IrcConfig.get();
        List<IrcConfig.WaypointEntry> list = config.waypoints;
        if (list == null) {
            list = new ArrayList<>();
            config.waypoints = list;
        }

        boolean anyValid = false;
        for (RouteManager.Entry coord : routeEntries) {
            if (RouteManager.valid(coord)) {
                anyValid = true;
                break;
            }
        }
        if (!anyValid) return 0;

        list.removeIf(entry -> entry != null && source.equals(entry.source));
        int added = 0;
        for (int i = 0; i < routeEntries.size(); i++) {
            RouteManager.Entry coord = routeEntries.get(i);
            if (!RouteManager.valid(coord)) continue;
            if (list.size() >= MAX_WAYPOINTS) break;

            String name = coord.name == null || coord.name.isBlank() ? EMPTY_NAME : coord.name.trim();
            if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
            double x = Double.parseDouble(coord.x.trim());
            double y = Double.parseDouble(coord.y.trim());
            double z = Double.parseDouble(coord.z.trim());

            IrcConfig.WaypointEntry entry = new IrcConfig.WaypointEntry(name, x, y, z);
            entry.source = source;
            list.add(entry);
            added++;
        }

        IrcConfig.save();
        return added;
    }

    public static int showChatWaypoint(double x, double y, double z) {
        return showChatWaypoint(x, y, z, "");
    }

    public static int showChatWaypoint(double x, double y, double z, String sender) {
        if (!isValidCoord(x, y, z)) {
            ChatNotifier.send("§c[Waypoint] 坐标无效 | 超出范围", NotificationType.ERR);
            return 0;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ChatNotifier.send("§c[Waypoint] 当前不在世界中", NotificationType.ERR);
            return 0;
        }

        String cleanSender = sender == null ? "" : sender.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleanSender.length() > MAX_NAME_LENGTH) {
            cleanSender = cleanSender.substring(0, MAX_NAME_LENGTH);
        }

        chatWaypoint = new ChatWaypoint(BlockPos.containing(x, y, z), mc.level.dimension(), cleanSender);
        nearSince = 0L;
        ChatNotifier.send("§a[Waypoint] 已标记 | " + formatCoordMessage(x, y, z), NotificationType.SUCCESS);
        return 1;
    }

    public static Coord parseChat(String message) {
        if (message == null || message.isEmpty()) return null;

        String text = FORMATTING_PATTERN.matcher(message).replaceAll("");
        if (text.length() > MAX_MESSAGE_LENGTH) return null;

        Matcher matcher = LABELED_PATTERN.matcher(text);
        if (matcher.find()) {
            return coord(matcher.group("x"), matcher.group("y"), matcher.group("z"));
        }

        matcher = BARE_PATTERN.matcher(text);
        if (matcher.find()) {
            return coord(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        return null;
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

    private static Coord coord(String x, String y, String z) {
        try {
            double dx = Double.parseDouble(x);
            double dy = Double.parseDouble(y);
            double dz = Double.parseDouble(z);
            return isValidCoord(dx, dy, dz) ? new Coord(dx, dy, dz) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void renderWaypoints() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<IrcConfig.WaypointEntry> list = IrcConfig.get().waypoints;
        if (list != null) {
            int count = 0;
            for (IrcConfig.WaypointEntry entry : list) {
                if (entry == null || !entry.enabled || !isValidCoord(entry.x, entry.y, entry.z)) continue;
                if (++count > MAX_WAYPOINTS) break;

                BlockPos pos = BlockPos.containing(entry.x, entry.y, entry.z);
                Gizmos.cuboid(pos, FILL_STYLE).setAlwaysOnTop();
                if (entry.name != null && !entry.name.isBlank() && !EMPTY_NAME.equals(entry.name)) {
                    Gizmos.billboardTextOverBlock(entry.name, pos, 0, TEXT_COLOR, 1.0f);
                }
            }
        }

        ChatWaypoint waypoint = chatWaypoint;
        if (waypoint != null && waypoint.dimension().equals(mc.level.dimension())) {
            Gizmos.cuboid(waypoint.pos(), FILL_STYLE).setAlwaysOnTop();
            if (!waypoint.sender().isBlank()) {
                Gizmos.billboardTextOverBlock(waypoint.sender(), waypoint.pos(), 0, TEXT_COLOR, 1.0f);
            }
        }
    }

    private static void extractBeam(LevelExtractionContext context) {
        ChatWaypoint waypoint = chatWaypoint;
        Minecraft mc = Minecraft.getInstance();
        if (waypoint == null || mc.level == null || !waypoint.dimension().equals(mc.level.dimension())) return;

        BeaconRenderState state = new BeaconRenderState();
        state.blockPos = waypoint.pos();
        state.blockEntityType = BlockEntityType.BEACON;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.animationTime = Math.floorMod(context.level().getGameTime(), 40)
                + context.deltaTracker().getGameTimeDeltaPartialTick(true);
        float distance = (float) context.camera().position().distanceTo(Vec3.atCenterOf(waypoint.pos()));
        state.beamRadiusScale = Math.max(1.0f, distance / 96.0f);
        state.sections.add(new BeaconRenderState.Section(BEAM_COLOR, BeaconRenderer.MAX_RENDER_Y));
        context.levelState().blockEntityRenderStates.add(state);
    }

    private static void onTick(Minecraft mc) {
        ChatWaypoint waypoint = chatWaypoint;
        if (waypoint == null) return;
        if (mc.level == null || mc.player == null || !waypoint.dimension().equals(mc.level.dimension())) {
            chatWaypoint = null;
            nearSince = 0L;
            return;
        }

        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(waypoint.pos())) > 36.0) {
            nearSince = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (nearSince == 0L) {
            nearSince = now;
            return;
        }
        if (now - nearSince >= 3000L) {
            chatWaypoint = null;
            nearSince = 0L;
        }
    }

    public record Coord(double x, double y, double z) {
    }

    private record ChatWaypoint(BlockPos pos, ResourceKey<Level> dimension, String sender) {
    }
}
