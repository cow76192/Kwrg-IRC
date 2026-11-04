package me.cookie.kwrg.util;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class Navigator {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-navigator");
    private static final int MAX_NODES = 20_000;
    private static final int MAX_PENDING = 8;
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final int MAX_STATES = 65_536;
    private static final Long2ObjectOpenHashMap<BlockState> STATES = new Long2ObjectOpenHashMap<>(4_096);
    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();
    private static final int Y_RADIUS = 32;
    private static final int WINDOW = Y_RADIUS * 2 + 1;
    private static final double[] COEFFICIENTS = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0};
    private static final float FAVOR_STRENGTH = 0.3f;
    private static final int FAVOR_LIMIT = 4_096;
    private static final float MIN_PROGRESS = 1.0f;
    private static final Long2FloatOpenHashMap FAVOR = new Long2FloatOpenHashMap();
    private static final int MAX_RADIUS = 128;
    private static final int MAX_FALL = 8;
    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final float STEP_UP_COST = 0.4f;
    private static final float FALL_COST = 0.8f;
    private static final float WATER_COST = 0.6f;
    private static final float WADE_PENALTY = 64.0f;
    private static final float CLIMB_COST = 1.0f;
    private static final float FALL_PENALTY = 0.5f;
    private static final int MAX_HOP = 8;
    private static final int MAX_JUMP_UP = 1;
    private static final int TELEPORT_RANGE = 61;
    private static final float TELEPORT_PENALTY = 12.0f;
    private static final float TELEPORT_FACTOR = 1.6f;
    private static final int TELEPORT_BUDGET = 128;
    private static final float GOAL_RADIUS_SQ = 6.0f;
    private static final float MIN_HEURISTIC_WEIGHT = 0.8f;
    private static final float MAX_HEURISTIC_WEIGHT = 2.2f;

    private static volatile float heuristicWeight = 1.0f;
    private static volatile boolean allowSwimming = true;
    private static volatile boolean allowJumping = true;
    private static volatile boolean allowTeleport = true;
    private static volatile boolean allowClimbing = true;

    public static void setHeuristicWeight(float weight) {
        if (!Float.isFinite(weight)) return;
        heuristicWeight = Math.min(MAX_HEURISTIC_WEIGHT, Math.max(MIN_HEURISTIC_WEIGHT, weight));
    }

    public static void setAllowSwimming(boolean allow) {
        allowSwimming = allow;
    }

    public static boolean isAllowSwimming() {
        return allowSwimming;
    }

    public static void setAllowJumping(boolean allow) {
        allowJumping = allow;
    }

    public static boolean isAllowJumping() {
        return allowJumping;
    }

    public static void setAllowTeleport(boolean allow) {
        allowTeleport = allow;
    }

    public static boolean isAllowTeleport() {
        return allowTeleport;
    }

    public static void setAllowClimbing(boolean allow) {
        allowClimbing = allow;
    }

    public static boolean isAllowClimbing() {
        return allowClimbing;
    }

    private static final double SMOOTHING_STEP = 0.5;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final int[][][] RAY_STEPS = RaycastUtil.buildRaySteps(2, TELEPORT_RANGE);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "kwrg-navigator");
        thread.setDaemon(true);
        return thread;
    });

    private static String reportedFailure;

    private Navigator() {
    }

    public record Result(List<Vec3> points, boolean complete, boolean truncated) {
    }

    public static void searchAsync(Level level, BlockPos from, BlockPos to, Consumer<Result> callback) {
        if (PENDING.incrementAndGet() > MAX_PENDING) {
            PENDING.decrementAndGet();
            report("queue-full");
            callback.accept(new Result(List.of(), false, true));
            return;
        }
        EXECUTOR.submit(() -> {
            Result result;
            try {
                result = attempt(level, from, to);
            } catch (Throwable failure) {
                report("unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                result = new Result(List.of(), false, true);
            } finally {
                PENDING.decrementAndGet();
            }
            Result ready = result;
            Minecraft.getInstance().execute(() -> callback.accept(ready));
        });
    }

    private static Result attempt(Level level, BlockPos from, BlockPos to) {
        for (int tries = 0; tries < 2; tries++) {
            try {
                return search(level, from, to);
            } catch (RuntimeException e) {
                report((tries == 0 ? "" : "retry ") + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return new Result(List.of(), false, true);
    }

    private static Result search(Level level, BlockPos from, BlockPos to) {
        STATES.clear();
        if (!level.hasChunkAt(from) || !level.hasChunkAt(to)) return new Result(List.of(), false, true);

        BlockPos start = surface(level, from);
        if (start == null) {
            report("no-start");
            return new Result(List.of(), false, true);
        }
        BlockPos goal = snapTarget(level, to);
        if (Math.abs(goal.getX() - start.getX()) > MAX_RADIUS || Math.abs(goal.getZ() - start.getZ()) > MAX_RADIUS) {
            report("out-of-radius " + goal.toShortString());
            return new Result(List.of(), false, true);
        }

        boolean direct = (standable(level, start) && standable(level, goal) && clearWalkLine(level, start, goal))
                || (allowTeleport && grounded(level, start) && grounded(level, goal)
                && start.distSqr(goal) <= (double) TELEPORT_RANGE * TELEPORT_RANGE
                && teleportLine(level, start, goal));
        if (direct) return new Result(List.of(center(start), center(goal)), true, false);

        Search search = new Search(level, start, goal);
        Node reached = search.run();

        if (reached == null) {
            Node best = search.bestEffort();
            report("no-path " + from.toShortString() + " -> " + to.toShortString()
                    + " feet=" + blocked(level, to)
                    + " head=" + blocked(level, to.above())
                    + " ground=" + blocked(level, to.below())
                    + " dy=" + (to.getY() - start.getY())
                    + " snapped=" + goal.toShortString()
                    + " best=" + best.pos().toShortString()
                    + " nodes=" + search.visited
                    + " edges=" + search.considered
                    + " ms=" + search.elapsed);
            Traced traced = trace(best);
            return new Result(finish(level, smooth(traced.points(), traced.jump()), best, goal, false), false, search.truncated);
        }
        search.favor(reached);
        Traced traced = trace(reached);
        List<Vec3> points = finish(level, smooth(traced.points(), traced.jump()), reached, goal, true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Navigator] nodes={} edges={} ms={} points={}", search.visited, search.considered, search.elapsed, points.size());
        }
        return new Result(points, true, false);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5);
    }

    private static List<Vec3> finish(Level level, List<Vec3> points, Node endNode, BlockPos goal, boolean complete) {
        if (points.isEmpty()) return points;
        if (endNode.x == goal.getX() && endNode.y == goal.getY() && endNode.z == goal.getZ()) {
            return points;
        }
        if (complete && canReachStep(level, endNode.x, endNode.y, endNode.z, goal.getX(), goal.getY(), goal.getZ())) {
            Vec3 target = center(goal);
            if (points.getLast().distanceToSqr(target) > 1.0e-4) {
                points.add(target);
            }
        }
        return points;
    }

    private static boolean canReachStep(Level level, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        int dz = toZ - fromZ;
        if (dx == 0 && dz == 0) {
            if (dy == 0) return true;
            if (Math.abs(dy) == 1) {
                if (allowClimbing && climbable(level, fromX, fromY, fromZ)) {
                    return dy > 0 ? free(level, toX, toY, toZ) : walkable(level, toX, toY, toZ);
                }
                if (allowSwimming && fluid(level, fromX, fromY, fromZ)) {
                    return dy > 0 ? (free(level, toX, toY, toZ) && fluid(level, toX, toY, toZ)) : walkable(level, toX, toY, toZ);
                }
            }
            return false;
        }
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            if (!clearCorner(level, fromX, fromY, fromZ, dx, dz)) return false;
            if (dy == 0) return free(level, toX, toY, toZ);
            if (dy == 1) {
                boolean canStepUp = allowJumping || (allowSwimming && fluid(level, fromX, fromY, fromZ));
                return canStepUp
                        && free(level, fromX, fromY + 1, fromZ)
                        && clearCorner(level, fromX, fromY + 1, fromZ, dx, dz)
                        && free(level, toX, toY, toZ);
            }
            if (dy < 0 && dy >= -MAX_FALL) {
                for (int stepY = fromY; stepY >= toY; stepY--) {
                    if (!free(level, toX, stepY, toZ)) return false;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean clearWalkLine(Level level, BlockPos from, BlockPos to) {
        if (from.getY() != to.getY()) return false;
        int y = from.getY();
        if (fluid(level, from.getX(), y, from.getZ()) || fluid(level, to.getX(), y, to.getZ())) return false;

        return RaycastUtil.isLineClear(from, to,
                (x, sy, z) -> standable(level, x, sy, z) && !fluid(level, x, sy, z),
                (x, sy, z) -> free(level, x, sy, z));
    }

    private static boolean teleportLine(Level level, BlockPos from, BlockPos to) {
        return RaycastUtil.isLineClear(from, to,
                (x, y, z) -> !blocked(level, x, y, z) && !blocked(level, x, y + 1, z),
                (x, y, z) -> free(level, x, y, z));
    }

    private static boolean clearCorner(Level level, int x, int y, int z, int dx, int dz) {
        return RaycastUtil.isCornerClear(x, y, z, dx, dz, (cx, cy, cz) -> free(level, cx, cy, cz));
    }

    private static BlockPos snapTarget(Level level, BlockPos to) {
        if (walkable(level, to.getX(), to.getY(), to.getZ())) return to;
        for (int radius = 1; radius <= 4; radius++) {
            for (int dy = 2; dy >= -4; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = to.getX() + dx;
                        int cy = to.getY() + dy;
                        int cz = to.getZ() + dz;
                        if (walkable(level, cx, cy, cz)) return new BlockPos(cx, cy, cz);
                    }
                }
            }
        }
        return to;
    }

    private static BlockPos surface(Level level, BlockPos from) {
        for (int dy = 0; dy <= 2; dy++) {
            if (walkable(level, from.getX(), from.getY() + dy, from.getZ())) return from.above(dy);
        }
        return descendTo(level, from.getX(), from.getY(), from.getZ(), MAX_FALL);
    }

    private static BlockPos walkNeighbour(Level level, int x, int y, int z, int dx, int dz) {
        if (!clearCorner(level, x, y, z, dx, dz)) return null;
        int maxDy = (allowJumping || (allowSwimming && fluid(level, x, y, z))) ? 1 : 0;
        for (int dy = maxDy; dy >= 0; dy--) {
            if (dy > 0 && (!free(level, x, y + 1, z) || !clearCorner(level, x, y + 1, z, dx, dz))) continue;
            if (walkable(level, x + dx, y + dy, z + dz)) return new BlockPos(x + dx, y + dy, z + dz);
        }
        return null;
    }

    private static BlockPos fallNeighbour(Level level, int x, int y, int z, int dx, int dz) {
        if (!clearCorner(level, x, y, z, dx, dz)) return null;
        return descendTo(level, x + dx, y, z + dz, MAX_FALL);
    }

    private static BlockPos descendTo(Level level, int x, int y, int z, int maxDown) {
        for (int dy = -1; dy >= -maxDown; dy--) {
            int ny = y + dy;
            if (blocked(level, x, ny, z) || blocked(level, x, ny + 1, z)) return null;
            if (walkable(level, x, ny, z)) return new BlockPos(x, ny, z);
            // 当关闭游泳且遇到深水时（当前格是水但不可站立），不可继续向下穿透深水
            if (!allowSwimming && fluid(level, x, ny, z)) return null;
        }
        return null;
    }

    private static BlockPos hop(Level level, int x, int y, int z, int dx, int dz) {
        if (!allowJumping) return null;
        BlockPos best = null;
        int bestStep = 0;
        double bestScore = Double.MAX_VALUE;

        for (int step = 1; step <= MAX_HOP + MAX_FALL; step++) {
            int columnX = x + dx * step;
            int columnZ = z + dz * step;
            if (!clearCorner(level, columnX - dx, y, columnZ - dz, dx, dz)) break;
            // 跳跃腾空轨迹不可穿过水体
            if (fluid(level, columnX, y, columnZ) || fluid(level, columnX, y + 1, columnZ)) break;
            // 关闭游泳时，禁止在深水上方进行超远跨跳（防止路线斜穿水池内部）
            if (!allowSwimming && fluid(level, columnX, y - 1, columnZ)) break;

            BlockPos landing = landing(level, columnX, y, columnZ);
            if (landing != null) {
                // 关闭游泳时，禁止通过跳跃落入水中
                if (!allowSwimming && fluid(level, landing)) {
                    if (!free(level, columnX, y, columnZ)) break;
                    continue;
                }
                int vertical = landing.getY() - y;
                if (vertical <= MAX_JUMP_UP && step <= MAX_HOP - vertical) {
                    double score = Math.abs(vertical) * 2.0 + step;
                    if (score < bestScore) {
                        bestScore = score;
                        best = landing;
                        bestStep = step;
                    }
                }
            }
            if (!free(level, columnX, y, columnZ)) break;
        }
        if (best == null) return null;
        if (dx != 0 && dz != 0
                && (!free(level, x + dx * bestStep, y, z) || !free(level, x, y, z + dz * bestStep))) {
            return null;
        }
        return best;
    }

    private static BlockPos landing(Level level, int x, int y, int z) {
        if (standable(level, x, y, z)) return new BlockPos(x, y, z);
        if (allowJumping && standable(level, x, y + 1, z)) return new BlockPos(x, y + 1, z);
        return descendTo(level, x, y, z, MAX_FALL);
    }

    private static boolean standable(Level level, BlockPos pos) {
        return standable(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean standable(Level level, int x, int y, int z) {
        if (!free(level, x, y, z)) return false;
        if (fluid(level, x, y, z)) {
            return allowSwimming || (blocked(level, x, y - 1, z) && !fluid(level, x, y + 1, z));
        }
        return blocked(level, x, y - 1, z);
    }

    private static boolean grounded(Level level, BlockPos pos) {
        return grounded(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean grounded(Level level, int x, int y, int z) {
        return free(level, x, y, z) && blocked(level, x, y - 1, z);
    }

    private static boolean climbNode(Level level, int x, int y, int z) {
        return free(level, x, y, z) && climbable(level, x, y, z);
    }

    private static boolean free(Level level, BlockPos pos) {
        return free(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean free(Level level, int x, int y, int z) {
        if (!level.hasChunkAt(SCRATCH.set(x, y, z))) return false;
        return !blocked(level, x, y, z) && !blocked(level, x, y + 1, z);
    }

    private static boolean walkable(Level level, int x, int y, int z) {
        return standable(level, x, y, z) || (allowClimbing && climbNode(level, x, y, z));
    }

    private static boolean climbable(Level level, BlockPos pos) {
        return climbable(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean climbable(Level level, int x, int y, int z) {
        return stateAt(level, x, y, z).is(BlockTags.CLIMBABLE);
    }

    private static boolean blocked(Level level, BlockPos pos) {
        return blocked(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean blocked(Level level, int x, int y, int z) {
        BlockState state = stateAt(level, x, y, z);
        if (state.getBlock() instanceof DoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return !state.getValue(FenceGateBlock.OPEN);
        return state.blocksMotion();
    }

    private static boolean fluid(Level level, BlockPos pos) {
        return fluid(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean fluid(Level level, int x, int y, int z) {
        return !stateAt(level, x, y, z).getFluidState().isEmpty();
    }

    private static float stepCost(Level level, int fromX, int fromY, int fromZ, BlockPos to, boolean diagonal) {
        float cost = diagonal ? (float) DIAGONAL : 1.0f;
        if (to.getY() > fromY) cost += STEP_UP_COST;
        if (to.getY() < fromY) cost += FALL_PENALTY + FALL_COST * (fromY - to.getY());
        if (fluid(level, to)) cost += allowSwimming ? WATER_COST : WADE_PENALTY;
        return cost;
    }

    private record Traced(List<Vec3> points, boolean[] jump) {
    }

    private static Traced trace(Node end) {
        List<Node> chain = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) chain.add(node);
        int size = chain.size();
        List<Vec3> points = new ArrayList<>(size);
        boolean[] jump = new boolean[size];
        for (int index = 0; index < size; index++) {
            Node node = chain.get(size - 1 - index);
            points.add(center(node.pos()));
            jump[index] = node.teleport;
        }
        return new Traced(points, jump);
    }

    private static BlockState stateAt(Level level, BlockPos pos) {
        return stateAt(level, pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockState stateAt(Level level, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        BlockState cached = STATES.get(key);
        if (cached != null) return cached;
        if (STATES.size() >= MAX_STATES) return level.getBlockState(SCRATCH.set(x, y, z));
        BlockState state = level.getBlockState(SCRATCH.set(x, y, z));
        STATES.put(key, state);
        return state;
    }

    private static void report(String reason) {
        if (reason.equals(reportedFailure)) return;
        reportedFailure = reason;
        LOGGER.warn("[Navigator] path search failed: {}", reason);
    }

    private static boolean jumpAt(boolean[] jump, int index) {
        return index >= 0 && index < jump.length && jump[index];
    }

    private static List<Vec3> smooth(List<Vec3> points, boolean[] jump) {
        if (points.size() < 2) return points;
        List<Vec3> result = new ArrayList<>(points.size() * 2);
        result.add(points.getFirst());
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 p1 = points.get(index);
            Vec3 p2 = points.get(index + 1);
            if (jumpAt(jump, index) || jumpAt(jump, index + 1) || jumpAt(jump, index + 2)) {
                result.add(p2);
                continue;
            }
            Vec3 p0 = points.get(Math.max(0, index - 1));
            Vec3 p3 = points.get(Math.min(points.size() - 1, index + 2));
            int steps = Math.max(1, (int) (p1.distanceTo(p2) / SMOOTHING_STEP));
            for (int step = 1; step <= steps; step++) {
                result.add(catmullRom(p0, p1, p2, p3, (double) step / steps));
            }
        }
        return result;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(
                axis(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
                axis(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                axis(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double axis(double p0, double p1, double p2, double p3, double t, double t2, double t3) {
        return (p1 * 2.0
                + (p2 - p0) * t
                + (p0 * 2.0 - p1 * 5.0 + p2 * 4.0 - p3) * t2
                + (p1 * 3.0 - p0 - p2 * 3.0 + p3) * t3) * 0.5;
    }

    private static final class Node {
        private final int x;
        private final int y;
        private final int z;
        private final long key;
        private final float h;
        private float g = Float.MAX_VALUE;
        private float f;
        private int heapIndex = -1;
        private Node parent;
        private boolean teleport;
        private BlockPos pos;

        private Node(int x, int y, int z, float h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = keyOf(x, y, z);
            this.h = h;
        }

        private BlockPos pos() {
            if (pos == null) pos = new BlockPos(x, y, z);
            return pos;
        }
    }

    private static final class Search {
        private final Level level;
        private final BlockPos start;
        private final BlockPos goal;
        private final boolean goalWalkable;
        private final int baseY;
        private final float hStart;
        private final float weight = heuristicWeight;
        private final long startedAt = System.currentTimeMillis();
        private final Long2ObjectOpenHashMap<Node[]> columns = new Long2ObjectOpenHashMap<>(1 << 12);
        private final Node[] best = new Node[COEFFICIENTS.length];
        private Node[] heap = new Node[1_024];
        private int heapSize;
        private int visited;
        private int considered;
        private final boolean favorEmpty = FAVOR.isEmpty();
        private int teleportBudget = TELEPORT_BUDGET;
        private boolean truncated;
        private long elapsed;

        private Search(Level level, BlockPos start, BlockPos goal) {
            this.level = level;
            this.start = start;
            this.goal = goal;
            this.goalWalkable = walkable(level, goal.getX(), goal.getY(), goal.getZ());
            this.baseY = start.getY() - Y_RADIUS;
            this.hStart = Math.max(distance(start.getX(), start.getY(), start.getZ()), 1.0f);
        }

        private Node run() {
            Node first = node(start.getX(), start.getY(), start.getZ());
            if (first == null) return null;
            first.g = 0.0f;
            first.f = first.h * weight;
            push(first);

            int goalX = goal.getX();
            int goalY = goal.getY();
            int goalZ = goal.getZ();

            Node reached = null;
            while (heapSize > 0 && visited < MAX_NODES) {
                Node current = pop();
                visited++;
                if (current.x == goalX && current.y == goalY && current.z == goalZ) {
                    reached = current;
                    break;
                }
                if (!goalWalkable && current.h * current.h <= GOAL_RADIUS_SQ
                        && canReachStep(level, current.x, current.y, current.z, goalX, goalY, goalZ)) {
                    reached = current;
                    break;
                }
                for (int index = 0; index < COEFFICIENTS.length; index++) {
                    if (current.g + current.h > COEFFICIENTS[index] * hStart) continue;
                    if (best[index] == null || current.h < best[index].h) best[index] = current;
                }
                expand(current);
            }
            truncated = reached == null && heapSize > 0;
            elapsed = System.currentTimeMillis() - startedAt;
            return reached;
        }

        private void expand(Node current) {
            BlockPos here = current.pos();
            boolean inFluid = fluid(level, current.x, current.y, current.z);

            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0];
                int z = current.z + direction[1];
                if (Math.abs(x - start.getX()) > MAX_RADIUS || Math.abs(z - start.getZ()) > MAX_RADIUS) continue;
                boolean diagonal = direction[0] != 0 && direction[1] != 0;

                BlockPos walk = walkNeighbour(level, current.x, current.y, current.z, direction[0], direction[1]);
                if (walk != null) {
                    relax(current, walk, stepCost(level, current.x, current.y, current.z, walk, diagonal));
                } else if (allowJumping && !inFluid) {
                    BlockPos jump = hop(level, current.x, current.y, current.z, direction[0], direction[1]);
                    if (jump != null) {
                        float jumpCost = (float) Math.sqrt(here.distSqr(jump));
                        if (fluid(level, jump)) jumpCost += allowSwimming ? WATER_COST : WADE_PENALTY;
                        // 第四个参数传 true，跳跃段保持直线，不被 Catmull-Rom 曲线向下弯曲
                        relax(current, jump, jumpCost, true);
                    }
                }

                BlockPos drop = fallNeighbour(level, current.x, current.y, current.z, direction[0], direction[1]);
                if (drop != null) {
                    relax(current, drop, stepCost(level, current.x, current.y, current.z, drop, diagonal));
                }
            }

            if (allowClimbing && climbable(level, current.x, current.y, current.z)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    int ny = current.y + dy;
                    if (!(dy > 0 ? free(level, current.x, ny, current.z) : walkable(level, current.x, ny, current.z))) continue;
                    relax(current, here.above(dy), CLIMB_COST);
                }
            }

            if (allowSwimming && inFluid) {
                if (free(level, current.x, current.y + 1, current.z) && fluid(level, current.x, current.y + 1, current.z)) {
                    relax(current, here.above(), 1.0f + WATER_COST);
                }
                if (walkable(level, current.x, current.y - 1, current.z)) {
                    relax(current, here.below(), 1.0f + WATER_COST);
                }
            }

            if (allowTeleport && teleportBudget > 0 && grounded(level, current.x, current.y, current.z)) {
                teleportBudget--;
                teleportEdges(current);
            }
        }

        private void teleportEdges(Node current) {
            BlockPos here = current.pos();
            RaycastUtil.CellPredicate loaded = (x, y, z) -> level.hasChunkAt(SCRATCH.set(x, y, z));
            RaycastUtil.CellPredicate passable = (x, y, z) -> free(level, x, y, z);
            RaycastUtil.CellPredicate target = (x, y, z) -> grounded(level, x, y, z);
            for (int[][] ray : RAY_STEPS) {
                BlockPos cell = RaycastUtil.firstTarget(here, ray, loaded, passable, target);
                if (cell != null) {
                    relax(current, cell, (float) (Math.sqrt(here.distSqr(cell)) * TELEPORT_FACTOR + TELEPORT_PENALTY), true);
                }
            }
        }

        private void relax(Node current, BlockPos next, float step) {
            relax(current, next, step, false);
        }

        private void relax(Node current, BlockPos next, float step, boolean teleport) {
            considered++;
            int x = next.getX();
            int z = next.getZ();
            if (Math.abs(x - start.getX()) > MAX_RADIUS || Math.abs(z - start.getZ()) > MAX_RADIUS) return;
            Node node = node(x, next.getY(), z);
            if (node == null) return;
            float tentative = current.g + step * (1.0f - (favorEmpty ? 0.0f : FAVOR.get(node.key)) * FAVOR_STRENGTH);
            if (tentative >= node.g) return;
            node.g = tentative;
            node.f = tentative + node.h * weight;
            node.parent = current;
            node.teleport = teleport;
            push(node);
        }

        private Node node(int x, int y, int z) {
            int slot = y - baseY;
            if (slot < 0 || slot >= WINDOW) return null;
            long column = ((long) x << 32) | (z & 0xFFFFFFFFL);
            Node[] cells = columns.get(column);
            if (cells == null) {
                cells = new Node[WINDOW];
                columns.put(column, cells);
            }
            Node node = cells[slot];
            if (node == null) {
                node = new Node(x, y, z, distance(x, y, z));
                cells[slot] = node;
            }
            return node;
        }

        private float distance(int x, int y, int z) {
            double dx = x - goal.getX();
            double dy = y - goal.getY();
            double dz = z - goal.getZ();
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private Node bestEffort() {
            Node fallback = null;
            for (Node candidate : best) {
                if (candidate == null) continue;
                if (candidate.h <= hStart - MIN_PROGRESS) return candidate;
                if (fallback == null || candidate.h < fallback.h) fallback = candidate;
            }
            return fallback == null ? node(start.getX(), start.getY(), start.getZ()) : fallback;
        }

        private void favor(Node end) {
            for (Node node = end; node != null && node.parent != null; node = node.parent) {
                FAVOR.put(node.key, 1.0f);
            }
            if (FAVOR.size() > FAVOR_LIMIT) FAVOR.clear();
        }

        private void push(Node node) {
            if (node.heapIndex >= 0) {
                siftUp(node);
                return;
            }
            if (heapSize == heap.length) heap = Arrays.copyOf(heap, heapSize << 1);
            node.heapIndex = heapSize;
            heap[heapSize++] = node;
            siftUp(node);
        }

        private Node pop() {
            Node top = heap[0];
            Node last = heap[--heapSize];
            heap[heapSize] = null;
            top.heapIndex = -1;
            if (heapSize > 0) {
                heap[0] = last;
                last.heapIndex = 0;
                siftDown(last);
            }
            return top;
        }

        private void siftUp(Node node) {
            int index = node.heapIndex;
            while (index > 0) {
                int parentIndex = (index - 1) >> 1;
                Node parent = heap[parentIndex];
                if (parent.f <= node.f) break;
                heap[index] = parent;
                parent.heapIndex = index;
                index = parentIndex;
            }
            heap[index] = node;
            node.heapIndex = index;
        }

        private void siftDown(Node node) {
            int index = node.heapIndex;
            while (true) {
                int child = (index << 1) + 1;
                if (child >= heapSize) break;
                int right = child + 1;
                if (right < heapSize && heap[right].f < heap[child].f) child = right;
                Node smaller = heap[child];
                if (node.f <= smaller.f) break;
                heap[index] = smaller;
                smaller.heapIndex = index;
                index = child;
            }
            heap[index] = node;
            node.heapIndex = index;
        }
    }

    private static long keyOf(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}