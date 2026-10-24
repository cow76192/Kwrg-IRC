package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.render.WaypointRenderer;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.Navigator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatWaypointManager {

    private static final String BUTTON_TEXT = "[show waypoints]";

    private static final int MAX_POINTS = 64;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int MAX_COORD = 30_000_000;
    private static final double NEAR_DISTANCE_SQ = 36.0;
    private static final double ARRIVED_DISTANCE_SQ = 9.0;
    private static final double OFF_PATH_GUIDE_SQ = 9.0;
    private static final double BREAK_SPACING = 2.0;
    private static final double NEAR_PLANE = 2.0;
    private static final long DWELL_MS = 3000L;
    private static final long EXPIRY_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long REFRESH_INTERVAL_MS = 200L;

    private static final String NUMBER = "-?\\d{1,9}(?:\\.\\d{1,14})?";
    private static final Pattern LABELED_PATTERN = Pattern.compile(
            "(?i)(?<![\\w:.])x\\s*[:：=]?\\s*(" + NUMBER + ")\\s*[,，]?\\s*y\\s*[:：=]?\\s*(" + NUMBER + ")\\s*[,，]?\\s*z\\s*[:：=]?\\s*(" + NUMBER + ")(?![\\d.])"
    );
    private static final Pattern BARE_PATTERN = Pattern.compile(
            "(?<![\\d.\\-])(" + NUMBER + ")\\s+(" + NUMBER + ")\\s+(" + NUMBER + ")(?![\\d.])"
    );
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern UNSAFE_PATTERN = Pattern.compile("[^A-Za-z0-9_\\[\\]+ ]");

    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF1E2836, 1.0f, 0x8011161F);
    private static final int BEAM_COLOR = 0xFF2B3648;
    private static final int COLOR = 0xFF9FB6CC;
    private static final int PATH_COLOR = 0xFFA64DFF;

    private static final Map<String, ChatWaypoint> POINTS = new LinkedHashMap<>();

    private ChatWaypointManager() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(ChatWaypointManager::render);
        ClientTickEvents.END_CLIENT_TICK.register(ChatWaypointManager::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> POINTS.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POINTS.clear());
    }

    public static void clear() {
        POINTS.clear();
    }

    public static int[] parse(String message) {
        if (message == null || message.isEmpty()) return null;

        String text = FORMATTING_PATTERN.matcher(message).replaceAll("");
        if (text.length() > MAX_MESSAGE_LENGTH) return null;

        Matcher matcher = LABELED_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = BARE_PATTERN.matcher(text);
            if (!matcher.find()) return null;
        }
        return blockPos(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private static int[] blockPos(String x, String y, String z) {
        try {
            double dx = Double.parseDouble(x);
            double dy = Double.parseDouble(y);
            double dz = Double.parseDouble(z);
            if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return null;
            if (Math.abs(dx) > MAX_COORD || Math.abs(dy) > MAX_COORD || Math.abs(dz) > MAX_COORD) return null;
            return new int[]{(int) Math.round(dx), (int) Math.round(dy), (int) Math.round(dz)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void appendButton(List<ChatNotifier.AnimatedSegment> segments, String body, String rank, String username) {
        int[] coord = parse(body);
        if (coord == null) return;

        String label = label(rank, username);
        String command = "/irc waypoint chat " + coord[0] + " " + coord[1] + " " + coord[2] + " " + label;
        Style style = Style.EMPTY
                .withColor(COLOR)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a标记 §f" + label + " §a的坐标")));
        segments.add(new ChatNotifier.AnimatedSegment(" ", "", true));
        segments.add(new ChatNotifier.AnimatedSegment(BUTTON_TEXT, "", true, style));
    }

    private static String label(String rank, String username) {
        String formatted = me.cookie.kwrg.irc.IrcMessageHandler.formatRankWithBrackets(rank);
        String raw = username == null ? "" : username;
        String name = formatted.isEmpty() ? raw : formatted + " " + raw;
        String clean = UNSAFE_PATTERN.matcher(FORMATTING_PATTERN.matcher(name).replaceAll("")).replaceAll("").trim();
        if (clean.isEmpty()) return "Unknown";
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    public static int mark(int x, int y, int z, String label) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Math.abs(x) > MAX_COORD || Math.abs(y) > MAX_COORD || Math.abs(z) > MAX_COORD) return 0;

        BlockPos pos = new BlockPos(x, y, z);
        String key = mc.level.dimension().toString() + ":" + pos.asLong();
        if (POINTS.size() >= MAX_POINTS && !POINTS.containsKey(key)) {
            POINTS.remove(POINTS.keySet().iterator().next());
        }
        POINTS.put(key, new ChatWaypoint(pos, label, mc.level.dimension(), mc.player == null ? pos : mc.player.blockPosition()));
        ChatNotifier.send("§a[Waypoint] 已标记 | x: " + x + " y: " + y + " z: " + z, NotificationType.SUCCESS);
        return 1;
    }

    private static void render(LevelRenderContext context) {
        if (POINTS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !context.levelState().cameraRenderState.initialized) return;

        float animationTime = Math.floorMod(mc.level.getGameTime(), 40)
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        WaypointRenderer renderer = new WaypointRenderer(context, animationTime);
        Vec3 playerPos = mc.player.position();

        for (ChatWaypoint waypoint : POINTS.values()) {
            if (!waypoint.dimension.equals(mc.level.dimension())) continue;
            Gizmos.cuboid(waypoint.pos, BLOCK_STYLE).setAlwaysOnTop();
            renderer.beam(waypoint.pos, BEAM_COLOR);
            Gizmos.billboardTextOverBlock(waypoint.label, waypoint.pos, 0, COLOR, 1.0f);

            List<Vec3> visible = waypoint.visible(playerPos);
            if (!visible.isEmpty()) renderer.path(visible, PATH_COLOR);
            if (!waypoint.guidePath.isEmpty()) renderer.path(waypoint.guidePath, PATH_COLOR);
        }
        renderer.draw();
    }

    private static void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            POINTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 playerPos = mc.player.position();

        for (var iterator = POINTS.values().iterator(); iterator.hasNext(); ) {
            ChatWaypoint waypoint = iterator.next();
            if (!waypoint.dimension.equals(mc.level.dimension())) {
                iterator.remove();
                continue;
            }

            if (now - waypoint.createdAt >= EXPIRY_MS) {
                iterator.remove();
                continue;
            }

            double dx = playerPos.x() - (waypoint.pos.getX() + 0.5);
            double dz = playerPos.z() - (waypoint.pos.getZ() + 0.5);
            double horizontalSq = dx * dx + dz * dz;
            boolean samePlane = Math.abs(playerPos.y() - (waypoint.pos.getY() + 0.5)) <= NEAR_PLANE;

            if (!samePlane || horizontalSq > NEAR_DISTANCE_SQ) {
                waypoint.nearSince = 0L;
            } else if (waypoint.nearSince == 0L) {
                waypoint.nearSince = now;
            } else if (now - waypoint.nearSince >= DWELL_MS) {
                iterator.remove();
                continue;
            }

            if (samePlane && horizontalSq <= ARRIVED_DISTANCE_SQ) {
                waypoint.path = List.of();
                continue;
            }
            if (now >= waypoint.nextAttemptAt) {
                waypoint.replan(mc.level, now);
            }
            waypoint.updateGuide(mc.level, mc.player.blockPosition(), playerPos, now);
        }
    }

    private static final class ChatWaypoint {
        private final BlockPos pos;
        private final String label;
        private final ResourceKey<Level> dimension;
        private final BlockPos origin;
        private final long createdAt = System.currentTimeMillis();
        private List<Vec3> path = List.of();
        private List<Vec3> guidePath = List.of();
        private int[] breaks = new int[0];
        private long nextGuideAt;
        private boolean searching;
        private boolean guideSearching;
        private long nearSince;
        private long nextAttemptAt;
        private boolean warned;

        private ChatWaypoint(BlockPos pos, String label, ResourceKey<Level> dimension, BlockPos origin) {
            this.pos = pos;
            this.label = label;
            this.dimension = dimension;
            this.origin = origin;
        }

        private void replan(Level level, long now) {
            if (searching) return;
            searching = true;
            nextAttemptAt = now + REFRESH_INTERVAL_MS;
            Navigator.searchAsync(level, origin, pos, found -> {
                searching = false;
                path = found;
                breaks = breakpoints(path);
                nextAttemptAt = System.currentTimeMillis() + (path.isEmpty() ? RETRY_INTERVAL_MS : REFRESH_INTERVAL_MS);
                if (path.isEmpty() && !warned) {
                    warned = true;
                    ChatNotifier.send("§e[Waypoint] 暂未找到可步行路径，先按直线指向", NotificationType.INFO);
                }
            });
        }

        private static int[] breakpoints(List<Vec3> points) {
            if (points.size() < 2) return new int[0];
            List<Integer> indices = new ArrayList<>();
            indices.add(0);
            double accumulated = 0.0;
            for (int index = 1; index < points.size(); index++) {
                accumulated += points.get(index).distanceTo(points.get(index - 1));
                boolean levelChange = (int) Math.floor(points.get(index).y()) != (int) Math.floor(points.get(index - 1).y());
                if (levelChange || accumulated >= BREAK_SPACING) {
                    indices.add(index);
                    accumulated = 0.0;
                }
            }
            if (points.size() > 1 && points.size() - 2 > indices.get(indices.size() - 1)) {
                indices.add(points.size() - 2);
            }
            if (points.size() - 1 > indices.get(indices.size() - 1)) {
                indices.add(points.size() - 1);
            }
            int[] result = new int[indices.size()];
            for (int index = 0; index < result.length; index++) {
                result[index] = indices.get(index);
            }
            return result;
        }

        private int nearestIndex(Vec3 playerPos) {
            int nearest = 0;
            double bestSq = Double.MAX_VALUE;
            for (int index = 0; index < path.size(); index++) {
                double distance = path.get(index).distanceToSqr(playerPos);
                if (distance < bestSq) {
                    bestSq = distance;
                    nearest = index;
                }
            }
            return nearest;
        }

        private List<Vec3> visible(Vec3 playerPos) {
            if (path.isEmpty()) return List.of();
            int start = 0;
            int nearest = nearestIndex(playerPos);
            for (int index : breaks) {
                if (index <= nearest) start = index;
            }
            if (!guidePath.isEmpty()) return List.of();
            return start == 0 ? path : path.subList(start, path.size());
        }

        private void updateGuide(Level level, BlockPos from, Vec3 playerPos, long now) {
            if (now < nextGuideAt) return;
            nextGuideAt = now + REFRESH_INTERVAL_MS;

            if (!path.isEmpty()) {
                int nearest = nearestIndex(playerPos);
                if (path.get(nearest).distanceToSqr(playerPos) <= OFF_PATH_GUIDE_SQ) {
                    guidePath = List.of();
                    return;
                }
            }
            if (guideSearching) return;
            guideSearching = true;
            Navigator.searchAsync(level, from, pos, found -> {
                guideSearching = false;
                List<Vec3> points = new ArrayList<>(found);
                Vec3 goal = Vec3.atCenterOf(pos);
                if (!points.isEmpty() && points.get(points.size() - 1).distanceToSqr(goal) > 1.0) {
                    points.add(new Vec3(goal.x(), pos.getY() + 0.05, goal.z()));
                }
                guidePath = points;
            });
        }

        private static BlockPos blockOf(Vec3 point) {
            return BlockPos.containing(point.x(), point.y(), point.z());
        }

    }
}
