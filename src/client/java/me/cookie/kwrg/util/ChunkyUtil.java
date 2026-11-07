package me.cookie.kwrg.util;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

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
     * 轻量级区块段引用快照（在主线程采集耗时 < 0.02ms，供异步线程安全读取）
     */
    public record ChunkSnapshot(
            long chunkKey,
            int baseX,
            int baseZ,
            LevelChunkSection[] sections,
            int[] sectionBaseYs
    ) {
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
     * 将以 (centerChunkX, centerChunkZ) 为中心、rangeBlocks 半径内的所有区块坐标
     * 按距离由近到远排序后推入分片队列。
     */
    public static void enqueueChunksSortedByDistance(
            Queue<Long> targetQueue, int centerChunkX, int centerChunkZ, int rangeBlocks
    ) {
        int chunkRadius = (rangeBlocks + 15) >> 4;
        List<long[]> coords = new ArrayList<>((chunkRadius * 2 + 1) * (chunkRadius * 2 + 1));

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= (chunkRadius + 1) * (chunkRadius + 1)) {
                    long key = packChunkPos(centerChunkX + dx, centerChunkZ + dz);
                    coords.add(new long[]{key, distSq});
                }
            }
        }

        coords.sort(Comparator.comparingLong(a -> a[1]));
        for (long[] entry : coords) {
            targetQueue.offer(entry[0]);
        }
    }

    /**
     * 从待扫描队列中弹出最多 maxSliceSize 个已加载区块并生成快照（绝不触发服务端区块加载）
     */
    public static List<ChunkSnapshot> pollLoadedChunkSlice(
            ClientLevel level, Queue<Long> pendingQueue, int maxSliceSize, int centerY, int rangeBlocks
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
                slice.add(captureChunkSnapshot(chunk, cx, cz, minSectionY, minY, maxY));
            }
        }
        return slice;
    }

    /**
     * 采集中心区块周围 (2 * chunkRadius + 1)^2 范围内的已加载区块快照（用于近距离高频刷新）
     */
    public static List<ChunkSnapshot> collectNearbySnapshots(
            ClientLevel level, int centerChunkX, int centerChunkZ, int chunkRadius, int centerY, int rangeBlocks
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
                    list.add(captureChunkSnapshot(chunk, cx, cz, minSectionY, minY, maxY));
                }
            }
        }
        return list;
    }

    /**
     * 截取单个区块在 [minY, maxY] 高度范围内的 Section 引用快照
     */
    public static ChunkSnapshot captureChunkSnapshot(
            LevelChunk chunk, int cx, int cz, int minSectionY, int minY, int maxY
    ) {
        LevelChunkSection[] sections = chunk.getSections();
        int startSecIdx = Math.max(0, (minY >> 4) - minSectionY);
        int endSecIdx = Math.min(sections.length - 1, (maxY >> 4) - minSectionY);

        int count = Math.max(0, endSecIdx - startSecIdx + 1);
        LevelChunkSection[] sliceSections = new LevelChunkSection[count];
        int[] sectionBaseYs = new int[count];

        for (int i = 0; i < count; i++) {
            int secIdx = startSecIdx + i;
            sliceSections[i] = sections[secIdx];
            sectionBaseYs[i] = (secIdx + minSectionY) << 4;
        }
        return new ChunkSnapshot(packChunkPos(cx, cz), cx << 4, cz << 4, sliceSections, sectionBaseYs);
    }

    /**
     * 在异步线程中扫描区块快照：利用 Section 调色板预检 (maybeHas) O(1) 跳过无关区块段
     */
    public static Long2ByteOpenHashMap scanChunkSnapshot(
            ChunkSnapshot snapshot,
            Predicate<BlockState> paletteFilter,
            BlockClassifier classifier
    ) {
        Long2ByteOpenHashMap found = new Long2ByteOpenHashMap();
        LevelChunkSection[] sections = snapshot.sections;
        int[] baseYs = snapshot.sectionBaseYs;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(paletteFilter)) continue;

            int baseY = baseYs[i];
            for (int ly = 0; ly < 16; ly++) {
                int worldY = baseY + ly;
                for (int lz = 0; lz < 16; lz++) {
                    int worldZ = snapshot.baseZ + lz;
                    for (int lx = 0; lx < 16; lx++) {
                        byte typeId = classifier.classify(section.getBlockState(lx, ly, lz));
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
            Long2ObjectMap<?> chunkCache, int centerChunkX, int centerChunkZ, int rangeBlocks
    ) {
        boolean removedAny = false;
        int maxChunkDist = ((rangeBlocks + 15) >> 4) + 1;
        int maxDistSq = maxChunkDist * maxChunkDist;

        var iterator = chunkCache.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long key = entry.getLongKey();
            int dx = unpackChunkX(key) - centerChunkX;
            int dz = unpackChunkZ(key) - centerChunkZ;
            if (dx * dx + dz * dz > maxDistSq) {
                iterator.remove();
                removedAny = true;
            }
        }
        return removedAny;
    }
}