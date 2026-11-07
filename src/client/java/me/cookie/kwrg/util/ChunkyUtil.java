package me.cookie.kwrg.util;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;

public final class ChunkyUtil {

    private ChunkyUtil() {
    }

    @FunctionalInterface
    public interface BlockClassifier {
        /**
         * @return 匹配到的方块类型 ID（非 0 表示命中，0 表示忽略）
         */
        byte classify(BlockState state);
    }

    /**
     * 在客户端线程复制的方块状态，供后台扫描。
     */
    public record ChunkSnapshot(
            long chunkKey,
            int baseX,
            int baseZ,
            List<SectionSnapshot> sections
    ) {
    }

    private record SectionSnapshot(int baseY, PalettedContainerRO<BlockState> states) {
    }

    // ==================== 区块坐标位运算打包 / 解包 ====================

    public static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int unpackChunkX(long packedChunkPos) {
        return (int) (packedChunkPos & 0xFFFFFFFFL);
    }

    public static int unpackChunkZ(long packedChunkPos) {
        return (int) (packedChunkPos >>> 32);
    }

    // ==================== 分片队列与快照采集 ====================

    /**
     * 按玩家到区块内最近方块的水平距离排序，保留与扫描半径相交的区块。
     */
    public static void enqueueChunksSortedByDistance(
            Queue<Long> targetQueue, BlockPos center, int rangeBlocks
    ) {
        int minChunkX = (center.getX() - rangeBlocks) >> 4;
        int maxChunkX = (center.getX() + rangeBlocks) >> 4;
        int minChunkZ = (center.getZ() - rangeBlocks) >> 4;
        int maxChunkZ = (center.getZ() + rangeBlocks) >> 4;
        long rangeSquared = (long) rangeBlocks * rangeBlocks;
        List<long[]> coords = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long distSq = distanceSquaredToChunk(center, cx, cz);
                if (distSq <= rangeSquared) {
                    coords.add(new long[]{packChunkPos(cx, cz), distSq});
                }
            }
        }

        coords.sort(Comparator.comparingLong(a -> a[1]));
        for (long[] entry : coords) {
            targetQueue.offer(entry[0]);
        }
    }

    private static long distanceSquaredToChunk(BlockPos center, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        long dx = (long) center.getX() - Math.clamp(center.getX(), minX, minX + 15);
        long dz = (long) center.getZ() - Math.clamp(center.getZ(), minZ, minZ + 15);
        return dx * dx + dz * dz;
    }

    /**
     * 从待扫描队列中弹出最多 maxSliceSize 个已加载区块并生成快照（绝不触发服务端区块加载）
     */
    public static List<ChunkSnapshot> pollLoadedChunkSlice(
            ClientLevel level, Queue<Long> pendingQueue, int maxSliceSize, int centerY, int rangeBlocks,
            Predicate<BlockState> paletteFilter
    ) {
        List<ChunkSnapshot> slice = new ArrayList<>(maxSliceSize);
        int minSectionY = level.getMinSectionY();
        int minY = Math.max(level.getMinY(), centerY - rangeBlocks);
        int maxY = Math.min(level.getMaxY() - 1, centerY + rangeBlocks);

        while (slice.size() < maxSliceSize && !pendingQueue.isEmpty()) {
            long chunkKey = pendingQueue.poll();
            int cx = unpackChunkX(chunkKey);
            int cz = unpackChunkZ(chunkKey);

            // false = 仅从本地客户端缓存获取，不向服务器发送任何请求
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk != null && !chunk.isEmpty()) {
                slice.add(captureChunkSnapshot(chunk, cx, cz, minSectionY, minY, maxY, paletteFilter));
            }
        }
        return slice;
    }

    /**
     * 采集中心区块周围 (2 * chunkRadius + 1)^2 范围内的已加载区块快照（用于近距离高频刷新）
     */
    public static List<ChunkSnapshot> collectNearbySnapshots(
            ClientLevel level, int centerChunkX, int centerChunkZ, int chunkRadius, int centerY, int rangeBlocks,
            Predicate<BlockState> paletteFilter
    ) {
        int side = chunkRadius * 2 + 1;
        List<ChunkSnapshot> list = new ArrayList<>(side * side);
        int minSectionY = level.getMinSectionY();
        int minY = Math.max(level.getMinY(), centerY - rangeBlocks);
        int maxY = Math.min(level.getMaxY() - 1, centerY + rangeBlocks);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk != null && !chunk.isEmpty()) {
                    list.add(captureChunkSnapshot(chunk, cx, cz, minSectionY, minY, maxY, paletteFilter));
                }
            }
        }
        return list;
    }

    /**
     * 先用调色板跳过无关区块段，再复制命中区块段的方块状态。
     */
    private static ChunkSnapshot captureChunkSnapshot(
            LevelChunk chunk, int cx, int cz, int minSectionY, int minY, int maxY,
            Predicate<BlockState> paletteFilter
    ) {
        LevelChunkSection[] sections = chunk.getSections();
        int startSecIdx = Math.max(0, (minY >> 4) - minSectionY);
        int endSecIdx = Math.min(sections.length - 1, (maxY >> 4) - minSectionY);

        List<SectionSnapshot> snapshots = new ArrayList<>();
        for (int i = startSecIdx; i <= endSecIdx; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(paletteFilter)) continue;
            snapshots.add(new SectionSnapshot((i + minSectionY) << 4, section.getStates().copy()));
        }
        return new ChunkSnapshot(packChunkPos(cx, cz), cx << 4, cz << 4, snapshots);
    }

    /**
     * 在后台线程扫描已复制的区块段。
     */
    public static Long2ByteOpenHashMap scanChunkSnapshot(
            ChunkSnapshot snapshot,
            BlockClassifier classifier
    ) {
        Long2ByteOpenHashMap found = new Long2ByteOpenHashMap();
        for (SectionSnapshot section : snapshot.sections) {
            int baseY = section.baseY();
            for (int ly = 0; ly < 16; ly++) {
                int worldY = baseY + ly;
                for (int lz = 0; lz < 16; lz++) {
                    int worldZ = snapshot.baseZ + lz;
                    for (int lx = 0; lx < 16; lx++) {
                        byte typeId = classifier.classify(section.states().get(lx, ly, lz));
                        if (typeId != 0) {
                            int worldX = snapshot.baseX + lx;
                            found.put(BlockPos.asLong(worldX, worldY, worldZ), typeId);
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * 清理缓存中超出扫描距离的旧区块数据
     *
     * @return 若移除了任何过期区块则返回 true
     */
    public static boolean pruneOutOfRangeChunks(
            Long2ObjectMap<?> chunkCache, BlockPos center, int rangeBlocks
    ) {
        boolean removedAny = false;
        long maxDistSq = (long) rangeBlocks * rangeBlocks;

        var iterator = chunkCache.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long key = entry.getLongKey();
            if (distanceSquaredToChunk(center, unpackChunkX(key), unpackChunkZ(key)) > maxDistSq) {
                iterator.remove();
                removedAny = true;
            }
        }
        return removedAny;
    }
}
