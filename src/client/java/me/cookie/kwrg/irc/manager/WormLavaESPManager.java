package me.cookie.kwrg.irc.manager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.modules.cheat.WormLavaESPModule;
import me.cookie.kwrg.render.BlockESPRender;
import me.cookie.kwrg.render.BlockESPRender.CompiledMesh;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ChunkyUtil;
import me.cookie.kwrg.util.ChunkyUtil.ScannedChunk;
import me.cookie.kwrg.util.ColorUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class WormLavaESPManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-worm-lava-esp");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "kwrg-worm-lava-scan");
        thread.setDaemon(true);
        return thread;
    });

    private static final Long2ObjectMap<ScannedChunk> CACHE = new Long2ObjectOpenHashMap<>();
    private static final Queue<Long> PENDING = new ArrayDeque<>();
    private static final LongOpenHashSet UNLOADED = new LongOpenHashSet();
    private static boolean inCrystalHollows;
    private static ClientLevel lastLevel;
    private static boolean workerBusy;
    private static int cooldown;
    private static volatile CompiledMesh activeMesh = CompiledMesh.EMPTY;

    private WormLavaESPManager() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        BlockESPRender.register(WormLavaESPManager::shouldRender, WormLavaESPManager::getActiveMesh,
                WormLavaESPManager::getOpacity);
        try {
            HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
            HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet ->
                    Minecraft.getInstance().execute(() -> {
                        inCrystalHollows = "crystal_hollows".equalsIgnoreCase(packet.getMode().orElse(""));
                        reset();
                    }));
        } catch (Throwable error) {
            LOGGER.warn("[WormLavaESP] 注册位置监听失败", error);
        }
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (level == lastLevel) {
                var pos = chunk.getPos();
                UNLOADED.add(ChunkyUtil.packChunkPos(pos.x(), pos.z()));
            }
        });
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (level == lastLevel && shouldRender()) {
                var pos = chunk.getPos();
                long key = ChunkyUtil.packChunkPos(pos.x(), pos.z());
                if (!PENDING.contains(key)) PENDING.offer(key);
            }
        });
    }

    public static boolean isEnabled() { return WormLavaESPModule.CONFIG.get().wormLavaEspEnabled; }
    public static boolean shouldRender() { return isEnabled() && inCrystalHollows; }
    public static CompiledMesh getActiveMesh() { return activeMesh; }
    public static int getOpacity() { return WormLavaESPModule.CONFIG.get().wormLavaEspOpacity; }

    public static void toggle() {
        var settings = WormLavaESPModule.CONFIG.get();
        settings.wormLavaEspEnabled = !settings.wormLavaEspEnabled;
        reset();
        ChatNotifier.send("§a[WormLavaESP] " + (settings.wormLavaEspEnabled ? "已开启（进入水晶矿洞后生效）" : "已关闭"),
                NotificationType.INFO);
    }

    public static void reset() {
        GENERATION.incrementAndGet();
        activeMesh = CompiledMesh.EMPTY;
        PENDING.clear();
        UNLOADED.clear();
        cooldown = 0;
        WORKER.execute(CACHE::clear);
    }

    public static void onDisconnect() {
        inCrystalHollows = false;
        lastLevel = null;
        reset();
    }

    public static boolean isInSapphireRegion(int x, int y, int z) {
        if (x < 513 || x > 823 || z < 513 || z > 823 || y < 64) return false;
        // Crystal Nucleus takes priority over quadrant classification.
        return !(x < 564 && z < 565 && y < 181);
    }

    public static boolean isFlowingLava(BlockState state) {
        var fluid = state.getFluidState();
        return fluid.is(FluidTags.LAVA) && !fluid.isSource();
    }

    public static void onTick(Minecraft client) {
        if (client.level != lastLevel) {
            lastLevel = client.level;
            reset();
        }

        if (!shouldRender() || client.level == null || client.player == null || workerBusy) return;

        var level = client.level;
        if (PENDING.isEmpty() && cooldown == 0) {

            BlockPos center = client.player.blockPosition();
            for (int x = 513 >> 4; x <= 823 >> 4; x++) {
                for (int z = 513 >> 4; z <= 823 >> 4; z++) PENDING.offer(ChunkyUtil.packChunkPos(x, z));
            }

            var sorted = new java.util.ArrayList<>(PENDING);
            sorted.sort(java.util.Comparator.comparingLong(key -> {
                long dx = (ChunkyUtil.unpackChunkX(key) << 4) + 8L - center.getX();
                long dz = (ChunkyUtil.unpackChunkZ(key) << 4) + 8L - center.getZ();
                return dx * dx + dz * dz;
            }));
            PENDING.clear();
            PENDING.addAll(sorted);
            cooldown = 20;
        } else if (PENDING.isEmpty() && cooldown > 0) {
            cooldown--;
        }

        var slice = ChunkyUtil.pollLoadedChunkSlice(level, PENDING, 8,
                (64 + level.getMaxY()) / 2, level.getHeight(), WormLavaESPManager::isFlowingLava);
        if (slice.isEmpty() && UNLOADED.isEmpty()) return;

        long[] unloaded = UNLOADED.toLongArray();
        UNLOADED.clear();
        int generation = GENERATION.get();

        var color = ColorUtil.parseSingleColor(WormLavaESPModule.CONFIG.get().wormLavaEspColor);
        int rgb = color == null ? 0xFF8800 : color.getRGB() & 0xFFFFFF;
        workerBusy = true;
        WORKER.execute(() -> {
            try {
                if (generation != GENERATION.get()) return;

                for (long key : unloaded) CACHE.remove(key);

                for (var snapshot : slice) {
                    var scanned = ChunkyUtil.scanChunkSnapshot(snapshot,
                            state -> (byte) (isFlowingLava(state) ? 1 : 0));
                    scanned.gemstones().keySet().removeIf((long pos) -> !isInSapphireRegion(
                            BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos)));
                    CACHE.put(snapshot.chunkKey(), scanned);
                }

                LongOpenHashSet blocks = new LongOpenHashSet();
                for (var scanned : CACHE.values()) blocks.addAll(scanned.gemstones().keySet());
                CompiledMesh mesh = BlockESPRender.compileBlocks(blocks, 0xF0000000 | rgb, 0x42000000 | rgb);
                client.execute(() -> {
                    if (generation == GENERATION.get() && shouldRender() && client.level == level) activeMesh = mesh;
                });
            } catch (Exception e) {
                LOGGER.error("[WormLavaESP] 扫描失败", e);
            } finally {
                client.execute(() -> workerBusy = false);
            }
        });
    }
}

