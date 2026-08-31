package me.cookie.kwrg.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern RGB_PATTERN = Pattern.compile("(?:a?rgb)\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*\\d+)?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HSL_PATTERN = Pattern.compile("(?:a?hsl)\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*,\\s*(\\d+(?:\\.\\d+)?)%?\\s*,\\s*(\\d+(?:\\.\\d+)?)%?(?:\\s*,\\s*\\d+(?:\\.\\d+)?%?)?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("(?:linear-)?gradient\\s*\\((.+)\\)", Pattern.CASE_INSENSITIVE);

    public static Component applyStyle(String text, String styleConfig) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (styleConfig == null || styleConfig.isBlank()) return Component.literal(text);

        String raw = styleConfig.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        String transformedText = text;
        if (lower.contains("uppercase") || lower.contains("text-transform: uppercase")) {
            transformedText = text.toUpperCase(Locale.ROOT);
        } else if (lower.contains("lowercase") || lower.contains("text-transform: lowercase")) {
            transformedText = text.toLowerCase(Locale.ROOT);
        } else if (lower.contains("capitalize") || lower.contains("text-transform: capitalize")) {
            transformedText = capitalizeWords(text);
        }

        boolean bold = lower.contains("bold") || lower.contains("font-weight: bold") || lower.contains("&l") || lower.contains("§l");
        boolean italic = lower.contains("italic") || lower.contains("font-style: italic") || lower.contains("&o") || lower.contains("§o");
        boolean underlined = lower.contains("underline") || lower.contains("text-decoration: underline") || lower.contains("&n") || lower.contains("§n");
        boolean strikethrough = lower.contains("strikethrough") || lower.contains("text-decoration: line-through") || lower.contains("&m") || lower.contains("§m");
        boolean obfuscated = lower.contains("obfuscated") || lower.contains("&k") || lower.contains("§k");

        // 默认让 rainbow 以及带有 flow/wave/animate 关键词，或任意渐变预设支持滚动
        boolean isAnimated = lower.contains("rainbow") || lower.contains("animate")
                || lower.contains("flow") || lower.contains("wave") || lower.contains("scroll");

        // 3秒一个完整周期（速度可以通过改 3000L 调节）
        float timePhase = (float) ((System.currentTimeMillis() % 3000L) / 3000.0);

        FontDescription fontDescription = null;
        if (lower.contains("alt") || lower.contains("font-family: alt") || lower.contains("sga")) {
            fontDescription = new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
        } else if (lower.contains("uniform") || lower.contains("font-family: uniform")) {
            fontDescription = new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));
        }

        // 1. 彩虹流光
        if (lower.startsWith("rainbow") || lower.contains("rainbow")) {
            return buildRainbowComponent(transformedText, timePhase, bold, italic, underlined, strikethrough, obfuscated, fontDescription);
        }

        // 2. 预设颜色流光（sunset, ocean, fire, sakura, neon, gold, purple 等）
        Color[] presetColors = getPresetColors(lower);
        if (presetColors != null) {
            return buildGradientFlowComponent(transformedText, presetColors, isAnimated ? timePhase : 0.0F, bold, italic, underlined, strikethrough, obfuscated, fontDescription);
        }

        // 3. CSS linear-gradient 流光
        Matcher gradMatcher = GRADIENT_PATTERN.matcher(raw);
        if (gradMatcher.find()) {
            Color[] colors = parseColorList(gradMatcher.group(1));
            if (colors.length >= 2) {
                return buildGradientFlowComponent(transformedText, colors, isAnimated ? timePhase : 0.0F, bold, italic, underlined, strikethrough, obfuscated, fontDescription);
            }
        }

        // 4. 单色
        Color singleColor = parseSingleColor(raw);
        if (singleColor != null) {
            Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(singleColor.getRGB() & 0xFFFFFF))
                    .withBold(bold)
                    .withItalic(italic)
                    .withUnderlined(underlined)
                    .withStrikethrough(strikethrough)
                    .withObfuscated(obfuscated)
                    .withFont(fontDescription);
            return Component.literal(transformedText).withStyle(style);
        }

        String legacy = raw.replace('&', '§') + transformedText + "§r";
        return Component.literal(legacy);
    }

    /**
     * 彩虹平滑滚动
     */
    private static Component buildRainbowComponent(String text, float timePhase, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated, FontDescription font) {
        MutableComponent root = Component.empty();
        int[] codePoints = text.codePoints().toArray();
        int length = codePoints.length;

        for (int i = 0; i < length; i++) {
            // 时间相位 + 字符间距偏移
            float hue = (timePhase + (float) i * 0.05F) % 1.0F;
            if (hue < 0) hue += 1.0F;
            int rgb = Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF;

            Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(rgb))
                    .withBold(bold)
                    .withItalic(italic)
                    .withUnderlined(underlined)
                    .withStrikethrough(strikethrough)
                    .withObfuscated(obfuscated)
                    .withFont(font);

            root.append(Component.literal(new String(Character.toChars(codePoints[i]))).withStyle(style));
        }
        return root;
    }

    /**
     * 多色渐变平滑滚动核心引擎（支持任意多颜色列表，无缝循环滚动）
     */
    private static Component buildGradientFlowComponent(String text, Color[] colors, float timePhase, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated, FontDescription font) {
        MutableComponent root = Component.empty();
        int[] codePoints = text.codePoints().toArray();
        int length = codePoints.length;

        if (colors == null || colors.length == 0) {
            return Component.literal(text);
        }

        // 如果只有一种颜色，直接单色返回
        if (colors.length == 1) {
            Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(colors[0].getRGB() & 0xFFFFFF))
                    .withBold(bold)
                    .withItalic(italic)
                    .withUnderlined(underlined)
                    .withStrikethrough(strikethrough)
                    .withObfuscated(obfuscated)
                    .withFont(font);
            return Component.literal(text).withStyle(style);
        }

        // 构造首尾闭合的调色板（使渐变滚动首尾能够无缝连接）
        Color[] cycleColors;
        if (timePhase > 0.0F) {
            cycleColors = new Color[colors.length + 1];
            System.arraycopy(colors, 0, cycleColors, 0, colors.length);
            cycleColors[colors.length] = colors[0]; // 尾部连回头部
        } else {
            cycleColors = colors;
        }

        int segments = cycleColors.length - 1;

        for (int i = 0; i < length; i++) {
            float progress;
            if (timePhase > 0.0F) {
                // 结合时间与字间距循环滚动
                float charOffset = (float) i * 0.06F;
                progress = (charOffset - timePhase) % 1.0F;
                if (progress < 0) progress += 1.0F;
            } else {
                // 静态线性渐变
                progress = (length <= 1) ? 0 : (float) i / (float) (length - 1);
            }

            float scaled = progress * segments;
            int index = Math.min((int) scaled, segments - 1);
            float factor = scaled - index;

            Color c1 = cycleColors[index];
            Color c2 = cycleColors[index + 1];

            // 线性插值颜色计算
            int r = (int) (c1.getRed() + factor * (c2.getRed() - c1.getRed()));
            int g = (int) (c1.getGreen() + factor * (c2.getGreen() - c1.getGreen()));
            int b = (int) (c1.getBlue() + factor * (c2.getBlue() - c1.getBlue()));
            int rgb = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);

            Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(rgb))
                    .withBold(bold)
                    .withItalic(italic)
                    .withUnderlined(underlined)
                    .withStrikethrough(strikethrough)
                    .withObfuscated(obfuscated)
                    .withFont(font);

            root.append(Component.literal(new String(Character.toChars(codePoints[i]))).withStyle(style));
        }
        return root;
    }

    private static Color[] getPresetColors(String name) {
        if (name.contains("sunset")) {
            return new Color[]{new Color(0xFF5E62), new Color(0xFF9966), new Color(0xE056FD)};
        } else if (name.contains("ocean")) {
            return new Color[]{new Color(0x2B5876), new Color(0x4E4376), new Color(0x00D2FF)};
        } else if (name.contains("fire")) {
            return new Color[]{new Color(0xFF0844), new Color(0xFFB199), new Color(0xF6D365)};
        } else if (name.contains("sakura")) {
            return new Color[]{new Color(0xFF9A9E), new Color(0xFECFEF), new Color(0xFFA6B9)};
        } else if (name.contains("neon")) {
            return new Color[]{new Color(0x00F2FE), new Color(0x4FACFE), new Color(0x00FF87)};
        } else if (name.contains("gold")) {
            return new Color[]{new Color(0xF12711), new Color(0xF5AF19), new Color(0xFFDF00)};
        } else if (name.contains("purple")) {
            return new Color[]{new Color(0x8A2387), new Color(0xE94057), new Color(0xF27121)};
        }
        return null;
    }

    public static Color parseSingleColor(String str) {
        if (str == null || str.isBlank()) return null;

        Matcher hslMatcher = HSL_PATTERN.matcher(str);
        if (hslMatcher.find()) {
            float h = Float.parseFloat(hslMatcher.group(1)) % 360.0F / 360.0F;
            float s = Float.parseFloat(hslMatcher.group(2)) / 100.0F;
            float l = Float.parseFloat(hslMatcher.group(3)) / 100.0F;
            return hslToRgb(h, s, l);
        }

        Matcher rgbMatcher = RGB_PATTERN.matcher(str);
        if (rgbMatcher.find()) {
            int r = Integer.parseInt(rgbMatcher.group(1));
            int g = Integer.parseInt(rgbMatcher.group(2));
            int b = Integer.parseInt(rgbMatcher.group(3));
            return new Color(Math.clamp(r, 0, 255), Math.clamp(g, 0, 255), Math.clamp(b, 0, 255));
        }

        Matcher hexMatcher = HEX_PATTERN.matcher(str);
        if (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            return new Color(Integer.parseInt(hex, 16));
        }

        return null;
    }

    private static Color[] parseColorList(String colorListStr) {
        List<Color> result = new ArrayList<>();
        String[] parts = colorListStr.split(",");
        for (String part : parts) {
            Color c = parseSingleColor(part.trim());
            if (c != null) {
                result.add(c);
            }
        }
        return result.toArray(new Color[0]);
    }

    private static Color hslToRgb(float h, float s, float l) {
        float q = l < 0.5F ? l * (1.0F + s) : l + s - l * s;
        float p = 2.0F * l - q;
        float r = hueToRgb(p, q, h + 1.0F / 3.0F);
        float g = hueToRgb(p, q, h);
        float b = hueToRgb(p, q, h - 1.0F / 3.0F);
        return new Color(Math.clamp((int) (r * 255), 0, 255), Math.clamp((int) (g * 255), 0, 255), Math.clamp((int) (b * 255), 0, 255));
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0F) t += 1.0F;
        if (t > 1.0F) t -= 1.0F;
        if (t < 1.0F / 6.0F) return p + (q - p) * 6.0F * t;
        if (t < 1.0F / 2.0F) return q;
        if (t < 2.0F / 3.0F) return p + (q - p) * (2.0F / 3.0F - t) * 6.0F;
        return p;
    }

    private static String capitalizeWords(String str) {
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    sb.append(words[i].substring(1).toLowerCase());
                }
            }
            if (i < words.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    public static boolean isValidStyle(String styleConfig) {
        if (styleConfig == null || styleConfig.isBlank()) return true;

        String raw = styleConfig.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher gradMatcher = GRADIENT_PATTERN.matcher(raw);
        if (gradMatcher.find()) {
            Color[] colors = parseColorList(gradMatcher.group(1));
            if (colors == null || colors.length < 2) return false;
            raw = gradMatcher.replaceAll("").trim();
            lower = raw.toLowerCase(Locale.ROOT);
        }

        raw = RGB_PATTERN.matcher(raw).replaceAll("").trim();
        raw = HSL_PATTERN.matcher(raw).replaceAll("").trim();
        raw = HEX_PATTERN.matcher(raw).replaceAll("").trim();
        lower = raw.toLowerCase(Locale.ROOT);

        if (raw.isBlank()) return true;

        String[] tokens = lower.split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (isKnownToken(token)) continue;
            if (token.matches("(?i)[&§][0-9a-fk-or]")) continue;

            return false;
        }

        return true;
    }

    private static boolean isKnownToken(String token) {
        return token.equals("rainbow") || token.equals("animate") || token.equals("flow") || token.equals("wave") || token.equals("scroll")
                || token.equals("sunset") || token.equals("ocean") || token.equals("fire") || token.equals("sakura")
                || token.equals("neon") || token.equals("gold") || token.equals("purple")
                || token.equals("bold") || token.equals("italic") || token.equals("underline")
                || token.equals("strikethrough") || token.equals("obfuscated")
                || token.equals("uppercase") || token.equals("lowercase") || token.equals("capitalize")
                || token.equals("alt") || token.equals("sga") || token.equals("uniform");
    }

    public static boolean isAnimatedStyle(String styleConfig) {
        if (styleConfig == null || styleConfig.isBlank()) return false;
        String lower = styleConfig.toLowerCase(Locale.ROOT);
        return lower.contains("rainbow") || lower.contains("animate")
                || lower.contains("flow") || lower.contains("wave") || lower.contains("scroll");
    }
}