package me.cookie.kwrg.gui.util;

import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

import java.util.concurrent.atomic.AtomicLong;

public class ServerLatencyTracker {

    private static final int PING_TRANSACTION_ID = 0x50494E47;

    private static final AtomicLong smoothedLatency = new AtomicLong(-1L);

    private static volatile long lastPassivePongTime = 0L;
    private static volatile long myActiveSentTime = 0L;
    private static volatile boolean awaitingActiveResponse = false;

    private static final long PASSIVE_TIMEOUT_MS = 3500L;
    private static final long ACTIVE_INTERVAL_MS = 1000L;

    public static boolean isTrackingActive() {
        IrcConfig config = IrcConfig.get();
        if (config == null || !config.islandShowServerPing) {
            return false;
        }
        return DynamicIslandRenderer.isEnabled();
    }

    public static long getLatency() {
        if (!isTrackingActive()) {
            return -1L;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return -1L;
        }
        if (mc.isLocalServer() || mc.getSingleplayerServer() != null) {
            return 0L;
        }

        PlayerInfo info = mc.player != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
        int tabPing = info != null ? info.getLatency() : -1;
        if (tabPing > 1) {
            updateLatency(tabPing);
            return smoothedLatency.get();
        }

        long now = System.currentTimeMillis();

        boolean isOtherModPinging = (now - lastPassivePongTime < PASSIVE_TIMEOUT_MS);

        if (isOtherModPinging) {
            awaitingActiveResponse = false;
        } else {
            if (now - myActiveSentTime >= ACTIVE_INTERVAL_MS) {
                myActiveSentTime = now;
                sendIsolatedActiveProbe(mc, now);
            }
        }

        return smoothedLatency.get();
    }

    private static void sendIsolatedActiveProbe(Minecraft mc, long now) {
        if (!isTrackingActive() || mc.getConnection() == null) {
            return;
        }

        awaitingActiveResponse = true;
        mc.getConnection().send(new ServerboundCommandSuggestionPacket(PING_TRANSACTION_ID, "/"));
    }

    public static void onPassivePongReceived(ClientboundPongResponsePacket packet) {
        if (!isTrackingActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        long payload = packet.time();
        long rtt = now - payload;

        if (rtt >= 0L && rtt <= 3000L) {
            lastPassivePongTime = now;
            updateLatency(rtt);
        }
    }

    public static void onActiveCommandSuggestionReceived(ClientboundCommandSuggestionsPacket packet) {
        if (!isTrackingActive()) {
            return;
        }

        if (packet.id() != PING_TRANSACTION_ID) {
            return;
        }

        long now = System.currentTimeMillis();
        if (awaitingActiveResponse && myActiveSentTime > 0) {
            long rtt = now - myActiveSentTime;
            awaitingActiveResponse = false;

            if (rtt >= 0L && rtt <= 3000L) {
                updateLatency(rtt);
            }
        }
    }

    private static void updateLatency(long realLatency) {
        if (realLatency <= 1L) return;

        smoothedLatency.updateAndGet(prev -> {
            if (prev < 0L) {
                return realLatency;
            }
            return (long) (prev * 0.65 + realLatency * 0.35);
        });
    }

    public static void reset() {
        lastPassivePongTime = 0L;
        myActiveSentTime = 0L;
        awaitingActiveResponse = false;
        smoothedLatency.set(-1L);
    }
}