package me.cookie.kwrg.gui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.concurrent.atomic.AtomicLong;

public class ServerLatencyTracker {

    private static final AtomicLong smoothedLatency = new AtomicLong(-1L);
    private static long lastUpdateTime = 0L;

    public static long getLatency() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return -1L;
        }
        if (mc.isLocalServer() || mc.getSingleplayerServer() != null) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > 500L) {
            lastUpdateTime = now;
            updateFromPlayerInfo(mc);
        }

        return smoothedLatency.get();
    }

    private static void updateFromPlayerInfo(Minecraft mc) {
        if (mc.player == null) return;

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;

        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        if (info != null) {
            int latency = info.getLatency();
            if (latency >= 0) {
                onLatencySample(latency);
            }
        }
    }

    public static void onLatencySample(long realLatency) {
        if (realLatency < 0L || realLatency > 5000L) return;

        smoothedLatency.updateAndGet(prev -> {
            if (prev < 0L) {
                return realLatency;
            }
            return (long) (prev * 0.7 + realLatency * 0.3);
        });
    }

    public static void reset() {
        smoothedLatency.set(-1L);
        lastUpdateTime = 0L;
    }
}