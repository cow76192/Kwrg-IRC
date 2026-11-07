package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class AutoPurchasePassModule extends Module {
    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kwrg-pass-delay");
                t.setDaemon(true);
                return t;
            });

    public static class Settings {
        public boolean autoPurchasePass = false;
    }

    private static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);
    private static volatile boolean enabled = false;
    private static int enableGeneration;

    private static long lastPurchaseTime = 0L;
    private static final long COOLDOWN_MS = 6000L;

    public AutoPurchasePassModule() {
        super("AutoPurchasePass", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> enabled, CONFIG);
    }

    @Override
    protected void onConfigLoaded() {
        setEnabled(CONFIG.get().autoPurchasePass);
    }

    @Override
    public void onExecute() {
        boolean nextState = !enabled;
        setEnabled(nextState);
        CONFIG.update(s -> s.autoPurchasePass = nextState);

        String status = enabled ? "§a已开启" : "§c已关闭";
        ChatNotifier.send("AutoPurchasePass通行证: " + status, enabled ? NotificationType.SUCCESS : NotificationType.INFO);
    }

    private static void setEnabled(boolean nextState) {
        if (enabled != nextState) enableGeneration++;
        enabled = nextState;
    }

    public static void onSystemChat(ClientPacketListener listener, Component content) {
        if (!enabled || content == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.getConnection() != listener || !listener.isAcceptingMessages()) return;

        String rawText = ChatFormatting.stripFormatting(content.getString());
        if (rawText == null) return;

        String lower = rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("click to buy a new pass")) {
            long now = System.currentTimeMillis();
            if (now - lastPurchaseTime < COOLDOWN_MS) {
                return;
            }

            String command = extractRunCommand(content);
            if (command != null && !command.isBlank()) {
                lastPurchaseTime = now;
                executeClickCommand(mc, listener, command);
            }
        }
    }

    private static String extractRunCommand(Component component) {
        Style style = component.getStyle();
        if (style != null && style.getClickEvent() != null) {
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent instanceof ClickEvent.RunCommand(String command)) {
                return command;
            }
        }

        for (Component sibling : component.getSiblings()) {
            String found = extractRunCommand(sibling);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void executeClickCommand(Minecraft mc, ClientPacketListener listener, String rawCommand) {
        // 模拟人类点击聊天栏的反应时间：350ms ~ 850ms 随机波动
        long humanDelayMs = ThreadLocalRandom.current().nextLong(350L, 850L);
        int generation = enableGeneration;

        DELAY_EXECUTOR.schedule(() -> mc.execute(() -> {
            if (!enabled || generation != enableGeneration || mc.getConnection() != listener
                    || mc.player == null || !listener.isAcceptingMessages()) return;
            String cleanCmd = rawCommand.trim();
            if (cleanCmd.startsWith("/")) {
                cleanCmd = cleanCmd.substring(1);
            }

            if (!cleanCmd.isEmpty()) {
                listener.sendCommand(cleanCmd);
                ChatNotifier.send("§a[AutoPurchasePass] 已自动触发购买新通行证! (" + humanDelayMs + "ms)", NotificationType.SUCCESS);
            }
        }), humanDelayMs, TimeUnit.MILLISECONDS);
    }
}
