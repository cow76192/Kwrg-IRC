package me.cookie.kwrg.gui.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public record ServerRetry(Screen parent, ServerAddress address, ServerData server) {
    private static ServerRetry current;

    public static void remember(Screen parent, ServerAddress address, ServerData server, boolean transferred) {
        current = parent instanceof JoinMultiplayerScreen && server != null && !server.isRealm() && !transferred
                ? new ServerRetry(parent, address, server) : null;
    }

    public static ServerRetry current() {
        return current;
    }

    public static void clearOnMenu(Screen screen) {
        if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) current = null;
    }

    public void connect(Minecraft game) {
        ConnectScreen.startConnecting(parent, game, address, server, false, null);
    }
}
