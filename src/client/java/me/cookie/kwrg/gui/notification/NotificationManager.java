package me.cookie.kwrg.gui.notification;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationManager {

    private static final List<NotificationToast> TOASTS = new CopyOnWriteArrayList<>();
    private static final int ITEM_SPACING = 8;

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("[§&]([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);

    public static String stripColorCodes(String text) {
        if (text == null) return "";
        return COLOR_CODE_PATTERN.matcher(text).replaceAll("");
    }

    public static NotificationType resolveTypeFromCodes(String text) {
        if (text == null || text.isEmpty()) return NotificationType.INFO;
        Matcher m = COLOR_CODE_PATTERN.matcher(text);
        int depth = 0;
        int scanned = 0;
        while (m.find()) {
            for (int i = scanned; i < m.start(); i++) {
                char ch = text.charAt(i);
                if (ch == '[') depth++;
                else if (ch == ']' && depth > 0) depth--;
            }
            scanned = m.end();
            if (depth > 0) continue;
            switch (Character.toLowerCase(m.group(1).charAt(0))) {
                case 'c' -> { return NotificationType.ERR; }
                case 'e', '6' -> { return NotificationType.WARN; }
                case 'a' -> { return NotificationType.SUCCESS; }
                default -> { }
            }
        }
        return NotificationType.INFO;
    }

    private static NotificationType resolveType(String title, String subtitle) {
        NotificationType t = resolveTypeFromCodes(title);
        if (t == NotificationType.INFO) t = resolveTypeFromCodes(subtitle);
        return t;
    }

    private static long defaultDuration(NotificationType type) {
        return switch (type) {
            case WARN -> 4000L;
            case ERR -> 4500L;
            default -> 3500L;
        };
    }

    public static void send(String title) {
        NotificationType type = resolveType(title, null);
        send(type, Component.literal(stripColorCodes(title)), Component.empty(), null, defaultDuration(type));
    }

    public static void send(String title, String subtitle) {
        NotificationType type = resolveType(title, subtitle);
        send(type, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, defaultDuration(type));
    }

    public static void send(NotificationType type, String title, String subtitle) {
        send(type, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, 3500L);
    }

    public static void send(NotificationType type, Component title, Component subtitle) {
        send(type, title, subtitle, null, 3500L);
    }

    public static void info(String title, String subtitle) {
        send(NotificationType.INFO, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, 3500L);
    }

    public static void info(Component title, Component subtitle) {
        send(NotificationType.INFO, title, subtitle, null, 3500L);
    }

    public static void success(String title, String subtitle) {
        send(NotificationType.SUCCESS, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, 3500L);
    }

    public static void success(Component title, Component subtitle) {
        send(NotificationType.SUCCESS, title, subtitle, null, 3500L);
    }

    public static void warn(String title, String subtitle) {
        send(NotificationType.WARN, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, 4000L);
    }

    public static void warn(Component title, Component subtitle) {
        send(NotificationType.WARN, title, subtitle, null, 4000L);
    }

    public static void err(String title, String subtitle) {
        send(NotificationType.ERR, Component.literal(stripColorCodes(title)), Component.literal(stripColorCodes(subtitle)), null, 4500L);
    }

    public static void err(Component title, Component subtitle) {
        send(NotificationType.ERR, title, subtitle, null, 4500L);
    }

    public static void send(NotificationType type, String title, String subtitle, Runnable onClick, long durationMs) {
        send(type, Component.literal(stripColorCodes(title)), subtitle != null && !subtitle.isEmpty() ? Component.literal(stripColorCodes(subtitle)) : Component.empty(), onClick, durationMs);
    }

    public static void send(NotificationType type, Component title, Component subtitle, Runnable onClick, long durationMs) {
        TOASTS.add(new NotificationToast(type, title, subtitle, onClick, durationMs));
    }

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        render(OwoUIGraphics.of(graphics), mouseX, mouseY, delta);
    }

    public static void render(OwoUIGraphics graphics, int mouseX, int mouseY, float delta) {
        if (TOASTS.isEmpty()) return;

        var window = Minecraft.getInstance().getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();

        int bottomBaseY = screenHeight - 20 - NotificationToast.BASE_HEIGHT;
        int currentStackY = bottomBaseY;

        List<NotificationToast> toRemove = new ArrayList<>();

        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            NotificationToast toast = TOASTS.get(i);

            if (toast.isFinished()) {
                toRemove.add(toast);
                continue;
            }

            toast.update(currentStackY, delta);
            toast.draw(graphics, screenWidth, mouseX, mouseY, delta);

            currentStackY -= (toast.getLayoutFootprint() + ITEM_SPACING);
        }

        TOASTS.removeAll(toRemove);
    }

    public static boolean onMouseDown(MouseButtonEvent click) {
        if (click.button() == 0 && !TOASTS.isEmpty()) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            for (NotificationToast toast : TOASTS) {
                if (toast.onClick(click.x(), click.y(), screenWidth)) {
                    return true;
                }
            }
        }
        return false;
    }
}