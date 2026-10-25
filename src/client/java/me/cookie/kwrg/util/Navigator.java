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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class Navigator {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-navigator");
    private static final int MAX_NODES = 20_000;
    private static final int MAX_STATES = 65_536;
    private static final Long2ObjectOpenHashMap<BlockState> STATES = new Long2ObjectOpenHashMap<>(4_096);
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
    private static final float CLIMB_COST = 1.0f;
    private static final float HOP_COST = 0.0f;
    private static final float FALL_PENALTY = 0.5f;
    private static final int MAX_HOP = 8;
    private static final int MAX_JUMP_UP = 1;
    private static final int TELEPORT_RANGE = 61;
    private static final float TELEPORT_COST = 2.0f;
    private static final int TELEPORT_BUDGET = 64;
    private static final float GOAL_RADIUS_SQ = 6.0f;
    private static final float MIN_HEURISTIC_WEIGHT = 0.8f;
    private static final float MAX_HEURISTIC_WEIGHT = 2.2f;

    private static float heuristicWeight = 1.0f;

    public static void setHeuristicWeight(float weight) {
        if (!Float.isFinite(weight)) return;
        heuristicWeight = Math.min(MAX_HEURISTIC_WEIGHT, Math.max(MIN_HEURISTIC_WEIGHT, weight));
    }
    private static final double SMOOTHING_STEP = 0.5;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final int[][] RAY_DIRECTIONS = buildRayDirections();

    private static int[][] buildRayDirections() {
        List<int[]> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    list.add(new int[]{dx, dy, dz});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "kwrg-navigator");
        thread.setDaemon(true);
        return thread;
    });

    private static String reportedFailure;

    private Navigator() {
    }

    public record Result(List<Vec3> points, boolean complete) {
    }

    public static void searchAsync(Level level, BlockPos from, BlockPos to, Consumer<Result> callback) {
        EXECUTOR.submit(() -> {
            Result result;
            try {
                result = attempt(level, from, to);
            } catch (Throwable failure) {
                report("unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                result = new Result(List.of(), false);
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
        return new Result(List.of(), false);
    }

    private static Result search(Level level, BlockPos from, BlockPos to) {
        STATES.clear();
        if (!level.hasChunkAt(from) || !level.hasChunkAt(to)) return new Result(List.of(), false);

        BlockPos start = surface(level, from);
        if (start == null) {
            report("no-start");
            return new Result(List.of(), false);
        }
        BlockPos goal = snapTarget(level, to);

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
            return new Result(finish(smooth(trace(best)), goal), false);
        }
        search.favor(reached);
        List<Vec3> points = finish(smooth(trace(reached)), goal);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Navigator] nodes={} edges={} ms={} points={}", search.visited, search.considered, search.elapsed, points.size());
        }
        return new Result(points, true);
    }

    private static List<Vec3> finish(List<Vec3> points, BlockPos goal) {
        Vec3 target = new Vec3(goal.getX() + 0.5, goal.getY() + 0.05, goal.getZ() + 0.5);
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(target) > 1.0) {
            points.add(target);
        }
        return points;
    }

    private static boolean clearLine(Level level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int step = 1; step < steps; step++) {
            double ratio = (double) step / steps;
            BlockPos cell = new BlockPos(
                    (int) Math.floor(from.getX() + dx * ratio),
                    (int) Math.floor(from.getY() + dy * ratio),
                    (int) Math.floor(from.getZ() + dz * ratio));
            if (!free(level, cell)) return false;
        }
        return true;
    }

    private static BlockPos snapTarget(Level level, BlockPos to) {
        if (standable(level, to)) return to;
        for (int radius = 1; radius <= 4; radius++) {
            for (int dy = 2; dy >= -4; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos candidate = to.offset(dx, dy, dz);
                        if (standable(level, candidate)) return candidate;
                    }
                }
            }
        }
        return to;
    }

    private static BlockPos surface(Level level, BlockPos from) {
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos candidate = from.above(dy);
            if (walkable(level, candidate)) return candidate;
        }
        return descendTo(level, from, MAX_FALL);
    }

    private static BlockPos walkNeighbour(Level level, BlockPos pos, int dx, int dz) {
        if (dx != 0 && dz != 0) {
            if (blocked(level, pos.offset(dx, 0, 0)) || blocked(level, pos.offset(0, 0, dz))) return null;
        }
        BlockPos column = pos.offset(dx, 0, dz);
        for (int dy = 1; dy >= 0; dy--) {
            BlockPos candidate = column.above(dy);
            if (walkable(level, candidate)) return candidate;
        }
        return null;
    }

    private static BlockPos fallNeighbour(Level level, BlockPos pos, int dx, int dz) {
        return descendTo(level, pos.offset(dx, 0, dz), MAX_FALL);
    }

    private static BlockPos descendTo(Level level, BlockPos column, int maxDown) {
        for (int dy = -1; dy >= -maxDown; dy--) {
            BlockPos candidate = column.above(dy);
            if (blocked(level, candidate) || blocked(level, candidate.above())) return null;
            if (walkable(level, candidate)) return candidate;
        }
        return null;
    }

    private static BlockPos hop(Level level, BlockPos from, int dx, int dz) {
        BlockPos best = null;
        int bestStep = 0;
        double bestScore = Double.MAX_VALUE;

        for (int step = 1; step <= MAX_HOP + MAX_FALL; step++) {
            BlockPos column = from.offset(dx * step, 0, dz * step);
            BlockPos landing = landing(level, column);
            if (landing != null) {
                int vertical = landing.getY() - from.getY();
                if (vertical <= MAX_JUMP_UP && step <= MAX_HOP - vertical) {
                    double score = Math.abs(vertical) * 2.0 + step;
                    if (score < bestScore) {
                        bestScore = score;
                        best = landing;
                        bestStep = step;
                    }
                }
            }
            if (!free(level, column)) break;
        }
        if (best == null) return null;
        if (dx != 0 && dz != 0
                && (!free(level, from.offset(dx * bestStep, 0, 0)) || !free(level, from.offset(0, 0, dz * bestStep)))) {
            return null;
        }
        return best;
    }

    private static BlockPos landing(Level level, BlockPos column) {
        if (standable(level, column)) return column;
        BlockPos above = column.above(1);
        if (free(level, above) && standable(level, above)) return above;
        for (int dy = -1; dy >= -MAX_FALL; dy--) {
            BlockPos candidate = column.above(dy);
            if (blocked(level, candidate) || blocked(level, candidate.above())) return null;
            if (standable(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean standable(Level level, BlockPos pos) {
        if (!free(level, pos)) return false;
        return blocked(level, pos.below()) || fluid(level, pos) || fluid(level, pos.below());
    }

    private static boolean climbNode(Level level, BlockPos pos) {
        return free(level, pos) && climbable(level, pos);
    }

    private static boolean free(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        return !blocked(level, pos) && !blocked(level, pos.above());
    }

    private static boolean walkable(Level level, BlockPos pos) {
        return standable(level, pos) || climbNode(level, pos);
    }

    private static boolean climbable(Level level, BlockPos pos) {
        return stateAt(level, pos).is(BlockTags.CLIMBABLE);
    }

    private static boolean blocked(Level level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state.getBlock() instanceof DoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return !state.getValue(FenceGateBlock.OPEN);
        return state.blocksMotion();
    }

    private static boolean fluid(Level level, BlockPos pos) {
        return !stateAt(level, pos).getFluidState().isEmpty();
    }

    private static float stepCost(Level level, BlockPos from, BlockPos to, boolean diagonal) {
        float cost = diagonal ? (float) DIAGONAL : 1.0f;
        if (to.getY() > from.getY()) cost += STEP_UP_COST;
        if (to.getY() < from.getY()) cost += FALL_PENALTY + FALL_COST * (from.getY() - to.getY());
        if (fluid(level, to)) cost += WATER_COST;
        return cost;
    }

    private static List<Vec3> trace(Node end) {
        List<Vec3> points = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            points.add(new Vec3(node.x + 0.5, node.y + 0.05, node.z + 0.5));
        }
        Collections.reverse(points);
        return points;
    }

    private static BlockState stateAt(Level level, BlockPos pos) {
        long key = pos.asLong();
        BlockState cached = STATES.get(key);
        if (cached != null) return cached;
        if (STATES.size() >= MAX_STATES) STATES.clear();
        BlockState state = level.getBlockState(pos);
        STATES.put(key, state);
        return state;
    }

    private static void report(String reason) {
        if (reason.equals(reportedFailure)) return;
        reportedFailure = reason;
        LOGGER.warn("[Navigator] path search failed: {}", reason);
    }

    private static List<Vec3> smooth(List<Vec3> points) {
        if (points.size() < 2) return points;
        List<Vec3> result = new ArrayList<>(points.size() * 2);
        result.add(points.getFirst());
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 p0 = points.get(Math.max(0, index - 1));
            Vec3 p1 = points.get(index);
            Vec3 p2 = points.get(index + 1);
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
        private int teleportBudget = TELEPORT_BUDGET;
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
                if (current.h * current.h <= GOAL_RADIUS_SQ && clearLine(level, current.pos(), goal)) {
                    reached = current;
                    break;
                }
                for (int index = 0; index < COEFFICIENTS.length; index++) {
                    if (current.g + current.h > COEFFICIENTS[index] * hStart) continue;
                    if (best[index] == null || current.h < best[index].h) best[index] = current;
                }
                expand(current);
            }
            elapsed = System.currentTimeMillis() - startedAt;
            return reached;
        }

        private void expand(Node current) {
            BlockPos here = current.pos();
            boolean progressed = false;

            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0];
                int z = current.z + direction[1];
                if (Math.abs(x - start.getX()) > MAX_RADIUS || Math.abs(z - start.getZ()) > MAX_RADIUS) continue;
                boolean diagonal = direction[0] != 0 && direction[1] != 0;

                BlockPos walk = walkNeighbour(level, here, direction[0], direction[1]);
                if (walk != null) {
                    relax(current, walk, stepCost(level, here, walk, diagonal));
                    progressed = true;
                } else {
                    BlockPos jump = hop(level, here, direction[0], direction[1]);
                    if (jump != null) {
                        relax(current, jump, (float) Math.sqrt(here.distSqr(jump)) + HOP_COST);
                        progressed = true;
                    }
                }

                BlockPos drop = fallNeighbour(level, here, direction[0], direction[1]);
                if (drop != null) {
                    relax(current, drop, stepCost(level, here, drop, diagonal));
                    progressed = true;
                }
            }

            if (climbable(level, here)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    BlockPos candidate = here.above(dy);
                    if (!(dy > 0 ? free(level, candidate) : walkable(level, candidate))) continue;
                    relax(current, candidate, CLIMB_COST);
                    progressed = true;
                }
            }

            if (!progressed && teleportBudget > 0) {
                teleportBudget--;
                teleportEdges(current);
            }
        }

        private void teleportEdges(Node current) {
            BlockPos here = current.pos();
            for (int[] direction : RAY_DIRECTIONS) {
                for (int step = 1; step <= TELEPORT_RANGE; step++) {
                    BlockPos cell = here.offset(direction[0] * step, direction[1] * step, direction[2] * step);
                    if (!level.hasChunkAt(cell)) continue;
                    if (blocked(level, cell)) break;
                    if (standable(level, cell)) {
                        relax(current, cell, (float) Math.sqrt(here.distSqr(cell)) + TELEPORT_COST);
                        break;
                    }
                }
            }
        }

        private void relax(Node current, BlockPos next, float step) {
            considered++;
            int x = next.getX();
            int z = next.getZ();
            if (Math.abs(x - start.getX()) > MAX_RADIUS || Math.abs(z - start.getZ()) > MAX_RADIUS) return;
            Node node = node(x, next.getY(), z);
            if (node == null) return;
            float tentative = current.g + step * (1.0f - FAVOR.get(node.key) * FAVOR_STRENGTH);
            if (tentative >= node.g) return;
            node.g = tentative;
            node.f = tentative + node.h * weight;
            node.parent = current;
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
