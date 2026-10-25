package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.IrcMessageHandler;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatWaypointManager {

    private static final String BUTTON_TEXT = "[show waypoints]";

    private static final int MAX_MESSAGE_LENGTH = 1024;
    public static final int MAX_COORD = 30_000_000;
    private static final double NEAR_DISTANCE_SQ = 36.0;
    private static final double ARRIVED_DISTANCE_SQ = 9.0;
    private static final double OFF_PATH_GUIDE_SQ = 9.0;
    private static final double BREAK_SPACING = 2.0;
    private static final double NEAR_PLANE = 2.0;
    private static final long DWELL_MS = 3000L;
    private static final long EXPIRY_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long REFRESH_INTERVAL_MS = 200L;
    private static final long IDLE_REFRESH_MS = 1000L;

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
        Navigator.setHeuristicWeight(IrcConfig.get().navigatorWeight);
        LevelRenderEvents.BEFORE_GIZMOS.register(ChatWaypointManager::render);
        ClientTickEvents.END_CLIENT_TICK.register(ChatWaypointManager::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> POINTS.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POINTS.clear());
    }

    public static void clear() {
        POINTS.clear();
    }

    private static int[] parse(String message) {
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
            return new int[]{(int) Math.floor(dx), (int) Math.floor(dy), (int) Math.floor(dz)};
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
        String formatted = IrcMessageHandler.formatRankWithBrackets(rank);
        String raw = username == null ? "" : username;
        String name = formatted.isEmpty() ? raw : formatted + " " + raw;
        String clean = UNSAFE_PATTERN.matcher(FORMATTING_PATTERN.matcher(name).replaceAll("")).replaceAll("").trim();
        if (clean.isEmpty()) return "Unknown";
        return clean.length() > WaypointManager.MAX_NAME_LENGTH ? clean.substring(0, WaypointManager.MAX_NAME_LENGTH) : clean;
    }

    public static int mark(int x, int y, int z, String label) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;

        BlockPos pos = new BlockPos(x, y, z);
        String key = mc.level.dimension().toString() + ":" + pos.asLong();
        if (POINTS.size() >= WaypointManager.MAX_WAYPOINTS && !POINTS.containsKey(key)) {
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
        Vec3 cameraPos = context.levelState().cameraRenderState.pos;

        for (ChatWaypoint waypoint : POINTS.values()) {
            if (!waypoint.dimension.equals(mc.level.dimension())) continue;
            Gizmos.cuboid(waypoint.pos, BLOCK_STYLE).setAlwaysOnTop();
            renderer.beam(waypoint.pos, BEAM_COLOR);
            Gizmos.billboardTextOverBlock(waypoint.label + " §b[" + (int) cameraPos.distanceTo(Vec3.atCenterOf(waypoint.pos)) + "m]", waypoint.pos, 0, COLOR, 1.0f);

            List<Vec3> visible = waypoint.visible();
            if (visible.isEmpty()) visible = waypoint.guidePath();
            if (!visible.isEmpty()) renderer.path(visible, PATH_COLOR);
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
        BlockPos from = mc.player.blockPosition();
        ChatWaypoint active = null;
        double activeDistanceSq = Double.MAX_VALUE;
        for (ChatWaypoint candidate : POINTS.values()) {
            if (!candidate.dimension.equals(mc.level.dimension())) continue;
            double distance = playerPos.distanceToSqr(Vec3.atCenterOf(candidate.pos));
            if (distance < activeDistanceSq) {
                activeDistanceSq = distance;
                active = candidate;
            }
        }

        for (var iterator = POINTS.values().iterator(); iterator.hasNext(); ) {
            ChatWaypoint waypoint = iterator.next();
            if (!waypoint.dimension.equals(mc.level.dimension())) {
                iterator.remove();
                continue;
            }

            double dx = playerPos.x() - (waypoint.pos.getX() + 0.5);
            double dz = playerPos.z() - (waypoint.pos.getZ() + 0.5);
            double horizontalSq = dx * dx + dz * dz;
            boolean samePlane = Math.abs(playerPos.y() - (waypoint.pos.getY() + 0.5)) <= NEAR_PLANE;

            if (horizontalSq < waypoint.bestDistanceSq - 1.0) {
                waypoint.bestDistanceSq = horizontalSq;
                waypoint.expiresAt = now + EXPIRY_MS;
            }
            if (now >= waypoint.expiresAt) {
                iterator.remove();
                continue;
            }

            boolean seen = samePlane && horizontalSq <= NEAR_DISTANCE_SQ && seesBlock(mc, waypoint.pos);

            if (!seen) {
                waypoint.nearSince = 0L;
            } else if (waypoint.nearSince == 0L) {
                waypoint.nearSince = now;
            } else if (now - waypoint.nearSince >= DWELL_MS) {
                iterator.remove();
                continue;
            }

            if (seen && horizontalSq <= ARRIVED_DISTANCE_SQ) {
                waypoint.clearPath();
                continue;
            }
            waypoint.arrived = false;
            waypoint.tick(mc.level, from, playerPos, now, waypoint == active);
        }
    }

    private static boolean seesBlock(Minecraft mc, BlockPos pos) {
        Vec3 from = mc.gameRenderer.getMainCamera().position();
        Vec3 to = Vec3.atCenterOf(pos);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getBlockPos().equals(pos) || hit.getLocation().distanceToSqr(from) > from.distanceToSqr(to);
    }

    private static final class Plan {
        private List<Vec3> path = List.of();
        private int[] breaks = new int[0];
        private boolean searching;
        private long nextAt;
        private boolean warned;
    }

    private static final class ChatWaypoint {
        private final BlockPos pos;
        private final String label;
        private final ResourceKey<Level> dimension;
        private final BlockPos origin;
        private final long createdAt = System.currentTimeMillis();
        private long expiresAt = createdAt + EXPIRY_MS;
        private double bestDistanceSq = Double.MAX_VALUE;
        private final Plan main = new Plan();
        private final Plan guide = new Plan();
        private boolean arrived;
        private int nearest;
        private long nearSince;

        private ChatWaypoint(BlockPos pos, String label, ResourceKey<Level> dimension, BlockPos origin) {
            this.pos = pos;
            this.label = label;
            this.dimension = dimension;
            this.origin = origin;
        }

        private void clearPath() {
            arrived = true;
            main.path = List.of();
            main.breaks = new int[0];
        }

        private List<Vec3> guidePath() {
            return guide.path;
        }

        private void tick(Level level, BlockPos from, Vec3 playerPos, long now, boolean active) {
            if (now >= main.nextAt) replan(level, now, active);
            updateNearest(playerPos);
            updateGuide(level, from, playerPos, now, active);
        }

        private static long refresh(boolean active) {
            return active ? REFRESH_INTERVAL_MS : IDLE_REFRESH_MS;
        }

        private void replan(Level level, long now, boolean active) {
            if (main.searching) return;
            main.searching = true;
            main.nextAt = now + refresh(active);
            Navigator.searchAsync(level, origin, pos, result -> {
                main.searching = false;
                if (Minecraft.getInstance().level != level) return;
                if (arrived) return;
                main.path = result.points();
                main.breaks = breakpoints(main.path);
                main.nextAt = System.currentTimeMillis()
                        + (result.complete() ? refresh(active) : RETRY_INTERVAL_MS);
                if (!result.complete() && !main.warned && Minecraft.getInstance().level != null) {
                    main.warned = true;
                    ChatNotifier.send("§e[Waypoint] 不能完整步行到达", NotificationType.INFO);
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
            if (points.size() - 2 > indices.get(indices.size() - 1)) {
                indices.add(points.size() - 2);
            }
            if (points.size() - 1 > indices.get(indices.size() - 1)) {
                indices.add(points.size() - 1);
            }
            return indices.stream().mapToInt(Integer::intValue).toArray();
        }

        private void updateNearest(Vec3 playerPos) {
            int index = 0;
            double bestSq = Double.MAX_VALUE;
            for (int candidate = 0; candidate < main.path.size(); candidate++) {
                double distance = main.path.get(candidate).distanceToSqr(playerPos);
                if (distance < bestSq) {
                    bestSq = distance;
                    index = candidate;
                }
            }
            nearest = index;
        }

        private List<Vec3> visible() {
            if (main.path.isEmpty()) return List.of();
            int start = 0;
            for (int index : main.breaks) {
                if (index <= nearest) start = index;
            }
            if (!guide.path.isEmpty()) return List.of();
            return start == 0 ? main.path : main.path.subList(start, main.path.size());
        }

        private void updateGuide(Level level, BlockPos from, Vec3 playerPos, long now, boolean active) {
            if (now < guide.nextAt) return;
            guide.nextAt = now + refresh(active);

            if (!main.path.isEmpty()) {
                int index = Math.min(nearest, main.path.size() - 1);
                if (main.path.get(index).distanceToSqr(playerPos) <= OFF_PATH_GUIDE_SQ) {
                    guide.path = List.of();
                    return;
                }
            }
            if (guide.searching) return;
            guide.searching = true;
            Navigator.searchAsync(level, from, pos, result -> {
                guide.searching = false;
                if (Minecraft.getInstance().level != level) return;
                guide.path = result.points();
            });
        }
    }
}
