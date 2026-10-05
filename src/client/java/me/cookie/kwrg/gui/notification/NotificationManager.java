package me.cookie.kwrg.gui.notification;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
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

    public static final int PLACEHOLDER_WIDTH = 200;

    private static NotificationToast previewToast = null;

    public static void setEditorPreview(boolean enabled) {
        if (enabled) {
            if (previewToast == null) {
                previewToast = new NotificationToast(NotificationType.INFO, "通知", "预览 · 拖动调整位置", null, 3_600_000L);
                TOASTS.add(previewToast);
            }
        } else if (previewToast != null) {
            TOASTS.remove(previewToast);
            previewToast = null;
        }
    }

    public static boolean hasToasts() {
        return !TOASTS.isEmpty();
    }

    public static float[] getGroupBounds() {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window != null ? window.getGuiScaledWidth() : 0;
        int screenH = window != null ? window.getGuiScaledHeight() : 0;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.NOTIFICATIONS);
        float[] size = measureGroup();
        float groupX = HudElement.Anchor.resolveX(state.anchorX, state.marginX, state.offsetX, size[0], screenW);
        float groupY = HudElement.Anchor.resolveY(state.anchorY, state.marginY, state.offsetY, size[1], screenH);
        return new float[]{groupX, groupY, size[0], size[1]};
    }

    private static float[] measureGroup() {
        float width = 0.0f;
        float height = 0.0f;
        for (NotificationToast toast : TOASTS) {
            width = Math.max(width, toast.getCardWidth());
            height += toast.getLayoutFootprint() + ITEM_SPACING;
        }
        if (height > 0.0f) height -= ITEM_SPACING;
        return new float[]{Math.max(width, PLACEHOLDER_WIDTH), Math.max(height, NotificationToast.BASE_HEIGHT)};
    }

    private static float resolveToastX(HudElement.State state, float groupX, float groupW, int cardWidth) {
        return switch (HudElement.Anchor.normalizeX(state.anchorX)) {
            case HudElement.Anchor.LEFT -> groupX;
            case HudElement.Anchor.RIGHT -> groupX + groupW - cardWidth;
            default -> groupX + (groupW - cardWidth) / 2.0f;
        };
    }

    public static void render(OwoUIGraphics graphics, int mouseX, int mouseY, float delta) {
        if (TOASTS.isEmpty()) return;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.NOTIFICATIONS);
        float[] bounds = getGroupBounds();
        float groupX = bounds[0];
        float groupY = bounds[1];
        float groupW = bounds[2];
        float groupH = bounds[3];

        boolean stackDown = !HudElement.Anchor.BOTTOM.equals(HudElement.Anchor.normalizeY(state.anchorY));
        float cursor = stackDown ? groupY : groupY + groupH;

        List<NotificationToast> toRemove = new ArrayList<>();

        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            NotificationToast toast = TOASTS.get(i);

            if (toast.isFinished()) {
                if (toast == previewToast) previewToast = null;
                toRemove.add(toast);
                continue;
            }

            float footprint = toast.getLayoutFootprint();
            float toastY = stackDown ? cursor : cursor - footprint;
            cursor += stackDown ? (footprint + ITEM_SPACING) : -(footprint + ITEM_SPACING);

            toast.update((int) toastY, delta);

            if (!state.enabled) continue;

            toast.draw(graphics, resolveToastX(state, groupX, groupW, toast.getCardWidth()), mouseX, mouseY, delta);
        }

        TOASTS.removeAll(toRemove);
    }

    public static boolean onMouseDown(MouseButtonEvent click) {
        if (click.button() != 0 || TOASTS.isEmpty()) return false;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.NOTIFICATIONS);
        if (!state.enabled) return false;

        float[] bounds = getGroupBounds();

        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            NotificationToast toast = TOASTS.get(i);
            if (toast.onClick(click.x(), click.y(), resolveToastX(state, bounds[0], bounds[2], toast.getCardWidth()))) {
                return true;
            }
        }
        return false;
    }
}
