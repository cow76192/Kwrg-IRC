package me.cookie.kwrg.gui.notification;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class NotificationToast {

    public static final int BASE_HEIGHT = 28;
    private static final long SPAWN_DURATION = 250L;
    private static final long DISMISS_DURATION = 250L;

    private final NotificationType type;
    private final Component titleComp;
    private final Component subtitleComp;
    private final Runnable onClickAction;
    private final long totalDurationMs;

    private final long createTime;
    private long pausedTimeTotal = 0L;
    private long pauseStartTime = 0L;
    private boolean isPaused = false;

    private boolean isDismissing = false;
    private long dismissStartTime = 0L;
    private boolean isFinished = false;

    private float hoverAnimation = 0.0f;
    private float smoothY = -1.0f;

    public NotificationToast(NotificationType type, String title, String subtitle, Runnable onClickAction, long durationMs) {
        this(type, Component.literal(title != null ? title : ""), subtitle != null && !subtitle.isEmpty() ? Component.literal(subtitle) : Component.empty(), onClickAction, durationMs);
    }

    public NotificationToast(NotificationType type, Component title, Component subtitle, Runnable onClickAction, long durationMs) {
        this.type = type;
        this.titleComp = title != null ? title : Component.empty();
        this.subtitleComp = subtitle != null ? subtitle : Component.empty();
        this.onClickAction = onClickAction;
        this.totalDurationMs = durationMs;
        this.createTime = System.currentTimeMillis();
    }

    public void update(int targetY, float delta) {
        long now = System.currentTimeMillis();

        long currentPauseDuration = this.pausedTimeTotal + (this.isPaused ? (now - this.pauseStartTime) : 0L);
        long effectiveElapsed = now - this.createTime - currentPauseDuration - SPAWN_DURATION;

        if (!this.isDismissing && effectiveElapsed >= this.totalDurationMs) {
            this.dismiss();
        }

        if (this.isDismissing) {
            long dismissElapsed = now - this.dismissStartTime;
            if (dismissElapsed >= DISMISS_DURATION) {
                this.isFinished = true;
            }
        }

        if (this.smoothY < 0.0f) {
            this.smoothY = targetY;
        } else {
            this.smoothY = Mth.lerp(delta * 0.32f, this.smoothY, (float) targetY);
            if (Math.abs(this.smoothY - targetY) < 0.05f) {
                this.smoothY = targetY;
            }
        }
    }

    public void dismiss() {
        if (!this.isDismissing) {
            this.isDismissing = true;
            this.dismissStartTime = System.currentTimeMillis();
        }
    }

    public int getLayoutFootprint() {
        if (this.isDismissing) {
            long elapsed = System.currentTimeMillis() - this.dismissStartTime;
            float rawT = Mth.clamp((float) elapsed / DISMISS_DURATION, 0.0f, 1.0f);
            float dismissEase = RenderUtil.easeOutCubic(rawT);
            return (int) Math.round((1.0f - dismissEase) * BASE_HEIGHT);
        }
        return BASE_HEIGHT;
    }

    public int getCardWidth() {
        var font = Minecraft.getInstance().font;
        Component styledTitle = FontManagerHelper.apply(this.titleComp);
        Component styledSubtitle = FontManagerHelper.apply(this.subtitleComp);
        int titleW = font.width(styledTitle);
        boolean hasSub = !this.subtitleComp.getString().isEmpty();
        int subtitleW = hasSub ? (font.width(styledSubtitle) + 8) : 0;
        return Math.max(160, 32 + titleW + subtitleW + 16);
    }

    public void draw(OwoUIGraphics graphics, float targetX, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();

        long spawnElapsed = now - this.createTime;
        float spawnRaw = Mth.clamp((float) spawnElapsed / SPAWN_DURATION, 0.0f, 1.0f);
        float spawnEase = RenderUtil.easeOutCubic(spawnRaw);

        float dismissEase = 0.0f;
        if (this.isDismissing) {
            long dismissElapsed = now - this.dismissStartTime;
            float dismissRaw = Mth.clamp((float) dismissElapsed / DISMISS_DURATION, 0.0f, 1.0f);
            dismissEase = RenderUtil.easeOutCubic(dismissRaw);
        }

        float alpha = (1.0f - dismissEase) * spawnEase;
        if (alpha <= 0.005f) return;

        Component styledTitle = FontManagerHelper.apply(this.titleComp);
        Component styledSubtitle = FontManagerHelper.apply(this.subtitleComp);

        var font = Minecraft.getInstance().font;
        int titleW = font.width(styledTitle);
        boolean hasSub = !this.subtitleComp.getString().isEmpty();
        int subtitleW = hasSub ? (font.width(styledSubtitle) + 8) : 0;
        int cardWidth = Math.max(160, 32 + titleW + subtitleW + 16);
        int cardHeight = BASE_HEIGHT;

        float renderY = this.smoothY;

        boolean isHovered = mouseX >= targetX && mouseX <= targetX + cardWidth && mouseY >= renderY && mouseY <= renderY + cardHeight;

        if (isHovered && !this.isPaused && !this.isDismissing) {
            this.isPaused = true;
            this.pauseStartTime = now;
        } else if (!isHovered && this.isPaused) {
            this.isPaused = false;
            this.pausedTimeTotal += (now - this.pauseStartTime);
        }

        float targetHover = isHovered ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        int accentRgb = this.type.getAccentRgb();
        int bgRgb = this.type.getBgRgb();

        int bgAlpha = (int) (255 * (0.88f + this.hoverAnimation * 0.12f) * alpha);
        Color bgColor = Color.ofArgb(ARGB.color(bgAlpha, (bgRgb >> 16) & 0xFF, (bgRgb >> 8) & 0xFF, bgRgb & 0xFF));

        int borderAlpha = (int) (255 * (0.50f + this.hoverAnimation * 0.50f) * alpha);
        Color borderColor = Color.ofArgb(ARGB.color(borderAlpha, (accentRgb >> 16) & 0xFF, (accentRgb >> 8) & 0xFF, accentRgb & 0xFF));

        float curScale = spawnEase;
        float slideOutOffsetX = dismissEase * 80.0f;
        float cx = targetX + cardWidth / 2.0f + slideOutOffsetX;
        float cy = renderY + cardHeight / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(curScale, curScale);

        int halfW = cardWidth / 2;
        int halfH = cardHeight / 2;

        RenderUtil.drawRoundedPanel(graphics, -halfW, -halfH, cardWidth, cardHeight, 8.0f, bgColor, borderColor);

        int iconBadgeCX = -halfW + 14;
        Color badgeBg = RenderUtil.withAlpha(0x1D2633, (int) (255 * alpha));
        graphics.drawCircle(iconBadgeCX, 0, 16, 7.5, badgeBg);
        graphics.drawRing(iconBadgeCX, 0, 16, 6.5, 7.5, borderColor, borderColor);

        float iconW = font.width(this.type.getIcon());
        int iconColor = RenderUtil.withAlpha(accentRgb, (int) (255 * alpha)).argb();
        RenderUtil.drawSmoothText(graphics, Component.literal(this.type.getIcon()), iconBadgeCX - iconW / 2.0f, -4.5f, 1.0f, iconColor);

        int textColor = RenderUtil.withAlpha(0xFFFFFF, (int) (255 * alpha)).argb();
        RenderUtil.drawSmoothText(graphics, styledTitle, -halfW + 28, -4.5f, 1.0f, textColor);

        if (hasSub) {
            int subTextColor = RenderUtil.withAlpha(0x90A4AE, (int) (255 * alpha)).argb();
            RenderUtil.drawSmoothText(graphics, styledSubtitle, -halfW + 28 + titleW + 8, -4.5f, 1.0f, subTextColor);
        }

        long currentPause = this.pausedTimeTotal + (this.isPaused ? (now - this.pauseStartTime) : 0L);
        long elapsedActive = now - this.createTime - currentPause - SPAWN_DURATION;
        float remainingRatio = 1.0f - Mth.clamp((float) elapsedActive / (float) this.totalDurationMs, 0.0f, 1.0f);

        int barMaxW = cardWidth - 16;
        if (remainingRatio > 0.001f) {
            int barAlpha = (int) (255 * (0.80f + this.hoverAnimation * 0.20f) * alpha);
            Color barColor = Color.ofArgb(ARGB.color(barAlpha, (accentRgb >> 16) & 0xFF, (accentRgb >> 8) & 0xFF, accentRgb & 0xFF));

            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, halfH - 2.0f);
            graphics.pose().scale(remainingRatio, 1.0f);
            graphics.fill(0, 0, barMaxW, 2, barColor.argb());
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();
    }

    public boolean onClick(double mouseX, double mouseY, float targetX) {
        if (this.isDismissing) return false;

        int cardWidth = getCardWidth();
        float renderY = this.smoothY;

        if (mouseX >= targetX && mouseX <= targetX + cardWidth && mouseY >= renderY && mouseY <= renderY + BASE_HEIGHT) {
            if (this.onClickAction != null) {
                try { this.onClickAction.run(); } catch (Throwable t) { t.printStackTrace(); }
            }
            this.dismiss();
            return true;
        }
        return false;
    }

    public boolean isFinished() {
        return this.isFinished;
    }
}