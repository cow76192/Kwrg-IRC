package me.cookie.kwrg.navigator;

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    static final int MAX_FALL = 8;
    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final float STEP_UP_COST = 0.4f;
    private static final float FALL_COST = 0.8f;
    private static final float WATER_COST = 0.6f;
    private static final float CLIMB_COST = 1.0f;
    private static final float FALL_PENALTY = 0.5f;
    static final int MAX_HOP = 8;
    static final int MAX_JUMP_UP = 1;
    private static final int TELEPORT_RANGE = 61;
    private static final float TELEPORT_PENALTY = 12.0f;
    private static final float TELEPORT_FACTOR = 1.6f;
    private static final int TELEPORT_BUDGET = 128;
    private static final float GOAL_RADIUS_SQ = 6.0f;
    private static final float MIN_HEURISTIC_WEIGHT = 0.8f;
    private static final float MAX_HEURISTIC_WEIGHT = 2.2f;

    private static volatile float heuristicWeight = 1.0f;

    public static void setHeuristicWeight(float weight) {
        if (!Float.isFinite(weight)) return;
        heuristicWeight = Math.min(MAX_HEURISTIC_WEIGHT, Math.max(MIN_HEURISTIC_WEIGHT, weight));
    }
    private static final double SMOOTHING_STEP = 0.5;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final int[][] RAY_DIRECTIONS = NavigatorMath.buildRayDirections();
    private static final int[][][] RAY_STEPS = buildRaySteps();

    private static int[][][] buildRaySteps() {
        int[][][] rays = new int[RAY_DIRECTIONS.length][][];
        for (int index = 0; index < RAY_DIRECTIONS.length; index++) {
            int[] direction = RAY_DIRECTIONS[index];
            double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
            List<int[]> steps = new ArrayList<>(TELEPORT_RANGE);
            int lastX = 0;
            int lastY = 0;
            int lastZ = 0;
            for (int step = 1; step <= TELEPORT_RANGE; step++) {
                double ratio = step / length;
                int x = (int) Math.round(direction[0] * ratio);
                int y = (int) Math.round(direction[1] * ratio);
                int z = (int) Math.round(direction[2] * ratio);
                if (x == lastX && y == lastY && z == lastZ) continue;
                steps.add(new int[]{x, y, z});
                lastX = x;
                lastY = y;
                lastZ = z;
            }
            rays[index] = steps.toArray(new int[0][]);
        }
        return rays;
    }

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

        BlockPos start = NavigatorBlocks.surface(level, from);
        if (start == null) {
            report("no-start");
            return new Result(List.of(), false, true);
        }
        BlockPos goal = NavigatorBlocks.snapTarget(level, to);
        if (Math.abs(goal.getX() - start.getX()) > MAX_RADIUS || Math.abs(goal.getZ() - start.getZ()) > MAX_RADIUS) {
            report("out-of-radius " + goal.toShortString());
            return new Result(List.of(), false, true);
        }

        boolean direct = (NavigatorBlocks.standable(level, start) && NavigatorBlocks.standable(level, goal) && clearWalkLine(level, start, goal))
                || (NavigatorBlocks.grounded(level, start) && NavigatorBlocks.grounded(level, goal)
                && start.distSqr(goal) <= (double) TELEPORT_RANGE * TELEPORT_RANGE
                && teleportLine(level, start, goal));
        if (direct) return new Result(List.of(NavigatorMath.center(start), NavigatorMath.center(goal)), true, false);

        Search search = new Search(level, start, goal);
        Node reached = search.run();

        if (reached == null) {
            Node best = search.bestEffort();
            report("no-path " + from.toShortString() + " -> " + to.toShortString()
                    + " feet=" + NavigatorBlocks.blocked(level, to)
                    + " head=" + NavigatorBlocks.blocked(level, to.above())
                    + " ground=" + NavigatorBlocks.blocked(level, to.below())
                    + " dy=" + (to.getY() - start.getY())
                    + " snapped=" + goal.toShortString()
                    + " best=" + best.pos().toShortString()
                    + " nodes=" + search.visited
                    + " edges=" + search.considered
                    + " ms=" + search.elapsed);
            Traced traced = trace(best);
            return new Result(finish(level, smooth(traced.points(), traced.jump()), goal, false), false, search.truncated);
        }
        search.favor(reached);
        Traced traced = trace(reached);
        List<Vec3> points = finish(level, smooth(traced.points(), traced.jump()), goal, true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Navigator] nodes={} edges={} ms={} points={}", search.visited, search.considered, search.elapsed, points.size());
        }
        return new Result(points, true, false);
    }

    private static List<Vec3> finish(Level level, List<Vec3> points, BlockPos goal, boolean complete) {
        if (points.isEmpty()) return points;
        Vec3 target = NavigatorMath.center(goal);
        Vec3 last = points.get(points.size() - 1);
        if (last.distanceToSqr(target) <= 1.0) return points;
        if (complete || NavigatorBlocks.clearLine(level, BlockPos.containing(last.x, last.y, last.z), goal)) points.add(target);
        return points;
    }

    private static boolean sampleLine(Level level, BlockPos from, BlockPos to, Predicate<BlockPos> passable) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps == 0) return true;
        int samples = steps * 4;
        int lastX = from.getX();
        int lastY = from.getY();
        int lastZ = from.getZ();
        for (int index = 1; index < samples; index++) {
            double ratio = (double) index / samples;
            int x = (int) Math.floor(from.getX() + dx * ratio);
            int y = (int) Math.floor(from.getY() + dy * ratio);
            int z = (int) Math.floor(from.getZ() + dz * ratio);
            if (x != lastX || z != lastZ) {
                if (!clearCorner(level, new BlockPos(lastX, lastY, lastZ), x - lastX, z - lastZ)) return false;
                lastX = x;
                lastZ = z;
            }
            lastY = y;
            if (!passable.test(new BlockPos(x, y, z))) return false;
        }
        return true;
    }

    private static boolean clearWalkLine(Level level, BlockPos from, BlockPos to) {
        return sampleLine(level, from, to, cell -> NavigatorBlocks.standable(level, cell));
    }

    private static boolean teleportLine(Level level, BlockPos from, BlockPos to) {
        return sampleLine(level, from, to, cell -> !NavigatorBlocks.blocked(level, cell) && !NavigatorBlocks.blocked(level, cell.above()));
    }

    static boolean clearCorner(Level level, BlockPos from, int dx, int dz) {
        return clearCorner(level, from.getX(), from.getY(), from.getZ(), dx, dz);
    }

    static boolean clearCorner(Level level, int x, int y, int z, int dx, int dz) {
        if (dx == 0 || dz == 0) return true;
        return free(level, x + dx, y, z) && free(level, x, y, z + dz);
    }

    static boolean climbNode(Level level, int x, int y, int z) {
        return free(level, x, y, z) && NavigatorBlocks.climbable(level, x, y, z);
    }

    static boolean free(Level level, BlockPos pos) {
        return free(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean free(Level level, int x, int y, int z) {
        if (!level.hasChunkAt(SCRATCH.set(x, y, z))) return false;
        return !NavigatorBlocks.blocked(level, x, y, z) && !NavigatorBlocks.blocked(level, x, y + 1, z);
    }

    private static float stepCost(Level level, int fromX, int fromY, int fromZ, BlockPos to, boolean diagonal) {
        float cost = diagonal ? (float) DIAGONAL : 1.0f;
        if (to.getY() > fromY) cost += STEP_UP_COST;
        if (to.getY() < fromY) cost += FALL_PENALTY + FALL_COST * (fromY - to.getY());
        if (NavigatorBlocks.fluid(level, to)) cost += WATER_COST;
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
            points.add(NavigatorMath.center(node.pos()));
            jump[index] = node.teleport;
        }
        return new Traced(points, jump);
    }

    static BlockState stateAt(Level level, BlockPos pos) {
        return stateAt(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockState stateAt(Level level, int x, int y, int z) {
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

    private static List<Vec3> smooth(List<Vec3> points, boolean[] jump) {
        if (points.size() < 2) return points;
        List<Vec3> result = new ArrayList<>(points.size() * 2);
        result.add(points.getFirst());
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 p1 = points.get(index);
            Vec3 p2 = points.get(index + 1);
            if (NavigatorMath.jumpAt(jump, index) || NavigatorMath.jumpAt(jump, index + 1) || NavigatorMath.jumpAt(jump, index + 2)) {
                result.add(p2);
                continue;
            }
            Vec3 p0 = points.get(Math.max(0, index - 1));
            Vec3 p3 = points.get(Math.min(points.size() - 1, index + 2));
            int steps = Math.max(1, (int) (p1.distanceTo(p2) / SMOOTHING_STEP));
            for (int step = 1; step <= steps; step++) {
                result.add(NavigatorMath.catmullRom(p0, p1, p2, p3, (double) step / steps));
            }
        }
        return result;
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
            this.key = BlockPos.asLong(x, y, z);
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
            this.baseY = start.getY() - Y_RADIUS;
            this.hStart = Math.max(distance(start.getX(), start.getY(), start.getZ()), 1.0f);
        }

        private Node run() {
            Node first = node(start.getX(), start.getY(), start.getZ());
            if (first == null) return null;
            first.g = 0.0f;
            first.f = first.h * weight;
            push(first);

            Node reached = null;
            while (heapSize > 0 && visited < MAX_NODES) {
                Node current = pop();
                visited++;
                if (current.h * current.h <= GOAL_RADIUS_SQ && NavigatorBlocks.clearLine(level, current.pos(), goal)) {
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

            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0];
                int z = current.z + direction[1];
                if (Math.abs(x - start.getX()) > MAX_RADIUS || Math.abs(z - start.getZ()) > MAX_RADIUS) continue;
                boolean diagonal = direction[0] != 0 && direction[1] != 0;

                BlockPos walk = NavigatorBlocks.walkNeighbour(level, current.x, current.y, current.z, direction[0], direction[1]);
                if (walk != null) {
                    relax(current, walk, stepCost(level, current.x, current.y, current.z, walk, diagonal));
                } else {
                    BlockPos jump = NavigatorBlocks.hop(level, current.x, current.y, current.z, direction[0], direction[1]);
                    if (jump != null) {
                        relax(current, jump, (float) Math.sqrt(here.distSqr(jump)));
                    }
                }

                BlockPos drop = NavigatorBlocks.fallNeighbour(level, current.x, current.y, current.z, direction[0], direction[1]);
                if (drop != null) {
                    relax(current, drop, stepCost(level, current.x, current.y, current.z, drop, diagonal));
                }
            }

            if (NavigatorBlocks.climbable(level, here)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    BlockPos candidate = here.above(dy);
                    if (!(dy > 0 ? free(level, current.x, current.y + dy, current.z) : NavigatorBlocks.walkable(level, current.x, current.y + dy, current.z))) continue;
                    relax(current, candidate, CLIMB_COST);
                }
            }

            if (teleportBudget > 0 && NavigatorBlocks.grounded(level, here)) {
                teleportBudget--;
                teleportEdges(current);
            }
        }

        private void teleportEdges(Node current) {
            BlockPos here = current.pos();
            for (int[][] ray : RAY_STEPS) {
                BlockPos previous = here;
                for (int[] offset : ray) {
                    BlockPos cell = here.offset(offset[0], offset[1], offset[2]);
                    if (!level.hasChunkAt(cell)) {
                        previous = cell;
                        continue;
                    }
                    if (!free(level, cell)) break;
                    if (!clearCorner(level, previous, cell.getX() - previous.getX(), cell.getZ() - previous.getZ())) break;
                    if (NavigatorBlocks.grounded(level, cell)) {
                        relax(current, cell, (float) (Math.sqrt(here.distSqr(cell)) * TELEPORT_FACTOR + TELEPORT_PENALTY), true);
                        break;
                    }
                    previous = cell;
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

}
