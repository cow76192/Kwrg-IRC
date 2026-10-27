package me.cookie.kwrg.util;

import me.cookie.kwrg.irc.manager.ChatWaypointManager;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

/** Server-authoritative island detection, using the same location mode as SkyHanni. */
public final class SkyblockLocation {
    private static String island = "";
    private static String server = "";
    private SkyblockLocation() {}

    public static String island() {
        var level = Minecraft.getInstance().level;
        if (level != null && (island.equals("dynamic") || island.equals("garden"))) {
            var objective = level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null && objective.getDisplayName().getString().replaceAll("§.", "").strip().endsWith("GUEST")) {
                return island + "_guest";
            }
        }
        return island;
    }

    public static String server() {
        return server;
    }

    public static void init() {
        var api = HypixelModAPI.getInstance();
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
        api.createHandler(ClientboundLocationPacket.class, packet -> Minecraft.getInstance().execute(() -> {
            String previousServer = server;
            String previousIsland = island;
            String name = packet.getServerName();
            server = name == null ? "" : name;
            boolean skyblock = packet.getServerType().map(type -> type.name().equals("SKYBLOCK")).orElse(false);
            island = skyblock ? packet.getMode().orElse("") : "";
            if (!server.equals(previousServer) || !island.equals(previousIsland)) {
                ChatWaypointManager.clear();
            }
        }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void reset() {
        island = "";
        server = "";
    }
}
