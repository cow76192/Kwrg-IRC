package me.cookie.kwrg.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class NavigatorMath {

    private NavigatorMath() {
    }

    static int[][] buildRayDirections() {
        List<int[]> list = new ArrayList<>(98);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (greatestCommonDivisor(Math.abs(dx), greatestCommonDivisor(Math.abs(dy), Math.abs(dz))) != 1) continue;
                    list.add(new int[]{dx, dy, dz});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    static int greatestCommonDivisor(int a, int b) {
        return b == 0 ? a : greatestCommonDivisor(b, a % b);
    }

    static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5);
    }

    static boolean jumpAt(boolean[] jump, int index) {
        return index >= 0 && index < jump.length && jump[index];
    }

    static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(
                axis(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
                axis(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                axis(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    static double axis(double p0, double p1, double p2, double p3, double t, double t2, double t3) {
        return (p1 * 2.0
                + (p2 - p0) * t
                + (p0 * 2.0 - p1 * 5.0 + p2 * 4.0 - p3) * t2
                + (p1 * 3.0 - p0 - p2 * 3.0 + p3) * t3) * 0.5;
    }
}
