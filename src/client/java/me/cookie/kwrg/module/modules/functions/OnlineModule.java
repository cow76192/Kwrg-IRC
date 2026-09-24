package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OnlineModule extends Module {

    private static final List<String> CACHED_ONLINE_PLAYERS = new CopyOnWriteArrayList<>();
    private static volatile boolean pendingGuiQuery = false;

    public OnlineModule() {
        super("online", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        IrcClient.queryOnline();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(TextInfoPaneComponent.header("当前在线玩家"));
        pendingGuiQuery = true;
        try {
            IrcClient.queryOnline();
        } catch (Throwable ignored) {}

        renderOnlineList(panel, CACHED_ONLINE_PLAYERS);
    }

    public static boolean updateOnlinePlayers(List<String> players) {
        CACHED_ONLINE_PLAYERS.clear();
        if (players != null) {
            CACHED_ONLINE_PLAYERS.addAll(players);
        }

        Minecraft.getInstance().execute(() -> {
            IrcScreen screen = IrcScreen.getCurrentScreen();
            if (screen != null) {
                TertiaryPanelComponent activePanel = screen.getActiveTertiaryPanel();
                if (activePanel != null && "online".equalsIgnoreCase(activePanel.getItemTitle())) {
                    activePanel.clearChildren();
                    activePanel.child(TextInfoPaneComponent.header("当前在线玩家"));
                    renderOnlineList(activePanel, CACHED_ONLINE_PLAYERS);
                }
            }
        });

        if (pendingGuiQuery) {
            pendingGuiQuery = false;
            return true;
        }
        return false;
    }

    private static void renderOnlineList(TertiaryPanelComponent panel, List<String> players) {
        if (players == null || players.isEmpty()) {
            panel.child(new PanelItemComponent("Fetching...", PanelItemComponent.ItemType.TRIGGER, () -> {}, null, () -> false));
        } else {
            for (String p : players) {
                panel.child(new PanelItemComponent(
                        p,
                        PanelItemComponent.ItemType.TRIGGER,
                        () -> {
                            Minecraft.getInstance().keyboardHandler.setClipboard(p);
                            ChatNotifier.send("§a[IRC] 已复制玩家 ID: §f" + p, NotificationType.SUCCESS);
                        },
                        null,
                        () -> true
                ));
            }
        }
    }

    public static void setPendingGuiQuery(boolean pending) {
        pendingGuiQuery = pending;
    }

    public static List<String> getCachedOnlinePlayers() {
        return CACHED_ONLINE_PLAYERS;
    }
}