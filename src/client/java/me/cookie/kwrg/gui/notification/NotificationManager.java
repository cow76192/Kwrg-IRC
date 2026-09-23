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

    public static void send(String title) {
        send(NotificationType.INFO, Component.literal(title), Component.empty(), null, 3500L);
    }

    public static void send(String title, String subtitle) {
        send(NotificationType.INFO, Component.literal(title), Component.literal(subtitle), null, 3500L);
    }

    public static void send(NotificationType type, String title, String subtitle) {
        send(type, Component.literal(title), Component.literal(subtitle), null, 3500L);
    }

    public static void send(NotificationType type, Component title, Component subtitle) {
        send(type, title, subtitle, null, 3500L);
    }

    public static void info(String title, String subtitle) {
        send(NotificationType.INFO, Component.literal(title), Component.literal(subtitle), null, 3500L);
    }

    public static void info(Component title, Component subtitle) {
        send(NotificationType.INFO, title, subtitle, null, 3500L);
    }

    public static void success(String title, String subtitle) {
        send(NotificationType.SUCCESS, Component.literal(title), Component.literal(subtitle), null, 3500L);
    }

    public static void success(Component title, Component subtitle) {
        send(NotificationType.SUCCESS, title, subtitle, null, 3500L);
    }

    public static void warn(String title, String subtitle) {
        send(NotificationType.WARN, Component.literal(title), Component.literal(subtitle), null, 4000L);
    }

    public static void warn(Component title, Component subtitle) {
        send(NotificationType.WARN, title, subtitle, null, 4000L);
    }

    public static void err(String title, String subtitle) {
        send(NotificationType.ERR, Component.literal(title), Component.literal(subtitle), null, 4500L);
    }

    public static void err(Component title, Component subtitle) {
        send(NotificationType.ERR, title, subtitle, null, 4500L);
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