package me.cookie.kwrg.gui.util;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class RenderUtil {

    private RenderUtil() {}

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
        graphics.drawLine((int) Math.round(x1), (int) Math.round(y1), (int) Math.round(x2), (int) Math.round(y2), thickness, color);
    }

    public static void drawRoundedPanel(OwoUIGraphics graphics, int x, int y, int width, int height, float radius, Color bgColor, Color outlineColor) {
        int r = (int) radius;
        int x2 = x + width;
        int y2 = y + height;

        graphics.fill(x + r, y, x2 - r, y2, bgColor.argb());
        graphics.fill(x, y + r, x + r, y2 - r, bgColor.argb());
        graphics.fill(x2 - r, y + r, x2, y2 - r, bgColor.argb());

        graphics.drawCircle(x + r, y + r, 0.0, 90.0, 16, r, bgColor);
        graphics.drawCircle(x2 - r, y + r, 90.0, 180.0, 16, r, bgColor);
        graphics.drawCircle(x2 - r, y2 - r, 180.0, 270.0, 16, r, bgColor);
        graphics.drawCircle(x + r, y2 - r, 270.0, 360.0, 16, r, bgColor);

        graphics.fill(x + r, y, x2 - r, y + 1, outlineColor.argb());
        graphics.fill(x + r, y2 - 1, x2 - r, y2, outlineColor.argb());
        graphics.fill(x, y + r, x + 1, y2 - r, outlineColor.argb());
        graphics.fill(x2 - 1, y + r, x2, y2 - r, outlineColor.argb());

        graphics.drawRing(x + r, y + r, 0.0, 90.0, 16, r - 1.0, r, outlineColor, outlineColor);
        graphics.drawRing(x2 - r, y + r, 90.0, 180.0, 16, r - 1.0, r, outlineColor, outlineColor);
        graphics.drawRing(x2 - r, y2 - r, 180.0, 270.0, 16, r - 1.0, r, outlineColor, outlineColor);
        graphics.drawRing(x + r, y2 - r, 270.0, 360.0, 16, r - 1.0, r, outlineColor, outlineColor);
    }

    public static void drawSmoothText(OwoUIGraphics graphics, Component text, float x, float y, float scale, int textColor) {
        int softShadow = ARGB.color(55, 10, 15, 20);
        graphics.drawText(text, x - 0.5f, y, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(text, x + 0.5f, y, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(text, x, y - 0.5f, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);
        graphics.drawText(text, x, y + 0.5f, scale, softShadow, OwoUIGraphics.TextAnchor.TOP_LEFT);

        graphics.drawText(text, x, y, scale, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
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

    public static Color withAlpha(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Color.ofArgb(ARGB.color(alpha, r, g, b));
    }
}