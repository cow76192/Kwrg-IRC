package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PingModule extends Module {

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kwrg-ping-auto-refresh");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> autoRefreshTask = null;

    public PingModule() {
        super("ping", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        executePing(true);
    }

    public static void executePing(boolean notify) {
        if (!IrcClient.isIrcConnect) {
            if (notify) {
                ChatNotifier.send("§c[IRC] 未连接至服务器，无法测试延迟");
            }
            return;
        }

        if (notify) {
            ChatNotifier.send("§e[IRC] 正在测试连接延迟...");
        }

        IrcClient.sendPing(latency -> {
            if (notify) {
                if (latency >= 0) {
                    String color = latency < 80 ? "§a" : (latency < 160 ? "§e" : "§c");
                    ChatNotifier.send(color + "[IRC] 服务器延迟: " + latency + " ms");
                } else {
                    ChatNotifier.send("§c[IRC] 测试延迟超时或失败");
                }
            }
        });
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        executePing(false);

        panel.child(new TextInfoPaneComponent(
                "当前延迟",
                () -> {
                    if (!IrcClient.isIrcConnect) {
                        return Component.literal("§c未连接");
                    }
                    long latency = IrcClient.getLastLatency();
                    if (latency < 0) {
                        return Component.literal("§7测速中...");
                    }
                    String color = latency < 80 ? "§a" : (latency < 160 ? "§e" : "§c");
                    return Component.literal(color + latency + " ms");
                },
                true
        ));

        panel.child(new PanelItemComponent(
                "刷新当前延迟",
                PanelItemComponent.ItemType.TRIGGER,
                (item, click) -> {
                    executePing(false);
                },
                null,
                () -> false
        ));

        startAutoRefresh();
    }

    private static synchronized void startAutoRefresh() {
        stopAutoRefresh();

        autoRefreshTask = TIMER.scheduleAtFixedRate(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof IrcScreen screen) {
                if (screen.getActiveTertiaryPanel() != null && "ping".equalsIgnoreCase(screen.getActiveTertiaryPanel().getItemTitle())) {
                    if (IrcClient.isIrcConnect) {
                        executePing(false);
                    }
                    return;
                }
            }
            stopAutoRefresh();
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static synchronized void stopAutoRefresh() {
        if (autoRefreshTask != null && !autoRefreshTask.isDone()) {
            autoRefreshTask.cancel(true);
            autoRefreshTask = null;
        }
    }
}