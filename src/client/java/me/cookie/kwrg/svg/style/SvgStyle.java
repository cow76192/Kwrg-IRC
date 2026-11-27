package me.cookie.kwrg.svg.style;

import me.cookie.kwrg.svg.SvgNode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SvgStyle {

    public record Rule(String selector, Map<String, String> declarations, int order, int specificity) {
    }

    public static List<Rule> parseCss(String css) {
        List<Rule> rules = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return rules;
        }
        String text = css.replaceAll("(?s)/\\*.*?\\*/", " ");
        int order = 0;
        int index = 0;
        while (true) {
            int open = text.indexOf('{', index);
            if (open < 0) {
                break;
            }
            int close = text.indexOf('}', open);
            if (close < 0) {
                break;
            }
            String group = text.substring(index, open).trim();
            Map<String, String> declarations = parseDeclarations(text.substring(open + 1, close));
            for (String selector : group.split(",")) {
                String single = selector.trim();
                if (single.isEmpty() || single.startsWith("@")) {
                    continue;
                }
                rules.add(new Rule(single, declarations, order++, cssSpecificity(single)));
            }
            index = close + 1;
        }
        return rules;
    }

    public static Map<String, String> parseDeclarations(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null) {
            return map;
        }
        for (String part : body.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(colon + 1).trim();
            int important = value.toLowerCase(Locale.ROOT).indexOf("!important");
            if (important >= 0) {
                value = value.substring(0, important).trim();
            }
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            map.put(key, value);
        }
        return map;
    }

    public static boolean matches(SvgNode node, String selector) {
        if (node == null || selector == null) {
            return false;
        }
        String value = selector.trim();
        int pseudo = value.indexOf(':');
        if (pseudo >= 0) {
            value = value.substring(0, pseudo).trim();
        }
        if (value.isEmpty()) {
            return false;
        }
        String[] compounds = value.split("\\s+");
        SvgNode current = node;
        if (!matchCompound(current, compounds[compounds.length - 1])) {
            return false;
        }
        int remaining = compounds.length - 2;
        while (remaining >= 0) {
            SvgNode walk = current.parent();
            SvgNode hit = null;
            while (walk != null) {
                if (matchCompound(walk, compounds[remaining])) {
                    hit = walk;
                    break;
                }
                walk = walk.parent();
            }
            if (hit == null) {
                return false;
            }
            current = hit;
            remaining--;
        }
        return true;
    }

    private static boolean matchCompound(SvgNode node, String compound) {
        if (compound.isEmpty() || compound.equals("*")) {
            return true;
        }
        int i = 0;
        while (i < compound.length() && compound.charAt(i) != '.' && compound.charAt(i) != '#') {
            i++;
        }
        String tag = compound.substring(0, i);
        String id = null;
        List<String> classes = new ArrayList<>();
        while (i < compound.length()) {
            char kind = compound.charAt(i);
            i++;
            int start = i;
            while (i < compound.length() && compound.charAt(i) != '.' && compound.charAt(i) != '#') {
                i++;
            }
            String piece = compound.substring(start, i);
            if (kind == '#') {
                id = piece;
            } else {
                classes.add(piece);
            }
        }
        if (!tag.isEmpty() && !tag.equals(node.tag())) {
            return false;
        }
        if (id != null && !id.equals(node.id())) {
            return false;
        }
        if (!classes.isEmpty()) {
            String raw = node.attribute("class");
            if (raw == null) {
                return false;
            }
            List<String> owned = List.of(raw.trim().split("\\s+"));
            if (!owned.containsAll(classes)) {
                return false;
            }
        }
        return true;
    }

    private static int cssSpecificity(String selector) {
        int total = 0;
        for (String compound : selector.trim().split("\\s+")) {
            int ids = 0;
            int classes = 0;
            int tags = 0;
            int i = 0;
            while (i < compound.length() && compound.charAt(i) != '.' && compound.charAt(i) != '#') {
                i++;
            }
            if (i > 0) {
                tags = 1;
            }
            while (i < compound.length()) {
                char kind = compound.charAt(i);
                i++;
                while (i < compound.length() && compound.charAt(i) != '.' && compound.charAt(i) != '#') {
                    i++;
                }
                if (kind == '#') {
                    ids++;
                } else if (kind == '.') {
                    classes++;
                }
            }
            total += ids * 100 + classes * 10 + tags;
        }
        return total;
    }



    private static final Set<String> INHERITED = Set.of(
            "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity",
            "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-miterlimit",
            "font-size", "font-family", "font-weight", "font-style", "text-anchor", "color", "visibility"
    );

    private static final Set<String> PRESENTATION = Set.of(
            "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity",
            "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-miterlimit", "stroke-dashoffset",
            "font-size", "font-family", "font-weight", "font-style", "text-anchor", "color", "visibility",
            "opacity", "display", "clip-path", "mask", "filter", "stop-color", "stop-opacity", "letter-spacing"
    );

    private static final Map<String, Color> NAMED = new HashMap<>();

    private final Map<String, String> props = new HashMap<>();

    static {
        NAMED.put("black", new Color(0x000000));
        NAMED.put("white", new Color(0xFFFFFF));
        NAMED.put("red", new Color(0xFF0000));
        NAMED.put("lime", new Color(0x00FF00));
        NAMED.put("green", new Color(0x008000));
        NAMED.put("blue", new Color(0x0000FF));
        NAMED.put("yellow", new Color(0xFFFF00));
        NAMED.put("cyan", new Color(0x00FFFF));
        NAMED.put("aqua", new Color(0x00FFFF));
        NAMED.put("magenta", new Color(0xFF00FF));
        NAMED.put("fuchsia", new Color(0xFF00FF));
        NAMED.put("silver", new Color(0xC0C0C0));
        NAMED.put("gray", new Color(0x808080));
        NAMED.put("grey", new Color(0x808080));
        NAMED.put("maroon", new Color(0x800000));
        NAMED.put("olive", new Color(0x808000));
        NAMED.put("navy", new Color(0x000080));
        NAMED.put("teal", new Color(0x008080));
        NAMED.put("purple", new Color(0x800080));
        NAMED.put("orange", new Color(0xFFA500));
        NAMED.put("pink", new Color(0xFFC0CB));
        NAMED.put("brown", new Color(0xA52A2A));
        NAMED.put("gold", new Color(0xFFD700));
        NAMED.put("violet", new Color(0xEE82EE));
        NAMED.put("indigo", new Color(0x4B0082));
        NAMED.put("tomato", new Color(0xFF6347));
        NAMED.put("coral", new Color(0xFF7F50));
        NAMED.put("salmon", new Color(0xFA8072));
        NAMED.put("crimson", new Color(0xDC143C));
        NAMED.put("khaki", new Color(0xF0E68C));
        NAMED.put("wheat", new Color(0xF5DEB3));
        NAMED.put("tan", new Color(0xD2B48C));
        NAMED.put("azure", new Color(0xF0FFFF));
        NAMED.put("beige", new Color(0xF5F5DC));
        NAMED.put("ivory", new Color(0xFFFFF0));
        NAMED.put("whitesmoke", new Color(0xF5F5F5));
        NAMED.put("lightgray", new Color(0xD3D3D3));
        NAMED.put("lightgrey", new Color(0xD3D3D3));
        NAMED.put("darkgray", new Color(0xA9A9A9));
        NAMED.put("darkgrey", new Color(0xA9A9A9));
        NAMED.put("skyblue", new Color(0x87CEEB));
        NAMED.put("lightblue", new Color(0xADD8E6));
        NAMED.put("royalblue", new Color(0x4169E1));
        NAMED.put("steelblue", new Color(0x4682B4));
        NAMED.put("dodgerblue", new Color(0x1E90FF));
        NAMED.put("deepskyblue", new Color(0x00BFFF));
        NAMED.put("slateblue", new Color(0x6A5ACD));
        NAMED.put("midnightblue", new Color(0x191970));
        NAMED.put("seagreen", new Color(0x2E8B57));
        NAMED.put("forestgreen", new Color(0x228B22));
        NAMED.put("springgreen", new Color(0x00FF7F));
        NAMED.put("yellowgreen", new Color(0x9ACD32));
        NAMED.put("olivedrab", new Color(0x6B8E23));
        NAMED.put("orangered", new Color(0xFF4500));
        NAMED.put("firebrick", new Color(0xB22222));
        NAMED.put("darkorange", new Color(0xFF8C00));
        NAMED.put("darkred", new Color(0x8B0000));
        NAMED.put("darkgreen", new Color(0x006400));
        NAMED.put("darkblue", new Color(0x00008B));
        NAMED.put("goldenrod", new Color(0xDAA520));
        NAMED.put("turquoise", new Color(0x40E0D0));
        NAMED.put("orchid", new Color(0xDA70D6));
        NAMED.put("plum", new Color(0xDDA0DD));
        NAMED.put("chocolate", new Color(0xD2691E));
        NAMED.put("peru", new Color(0xCD853F));
        NAMED.put("sienna", new Color(0xA0522D));
        NAMED.put("mintcream", new Color(0xF5FFFA));
    }

    private SvgStyle() {
    }

    public static SvgStyle root() {
        SvgStyle style = new SvgStyle();
        style.props.put("fill", "black");
        style.props.put("fill-opacity", "1");
        style.props.put("fill-rule", "nonzero");
        style.props.put("stroke", "none");
        style.props.put("stroke-width", "1");
        style.props.put("stroke-opacity", "1");
        style.props.put("stroke-linecap", "butt");
        style.props.put("stroke-linejoin", "miter");
        style.props.put("font-size", "12");
        style.props.put("font-family", "SansSerif");
        style.props.put("text-anchor", "start");
        style.props.put("visibility", "visible");
        style.props.put("color", "black");
        return style;
    }

    public static SvgStyle of(SvgStyle parent, SvgNode node, List<Rule> rules, Map<String, String> overrides) {
        SvgStyle style = new SvgStyle();
        for (Map.Entry<String, String> entry : parent.props.entrySet()) {
            if (INHERITED.contains(entry.getKey())) {
                style.props.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : node.attributes().entrySet()) {
            if (PRESENTATION.contains(entry.getKey())) {
                style.props.put(entry.getKey(), entry.getValue());
            }
        }
        if (rules != null) {
            for (Rule rule : rules) {
                if (matches(node, rule.selector())) {
                    style.props.putAll(rule.declarations());
                }
            }
        }
        String inline = node.attribute("style");
        if (inline != null) {
            style.props.putAll(parseDeclarations(inline));
        }
        if (overrides != null && !overrides.isEmpty()) {
            style.props.putAll(overrides);
        }
        return style;
    }

    public String get(String key) {
        return props.get(key);
    }

    public String get(String key, String fallback) {
        String value = props.get(key);
        return value == null ? fallback : value;
    }

    public float number(String key, float fallback) {
        return parseNumber(props.get(key), fallback);
    }

    public float ratio(String key, float fallback) {
        String value = props.get(key);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("%")) {
            return parseNumber(trimmed.substring(0, trimmed.length() - 1), fallback * 100.0f) / 100.0f;
        }
        return parseNumber(trimmed, fallback);
    }

    public float opacity(String key, float fallback) {
        String value = props.get(key);
        if (value == null) {
            return fallback;
        }
        float parsed = value.trim().endsWith("%")
                ? parseNumber(value.trim().substring(0, value.trim().length() - 1), fallback * 100.0f) / 100.0f
                : parseNumber(value, fallback);
        if (parsed < 0.0f) {
            return 0.0f;
        }
        if (parsed > 1.0f) {
            return 1.0f;
        }
        return parsed;
    }

    public boolean visible() {
        if ("none".equals(props.get("display"))) {
            return false;
        }
        String visibility = props.get("visibility");
        return visibility == null || (!"hidden".equals(visibility) && !"collapse".equals(visibility));
    }

    public Color color(String key, Color fallback) {
        String raw = props.get(key);
        if ("currentColor".equals(raw)) {
            raw = props.get("color");
        }
        Color parsed = parseColor(raw);
        return parsed == null ? fallback : parsed;
    }

    public Map<String, String> props() {
        return props;
    }

    public static float parseNumber(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                if ((c == 'e' || c == 'E') && end < value.length()) {
                    end--;
                    continue;
                }
                break;
            }
            end--;
        }
        if (end == 0) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.substring(0, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Color parseColor(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("none") || value.equals("transparent")) {
            return null;
        }
        if (value.startsWith("#")) {
            return parseHex(value.substring(1));
        }
        if (value.startsWith("rgb")) {
            return parseRgb(value);
        }
        return NAMED.get(value);
    }

    private static Color parseHex(String hex) {
        try {
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                return new Color(r, g, b);
            }
            if (hex.length() == 4) {
                int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                int a = Integer.parseInt(hex.substring(3, 4), 16) * 17;
                return new Color(r, g, b, a);
            }
            if (hex.length() == 6) {
                return new Color((int) Long.parseLong(hex, 16));
            }
            if (hex.length() == 8) {
                long packed = Long.parseLong(hex, 16);
                int a = (int) ((packed >> 24) & 0xFF);
                int r = (int) ((packed >> 16) & 0xFF);
                int g = (int) ((packed >> 8) & 0xFF);
                int b = (int) (packed & 0xFF);
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static Color parseRgb(String raw) {
        int open = raw.indexOf('(');
        int close = raw.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        String[] parts = raw.substring(open + 1, close).replace("/", " ").split("[, ]+");
        float[] channels = {0.0f, 0.0f, 0.0f, 1.0f};
        int count = 0;
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty() || count >= 4) {
                continue;
            }
            if (token.endsWith("%")) {
                float ratio = parseNumber(token.substring(0, token.length() - 1), 0.0f) / 100.0f;
                channels[count] = count < 3 ? ratio * 255.0f : ratio;
            } else {
                channels[count] = parseNumber(token, 0.0f);
            }
            count++;
        }
        if (count < 3) {
            return null;
        }
        return new Color(clamp255(channels[0]), clamp255(channels[1]), clamp255(channels[2]), clamp255(channels[3] * 255.0f));
    }

    private static int clamp255(float value) {
        if (value < 0.0f) {
            return 0;
        }
        if (value > 255.0f) {
            return 255;
        }
        return Math.round(value);
    }
}
