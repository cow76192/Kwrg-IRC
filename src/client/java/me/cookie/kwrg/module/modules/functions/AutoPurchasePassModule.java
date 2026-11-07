package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Locale;

public class AutoPurchasePassModule extends Module {

    public static class Settings {
        public boolean autoPurchasePass = false;
    }

    private static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);
    private static volatile boolean enabled = false;

    private static long lastPurchaseTime = 0L;
    private static final long COOLDOWN_MS = 6000L;

    public AutoPurchasePassModule() {
        super("AutoPurchasePass", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> enabled, CONFIG);
    }

    @Override
    protected void onConfigLoaded() {
        enabled = CONFIG.get().autoPurchasePass;
    }

    @Override
    public void onExecute() {
        boolean nextState = !enabled;
        enabled = nextState;
        CONFIG.update(s -> s.autoPurchasePass = nextState);

        String status = enabled ? "§a已开启" : "§c已关闭";
        ChatNotifier.send("AutoPurchasePass通行证: " + status, enabled ? NotificationType.SUCCESS : NotificationType.INFO);
    }

    public static void onSystemChat(Component content) {
        if (!enabled || content == null) return;

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
                executeClickCommand(command);
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

    private static void executeClickCommand(String rawCommand) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() == null) return;

            String cleanCmd = rawCommand.trim();
            if (cleanCmd.startsWith("/")) {
                cleanCmd = cleanCmd.substring(1);
            }

            if (!cleanCmd.isEmpty()) {
                mc.getConnection().sendCommand(cleanCmd);
                ChatNotifier.send("§a[AutoPurchasePass] 已购买新通行证", NotificationType.SUCCESS);
            }
        });
    }
}