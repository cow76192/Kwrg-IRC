package me.cookie.kwrg.gui.notification;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {

    private static final List<NotificationToast> TOASTS = new CopyOnWriteArrayList<>();
    private static final int ITEM_SPACING = 8;

    public static final long DEFAULT_INFO_DURATION = 3500L;
    public static final long DEFAULT_WARN_DURATION = 4000L;
    public static final long DEFAULT_ERR_DURATION = 4500L;

    public static final long MIN_AUTO_DURATION = 2500L;
    public static final long MAX_AUTO_DURATION = 8000L;

    public static long getDefaultDuration(NotificationType type) {
        if (type == null) return DEFAULT_INFO_DURATION;
        return switch (type) {
            case WARN -> DEFAULT_WARN_DURATION;
            case ERR -> DEFAULT_ERR_DURATION;
            default -> DEFAULT_INFO_DURATION;
        };
    }

    public static long computeAutoDuration(String title, String subtitle) {
        int len = (title != null ? title.length() : 0) + (subtitle != null ? subtitle.length() : 0);
        long duration = 2500L + len * 60L;
        return Math.min(MAX_AUTO_DURATION, Math.max(MIN_AUTO_DURATION, duration));
    }

    public static long computeAutoDuration(Component title, Component subtitle) {
        String t = title != null ? title.getString() : "";
        String s = subtitle != null ? subtitle.getString() : "";
        return computeAutoDuration(t, s);
    }

    public static void send(String title) {
        send(title, false);
    }

    public static void send(String title, boolean isAutoDuration) {
        send(NotificationType.INFO, Component.literal(title), Component.empty(), null,
                isAutoDuration ? computeAutoDuration(title, "") : DEFAULT_INFO_DURATION);
    }

    public static void send(String title, String subtitle) {
        send(title, subtitle, false);
    }

    public static void send(String title, String subtitle, boolean isAutoDuration) {
        send(NotificationType.INFO, Component.literal(title), Component.literal(subtitle), null,
                isAutoDuration ? computeAutoDuration(title, subtitle) : DEFAULT_INFO_DURATION);
    }

    public static void send(NotificationType type, String title, String subtitle) {
        send(type, title, subtitle, false);
    }

    public static void send(NotificationType type, String title, String subtitle, boolean isAutoDuration) {
        long duration = isAutoDuration ? computeAutoDuration(title, subtitle) : getDefaultDuration(type);
        send(type, Component.literal(title), subtitle != null && !subtitle.isEmpty() ? Component.literal(subtitle) : Component.empty(), null, duration);
    }

    public static void send(NotificationType type, Component title, Component subtitle) {
        send(type, title, subtitle, false);
    }

    public static void send(NotificationType type, Component title, Component subtitle, boolean isAutoDuration) {
        long duration = isAutoDuration ? computeAutoDuration(title, subtitle) : getDefaultDuration(type);
        send(type, title, subtitle, null, duration);
    }

    public static void info(String title, String subtitle) {
        send(NotificationType.INFO, title, subtitle, false);
    }

    public static void info(String title, String subtitle, boolean isAutoDuration) {
        send(NotificationType.INFO, title, subtitle, isAutoDuration);
    }

    public static void info(Component title, Component subtitle) {
        send(NotificationType.INFO, title, subtitle, false);
    }

    public static void info(Component title, Component subtitle, boolean isAutoDuration) {
        send(NotificationType.INFO, title, subtitle, isAutoDuration);
    }

    public static void success(String title, String subtitle) {
        send(NotificationType.SUCCESS, title, subtitle, false);
    }

    public static void success(String title, String subtitle, boolean isAutoDuration) {
        send(NotificationType.SUCCESS, title, subtitle, isAutoDuration);
    }

    public static void success(Component title, Component subtitle) {
        send(NotificationType.SUCCESS, title, subtitle, false);
    }

    public static void success(Component title, Component subtitle, boolean isAutoDuration) {
        send(NotificationType.SUCCESS, title, subtitle, isAutoDuration);
    }

    public static void warn(String title, String subtitle) {
        send(NotificationType.WARN, title, subtitle, false);
    }

    public static void warn(String title, String subtitle, boolean isAutoDuration) {
        send(NotificationType.WARN, title, subtitle, isAutoDuration);
    }

    public static void warn(Component title, Component subtitle) {
        send(NotificationType.WARN, title, subtitle, false);
    }

    public static void warn(Component title, Component subtitle, boolean isAutoDuration) {
        send(NotificationType.WARN, title, subtitle, isAutoDuration);
    }

    public static void err(String title, String subtitle) {
        send(NotificationType.ERR, title, subtitle, false);
    }

    public static void err(String title, String subtitle, boolean isAutoDuration) {
        send(NotificationType.ERR, title, subtitle, isAutoDuration);
    }

    public static void err(Component title, Component subtitle) {
        send(NotificationType.ERR, title, subtitle, false);
    }

    public static void err(Component title, Component subtitle, boolean isAutoDuration) {
        send(NotificationType.ERR, title, subtitle, isAutoDuration);
    }

    public static void send(NotificationType type, String title, String subtitle, Runnable onClick, long durationMs) {
        send(type, Component.literal(title), subtitle != null && !subtitle.isEmpty() ? Component.literal(subtitle) : Component.empty(), onClick, durationMs);
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