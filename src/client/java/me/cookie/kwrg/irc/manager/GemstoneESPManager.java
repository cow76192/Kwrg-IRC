package me.cookie.kwrg.irc.manager;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.modules.cheat.GemstoneESPModule;
import me.cookie.kwrg.render.GemstoneESPRenderer;
import me.cookie.kwrg.render.GemstoneESPRenderer.CompiledMesh;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ChunkyUtil;
import me.cookie.kwrg.util.ChunkyUtil.ChunkSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GemstoneESPManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-gemstone-esp");

    public enum GemstoneType {
        RUBY((byte) 1, "Ruby",
                Blocks.RED_STAINED_GLASS, Blocks.RED_STAINED_GLASS_PANE,
                0xF0FF3B3B,
                0x42FF7A7A
        ),
        AMBER((byte) 2, "Amber",
                Blocks.ORANGE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS_PANE,
                0xF0FF9E22,
                0x42FFC266
        ),
        JADE((byte) 3, "Jade",
                Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS_PANE,
                0xF048FF48,
                0x428AFF8A
        ),
        JASPER((byte) 4, "Jasper",
                Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE,
                0xF0FF44CC,
                0x42FF88DF
        ),
        AMETHYST((byte) 5, "Amethyst",
                Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE,
                0xF0A844FF,
                0x42C888FF
        );

        public final byte id;
        public final String displayName;
        public final Block glassBlock;
        public final Block paneBlock;
        public final int lineColorArgb;
        public final int fillColorArgb;

        GemstoneType(byte id, String displayName, Block glassBlock, Block paneBlock, int lineColorArgb, int fillColorArgb) {
            this.id = id;
            this.displayName = displayName;
            this.glassBlock = glassBlock;
            this.paneBlock = paneBlock;
            this.lineColorArgb = lineColorArgb;
            this.fillColorArgb = fillColorArgb;
        }

        public static GemstoneType byId(byte id) {
            return switch (id) {
                case 1 -> RUBY;
                case 2 -> AMBER;
                case 3 -> JADE;
                case 4 -> JASPER;
                case 5 -> AMETHYST;
                default -> null;
            };
        }
    }

    // 分片与异步扫描状态
    private static final int CHUNKS_PER_SLICE = 16;          // 每 Tick 分片采集的最大区块数
    private static final int NEARBY_REFRESH_INTERVAL = 6;    // 近处 3x3 区块刷新间隔
    private static final int FULL_CYCLE_COOLDOWN_TICKS = 10; // 完整扫描轮次冷却

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kwrg-gemstone-esp-scanner");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicInteger SCAN_GENERATION = new AtomicInteger(0);

    private static volatile boolean inCrystalHollows = false;
    private static ClientLevel lastObservedLevel = null;
    private static BlockPos lastCompiledPlayerPos = BlockPos.ZERO;

    private static final Queue<Long> pendingChunkQueue = new ArrayDeque<>();
    private static final LongOpenHashSet pendingUnloadedChunks = new LongOpenHashSet();
    private static boolean workerBusy = false;
    private static int tickCounter = 0;
    private static int cooldownTicks = 0;

    private static final Long2ObjectMap<Long2ByteOpenHashMap> asyncChunkCache = new Long2ObjectOpenHashMap<>();
    private static volatile CompiledMesh activeMesh = CompiledMesh.EMPTY;

    private GemstoneESPManager() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        registerHypixelLocationListener();
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (level == lastObservedLevel && shouldRender()) {
                var pos = chunk.getPos();
                pendingUnloadedChunks.add(ChunkyUtil.packChunkPos(pos.x(), pos.z()));
            }
        });
        GemstoneESPRenderer.init();
    }

    private static void registerHypixelLocationListener() {
        try {
            HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
            HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> {
                String mode = packet.getMode().orElse("");
                Minecraft.getInstance().execute(() -> updateCrystalHollowsState("crystal_hollows".equalsIgnoreCase(mode)));
            });
        } catch (Throwable t) {
            LOGGER.warn("[Kwrg] 注册 HypixelModAPI LocationPacket 监听失败", t);
        }
    }

    private static void updateCrystalHollowsState(boolean crystal) {
        if (inCrystalHollows != crystal) {
            inCrystalHollows = crystal;
            if (!crystal) {
                clearAll();
            } else {
                requestImmediateRescan();
            }
        }
    }

    private static boolean detectCrystalHollowsFallback(Minecraft mc) {
        if (mc.hasSingleplayerServer()) {
            return true;
        }

        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getTabListDisplayName() == null) continue;
                String line = ChatFormatting.stripFormatting(info.getTabListDisplayName().getString());
                if (line == null) continue;
                String clean = line.trim().toLowerCase(Locale.ROOT);
                if (clean.startsWith("area:")) {
                    return clean.contains("crystal hollows");
                }
            }
        }

        if (mc.level != null) {
            Scoreboard scoreboard = mc.level.getScoreboard();
            for (PlayerTeam team : scoreboard.getPlayerTeams()) {
                String text = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
                String stripped = ChatFormatting.stripFormatting(text);
                if (stripped == null || stripped.isEmpty()) continue;
                String lower = stripped.toLowerCase(Locale.ROOT);
                if (lower.contains("crystal hollows")
                        || lower.contains("crystal nucleus")
                        || lower.contains("mithril deposits")
                        || lower.contains("goblin holdout")
                        || lower.contains("precursor remnants")
                        || lower.contains("magma fields")
                        || lower.contains("mines of divan")
                        || lower.contains("khazad")
                        || lower.contains("fairy grotto")
                        || lower.contains("jungle temple")
                        || lower.contains("goblin queen")) {
                    return true;
                }
            }
        }

        return inCrystalHollows;
    }

    public static boolean shouldRender() {
        return isEnabled() && inCrystalHollows;
    }

    public static CompiledMesh getActiveMesh() {
        return activeMesh;
    }

    private static GemstoneESPModule.Settings settings() {
        return GemstoneESPModule.CONFIG.get();
    }

    public static boolean isEnabled() {
        return settings().gemstoneEspEnabled;
    }

    public static boolean isInCrystalHollows() {
        return inCrystalHollows;
    }

    public static void toggle() {
        GemstoneESPModule.Settings s = settings();
        s.gemstoneEspEnabled = !s.gemstoneEspEnabled;
        if (!s.gemstoneEspEnabled) {
            clearAll();
            ChatNotifier.send("§c[GemstoneESP] 已关闭", NotificationType.INFO);
        } else {
            Minecraft mc = Minecraft.getInstance();
            inCrystalHollows = detectCrystalHollowsFallback(mc);
            requestImmediateRescan();
            String islandHint = inCrystalHollows ? "§a(当前位于水晶矿洞,已激活)" : "§e(进入水晶矿洞后生效)";
            ChatNotifier.send("§a[GemstoneESP] 已开启 " + islandHint, NotificationType.SUCCESS);
        }
    }

    public static void onConfigLoaded() {
        if (!isEnabled()) {
            clearAll();
        } else {
            requestImmediateRescan();
        }
    }

    public static int getScanRange() {
        return Mth.clamp(settings().gemstoneEspRange, 16, 128);
    }

    public static void setScanRange(int range) {
        int clamped = Mth.clamp(range, 16, 128);
        if (settings().gemstoneEspRange != clamped) {
            settings().gemstoneEspRange = clamped;
            pendingChunkQueue.clear();
            cooldownTicks = 0;
        }
    }

    public static int getOpacity() {
        return Mth.clamp(settings().gemstoneEspOpacity, 0, 100);
    }

    public static void setOpacity(int opacity) {
        settings().gemstoneEspOpacity = Mth.clamp(opacity, 0, 100);
    }

    public static boolean isRubyEnabled() {
        return settings().gemstoneEspRuby;
    }

    public static void toggleRuby() {
        settings().gemstoneEspRuby = !settings().gemstoneEspRuby;
        requestImmediateRescan();
    }

    public static boolean isAmberEnabled() {
        return settings().gemstoneEspAmber;
    }

    public static void toggleAmber() {
        settings().gemstoneEspAmber = !settings().gemstoneEspAmber;
        requestImmediateRescan();
    }

    public static boolean isJadeEnabled() {
        return settings().gemstoneEspJade;
    }

    public static void toggleJade() {
        settings().gemstoneEspJade = !settings().gemstoneEspJade;
        requestImmediateRescan();
    }

    public static boolean isJasperEnabled() {
        return settings().gemstoneEspJasper;
    }

    public static void toggleJasper() {
        settings().gemstoneEspJasper = !settings().gemstoneEspJasper;
        requestImmediateRescan();
    }

    public static boolean isAmethystEnabled() {
        return settings().gemstoneEspAmethyst;
    }

    public static void toggleAmethyst() {
        settings().gemstoneEspAmethyst = !settings().gemstoneEspAmethyst;
        requestImmediateRescan();
    }

    public static void requestImmediateRescan() {
        clearAll();
        cooldownTicks = 0;
    }

    public static void clearAll() {
        SCAN_GENERATION.incrementAndGet();
        pendingChunkQueue.clear();
        pendingUnloadedChunks.clear();
        activeMesh = CompiledMesh.EMPTY;
        SCAN_EXECUTOR.execute(asyncChunkCache::clear);
    }

    public static void onDisconnect() {
        inCrystalHollows = false;
        lastObservedLevel = null;
        clearAll();
    }

    public static void onTick(Minecraft mc) {
        ClientLevel level = mc.level;
        var player = mc.player;

        if (level == null || player == null || !isEnabled()) {
            if (activeMesh != CompiledMesh.EMPTY) {
                clearAll();
            }
            lastObservedLevel = null;
            return;
        }

        tickCounter++;

        if (lastObservedLevel != level) {
            lastObservedLevel = level;
            inCrystalHollows = detectCrystalHollowsFallback(mc);
            requestImmediateRescan();
        } else if (tickCounter % 20 == 0) {
            updateCrystalHollowsState(detectCrystalHollowsFallback(mc));
        }

        if (!inCrystalHollows) {
            if (activeMesh != CompiledMesh.EMPTY) {
                clearAll();
            }
            return;
        }

        if (workerBusy) return;

        int range = getScanRange();
        BlockPos playerPos = player.blockPosition();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        boolean forceMeshUpdate = playerPos.distSqr(lastCompiledPlayerPos) > 16.0;

        // 玩家身边 3x3 区块
        List<ChunkSnapshot> slice = List.of();
        if (tickCounter % NEARBY_REFRESH_INTERVAL == 0) {
            slice = ChunkyUtil.collectNearbySnapshots(
                    level, playerChunkX, playerChunkZ, 1, playerPos.getY(), range, GemstoneESPManager::isAnyGemstoneBlock
            );
        }

        // 若队列为空，按距离由近到远生成新一轮区块分片队列
        if (slice.isEmpty()) {
            if (pendingChunkQueue.isEmpty() && cooldownTicks > 0) {
                cooldownTicks--;
            } else {
                if (pendingChunkQueue.isEmpty()) {
                    ChunkyUtil.enqueueChunksSortedByDistance(pendingChunkQueue, playerPos, range);
                    cooldownTicks = FULL_CYCLE_COOLDOWN_TICKS;
                }
                slice = ChunkyUtil.pollLoadedChunkSlice(
                        level, pendingChunkQueue, CHUNKS_PER_SLICE, playerPos.getY(), range,
                        GemstoneESPManager::isAnyGemstoneBlock
                );
                forceMeshUpdate |= pendingChunkQueue.isEmpty();
            }
        }

        if (!slice.isEmpty() || !pendingUnloadedChunks.isEmpty()) {
            dispatchSliceToWorker(mc, slice, playerPos, range, forceMeshUpdate);
        }
    }

    private static void dispatchSliceToWorker(
            Minecraft mc, List<ChunkSnapshot> slice, BlockPos playerPos, int range, boolean forceMeshUpdate
    ) {
        int gen = SCAN_GENERATION.get();
        int mask = buildEnabledGemstoneMask();
        long[] unloadedChunks = pendingUnloadedChunks.toLongArray();
        pendingUnloadedChunks.clear();
        workerBusy = true;

        SCAN_EXECUTOR.execute(() -> {
            CompiledMesh updatedMesh = null;
            try {
                if (gen != SCAN_GENERATION.get()) return;
                for (long key : unloadedChunks) {
                    asyncChunkCache.remove(key);
                }
                if (mask == 0) {
                    updatedMesh = CompiledMesh.EMPTY;
                    return;
                }

                boolean anyChanged = false;
                for (ChunkSnapshot snapshot : slice) {
                    if (gen != SCAN_GENERATION.get()) return;

                    Long2ByteOpenHashMap scanned = ChunkyUtil.scanChunkSnapshot(
                            snapshot, state -> classifyEnabledBlock(state, mask)
                    );
                    Long2ByteOpenHashMap previous = asyncChunkCache.put(snapshot.chunkKey(), scanned);
                    if (previous == null ? !scanned.isEmpty() : !previous.equals(scanned)) {
                        anyChanged = true;
                    }
                }

                anyChanged |= ChunkyUtil.pruneOutOfRangeChunks(asyncChunkCache, playerPos, range);

                if (anyChanged || forceMeshUpdate || unloadedChunks.length != 0) {
                    updatedMesh = compileConnectedMesh(playerPos, range, mask);
                }
            } catch (Throwable t) {
                LOGGER.error("[Kwrg] GemstoneESP 扫描异常", t);
            } finally {
                CompiledMesh result = updatedMesh;
                mc.execute(() -> {
                    // 卸载发生在扫描期间时，由下一批任务清理缓存并重新生成网格。
                    if (result != null && gen == SCAN_GENERATION.get() && pendingUnloadedChunks.isEmpty()) {
                        lastCompiledPlayerPos = playerPos;
                        activeMesh = result;
                    }
                    workerBusy = false;
                });
            }
        });
    }

    private static int buildEnabledGemstoneMask() {
        GemstoneESPModule.Settings s = settings();
        int mask = 0;
        if (s.gemstoneEspRuby) mask |= (1 << GemstoneType.RUBY.id);
        if (s.gemstoneEspAmber) mask |= (1 << GemstoneType.AMBER.id);
        if (s.gemstoneEspJade) mask |= (1 << GemstoneType.JADE.id);
        if (s.gemstoneEspJasper) mask |= (1 << GemstoneType.JASPER.id);
        if (s.gemstoneEspAmethyst) mask |= (1 << GemstoneType.AMETHYST.id);
        return mask;
    }

    private static boolean isAnyGemstoneBlock(BlockState state) {
        return classifyBlock(state.getBlock()) != 0;
    }

    private static byte classifyEnabledBlock(BlockState state, int enabledMask) {
        byte typeId = classifyBlock(state.getBlock());
        return (typeId != 0 && (enabledMask & (1 << typeId)) != 0) ? typeId : 0;
    }

    private static byte classifyBlock(Block block) {
        if (block == Blocks.RED_STAINED_GLASS || block == Blocks.RED_STAINED_GLASS_PANE) {
            return GemstoneType.RUBY.id;
        }
        if (block == Blocks.ORANGE_STAINED_GLASS || block == Blocks.ORANGE_STAINED_GLASS_PANE) {
            return GemstoneType.AMBER.id;
        }
        if (block == Blocks.LIME_STAINED_GLASS || block == Blocks.LIME_STAINED_GLASS_PANE) {
            return GemstoneType.JADE.id;
        }
        if (block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.MAGENTA_STAINED_GLASS_PANE) {
            return GemstoneType.JASPER.id;
        }
        if (block == Blocks.PURPLE_STAINED_GLASS || block == Blocks.PURPLE_STAINED_GLASS_PANE) {
            return GemstoneType.AMETHYST.id;
        }
        return 0;
    }

    private static CompiledMesh compileConnectedMesh(BlockPos playerPos, int range, int enabledMask) {
        double maxDistSq = (double) range * (double) range;
        double px = playerPos.getX() + 0.5;
        double py = playerPos.getY() + 0.5;
        double pz = playerPos.getZ() + 0.5;

        Long2ByteOpenHashMap worldGemstones = new Long2ByteOpenHashMap(2048);
        for (Long2ByteOpenHashMap chunkMap : asyncChunkCache.values()) {
            for (Long2ByteMap.Entry entry : chunkMap.long2ByteEntrySet()) {
                byte typeId = entry.getByteValue();
                if ((enabledMask & (1 << typeId)) == 0) continue;

                long posLong = entry.getLongKey();
                int bx = BlockPos.getX(posLong);
                int by = BlockPos.getY(posLong);
                int bz = BlockPos.getZ(posLong);

                double dx = (bx + 0.5) - px;
                double dy = (by + 0.5) - py;
                double dz = (bz + 0.5) - pz;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    worldGemstones.put(posLong, typeId);
                }
            }
        }

        if (worldGemstones.isEmpty()) {
            return CompiledMesh.EMPTY;
        }
//        LOGGER.info("[GemstoneESP] 已扫描到宝石方块数量: {}", worldGemstones.size());
        DrawableGizmoPrimitives quadPrimitives = new DrawableGizmoPrimitives();
        DrawableGizmoPrimitives linePrimitives = new DrawableGizmoPrimitives();

        LongOpenHashSet[] edgeDedup = new LongOpenHashSet[24];
        for (int i = 0; i < edgeDedup.length; i++) {
            edgeDedup[i] = new LongOpenHashSet();
        }

        for (Long2ByteMap.Entry entry : worldGemstones.long2ByteEntrySet()) {
            long pos = entry.getLongKey();
            byte typeId = entry.getByteValue();
            GemstoneType type = GemstoneType.byId(typeId);
            if (type == null) continue;

            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);
            int fillArgb = type.fillColorArgb;
            int lineArgb = type.lineColorArgb;

            // 1. DOWN (-Y)
            if (isFaceExposed(worldGemstones, x, y - 1, z, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x, y, z,
                        x + 1, y, z,
                        x + 1, y, z + 1,
                        x, y, z + 1,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y, z - 1, 0, -1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z + 1, 0, -1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y, z + 1, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x - 1, y, z, 0, -1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x + 1, y, z, 0, -1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x + 1, y, z, lineArgb);
                }
            }

            // 2. UP (+Y)
            if (isFaceExposed(worldGemstones, x, y + 1, z, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x, y + 1, z,
                        x, y + 1, z + 1,
                        x + 1, y + 1, z + 1,
                        x + 1, y + 1, z,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y, z - 1, 0, 1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y + 1, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z + 1, 0, 1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y + 1, z + 1, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x - 1, y, z, 0, 1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x, y + 1, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x + 1, y, z, 0, 1, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x + 1, y + 1, z, lineArgb);
                }
            }

            // 3. NORTH (-Z)
            if (isFaceExposed(worldGemstones, x, y, z - 1, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x, y, z,
                        x, y + 1, z,
                        x + 1, y + 1, z,
                        x + 1, y, z,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y - 1, z, 0, 0, -1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y + 1, z, 0, 0, -1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y + 1, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x - 1, y, z, 0, 0, -1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x + 1, y, z, 0, 0, -1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x + 1, y, z, lineArgb);
                }
            }

            // 4. SOUTH (+Z)
            if (isFaceExposed(worldGemstones, x, y, z + 1, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x, y, z + 1,
                        x + 1, y, z + 1,
                        x + 1, y + 1, z + 1,
                        x, y + 1, z + 1,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y - 1, z, 0, 0, 1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y, z + 1, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y + 1, z, 0, 0, 1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 0, typeId, x, y + 1, z + 1, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x - 1, y, z, 0, 0, 1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x, y, z + 1, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x + 1, y, z, 0, 0, 1, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x + 1, y, z + 1, lineArgb);
                }
            }

            // 5. WEST (-X)
            if (isFaceExposed(worldGemstones, x - 1, y, z, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x, y, z,
                        x, y, z + 1,
                        x, y + 1, z + 1,
                        x, y + 1, z,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y - 1, z, -1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y + 1, z, -1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x, y + 1, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z - 1, -1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z + 1, -1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x, y, z + 1, lineArgb);
                }
            }

            // 6. EAST (+X)
            if (isFaceExposed(worldGemstones, x + 1, y, z, typeId)) {
                GemstoneESPRenderer.addQuadFace(
                        quadPrimitives,
                        x + 1, y, z,
                        x + 1, y + 1, z,
                        x + 1, y + 1, z + 1,
                        x + 1, y, z + 1,
                        fillArgb
                );
                if (!hasSameExposedFace(worldGemstones, x, y - 1, z, 1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x + 1, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y + 1, z, 1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 2, typeId, x + 1, y + 1, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z - 1, 1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x + 1, y, z, lineArgb);
                }
                if (!hasSameExposedFace(worldGemstones, x, y, z + 1, 1, 0, 0, typeId)) {
                    addUniqueUnitEdge(edgeDedup, linePrimitives, 1, typeId, x + 1, y, z + 1, lineArgb);
                }
            }
        }

        return new CompiledMesh(quadPrimitives, linePrimitives);
    }

    private static boolean isFaceExposed(Long2ByteOpenHashMap map, int nx, int ny, int nz, byte typeId) {
        return map.get(BlockPos.asLong(nx, ny, nz)) != typeId;
    }

    private static boolean hasSameExposedFace(
            Long2ByteOpenHashMap map, int bx, int by, int bz, int nx, int ny, int nz, byte typeId
    ) {
        if (map.get(BlockPos.asLong(bx, by, bz)) != typeId) return false;
        return map.get(BlockPos.asLong(bx + nx, by + ny, bz + nz)) != typeId;
    }

    private static void addUniqueUnitEdge(
            LongOpenHashSet[] edgeDedup, DrawableGizmoPrimitives linePrimitives,
            int axis, byte typeId, int vx, int vy, int vz, int colorArgb
    ) {
        int bucket = axis * 8 + typeId;
        if (edgeDedup[bucket].add(BlockPos.asLong(vx, vy, vz))) {
            int x2 = vx + (axis == 0 ? 1 : 0);
            int y2 = vy + (axis == 1 ? 1 : 0);
            int z2 = vz + (axis == 2 ? 1 : 0);
            GemstoneESPRenderer.addLineEdge(linePrimitives, vx, vy, vz, x2, y2, z2, colorArgb);
        }
    }
}
