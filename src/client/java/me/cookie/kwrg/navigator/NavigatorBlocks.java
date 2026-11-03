package me.cookie.kwrg.navigator;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

final class NavigatorBlocks {

    private NavigatorBlocks() {
    }

    static boolean clearLine(Level level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 1) return Navigator.clearCorner(level, from, dx, dz);
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
                if (!Navigator.clearCorner(level, new BlockPos(lastX, lastY, lastZ), x - lastX, z - lastZ)) return false;
                lastX = x;
                lastY = y;
                lastZ = z;
            }
            if (!Navigator.free(level, new BlockPos(x, y, z))) return false;
        }
        return true;
    }

    static BlockPos snapTarget(Level level, BlockPos to) {
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

    static BlockPos surface(Level level, BlockPos from) {
        for (int dy = 0; dy <= 2; dy++) {
            if (walkable(level, from.getX(), from.getY() + dy, from.getZ())) return from.above(dy);
        }
        return descendTo(level, from.getX(), from.getY(), from.getZ(), Navigator.MAX_FALL);
    }

    static BlockPos walkNeighbour(Level level, int x, int y, int z, int dx, int dz) {
        if (!Navigator.clearCorner(level, x, y, z, dx, dz)) return null;
        for (int dy = 1; dy >= 0; dy--) {
            if (walkable(level, x + dx, y + dy, z + dz)) return new BlockPos(x + dx, y + dy, z + dz);
        }
        return null;
    }

    static BlockPos fallNeighbour(Level level, int x, int y, int z, int dx, int dz) {
        if (!Navigator.clearCorner(level, x, y, z, dx, dz)) return null;
        return descendTo(level, x + dx, y, z + dz, Navigator.MAX_FALL);
    }

    static BlockPos descendTo(Level level, int x, int y, int z, int maxDown) {
        for (int dy = -1; dy >= -maxDown; dy--) {
            if (blocked(level, x, y + dy, z) || blocked(level, x, y + dy + 1, z)) return null;
            if (walkable(level, x, y + dy, z)) return new BlockPos(x, y + dy, z);
        }
        return null;
    }

    static BlockPos hop(Level level, int x, int y, int z, int dx, int dz) {
        BlockPos best = null;
        int bestStep = 0;
        double bestScore = Double.MAX_VALUE;

        for (int step = 1; step <= Navigator.MAX_HOP + Navigator.MAX_FALL; step++) {
            int columnX = x + dx * step;
            int columnZ = z + dz * step;
            BlockPos landing = landing(level, columnX, y, columnZ);
            if (landing != null) {
                int vertical = landing.getY() - y;
                if (vertical <= Navigator.MAX_JUMP_UP && step <= Navigator.MAX_HOP - vertical) {
                    double score = Math.abs(vertical) * 2.0 + step;
                    if (score < bestScore) {
                        bestScore = score;
                        best = landing;
                        bestStep = step;
                    }
                }
            }
            if (!Navigator.free(level, columnX, y, columnZ)) break;
        }
        if (best == null) return null;
        if (dx != 0 && dz != 0
                && (!Navigator.free(level, x + dx * bestStep, y, z) || !Navigator.free(level, x, y, z + dz * bestStep))) {
            return null;
        }
        return best;
    }

    static BlockPos landing(Level level, int x, int y, int z) {
        if (standable(level, x, y, z)) return new BlockPos(x, y, z);
        if (standable(level, x, y + 1, z)) return new BlockPos(x, y + 1, z);
        return descendTo(level, x, y, z, Navigator.MAX_FALL);
    }

    static boolean standable(Level level, BlockPos pos) {
        return standable(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean standable(Level level, int x, int y, int z) {
        if (!Navigator.free(level, x, y, z)) return false;
        return blocked(level, x, y - 1, z) || fluid(level, x, y, z) || fluid(level, x, y - 1, z);
    }

    static boolean grounded(Level level, BlockPos pos) {
        return grounded(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean grounded(Level level, int x, int y, int z) {
        return Navigator.free(level, x, y, z) && blocked(level, x, y - 1, z);
    }

    static boolean walkable(Level level, int x, int y, int z) {
        return standable(level, x, y, z) || Navigator.climbNode(level, x, y, z);
    }

    static boolean climbable(Level level, BlockPos pos) {
        return climbable(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean climbable(Level level, int x, int y, int z) {
        return Navigator.stateAt(level, x, y, z).is(BlockTags.CLIMBABLE);
    }

    static boolean blocked(Level level, BlockPos pos) {
        return blocked(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean blocked(Level level, int x, int y, int z) {
        BlockState state = Navigator.stateAt(level, x, y, z);
        if (state.getBlock() instanceof DoorBlock) return !state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return !state.getValue(FenceGateBlock.OPEN);
        return state.blocksMotion();
    }

    static boolean fluid(Level level, BlockPos pos) {
        return fluid(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean fluid(Level level, int x, int y, int z) {
        return !Navigator.stateAt(level, x, y, z).getFluidState().isEmpty();
    }
}
