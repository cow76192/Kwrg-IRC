package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;

public class CircleButtonComponent extends BaseUIComponent {

    private static final int BG_COLOR_RGB = 0x161D26;
    private static final double BASE_RADIUS = 26.0;
    private static final int BASE_IMG_SIZE = 32;

    private final String title;
    private final Identifier buttonTexture;
    private final Consumer<CircleButtonComponent> clickAction;

    private float hoverAnimation = 0.0f;
    private float clickFeedback = 0.0f;

    private float exactCenterX = 0.0f;
    private float exactCenterY = 0.0f;
    private boolean hasExactPos = false;
    private float visualAlpha = 1.0f;

    public CircleButtonComponent(String title, Consumer<CircleButtonComponent> clickAction) {
        this.title = title;
        this.buttonTexture = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/buttons/" + title.toLowerCase(Locale.ROOT) + ".png");
        this.clickAction = clickAction;
        this.cursorStyle(CursorStyle.HAND);
        this.sizing(Sizing.fixed(56), Sizing.fixed(56));
    }

    public void setExactCenter(float x, float y) {
        this.exactCenterX = x;
        this.exactCenterY = y;
        this.hasExactPos = true;
    }

    public float getExactCenterX() {
        return this.hasExactPos ? this.exactCenterX : (this.x() + this.width() / 2.0f);
    }

    public float getExactCenterY() {
        return this.hasExactPos ? this.exactCenterY : (this.y() + this.height() / 2.0f);
    }

    public void setVisualAlpha(float alpha) {
        this.visualAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.visualAlpha <= 0.01f) return;

        float targetHover = this.hovered ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        if (this.clickFeedback > 0.01f) {
            this.clickFeedback = Math.max(0.0f, this.clickFeedback - delta * 0.2f);
        }

        float cx = this.getExactCenterX();
        float cy = this.getExactCenterY();

        double currentRadius = (BASE_RADIUS + this.hoverAnimation * 1.5) * (1.0 - this.clickFeedback * 0.12);

        int bgAlpha = (int) (255 * this.visualAlpha);
        Color solidBgColor = RenderUtil.withAlpha(BG_COLOR_RGB, bgAlpha);
        Color solidBgFade = RenderUtil.withAlpha(BG_COLOR_RGB, 0);

        int lineAlpha = (int) (255 * (0.45f + this.hoverAnimation * 0.45f) * this.visualAlpha);
        Color borderPeakColor = RenderUtil.withAlpha(0xFFFFFF, lineAlpha);
        Color borderFadeColor = RenderUtil.withAlpha(0xFFFFFF, 0);

        RenderUtil.drawAACircle(graphics, cx, cy, currentRadius, solidBgColor, solidBgFade);
        RenderUtil.drawAARing(graphics, cx, cy, currentRadius, borderPeakColor, borderFadeColor);

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);

        boolean hasTexture = Minecraft.getInstance().getResourceManager().getResource(this.buttonTexture).isPresent();

        if (hasTexture) {
            int half = BASE_IMG_SIZE / 2;
            float hoverScale = 1.0f + this.hoverAnimation * 0.08f;

            graphics.pose().pushMatrix();
            graphics.pose().scale(hoverScale, hoverScale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.buttonTexture, -half, -half, 0, 0, BASE_IMG_SIZE, BASE_IMG_SIZE, BASE_IMG_SIZE, BASE_IMG_SIZE);
            graphics.pose().popMatrix();
        } else {
            var font = Minecraft.getInstance().font;
            float titleScale = 0.85f;
            float textW = font.width(this.title) * titleScale;
            float textH = font.lineHeight * titleScale;
            int textColor = RenderUtil.withAlpha(this.hovered ? 0xFFFFFF : 0x90A4AE, (int) (255 * this.visualAlpha)).argb();

            RenderUtil.drawSmoothText(graphics, Component.literal(this.title), -textW / 2.0f, -textH / 2.0f, titleScale, textColor);
        }

        graphics.pose().popMatrix();
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.visualAlpha > 0.8f && this.hovered && click.button() == 0) {
            this.clickFeedback = 1.0f;
            if (this.clickAction != null) {
                this.clickAction.accept(this);
            }
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean isInBoundingBox(double x, double y) {
        float cx = this.getExactCenterX();
        float cy = this.getExactCenterY();
        double r = BASE_RADIUS + 4.0;
        double dx = x - cx;
        double dy = y - cy;
        return (dx * dx + dy * dy) <= (r * r);
    }

    public String getTitle() {
        return this.title;
    }
}