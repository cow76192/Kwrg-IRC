package me.cookie.kwrg.util;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class Navigator {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-navigator");
    private static final int MAX_NODES = 20_000;
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

    public static void searchAsync(Level level, BlockPos from, BlockPos to, Consumer<List<Vec3>> callback) {
        EXECUTOR.submit(() -> {
            List<Vec3> result;
            try {
                result = search(level, from, to);
            } catch (RuntimeException e) {
                report(e.getClass().getSimpleName() + ": " + e.getMessage());
                result = List.of();
            }
            List<Vec3> found = result;
            Minecraft.getInstance().execute(() -> callback.accept(found));
        });
    }

    private static List<Vec3> search(Level level, BlockPos from, BlockPos to) {
        if (!level.hasChunkAt(from) || !level.hasChunkAt(to)) return List.of();

        BlockPos start = surface(level, from);
        if (start == null) {
            report("no-start");
            return List.of();
        }
        BlockPos goal = snapTarget(level, to);

        Map<Long, Float> scores = new HashMap<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));

        scores.put(start.asLong(), 0.0f);
        open.add(new Entry(start, 0.0f, heuristic(start, goal)));

        int visited = 0;
        int teleportBudget = TELEPORT_BUDGET;
        BlockPos reached = null;
        BlockPos best = start;
        double bestDistance = start.distSqr(goal);

        while (!open.isEmpty() && visited < MAX_NODES) {
            Entry current = open.poll();
            Float known = scores.get(current.pos.asLong());
            if (known == null || current.g > known) continue;
            visited++;

            if (current.pos.distSqr(goal) <= GOAL_RADIUS_SQ) {
                reached = current.pos;
                break;
            }
            double distance = current.pos.distSqr(goal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = current.pos;
            }

            boolean progressed = false;

            for (int[] direction : DIRECTIONS) {
                boolean diagonal = direction[0] != 0 && direction[1] != 0;

                BlockPos walk = walkNeighbour(level, current.pos, direction[0], direction[1]);
                if (walk != null) {
                    relax(scores, parents, open, current.pos, current.g, walk, stepCost(level, current.pos, walk, diagonal), goal, start);
                    progressed = true;
                } else {
                    BlockPos jump = hop(level, current.pos, direction[0], direction[1]);
                    if (jump != null) {
                        relax(scores, parents, open, current.pos, current.g, jump,
                                (float) Math.sqrt(current.pos.distSqr(jump)) + HOP_COST, goal, start);
                        progressed = true;
                    }
                }

                BlockPos drop = fallNeighbour(level, current.pos, direction[0], direction[1]);
                if (drop != null) {
                    relax(scores, parents, open, current.pos, current.g, drop, stepCost(level, current.pos, drop, diagonal), goal, start);
                    progressed = true;
                }
            }

            if (climbable(level, current.pos)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    BlockPos candidate = current.pos.above(dy);
                    if (!(dy > 0 ? free(level, candidate) : walkable(level, candidate))) continue;
                    relax(scores, parents, open, current.pos, current.g, candidate, CLIMB_COST, goal, start);
                    progressed = true;
                }
            }

            if (!progressed && teleportBudget > 0) {
                teleportBudget--;
                teleportEdges(level, current.pos, current.g, goal, start, scores, parents, open);
            }
        }

        if (reached == null) {
            report("no-path " + from.toShortString() + " -> " + to.toShortString()
                    + " feet=" + blocked(level, to)
                    + " head=" + blocked(level, to.above())
                    + " ground=" + blocked(level, to.below())
                    + " dy=" + (to.getY() - start.getY())
                    + " snapped=" + goal.toShortString()
                    + " best=" + best.toShortString());
            return finish(smooth(trace(best, parents)), goal);
        }
        return finish(smooth(trace(reached, parents)), goal);
    }

    private static List<Vec3> finish(List<Vec3> points, BlockPos goal) {
        Vec3 target = new Vec3(goal.getX() + 0.5, goal.getY() + 0.05, goal.getZ() + 0.5);
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(target) > 1.0) {
            points.add(target);
        }
        return points;
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
            if (walkable(level, candidate)) return candidate;
        }
        return null;
    }

    private static void relax(Map<Long, Float> scores, Map<Long, BlockPos> parents, PriorityQueue<Entry> open,
                              BlockPos current, float currentG, BlockPos next, float step, BlockPos goal, BlockPos start) {
        if (Math.abs(next.getX() - start.getX()) > MAX_RADIUS) return;
        if (Math.abs(next.getZ() - start.getZ()) > MAX_RADIUS) return;
        if (Math.abs(next.getY() - start.getY()) > 32) return;

        float tentative = currentG + step;

        long key = next.asLong();
        Float previous = scores.get(key);
        if (previous != null && tentative >= previous) return;
        scores.put(key, tentative);
        parents.put(key, current);
        open.add(new Entry(next, tentative, tentative + heuristic(next, goal)));
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

    private static void teleportEdges(Level level, BlockPos from, float g, BlockPos goal, BlockPos start,
                                      Map<Long, Float> scores, Map<Long, BlockPos> parents, PriorityQueue<Entry> open) {
        for (int[] direction : RAY_DIRECTIONS) {
            for (int step = 1; step <= TELEPORT_RANGE; step++) {
                BlockPos cell = from.offset(direction[0] * step, direction[1] * step, direction[2] * step);
                if (blocked(level, cell)) break;
                if (standable(level, cell)) {
                    relax(scores, parents, open, from, g, cell,
                            (float) Math.sqrt(from.distSqr(cell)) + TELEPORT_COST, goal, start);
                    break;
                }
            }
        }
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
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    private static boolean blocked(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return !state.getValue(FenceGateBlock.OPEN);
        return state.blocksMotion();
    }

    private static boolean fluid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getFluidState().isEmpty();
    }

    private static float stepCost(Level level, BlockPos from, BlockPos to, boolean diagonal) {
        float cost = diagonal ? (float) DIAGONAL : 1.0f;
        if (to.getY() > from.getY()) cost += STEP_UP_COST;
        if (to.getY() < from.getY()) cost += FALL_PENALTY + FALL_COST * (from.getY() - to.getY());
        if (fluid(level, to)) cost += WATER_COST;
        return cost;
    }

    private static float heuristic(BlockPos from, BlockPos to) {
        return (float) Math.sqrt(from.distSqr(to));
    }

    private static List<Vec3> trace(BlockPos reached, Map<Long, BlockPos> parents) {
        List<BlockPos> nodes = new ArrayList<>();
        BlockPos current = reached;
        while (current != null) {
            nodes.add(current);
            current = parents.get(current.asLong());
        }
        List<Vec3> points = new ArrayList<>(nodes.size());
        for (int index = nodes.size() - 1; index >= 0; index--) {
            BlockPos node = nodes.get(index);
            points.add(new Vec3(node.getX() + 0.5, node.getY() + 0.05, node.getZ() + 0.5));
        }
        return points;
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

    private record Entry(BlockPos pos, float g, float f) {
    }
}
