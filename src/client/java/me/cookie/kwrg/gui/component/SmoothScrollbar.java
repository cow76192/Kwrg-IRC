package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class SmoothScrollbar {

    private float scrollOffset = 0.0f;
    private float targetScrollOffset = 0.0f;
    private float maxScroll = 0.0f;
    private int totalContentHeight = 0;
    private int viewportHeight = 0;

    private boolean isDragging = false;
    private double dragStartMouseY = 0.0;
    private float dragStartScroll = 0.0f;

    private float fadeAlpha = 0.0f;
    private float hoverAnimation = 0.0f;

    private int lastScrollbarX;
    private int lastTrackY;
    private int lastTrackH;
    private int lastThumbY;
    private int lastThumbH;

    public void update(int totalContentHeight, int viewportHeight, float delta) {
        this.totalContentHeight = totalContentHeight;
        this.viewportHeight = viewportHeight;

        this.maxScroll = Math.max(0.0f, totalContentHeight - viewportHeight + 10);
        this.targetScrollOffset = Mth.clamp(this.targetScrollOffset, 0.0f, this.maxScroll);

        if (!this.isDragging) {
            this.scrollOffset = Mth.lerp(delta * 0.28f, this.scrollOffset, this.targetScrollOffset);
            if (Math.abs(this.targetScrollOffset - this.scrollOffset) < 0.05f) {
                this.scrollOffset = this.targetScrollOffset;
            }
        } else {
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0f, this.maxScroll);
        }

        float targetFade = (this.maxScroll > 0.5f) ? 1.0f : 0.0f;
        this.fadeAlpha = Mth.clamp(this.fadeAlpha + (targetFade - this.fadeAlpha) * delta * 0.25f, 0.0f, 1.0f);
    }

    public void draw(OwoUIGraphics graphics, int scrollbarX, int trackY, int trackH, int mouseX, int mouseY, float panelAlpha, float delta) {
        if (this.fadeAlpha <= 0.005f || trackH <= 20) return;

        this.lastScrollbarX = scrollbarX;
        this.lastTrackY = trackY;
        this.lastTrackH = trackH;

        int minThumbH = 20;
        int maxThumbH = Math.max(minThumbH, trackH - 12);
        int thumbH = Mth.clamp((int) ((float) trackH / Math.max(1, this.totalContentHeight) * trackH), minThumbH, maxThumbH);

        int availableTravel = trackH - thumbH;
        int thumbY = trackY;
        if (availableTravel > 0 && this.maxScroll > 0) {
            thumbY = trackY + (int) Math.round(availableTravel * (this.scrollOffset / this.maxScroll));
        }

        thumbY = Mth.clamp(thumbY, trackY, trackY + trackH - thumbH);

        this.lastThumbY = thumbY;
        this.lastThumbH = thumbH;

        boolean isHovered = mouseX >= scrollbarX - 6 && mouseX <= scrollbarX + 8 && mouseY >= thumbY - 2 && mouseY <= thumbY + thumbH + 2;
        float targetHover = (isHovered || this.isDragging) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        int alpha = (int) (255 * (0.45f + this.hoverAnimation * 0.40f) * this.fadeAlpha * panelAlpha);
        Color thumbColor = Color.ofArgb(ARGB.color(alpha, 255, 255, 255));

        RenderUtil.drawRoundedPanel(graphics, scrollbarX, thumbY, 2, thumbH, 1.0f, thumbColor, thumbColor);
    }

    public boolean handleMouseScroll(double mouseX, double mouseY, double amount, int panelX, int panelY, int panelWidth, int panelHeight) {
        if (this.maxScroll <= 0) return false;

        boolean inside = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
        if (inside) {
            this.targetScrollOffset = Mth.clamp(this.targetScrollOffset - (float) amount * 24.0f, 0.0f, this.maxScroll);
            return true;
        }
        return false;
    }

    public boolean handleMouseDown(MouseButtonEvent click) {
        if (click.button() == 0 && this.maxScroll > 0 && this.fadeAlpha > 0.1f) {
            if (click.x() >= this.lastScrollbarX - 6 && click.x() <= this.lastScrollbarX + 8
                    && click.y() >= this.lastThumbY - 2 && click.y() <= this.lastThumbY + this.lastThumbH + 2) {

                this.isDragging = true;
                this.dragStartMouseY = click.y();
                this.dragStartScroll = this.scrollOffset;
                return true;
            } else if (click.x() >= this.lastScrollbarX - 6 && click.x() <= this.lastScrollbarX + 8
                    && click.y() >= this.lastTrackY && click.y() <= this.lastTrackY + this.lastTrackH) {

                float ratio = (float) (click.y() - this.lastTrackY) / this.lastTrackH;
                this.targetScrollOffset = Mth.clamp(ratio * this.maxScroll, 0.0f, this.maxScroll);
                this.scrollOffset = this.targetScrollOffset;
                this.isDragging = true;
                this.dragStartMouseY = click.y();
                this.dragStartScroll = this.scrollOffset;
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.isDragging && this.maxScroll > 0) {
            double dy = click.y() - this.dragStartMouseY;
            int availableTrack = this.lastTrackH - this.lastThumbH;
            if (availableTrack > 0) {
                float scrollDelta = (float) (dy / availableTrack * this.maxScroll);
                this.targetScrollOffset = Mth.clamp(this.dragStartScroll + scrollDelta, 0.0f, this.maxScroll);
                this.scrollOffset = this.targetScrollOffset;
            }
            return true;
        }
        return false;
    }

    public void handleMouseUp(MouseButtonEvent click) {
        if (click.button() == 0) {
            this.isDragging = false;
        }
    }

    public float getScrollOffset() {
        return this.scrollOffset;
    }

    public float getMaxScroll() {
        return this.maxScroll;
    }
}