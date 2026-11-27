package me.cookie.kwrg.svg.style;

import me.cookie.kwrg.svg.SvgNode;
import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SvgPaint {

    private SvgPaint() {
    }

    public static Paint of(SvgNode root, SvgStyle style, String raw, Rectangle2D bounds) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equals("none")) {
            return null;
        }
        if (value.equals("currentColor")) {
            return style.color("color", Color.BLACK);
        }
        if (value.startsWith("url(")) {
            return gradient(root, value, bounds);
        }
        return SvgStyle.parseColor(value);
    }

    private static Paint gradient(SvgNode root, String raw, Rectangle2D bounds) {
        String id = innerId(raw);
        if (id == null || root == null) {
            return null;
        }
        SvgNode node = root.index().get(id);
        if (node == null) {
            return null;
        }
        List<Color> colors = new ArrayList<>();
        List<Float> stops = new ArrayList<>();
        collectStops(root, node, colors, stops, 0);
        if (colors.isEmpty()) {
            return null;
        }
        if (colors.size() == 1) {
            return colors.get(0);
        }
        float[] fractions = new float[stops.size()];
        for (int i = 0; i < fractions.length; i++) {
            fractions[i] = stops.get(i);
        }
        Color[] array = colors.toArray(new Color[0]);
        boolean userSpace = "userSpaceOnUse".equals(node.attribute("gradientUnits"));
        AffineTransform transform = SvgTransform.parse(node.attribute("gradientTransform"));
        Rectangle2D box = userSpace || bounds == null ? new Rectangle2D.Double(0, 0, 1, 1) : bounds;

        if ("radialGradient".equals(node.tag())) {
            float cx = axis(node, "cx", 0.5f, box.getX(), box.getWidth(), userSpace);
            float cy = axis(node, "cy", 0.5f, box.getY(), box.getHeight(), userSpace);
            float radius = axis(node, "r", 0.5f, 0.0, Math.max(box.getWidth(), box.getHeight()), userSpace);
            float fx = axis(node, "fx", Float.NaN, box.getX(), box.getWidth(), userSpace);
            float fy = axis(node, "fy", Float.NaN, box.getY(), box.getHeight(), userSpace);
            if (radius <= 0.001f) {
                radius = 0.5f;
            }
            Point2D center = new Point2D.Float(cx, cy);
            Point2D focus = new Point2D.Float(Float.isNaN(fx) ? cx : fx, Float.isNaN(fy) ? cy : fy);
            if (focus.distance(center) >= radius) {
                focus = center;
            }
            return new RadialGradientPaint(center, radius, focus, fractions, array,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB,
                    transform == null ? new AffineTransform() : transform);
        }

        float x1 = axis(node, "x1", 0.0f, box.getX(), box.getWidth(), userSpace);
        float y1 = axis(node, "y1", 0.0f, box.getY(), box.getHeight(), userSpace);
        float x2 = axis(node, "x2", 1.0f, box.getX(), box.getWidth(), userSpace);
        float y2 = axis(node, "y2", 0.0f, box.getY(), box.getHeight(), userSpace);
        if (Math.abs(x1 - x2) < 0.001f && Math.abs(y1 - y2) < 0.001f) {
            return array[0];
        }
        return new LinearGradientPaint(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2), fractions, array,
                MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB,
                transform == null ? new AffineTransform() : transform);
    }

    private static float axis(SvgNode node, String key, float fallback, double origin, double size, boolean userSpace) {
        String raw = node.attribute(key);
        if (raw == null || raw.isBlank()) {
            return userSpace ? (float) (origin + size * fallback) : fallback;
        }
        String value = raw.trim();
        float ratio = value.endsWith("%")
                ? SvgStyle.parseNumber(value.substring(0, value.length() - 1), fallback * 100.0f) / 100.0f
                : SvgStyle.parseNumber(value, fallback);
        if (userSpace && !value.endsWith("%")) {
            return ratio;
        }
        return (float) (origin + size * ratio);
    }

    private static void collectStops(SvgNode root, SvgNode gradient, List<Color> colors, List<Float> stops, int depth) {
        if (depth > 4) {
            return;
        }
        String href = gradient.attribute("href");
        if (href == null) {
            href = gradient.attribute("xlink:href");
        }
        if (href != null && href.startsWith("#")) {
            SvgNode base = root.index().get(href.substring(1));
            if (base != null && base != gradient) {
                collectStops(root, base, colors, stops, depth + 1);
            }
        }
        for (SvgNode child : gradient.children()) {
            if (!"stop".equals(child.tag())) {
                continue;
            }
            float offset = 0.0f;
            String rawOffset = child.attribute("offset");
            if (rawOffset != null) {
                String trimmed = rawOffset.trim();
                offset = trimmed.endsWith("%")
                        ? SvgStyle.parseNumber(trimmed.substring(0, trimmed.length() - 1), 0.0f) / 100.0f
                        : SvgStyle.parseNumber(trimmed, 0.0f);
            }
            if (offset < 0.0f) {
                offset = 0.0f;
            }
            if (offset > 1.0f) {
                offset = 1.0f;
            }
            String colorRaw = child.attribute("stop-color");
            if (colorRaw == null) {
                String css = child.attribute("style");
                if (css != null) {
                    colorRaw = SvgStyle.parseDeclarations(css).get("stop-color");
                }
            }
            Color color = SvgStyle.parseColor(colorRaw);
            if (color == null) {
                color = Color.BLACK;
            }
            float alpha = 1.0f;
            String opacityRaw = child.attribute("stop-opacity");
            if (opacityRaw != null) {
                alpha = SvgStyle.parseNumber(opacityRaw.trim().replace("%", ""), 1.0f);
            }
            if (alpha < 0.0f) {
                alpha = 0.0f;
            }
            if (alpha > 1.0f) {
                alpha = 1.0f;
            }
            int a = Math.round(alpha * 255.0f);
            colors.add(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
            stops.add(offset);
        }
        if (gradient.attribute("href") != null || gradient.attribute("xlink:href") != null) {
            return;
        }
    }

    public static String innerId(String raw) {
        int open = raw.indexOf("url(");
        if (open < 0) {
            return null;
        }
        int hash = raw.indexOf('#', open);
        int close = raw.indexOf(')', hash);
        if (hash < 0 || close < 0) {
            return null;
        }
        String id = raw.substring(hash + 1, close).trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() ? null : id;
    }
}
