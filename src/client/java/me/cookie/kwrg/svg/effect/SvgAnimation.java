package me.cookie.kwrg.svg.effect;

import me.cookie.kwrg.svg.SvgNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SvgAnimation {
    private static final double EPSILON = 1.0e-9;
    private static final double MIDDLE = 0.5;
    private static final Map<String, int[]> COLORS = namedColors();

    private SvgAnimation() {
    }

    public static boolean animated(SvgNode node) {
        try {
            for (SvgNode child : children(node)) {
                if (isAnimation(tag(child))) return true;
            }
        } catch (RuntimeException error) {
            return false;
        }
        return false;
    }

    public static Map<String, String> overrides(SvgNode node, long timeMs) {
        Map<String, String> result = new LinkedHashMap<>();
        List<SvgNode> kids;
        try {
            kids = children(node);
        } catch (RuntimeException error) {
            return result;
        }
        for (SvgNode child : kids) {
            try {
                apply(node, child, timeMs, result);
            } catch (RuntimeException error) {
                continue;
            }
        }
        return result;
    }

    private static void apply(SvgNode node, SvgNode element, long timeMs, Map<String, String> result) {
        String kind = tag(element);
        if (!isAnimation(kind)) return;
        Window window = windowOf(element);
        if (window == null) return;
        double phase = progress(window, timeMs);
        if (phase < 0.0) return;
        boolean transform = kind.equals("animateTransform");
        String name = transform ? "transform" : attr(element, "attributeName");
        if (name == null) return;
        boolean discrete = isDiscrete(element);
        String value;
        if (kind.equals("set")) {
            value = attr(element, "to");
        } else if (transform) {
            value = transformValue(attr(element, "type"), transformAt(element, phase, discrete));
        } else {
            value = animateAt(node, element, name, phase, discrete);
        }
        if (value == null) return;
        if (transform) {
            String existing = attr(node, "transform");
            result.put(name, existing == null ? value : existing + " " + value);
        } else {
            result.put(name, value);
        }
    }

    private static String animateAt(SvgNode node, SvgNode element, String target, double phase, boolean discrete) {
        List<String> values = segments(attr(element, "values"));
        if (!values.isEmpty()) {
            List<Double> times = keyTimes(attr(element, "keyTimes"), values.size());
            int index = segmentIndex(times, phase);
            if (discrete || index >= values.size() - 1) return values.get(index);
            double span = times.get(index + 1) - times.get(index);
            double fraction = span <= EPSILON ? 1.0 : clamp((phase - times.get(index)) / span);
            return interpolate(values.get(index), values.get(index + 1), fraction, false);
        }
        String from = attr(element, "from");
        String to = attr(element, "to");
        if (from != null && to != null) return interpolate(from, to, phase, discrete);
        if (to != null) {
            String base = attr(node, target);
            return base == null ? to : interpolate(base, to, phase, discrete);
        }
        String by = attr(element, "by");
        if (by != null) return additive(attr(node, target), by);
        return from;
    }

    private static double[] transformAt(SvgNode element, double phase, boolean discrete) {
        List<String> values = segments(attr(element, "values"));
        if (!values.isEmpty()) {
            List<Double> times = keyTimes(attr(element, "keyTimes"), values.size());
            int index = segmentIndex(times, phase);
            double[] current = components(values.get(index));
            if (discrete || index >= values.size() - 1) return current;
            double[] next = components(values.get(index + 1));
            if (current == null || next == null || current.length != next.length) return current;
            double span = times.get(index + 1) - times.get(index);
            double fraction = span <= EPSILON ? 1.0 : clamp((phase - times.get(index)) / span);
            return blend(current, next, fraction);
        }
        String from = attr(element, "from");
        String to = attr(element, "to");
        if (from != null && to != null) {
            double[] start = components(from);
            double[] end = components(to);
            if (start == null || end == null || start.length != end.length) return phase < MIDDLE ? start : end;
            return blend(start, end, discrete ? (phase < MIDDLE ? 0.0 : 1.0) : clamp(phase));
        }
        return components(from != null ? from : to);
    }

    private static String transformValue(String type, double[] vector) {
        if (vector == null || vector.length == 0) return null;
        String kind = type == null ? "translate" : type;
        return switch (kind) {
            case "translate" -> "translate(" + vectorText(vector, 2) + ")";
            case "scale" -> "scale(" + vectorText(vector, 2) + ")";
            case "rotate" -> "rotate(" + (vector.length >= 3 ? vectorText(vector, 3) : format(vector[0])) + ")";
            case "matrix" -> vector.length >= 6 ? "matrix(" + vectorText(vector, 6) + ")" : null;
            case "skewX" -> "skewX(" + format(vector[0]) + ")";
            case "skewY" -> "skewY(" + format(vector[0]) + ")";
            default -> null;
        };
    }

    private static String vectorText(double[] vector, int limit) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < limit && i < vector.length; i++) {
            if (i > 0) text.append(' ');
            text.append(format(vector[i]));
        }
        return text.toString();
    }

    private static String interpolate(String first, String second, double phase, boolean discrete) {
        double fraction = clamp(phase);
        if (discrete) return fraction < MIDDLE ? first : second;
        Double start = parseNumber(first);
        Double end = parseNumber(second);
        if (start != null && end != null) return format(start + (end - start) * fraction);
        int[] startColor = color(first);
        int[] endColor = color(second);
        if (startColor != null && endColor != null) return formatColor(mix(startColor, endColor, fraction));
        return fraction < MIDDLE ? first : second;
    }

    private static String additive(String base, String delta) {
        if (base == null) return delta;
        Double start = parseNumber(base);
        Double shift = parseNumber(delta);
        if (start != null && shift != null) return format(start + shift);
        int[] baseColor = color(base);
        int[] deltaColor = color(delta);
        if (baseColor != null && deltaColor != null) {
            return formatColor(new int[]{clampByte(baseColor[0] + deltaColor[0]), clampByte(baseColor[1] + deltaColor[1]),
                    clampByte(baseColor[2] + deltaColor[2]), clampByte(baseColor[3] + deltaColor[3])});
        }
        if (baseColor != null && shift != null) {
            return formatColor(new int[]{clampByte(baseColor[0] + shift), clampByte(baseColor[1] + shift),
                    clampByte(baseColor[2] + shift), baseColor[3]});
        }
        return delta;
    }

    private static int[] color(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#")) return hexColor(lower);
        if (lower.startsWith("rgb(") || lower.startsWith("rgba(")) return functionColor(lower);
        int[] named = COLORS.get(lower);
        return named == null ? null : named.clone();
    }

    private static int[] hexColor(String text) {
        String digits = text.substring(1);
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) return null;
        }
        return switch (digits.length()) {
            case 3 -> new int[]{nibble(digits.charAt(0)) * 17, nibble(digits.charAt(1)) * 17, nibble(digits.charAt(2)) * 17, 255};
            case 4 -> new int[]{nibble(digits.charAt(0)) * 17, nibble(digits.charAt(1)) * 17, nibble(digits.charAt(2)) * 17, nibble(digits.charAt(3)) * 17};
            case 6 -> new int[]{byteAt(digits, 0), byteAt(digits, 2), byteAt(digits, 4), 255};
            case 8 -> new int[]{byteAt(digits, 0), byteAt(digits, 2), byteAt(digits, 4), byteAt(digits, 6)};
            default -> null;
        };
    }

    private static int nibble(char value) {
        return Character.digit(value, 16);
    }

    private static int byteAt(String digits, int index) {
        return nibble(digits.charAt(index)) * 16 + nibble(digits.charAt(index + 1));
    }

    private static int[] functionColor(String text) {
        int open = text.indexOf('(');
        int close = text.lastIndexOf(')');
        if (open < 0 || close <= open) return null;
        String[] parts = text.substring(open + 1, close).split(",");
        if (parts.length < 3 || parts.length > 4) return null;
        int[] result = new int[]{0, 0, 0, 255};
        for (int i = 0; i < parts.length; i++) {
            Integer channel = channelValue(parts[i].trim(), i == 3);
            if (channel == null) return null;
            result[i] = channel;
        }
        return result;
    }

    private static Integer channelValue(String part, boolean alpha) {
        boolean percent = part.endsWith("%");
        String text = percent ? part.substring(0, part.length() - 1).trim() : part;
        Double value = parseNumber(text);
        if (value == null) return null;
        double scaled = percent ? value * 255.0 / 100.0 : alpha ? value * 255.0 : value;
        return clampByte(scaled);
    }

    private static int[] mix(int[] first, int[] second, double fraction) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            result[i] = clampByte(first[i] + (second[i] - first[i]) * fraction);
        }
        return result;
    }

    private static String formatColor(int[] rgba) {
        if (rgba[3] >= 255) return String.format(Locale.ROOT, "#%02x%02x%02x", rgba[0], rgba[1], rgba[2]);
        return "rgba(" + rgba[0] + ", " + rgba[1] + ", " + rgba[2] + ", " + format(rgba[3] / 255.0) + ")";
    }

    private static int clampByte(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(255.0, value)));
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "0";
        double rounded = Math.round(value * 1000.0) / 1000.0;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1.0e15) return Long.toString((long) rounded);
        String text = String.format(Locale.ROOT, "%.3f", rounded);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, end);
    }

    private static Double parseNumber(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Double seconds(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.startsWith("+")) text = text.substring(1).trim();
        if (text.isEmpty()) return null;
        double scale = 1.0;
        if (text.endsWith("ms")) {
            text = text.substring(0, text.length() - 2).trim();
            scale = 0.001;
        } else if (text.endsWith("s")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        Double value = parseNumber(text);
        if (value == null) return null;
        double result = value * scale;
        return Double.isFinite(result) ? result : null;
    }

    private static Double beginSeconds(String raw) {
        if (raw == null) return 0.0;
        if (raw.equalsIgnoreCase("click")) return 0.0;
        Double value = seconds(raw);
        return value == null || value < 0.0 ? null : value;
    }

    private record Window(double start, double duration, double repeat, boolean indefinite) {
    }

    private static Window windowOf(SvgNode element) {
        Double begin = beginSeconds(attr(element, "begin"));
        if (begin == null) return null;
        Double duration = seconds(attr(element, "dur"));
        if (duration == null || duration <= 0.0) return null;
        String repeatText = attr(element, "repeatCount");
        if (repeatText == null) return new Window(begin, duration, 1.0, false);
        if (repeatText.equalsIgnoreCase("indefinite")) return new Window(begin, duration, Double.POSITIVE_INFINITY, true);
        Double repeat = parseNumber(repeatText);
        if (repeat == null || repeat < 0.0) return new Window(begin, duration, 1.0, false);
        return new Window(begin, duration, repeat, false);
    }

    private static double progress(Window window, long timeMs) {
        double local = timeMs / 1000.0 - window.start();
        if (local < -EPSILON) return -1.0;
        if (local < 0.0) local = 0.0;
        if (!window.indefinite() && local >= window.duration() * window.repeat()) return -1.0;
        return clamp((local % window.duration()) / window.duration());
    }

    private static boolean isDiscrete(SvgNode element) {
        return "discrete".equalsIgnoreCase(attr(element, "calcMode"));
    }

    private static List<Double> keyTimes(String raw, int count) {
        List<Double> result = new ArrayList<>(count);
        if (raw != null) {
            for (String part : raw.split(";")) {
                Double value = parseNumber(part);
                if (value == null) return evenKeyTimes(count);
                result.add(clamp(value));
            }
        }
        if (result.size() != count) return evenKeyTimes(count);
        if (count > 1) {
            result.set(0, 0.0);
            result.set(count - 1, 1.0);
        }
        return result;
    }

    private static List<Double> evenKeyTimes(int count) {
        List<Double> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(count <= 1 ? 0.0 : (double) i / (count - 1));
        }
        return result;
    }

    private static int segmentIndex(List<Double> times, double phase) {
        int pick = 0;
        for (int i = 0; i < times.size(); i++) {
            if (phase + EPSILON >= times.get(i)) pick = i;
        }
        return Math.min(pick, times.size() - 1);
    }

    private static List<String> segments(String raw) {
        if (raw == null) return List.of();
        String text = raw.trim();
        if (text.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : text.split(";", -1)) {
            result.add(part.trim());
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static double[] components(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replace(',', ' ');
        if (text.isEmpty()) return null;
        String[] parts = text.split("\\s+");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Double value = parseNumber(parts[i]);
            if (value == null) return null;
            result[i] = value;
        }
        return result;
    }

    private static double[] blend(double[] first, double[] second, double fraction) {
        double[] result = new double[first.length];
        for (int i = 0; i < first.length; i++) {
            result[i] = first[i] + (second[i] - first[i]) * fraction;
        }
        return result;
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        return Math.min(value, 1.0);
    }

    private static String attr(SvgNode node, String key) {
        if (node == null) return null;
        Map<String, String> attributes = node.attributes();
        if (attributes == null) return null;
        String value = attributes.get(key);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<SvgNode> children(SvgNode node) {
        if (node == null) return List.of();
        List<SvgNode> kids = node.children();
        return kids == null ? List.of() : kids;
    }

    private static String tag(SvgNode node) {
        if (node == null) return "";
        String name = node.tag();
        if (name == null) return "";
        String trimmed = name.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0) trimmed = trimmed.substring(colon + 1);
        return trimmed;
    }

    private static boolean isAnimation(String name) {
        return name.equals("animate") || name.equals("set") || name.equals("animateTransform");
    }

    private static Map<String, int[]> namedColors() {
        return Map.ofEntries(
                Map.entry("transparent", new int[]{0, 0, 0, 0}),
                Map.entry("black", new int[]{0, 0, 0, 255}),
                Map.entry("silver", new int[]{192, 192, 192, 255}),
                Map.entry("gray", new int[]{128, 128, 128, 255}),
                Map.entry("grey", new int[]{128, 128, 128, 255}),
                Map.entry("white", new int[]{255, 255, 255, 255}),
                Map.entry("maroon", new int[]{128, 0, 0, 255}),
                Map.entry("red", new int[]{255, 0, 0, 255}),
                Map.entry("purple", new int[]{128, 0, 128, 255}),
                Map.entry("magenta", new int[]{255, 0, 255, 255}),
                Map.entry("green", new int[]{0, 128, 0, 255}),
                Map.entry("lime", new int[]{0, 255, 0, 255}),
                Map.entry("olive", new int[]{128, 128, 0, 255}),
                Map.entry("yellow", new int[]{255, 255, 0, 255}),
                Map.entry("navy", new int[]{0, 0, 128, 255}),
                Map.entry("blue", new int[]{0, 0, 255, 255}),
                Map.entry("teal", new int[]{0, 128, 128, 255}),
                Map.entry("aqua", new int[]{0, 255, 255, 255}),
                Map.entry("orange", new int[]{255, 165, 0, 255}),
                Map.entry("gold", new int[]{255, 215, 0, 255}),
                Map.entry("pink", new int[]{255, 192, 203, 255}),
                Map.entry("brown", new int[]{165, 42, 42, 255})
        );
    }
}
