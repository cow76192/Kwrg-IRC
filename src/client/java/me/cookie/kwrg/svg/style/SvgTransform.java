package me.cookie.kwrg.svg.style;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SvgTransform {

    private SvgTransform() {
    }

    public static AffineTransform parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        AffineTransform result = new AffineTransform();
        boolean applied = false;
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('(', index);
            if (open < 0) {
                break;
            }
            int close = text.indexOf(')', open);
            if (close < 0) {
                break;
            }
            String name = text.substring(index, open).trim().toLowerCase(Locale.ROOT);
            float[] values = numbers(text.substring(open + 1, close));
            AffineTransform single = build(name, values);
            if (single != null) {
                result.concatenate(single);
                applied = true;
            }
            index = close + 1;
        }
        return applied ? result : null;
    }

    private static AffineTransform build(String name, float[] v) {
        switch (name) {
            case "translate":
                return AffineTransform.getTranslateInstance(at(v, 0, 0.0f), at(v, 1, 0.0f));
            case "scale":
                return AffineTransform.getScaleInstance(at(v, 0, 1.0f), at(v, 1, at(v, 0, 1.0f)));
            case "rotate": {
                double angle = Math.toRadians(at(v, 0, 0.0f));
                if (v.length >= 3) {
                    double cx = at(v, 1, 0.0f);
                    double cy = at(v, 2, 0.0f);
                    AffineTransform transform = AffineTransform.getRotateInstance(angle, cx, cy);
                    return transform;
                }
                return AffineTransform.getRotateInstance(angle);
            }
            case "matrix":
                if (v.length < 6) {
                    return null;
                }
                return new AffineTransform(v[0], v[1], v[2], v[3], v[4], v[5]);
            case "skewx":
                return AffineTransform.getShearInstance(Math.tan(Math.toRadians(at(v, 0, 0.0f))), 0.0);
            case "skewy":
                return AffineTransform.getShearInstance(0.0, Math.tan(Math.toRadians(at(v, 0, 0.0f))));
            default:
                return null;
        }
    }

    private static float at(float[] values, int index, float fallback) {
        return index < values.length ? values[index] : fallback;
    }

    public static float[] numbers(String body) {
        if (body == null || body.isBlank()) {
            return new float[0];
        }
        String cleaned = body.replace(',', ' ').trim();
        String[] parts = cleaned.split("\\s+");
        List<Float> values = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            float value = SvgStyle.parseNumber(part, Float.NaN);
            if (!Float.isNaN(value)) {
                values.add(value);
            }
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    public static float[] lengthList(String body) {
        return numbers(body);
    }
}
