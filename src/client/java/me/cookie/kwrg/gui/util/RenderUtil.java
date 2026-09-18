package me.cookie.kwrg.gui.util;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class RenderUtil {

    private RenderUtil() {}

    public static void fillSubpixel(OwoUIGraphics graphics, float minX, float minY, float maxX, float maxY, int color) {
        if (maxX <= minX || maxY <= minY) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(minX, minY);
        graphics.pose().scale(maxX - minX, maxY - minY);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    public static void drawQuadGradient(OwoUIGraphics graphics, float x1, float y1, float x2, float y2,
                                        int cTL, int cTR, int cBR, int cBL) {
        if (x2 <= x1 || y2 <= y1) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x1, y1);
        graphics.pose().scale(x2 - x1, y2 - y1);
        graphics.drawGradientRect(0, 0, 1, 1, cTL, cTR, cBR, cBL);
        graphics.pose().popMatrix();
    }

    public static void drawSubpixelCircle(OwoUIGraphics graphics, float cx, float cy, double angleFrom,
                                          double angleTo, int segments, double radius, Color color) {
        if (radius <= 0.0 || angleFrom >= angleTo) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawCircle(0, 0, angleFrom, angleTo, segments, radius, color);
        graphics.pose().popMatrix();
    }

    public static void drawSubpixelRingGradient(OwoUIGraphics graphics, float cx, float cy, double angleFrom,
                                                double angleTo, int segments, double innerRadius, double outerRadius,
                                                Color innerColor, Color outerColor) {
        if (outerRadius <= innerRadius || outerRadius <= 0.0 || angleFrom >= angleTo) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawRing(0, 0, angleFrom, angleTo, segments, Math.max(0.0, innerRadius), outerRadius, innerColor, outerColor);
        graphics.pose().popMatrix();
    }

    public static void drawSubpixelRoundedCore(OwoUIGraphics graphics, float x, float y, float width, float height,
                                               float r, Color color) {
        float x2 = x + width;
        float y2 = y + height;

        fillSubpixel(graphics, x + r, y, x2 - r, y2, color.argb());
        fillSubpixel(graphics, x, y + r, x + r, y2 - r, color.argb());
        fillSubpixel(graphics, x2 - r, y + r, x2, y2 - r, color.argb());

        drawSubpixelCircle(graphics, x + r, y + r, 0.0, 90.0, 32, r, color);
        drawSubpixelCircle(graphics, x2 - r, y + r, 90.0, 180.0, 32, r, color);
        drawSubpixelCircle(graphics, x2 - r, y2 - r, 180.0, 270.0, 32, r, color);
        drawSubpixelCircle(graphics, x + r, y2 - r, 270.0, 360.0, 32, r, color);
    }

    public static void drawSoftDropShadow(OwoUIGraphics graphics, float x, float y, float width, float height,
                                          float radius, float blur, float offsetY, int baseAlpha, float alpha) {
        if (alpha <= 0.005f || blur <= 0.1f) return;

        int maxA = (int) (baseAlpha * alpha);
        if (maxA <= 0) return;

        int innerRgb = ARGB.color(maxA, 0, 0, 0);
        int outerRgb = ARGB.color(0, 0, 0, 0);
        Color innerCol = Color.ofArgb(innerRgb);
        Color outerCol = Color.ofArgb(outerRgb);

        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        float sy = y + offsetY;

        drawSubpixelRoundedCore(graphics, x, sy, width, height, r, innerCol);

        if (width > 2.0f * r) {
            drawQuadGradient(graphics, x + r, sy - blur, x + width - r, sy, outerRgb, outerRgb, innerRgb, innerRgb);
            drawQuadGradient(graphics, x + r, sy + height, x + width - r, sy + height + blur, innerRgb, innerRgb, outerRgb, outerRgb);
        }

        if (height > 2.0f * r) {
            drawQuadGradient(graphics, x - blur, sy + r, x, sy + height - r, outerRgb, innerRgb, innerRgb, outerRgb);
            drawQuadGradient(graphics, x + width, sy + r, x + width + blur, sy + height - r, innerRgb, outerRgb, outerRgb, innerRgb);
        }

        drawSubpixelRingGradient(graphics, x + r, sy + r, 0.0, 90.0, 32, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + width - r, sy + r, 90.0, 180.0, 32, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + width - r, sy + height - r, 180.0, 270.0, 32, r, r + blur, innerCol, outerCol);
        drawSubpixelRingGradient(graphics, x + r, sy + height - r, 270.0, 360.0, 32, r, r + blur, innerCol, outerCol);
    }

    public static void drawSoftDropShadow(OwoUIGraphics graphics, float x, float y, float width, float height,
                                          float radius, float blur, float offsetY, float alpha) {
        drawSoftDropShadow(graphics, x, y, width, height, radius, blur, offsetY, 50, alpha);
    }

    public static void drawSubpixelRoundedPanel(OwoUIGraphics graphics, float x, float y, float width, float height,
                                                float radius, Color bgColor, Color borderColor) {
        if (graphics == null || width <= 0.0f || height <= 0.0f) return;

        boolean hasBg = bgColor != null && (bgColor.argb() & 0xFF000000) != 0;
        boolean hasBorder = borderColor != null && (borderColor.argb() & 0xFF000000) != 0;
        if (!hasBg && !hasBorder) return;

        float maxR = Math.min(width, height) / 2.0f;
        float r = Math.max(0.0f, Math.min(radius, maxR));

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);

        final int segments = 48;

        final float strokeWidth = 1.2f;
        final float aa = 0.35f;

        if (hasBg) {
            int bgRgb = bgColor.argb();

            if (r > 1.0f) {
                float bgInset = hasBorder ? (strokeWidth - aa * 0.5f) : 0.0f;
                float bgR = Math.max(0.0f, r - bgInset);

                fillSubpixel(graphics, r, bgInset, width - r, height - bgInset, bgRgb);
                fillSubpixel(graphics, bgInset, r, r, height - r, bgRgb);
                fillSubpixel(graphics, width - r, r, width - bgInset, height - r, bgRgb);

                drawSubpixelCircle(graphics, r, r, 0.0, 90.0, segments, bgR, bgColor);
                drawSubpixelCircle(graphics, width - r, r, 90.0, 180.0, segments, bgR, bgColor);
                drawSubpixelCircle(graphics, width - r, height - r, 180.0, 270.0, segments, bgR, bgColor);
                drawSubpixelCircle(graphics, r, height - r, 270.0, 360.0, segments, bgR, bgColor);

                if (!hasBorder) {
                    int bgFadeRgb = bgRgb & 0x00FFFFFF;
                    Color fadeBg = Color.ofArgb(bgFadeRgb);
                    drawSubpixelRingGradient(graphics, r, r, 0.0, 90.0, segments, r - 0.5f, r, bgColor, fadeBg);
                    drawSubpixelRingGradient(graphics, width - r, r, 90.0, 180.0, segments, r - 0.5f, r, bgColor, fadeBg);
                    drawSubpixelRingGradient(graphics, width - r, height - r, 180.0, 270.0, segments, r - 0.5f, r, bgColor, fadeBg);
                    drawSubpixelRingGradient(graphics, r, height - r, 270.0, 360.0, segments, r - 0.5f, r, bgColor, fadeBg);
                }
            } else {
                fillSubpixel(graphics, 0.0f, 0.0f, width, height, bgRgb);
            }
        }

        if (hasBorder) {
            int borderRgb = borderColor.argb();
            int borderFadeRgb = borderRgb & 0x00FFFFFF;
            Color fadeBorder = Color.ofArgb(borderFadeRgb);

            if (r > strokeWidth + 0.5f) {
                if (width > 2.0f * r) {
                    drawQuadGradient(graphics, r, 0.0f, width - r, aa, borderFadeRgb, borderFadeRgb, borderRgb, borderRgb);
                    fillSubpixel(graphics, r, aa, width - r, strokeWidth - aa, borderRgb);
                    drawQuadGradient(graphics, r, strokeWidth - aa, width - r, strokeWidth, borderRgb, borderRgb, borderFadeRgb, borderFadeRgb);

                    drawQuadGradient(graphics, r, height - strokeWidth, width - r, height - strokeWidth + aa, borderFadeRgb, borderFadeRgb, borderRgb, borderRgb);
                    fillSubpixel(graphics, r, height - strokeWidth + aa, width - r, height - aa, borderRgb);
                    drawQuadGradient(graphics, r, height - aa, width - r, height, borderRgb, borderRgb, borderFadeRgb, borderFadeRgb);
                }

                if (height > 2.0f * r) {
                    drawQuadGradient(graphics, 0.0f, r, aa, height - r, borderFadeRgb, borderRgb, borderRgb, borderFadeRgb);
                    fillSubpixel(graphics, aa, r, strokeWidth - aa, height - r, borderRgb);
                    drawQuadGradient(graphics, strokeWidth - aa, r, strokeWidth, height - r, borderRgb, borderFadeRgb, borderFadeRgb, borderRgb);

                    drawQuadGradient(graphics, width - strokeWidth, r, width - strokeWidth + aa, height - r, borderFadeRgb, borderRgb, borderRgb, borderFadeRgb);
                    fillSubpixel(graphics, width - strokeWidth + aa, r, width - aa, height - r, borderRgb);
                    drawQuadGradient(graphics, width - aa, r, width, height - r, borderRgb, borderFadeRgb, borderFadeRgb, borderRgb);
                }

                float rOuter    = r;
                float rOuterMid = r - aa;
                float rInnerMid = Math.max(0.0f, r - strokeWidth + aa);
                float rInner    = Math.max(0.0f, r - strokeWidth);

                drawSubpixelRingGradient(graphics, r, r, 0.0, 90.0, segments, rInner, rInnerMid, fadeBorder, borderColor);
                drawSubpixelRingGradient(graphics, r, r, 0.0, 90.0, segments, rInnerMid, rOuterMid, borderColor, borderColor);
                drawSubpixelRingGradient(graphics, r, r, 0.0, 90.0, segments, rOuterMid, rOuter, borderColor, fadeBorder);

                drawSubpixelRingGradient(graphics, width - r, r, 90.0, 180.0, segments, rInner, rInnerMid, fadeBorder, borderColor);
                drawSubpixelRingGradient(graphics, width - r, r, 90.0, 180.0, segments, rInnerMid, rOuterMid, borderColor, borderColor);
                drawSubpixelRingGradient(graphics, width - r, r, 90.0, 180.0, segments, rOuterMid, rOuter, borderColor, fadeBorder);

                drawSubpixelRingGradient(graphics, width - r, height - r, 180.0, 270.0, segments, rInner, rInnerMid, fadeBorder, borderColor);
                drawSubpixelRingGradient(graphics, width - r, height - r, 180.0, 270.0, segments, rInnerMid, rOuterMid, borderColor, borderColor);
                drawSubpixelRingGradient(graphics, width - r, height - r, 180.0, 270.0, segments, rOuterMid, rOuter, borderColor, fadeBorder);

                drawSubpixelRingGradient(graphics, r, height - r, 270.0, 360.0, segments, rInner, rInnerMid, fadeBorder, borderColor);
                drawSubpixelRingGradient(graphics, r, height - r, 270.0, 360.0, segments, rInnerMid, rOuterMid, borderColor, borderColor);
                drawSubpixelRingGradient(graphics, r, height - r, 270.0, 360.0, segments, rOuterMid, rOuter, borderColor, fadeBorder);

            } else {
                fillSubpixel(graphics, 0.0f, 0.0f, width, strokeWidth, borderRgb);
                fillSubpixel(graphics, 0.0f, height - strokeWidth, width, height, borderRgb);
                fillSubpixel(graphics, 0.0f, strokeWidth, strokeWidth, height - strokeWidth, borderRgb);
                fillSubpixel(graphics, width - strokeWidth, strokeWidth, width, height - strokeWidth, borderRgb);
            }
        }

        graphics.pose().popMatrix();
    }

    public static void drawRoundedPanel(OwoUIGraphics graphics, int x, int y, int width, int height,
                                        float radius, Color bgColor, Color outlineColor) {
        drawSubpixelRoundedPanel(graphics, (float) x, (float) y, (float) width, (float) height, radius, bgColor, outlineColor);
    }

    public static void drawAACircle(OwoUIGraphics graphics, float cx, float cy, double radius, Color solidColor, Color fadeColor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawCircle(0, 0, 64, radius - 0.6, solidColor);
        graphics.drawRing(0, 0, 64, radius - 0.6, radius + 0.6, solidColor, fadeColor);
        graphics.pose().popMatrix();
    }

    public static void drawAARing(OwoUIGraphics graphics, float cx, float cy, double radius, Color peakColor, Color fadeColor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawRing(0, 0, 64, radius - 1.0, radius, fadeColor, peakColor);
        graphics.drawRing(0, 0, 64, radius, radius + 1.0, peakColor, fadeColor);
        graphics.pose().popMatrix();
    }

    public static void drawAAArc(OwoUIGraphics graphics, float cx, float cy, double startDeg, double endDeg,
                                 double radius, Color peakColor, Color fadeColor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.drawRing(0, 0, startDeg, endDeg, 48, radius - 0.75, radius, fadeColor, peakColor);
        graphics.drawRing(0, 0, startDeg, endDeg, 48, radius, radius + 0.75, peakColor, fadeColor);
        graphics.pose().popMatrix();
    }

    public static void drawAALine(OwoUIGraphics graphics, float x1, float y1, float x2, float y2, double thickness, Color color) {
        graphics.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), thickness, color);
    }

    public static void drawFluidSubpixelText(OwoUIGraphics graphics, Component text, float x, float y, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.drawText(text, 0, 0, 1.0f, color, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.pose().popMatrix();
    }

    public static void drawSmoothText(OwoUIGraphics graphics, Component text, float x, float y, float scale, int textColor) {
        Component styledText = text.copy().setStyle(text.getStyle().applyTo(FontManagerHelper.getGuiStyle()));

        int softShadow = ARGB.color(55, 10, 15, 20);
        graphics.drawText(styledText, x - 0.5f, y, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(styledText, x + 0.5f, y, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(styledText, x, y - 0.5f, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(styledText, x, y + 0.5f, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);

        graphics.drawText(styledText, x, y, scale, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
    }

    public static void drawCenteredSmoothText(OwoUIGraphics graphics, Component text, float centerX, float centerY, float scale, int textColor) {
        var font = Minecraft.getInstance().font;
        float textWidth = font.width(text) * scale;
        float textHeight = 9.0f * scale;
        drawSmoothText(graphics, text, centerX - textWidth / 2.0f, centerY - textHeight / 2.0f, scale, textColor);
    }

    public static float easeSmoothDecel(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 4.0f);
    }

    public static float easeOutCubic(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 4.0f);
    }

    public static float easeOutQuint(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 5.0f);
    }

    public static float easeOutExpo(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return p >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * p);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static Color withAlpha(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Color.ofArgb(ARGB.color(alpha, r, g, b));
    }

    public static Color lerpColor(float delta, Color start, Color end) {
        float d = Mth.clamp(delta, 0.0f, 1.0f);
        int r = (int) (start.red() * 255 + (end.red() * 255 - start.red() * 255) * d);
        int g = (int) (start.green() * 255 + (end.green() * 255 - start.green() * 255) * d);
        int b = (int) (start.blue() * 255 + (end.blue() * 255 - start.blue() * 255) * d);
        int a = (int) (start.alpha() * 255 + (end.alpha() * 255 - start.alpha() * 255) * d);
        return Color.ofArgb(ARGB.color(a, r, g, b));
    }

    public static float getExactMouseX() {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        return (float) (client.mouseHandler.xpos() * ((double) window.getGuiScaledWidth() / (double) window.getScreenWidth()));
    }

    public static float getExactMouseY() {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        return (float) (client.mouseHandler.ypos() * ((double) window.getGuiScaledHeight() / (double) window.getScreenHeight()));
    }
}