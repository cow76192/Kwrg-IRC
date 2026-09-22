package me.cookie.kwrg;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.CompletableFuture;
import me.cookie.kwrg.gui.util.TpsTracker;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcCommand;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.GreetingManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.cookie.kwrg.util.SkyblockItemHelper;

public class CookieMain implements ClientModInitializer {
    public static final String MOD_ID = "kwrg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping openIrcKey = new KeyMapping(
            "key.kwrg.open_irc_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyMapping.Category.MISC
    );

    @Override
    public void onInitializeClient() {
        LOGGER.info(":D");
        IrcConfig.load();
        IrcCommand.register();

        CompletableFuture.runAsync(() -> {
            try {
                SkyblockItemHelper.indexItemRepo();
                LOGGER.info("indexItemRepo completed");
            } catch (Throwable e) {
                LOGGER.error("Failed to indexItemRepo", e);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChatNotifier.flush();
            TpsTracker.tick();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            GreetingManager.onSessionJoin();
            IrcClient.connect();
            String configuredFont = me.cookie.kwrg.irc.IrcConfig.get().guiFont;
            if (configuredFont != null && !configuredFont.isBlank() && !me.cookie.kwrg.gui.util.FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(configuredFont)) {
                me.cookie.kwrg.gui.util.FontManagerHelper.applyFont(configuredFont);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GreetingManager.onSessionDisconnect();
            IrcClient.disconnect();
        });

    }
}