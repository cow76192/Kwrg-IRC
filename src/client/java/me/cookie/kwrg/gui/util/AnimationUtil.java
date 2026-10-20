package me.cookie.kwrg.gui.util;

import io.wispforest.owo.ui.core.Color;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

    public class AnimationUtil {
    public static float easeSmoothDecel(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 4.0f);
    }

    public static float easeOutCubic(float progress) {
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - p, 3.0f);
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
        if (start == null) return end;
        if (end == null) return start;
        float d = Mth.clamp(delta, 0.0f, 1.0f);
        int r = Math.round(start.red() * 255.0f + (end.red() * 255.0f - start.red() * 255.0f) * d);
        int g = Math.round(start.green() * 255.0f + (end.green() * 255.0f - start.green() * 255.0f) * d);
        int b = Math.round(start.blue() * 255.0f + (end.blue() * 255.0f - start.blue() * 255.0f) * d);
        int a = Math.round(start.alpha() * 255.0f + (end.alpha() * 255.0f - start.alpha() * 255.0f) * d);
        return Color.ofArgb(ARGB.color(a, r, g, b));
    }
}
