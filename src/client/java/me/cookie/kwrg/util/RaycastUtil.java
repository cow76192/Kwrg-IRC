package me.cookie.kwrg.util;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class RaycastUtil {

    @FunctionalInterface
    public interface CellPredicate {
        boolean test(int x, int y, int z);
    }

    private RaycastUtil() {
    }

    public static int[][][] buildRaySteps(int directionRadius, int range) {
        if (directionRadius < 1 || range < 1) throw new IllegalArgumentException("Radius and range must be positive");

        List<int[][]> rays = new ArrayList<>();

        for (int dx = -directionRadius; dx <= directionRadius; dx++) {
            for (int dy = -directionRadius; dy <= directionRadius; dy++) {
                for (int dz = -directionRadius; dz <= directionRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (greatestCommonDivisor(Math.abs(dx), greatestCommonDivisor(Math.abs(dy), Math.abs(dz))) != 1) continue;

                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    List<int[]> steps = new ArrayList<>(range);

                    int lastX = 0;
                    int lastY = 0;
                    int lastZ = 0;

                    for (int step = 1; step <= range; step++) {
                        double ratio = step / length;

                        int x = (int) Math.round(dx * ratio);
                        int y = (int) Math.round(dy * ratio);
                        int z = (int) Math.round(dz * ratio);
                        if (x == lastX && y == lastY && z == lastZ) continue;

                        steps.add(new int[]{x, y, z});
                        lastX = x;
                        lastY = y;
                        lastZ = z;
                    }

                    rays.add(steps.toArray(new int[0][]));
                }
            }
        }
        return rays.toArray(new int[0][][]);
    }

    public static boolean isLineClear(BlockPos from, BlockPos to, CellPredicate passable, CellPredicate cornerPassable) {
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
                if (!isCornerClear(lastX, lastY, lastZ, x - lastX, z - lastZ, cornerPassable)) return false;
                lastX = x;
                lastZ = z;
            }

            lastY = y;

            if (!passable.test(x, y, z)) return false;
        }
        return true;
    }

    public static boolean isCornerClear(int x, int y, int z, int dx, int dz, CellPredicate passable) {
        if (dx == 0 || dz == 0) return true;
        return passable.test(x + dx, y, z) && passable.test(x, y, z + dz);
    }

    public static BlockPos firstTarget(BlockPos origin, int[][] offsets, CellPredicate loaded, CellPredicate passable, CellPredicate target) {
        BlockPos previous = origin;

        for (int[] offset : offsets) {
            BlockPos cell = origin.offset(offset[0], offset[1], offset[2]);
            int x = cell.getX();
            int y = cell.getY();
            int z = cell.getZ();

            if (!loaded.test(x, y, z)) {
                previous = cell;
                continue;
            }

            if (!passable.test(x, y, z)) break;

            if (!isCornerClear(previous.getX(), previous.getY(), previous.getZ(), x - previous.getX(), z - previous.getZ(), passable)) break;

            if (target.test(x, y, z)) return cell;
            previous = cell;
        }
        return null;
    }

    private static int greatestCommonDivisor(int a, int b) {
        return b == 0 ? a : greatestCommonDivisor(b, a % b);
    }
}
