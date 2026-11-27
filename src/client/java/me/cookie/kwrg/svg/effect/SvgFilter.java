package me.cookie.kwrg.svg.effect;

import me.cookie.kwrg.svg.SvgNode;
import me.cookie.kwrg.svg.style.SvgStyle;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SvgFilter {
    private static final String SOURCE_GRAPHIC = "SourceGraphic";
    private static final String SOURCE_ALPHA = "SourceAlpha";
    private static final String BACKGROUND_IMAGE = "BackgroundImage";
    private static final int IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;
    private SvgFilter() {
    }
    public static BufferedImage apply(SvgNode filterNode, BufferedImage source, Rectangle2D bounds, float scale) {
        BufferedImage input = source == null ? new BufferedImage(1, 1, IMAGE_TYPE) : source;
        BufferedImage snapshot = copyOf(input);
        if (filterNode == null) {
            return snapshot;
        }
        try {
            return render(filterNode, snapshot, bounds, scale);
        } catch (RuntimeException ex) {
            return copyOf(snapshot);
        }
    }
    private static BufferedImage render(SvgNode filterNode, BufferedImage source, Rectangle2D bounds, float rawScale) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = rawScale > 0.0f && Float.isFinite(rawScale) ? rawScale : 1.0f;
        if (width <= 0 || height <= 0) return copyOf(source);
        Rectangle2D box = bounds != null && bounds.getWidth() > 0.0 && bounds.getHeight() > 0.0 ? bounds
                : new Rectangle2D.Double(0.0, 0.0, width / (double) scale, height / (double) scale);
        List<SvgNode> children = filterNode.children();
        if (children == null || children.isEmpty()) return copyOf(source);
        Map<String, BufferedImage> results = new LinkedHashMap<>();
        results.put(SOURCE_GRAPHIC, source);
        results.put(SOURCE_ALPHA, alphaOf(source));
        results.put(BACKGROUND_IMAGE, new BufferedImage(width, height, IMAGE_TYPE));
        BufferedImage previous = null;
        for (SvgNode child : children) {
            if (child == null) continue;
            BufferedImage first = resolveInput(child, "in", results, previous, source);
            BufferedImage produced = switch (lower(child.tag(), "")) {
                case "fegaussianblur" -> blur(first, child, scale);
                case "feoffset" -> shift(first, number(attr(child, "dx"), 0.0) * scale, number(attr(child, "dy"), 0.0) * scale);
                case "feflood" -> flood(child, width, height);
                case "fecomposite" -> composite(first, resolveInput(child, "in2", results, previous, source), child);
                case "femerge" -> merge(child, first, results, source);
                case "fedropshadow" -> dropShadow(first, child, scale);
                case "fecolormatrix" -> colorMatrix(first, child);
                case "feblend" -> blend(first, resolveInput(child, "in2", results, previous, source), child);
                default -> first;
            };
            String name = attr(child, "result");
            if (name != null && !name.isEmpty()) results.put(name, produced);
            previous = produced;
        }
        return previous == null ? copyOf(source) : clip(previous, filterNode, box, scale);
    }
    private static BufferedImage blur(BufferedImage input, SvgNode node, float scale) {
        double[] deviation = deviations(attr(node, "stdDeviation"));
        double sigmaX = deviation[0] * scale;
        double sigmaY = deviation[1] * scale;
        if (sigmaX <= 0.0 && sigmaY <= 0.0) return copyOf(input);
        int width = input.getWidth();
        int height = input.getHeight();
        int[] pixels = pixelsOf(input);
        for (int pass = 0; pass < 3; pass++) pixels = boxPass(pixels, width, height, radiusOf(sigmaX), true);
        for (int pass = 0; pass < 3; pass++) pixels = boxPass(pixels, width, height, radiusOf(sigmaY), false);
        return imageOf(pixels, width, height);
    }
    private static int radiusOf(double sigma) {
        return sigma <= 0.01 ? 0 : Math.max(1, (int) Math.round(sigma));
    }
    private static int[] boxPass(int[] pixels, int width, int height, int radius, boolean horizontal) {
        int limit = horizontal ? width : height;
        int span = Math.min(radius, Math.max(0, limit - 1));
        if (span < 1) return pixels;
        int taps = span * 2 + 1;
        int[] out = new int[pixels.length];
        int lines = horizontal ? height : width, stride = horizontal ? 1 : width, run = horizontal ? width : height;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            long sa = 0, sr = 0, sg = 0, sb = 0;
            for (int step = -span; step <= span; step++) {
                int argb = pixels[base + edge(step, run) * stride];
                sa += argb >>> 24;
                sr += (argb >> 16) & 0xFF;
                sg += (argb >> 8) & 0xFF;
                sb += argb & 0xFF;
            }
            for (int step = 0; step < run; step++) {
                out[base + step * stride] = pack8((int) (sr / taps), (int) (sg / taps), (int) (sb / taps), (int) (sa / taps));
                int leaving = pixels[base + edge(step - span, run) * stride];
                int entering = pixels[base + edge(step + span + 1, run) * stride];
                sa += (entering >>> 24) - (leaving >>> 24);
                sr += ((entering >> 16) & 0xFF) - ((leaving >> 16) & 0xFF);
                sg += ((entering >> 8) & 0xFF) - ((leaving >> 8) & 0xFF);
                sb += (entering & 0xFF) - (leaving & 0xFF);
            }
        }
        return out;
    }
    private static int edge(int index, int size) {
        return index < 0 ? 0 : (index >= size ? size - 1 : index);
    }
    private static BufferedImage shift(BufferedImage input, double dx, double dy) {
        int width = input.getWidth();
        int height = input.getHeight();
        int offsetX = (int) Math.round(dx);
        int offsetY = (int) Math.round(dy);
        if (offsetX == 0 && offsetY == 0) return copyOf(input);
        int[] source = pixelsOf(input);
        int[] out = new int[source.length];
        int x0 = Math.max(0, offsetX), y0 = Math.max(0, offsetY);
        int x1 = Math.min(width, width + offsetX), y1 = Math.min(height, height + offsetY);
        for (int y = y0; y < y1; y++) {
            int row = y * width, from = (y - offsetY) * width - offsetX;
            for (int x = x0; x < x1; x++) out[row + x] = source[from + x];
        }
        return imageOf(out, width, height);
    }
    private static BufferedImage flood(SvgNode node, int width, int height) {
        Color color = color(node, "flood-color", Color.BLACK);
        double alpha = clamp01(opacity(attr(node, "flood-opacity"), 1.0) * (color.getAlpha() / 255.0));
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, ((int) Math.round(alpha * 255.0) << 24) | (color.getRGB() & 0x00FFFFFF));
        return imageOf(pixels, width, height);
    }
    private static BufferedImage composite(BufferedImage first, BufferedImage second, SvgNode node) {
        String operator = lower(attr(node, "operator"), "over");
        boolean arithmetic = "arithmetic".equals(operator);
        double k1 = number(attr(node, "k1"), 0.0), k2 = number(attr(node, "k2"), 0.0);
        double k3 = number(attr(node, "k3"), 0.0), k4 = number(attr(node, "k4"), 0.0);
        int[] front = pixelsOf(first), back = pixelsOf(second), out = new int[front.length];
        for (int i = 0; i < front.length; i++) {
            double[] f = premultiplied(front[i]);
            double[] b = premultiplied(i < back.length ? back[i] : 0);
            double[] factor = switch (operator) {
                case "in" -> new double[] {b[3], 0.0};
                case "out" -> new double[] {1.0 - b[3], 0.0};
                case "atop" -> new double[] {b[3], 1.0 - f[3]};
                case "xor" -> new double[] {1.0 - b[3], 1.0 - f[3]};
                default -> new double[] {1.0, 1.0 - f[3]};
            };
            double[] result = new double[4];
            for (int c = 0; c < 4; c++) {
                result[c] = arithmetic ? k1 * f[c] * b[c] + k2 * f[c] + k3 * b[c] + k4 : f[c] * factor[0] + b[c] * factor[1];
            }
            double alpha = clamp01(result[3]);
            out[i] = alpha <= 0.0 ? 0 : pack(result[0] / alpha, result[1] / alpha, result[2] / alpha, alpha);
        }
        return imageOf(out, first.getWidth(), first.getHeight());
    }
    private static BufferedImage merge(SvgNode node, BufferedImage first, Map<String, BufferedImage> results, BufferedImage source) {
        List<SvgNode> nodes = node.children();
        if (nodes == null || nodes.isEmpty()) return copyOf(first);
        int[] out = new int[first.getWidth() * first.getHeight()];
        for (SvgNode mergeNode : nodes) {
            int[] layer = mergeNode == null ? new int[0] : pixelsOf(resolveInput(mergeNode, "in", results, null, source));
            for (int i = 0; i < out.length; i++) out[i] = over(i < layer.length ? layer[i] : 0, out[i]);
        }
        return imageOf(out, first.getWidth(), first.getHeight());
    }
    private static BufferedImage dropShadow(BufferedImage input, SvgNode node, float scale) {
        double[] deviation = deviations(attr(node, "stdDeviation"));
        BufferedImage alpha = alphaOf(input);
        BufferedImage shadow = deviation[0] > 0.0 || deviation[1] > 0.0 ? blur(alpha, node, scale) : alpha;
        shadow = tint(shadow, color(node, "flood-color", Color.BLACK), opacity(attr(node, "flood-opacity"), 1.0));
        shadow = shift(shadow, number(attr(node, "dx"), 0.0) * scale, number(attr(node, "dy"), 0.0) * scale);
        int[] top = pixelsOf(input), bottom = pixelsOf(shadow);
        for (int i = 0; i < top.length; i++) top[i] = over(top[i], i < bottom.length ? bottom[i] : 0);
        return imageOf(top, input.getWidth(), input.getHeight());
    }
    private static BufferedImage colorMatrix(BufferedImage input, SvgNode node) {
        double[] matrix = matrixFor(lower(attr(node, "type"), "matrix"), numbers(attr(node, "values")));
        int[] pixels = pixelsOf(input);
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            double[] channels = {((argb >> 16) & 0xFF) / 255.0, ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0, (argb >>> 24) / 255.0};
            double[] result = new double[4];
            for (int row = 0; row < 4; row++) {
                double sum = matrix[row * 5 + 4];
                for (int column = 0; column < 4; column++) sum += matrix[row * 5 + column] * channels[column];
                result[row] = clamp01(sum);
            }
            pixels[i] = pack(result[0], result[1], result[2], result[3]);
        }
        return imageOf(pixels, input.getWidth(), input.getHeight());
    }
    private static double[] matrixFor(String type, double[] values) {
        if ("saturate".equals(type)) {
            double s = values.length > 0 ? values[0] : 1.0;
            return new double[] {0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s, 0.0, 0.0, 0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s, 0.0, 0.0, 0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};
        }
        if ("huerotate".equals(type)) {
            double radians = Math.toRadians(values.length > 0 ? values[0] : 0.0);
            double cos = Math.cos(radians), sin = Math.sin(radians);
            return new double[] {0.213 + cos * 0.787 - sin * 0.213, 0.715 - cos * 0.715 - sin * 0.715, 0.072 - cos * 0.072 + sin * 0.928, 0.0, 0.0, 0.213 - cos * 0.213 + sin * 0.143, 0.715 + cos * 0.285 + sin * 0.140, 0.072 - cos * 0.072 - sin * 0.283, 0.0, 0.0, 0.213 - cos * 0.213 - sin * 0.787, 0.715 - cos * 0.715 + sin * 0.715, 0.072 + cos * 0.928 + sin * 0.072, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};
        }
        if ("luminancetoalpha".equals(type)) {
            return new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.2125, 0.7154, 0.0721, 0.0, 0.0};
        }
        return values.length >= 20 ? values : new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};
    }
    private static BufferedImage blend(BufferedImage first, BufferedImage second, SvgNode node) {
        String mode = lower(attr(node, "mode"), "normal");
        int[] top = pixelsOf(first), bottom = pixelsOf(second), out = new int[top.length];
        for (int i = 0; i < top.length; i++) {
            int front = top[i], back = i < bottom.length ? bottom[i] : 0;
            double fa = (front >>> 24) / 255.0, ba = (back >>> 24) / 255.0;
            double[] source = {((front >> 16) & 0xFF) / 255.0, ((front >> 8) & 0xFF) / 255.0, (front & 0xFF) / 255.0};
            double[] backdrop = {((back >> 16) & 0xFF) / 255.0, ((back >> 8) & 0xFF) / 255.0, (back & 0xFF) / 255.0};
            double[] result = new double[3];
            for (int c = 0; c < 3; c++) result[c] = (1.0 - ba) * source[c] + ba * blendChannel(mode, backdrop[c], source[c]);
            out[i] = pack(result[0], result[1], result[2], fa + ba * (1.0 - fa));
        }
        return imageOf(out, first.getWidth(), first.getHeight());
    }
    private static double blendChannel(String mode, double backdrop, double source) {
        return switch (mode) {
            case "multiply" -> backdrop * source;
            case "screen" -> backdrop + source - backdrop * source;
            case "darken" -> Math.min(backdrop, source);
            case "lighten" -> Math.max(backdrop, source);
            default -> source;
        };
    }
    private static BufferedImage clip(BufferedImage image, SvgNode filterNode, Rectangle2D bounds, float scale) {
        boolean userSpace = "userSpaceOnUse".equalsIgnoreCase(lower(attr(filterNode, "filterUnits"), "objectBoundingBox"));
        double bx = bounds.getX(), by = bounds.getY(), bw = bounds.getWidth(), bh = bounds.getHeight();
        double left = length(attr(filterNode, "x"), -10.0, bx, bw, userSpace, false);
        double top = length(attr(filterNode, "y"), -10.0, by, bh, userSpace, false);
        double right = left + length(attr(filterNode, "width"), 120.0, bx, bw, userSpace, true);
        double bottom = top + length(attr(filterNode, "height"), 120.0, by, bh, userSpace, true);
        int width = image.getWidth(), height = image.getHeight();
        int x0 = (int) Math.max(0.0, Math.min(width, Math.floor((left - bx) * scale)));
        int y0 = (int) Math.max(0.0, Math.min(height, Math.floor((top - by) * scale)));
        int x1 = (int) Math.max(0.0, Math.min(width, Math.ceil((right - bx) * scale)));
        int y1 = (int) Math.max(0.0, Math.min(height, Math.ceil((bottom - by) * scale)));
        if (x1 <= x0 || y1 <= y0) return new BufferedImage(width, height, IMAGE_TYPE);
        if (x0 == 0 && y0 == 0 && x1 == width && y1 == height) return image;
        int[] pixels = pixelsOf(image);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y < y0 || y >= y1 || x < x0 || x >= x1) pixels[y * width + x] = 0;
            }
        }
        return imageOf(pixels, width, height);
    }
    private static double length(String raw, double defaultPercent, double origin, double extent, boolean userSpace, boolean measure) {
        String text = raw == null ? "" : raw.trim();
        boolean percent = text.isEmpty() || text.endsWith("%");
        double value = percent ? number(text, defaultPercent) : number(text, Double.NaN);
        if (!Double.isFinite(value)) {
            percent = true;
            value = defaultPercent;
        }
        if (percent) {
            double computed = extent * value / 100.0;
            return measure ? computed : origin + computed;
        }
        if (measure) return userSpace ? value : extent * value;
        return userSpace ? value : origin + extent * value;
    }
    private static String lower(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim().toLowerCase(Locale.ROOT);
    }
    private static BufferedImage resolveInput(SvgNode node, String key, Map<String, BufferedImage> results, BufferedImage previous, BufferedImage source) {
        String name = attr(node, key);
        if (name == null || name.isEmpty()) return previous == null ? source : previous;
        if (SOURCE_GRAPHIC.equalsIgnoreCase(name)) return source;
        BufferedImage known = results.get(name);
        return known == null ? new BufferedImage(source.getWidth(), source.getHeight(), IMAGE_TYPE) : known;
    }
    private static String attr(SvgNode node, String key) {
        if (node == null || key == null) return null;
        Map<String, String> values = node.attributes();
        String style = values == null ? null : values.get("style");
        if (style != null) {
            for (String part : style.split(";")) {
                int colon = part.indexOf(':');
                if (colon > 0 && part.substring(0, colon).trim().equalsIgnoreCase(key)) return part.substring(colon + 1).trim();
            }
        }
        String direct = values == null ? null : values.get(key);
        if (direct == null && node.attribute(key) != null) direct = node.attribute(key);
        return direct == null ? null : direct.trim();
    }
    private static double number(String raw, double fallback) {
        if (raw == null) return fallback;
        String text = raw.trim();
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') clean.append(c);
        }
        try {
            double value = Double.parseDouble(clean.toString());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
    private static double[] numbers(String raw) {
        if (raw == null || raw.isBlank()) return new double[0];
        String[] parts = raw.trim().split("[\\s,]+");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) values[i] = number(parts[i], 0.0);
        return values;
    }
    private static double[] deviations(String raw) {
        double[] values = numbers(raw);
        if (values.length == 0) return new double[] {0.0, 0.0};
        double sigmaX = Math.max(0.0, values[0]);
        double sigmaY = values.length > 1 ? Math.max(0.0, values[1]) : sigmaX;
        return new double[] {sigmaX, sigmaY};
    }
    private static double opacity(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String text = raw.trim();
        return clamp01(text.endsWith("%") ? number(text, fallback * 100.0) / 100.0 : number(text, fallback));
    }
    private static Color color(SvgNode node, String key, Color fallback) {
        String raw = attr(node, key);
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            Color parsed = SvgStyle.parseColor(raw);
            return parsed == null ? fallback : parsed;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
    private static BufferedImage tint(BufferedImage input, Color color, double opacity) {
        int[] pixels = pixelsOf(input);
        double factor = clamp01(opacity) * (color.getAlpha() / 255.0);
        int rgb = color.getRGB() & 0x00FFFFFF;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = ((int) Math.round((pixels[i] >>> 24) * factor) << 24) | rgb;
        }
        return imageOf(pixels, input.getWidth(), input.getHeight());
    }
    private static BufferedImage alphaOf(BufferedImage source) {
        int[] pixels = pixelsOf(source);
        for (int i = 0; i < pixels.length; i++) pixels[i] &= 0xFF000000;
        return imageOf(pixels, source.getWidth(), source.getHeight());
    }
    private static BufferedImage copyOf(BufferedImage source) {
        return imageOf(pixelsOf(source), Math.max(1, source.getWidth()), Math.max(1, source.getHeight()));
    }
    private static int[] pixelsOf(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[Math.max(1, width) * Math.max(1, height)];
        if (width > 0 && height > 0) image.getRGB(0, 0, width, height, pixels, 0, width);
        return pixels;
    }
    private static BufferedImage imageOf(int[] pixels, int width, int height) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), IMAGE_TYPE);
        if (width > 0 && height > 0) image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }
    private static int over(int front, int back) {
        int alpha = front >>> 24;
        if (alpha == 0) return back;
        if (alpha == 255) return front;
        double fa = alpha / 255.0, ba = (back >>> 24) / 255.0;
        double result = fa + ba * (1.0 - fa);
        double[] f = premultiplied(front), b = premultiplied(back);
        return pack((f[0] + b[0] * (1.0 - fa)) / result, (f[1] + b[1] * (1.0 - fa)) / result, (f[2] + b[2] * (1.0 - fa)) / result, result);
    }
    private static double[] premultiplied(int argb) {
        double alpha = (argb >>> 24) / 255.0;
        return new double[] {((argb >> 16) & 0xFF) / 255.0 * alpha, ((argb >> 8) & 0xFF) / 255.0 * alpha, (argb & 0xFF) / 255.0 * alpha, alpha};
    }
    private static int pack(double red, double green, double blue, double alpha) {
        int a = (int) Math.round(clamp01(alpha) * 255.0), r = (int) Math.round(clamp01(red) * 255.0);
        int g = (int) Math.round(clamp01(green) * 255.0), b = (int) Math.round(clamp01(blue) * 255.0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private static int pack8(int red, int green, int blue, int alpha) {
        return pack(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0);
    }
    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
    }
}
