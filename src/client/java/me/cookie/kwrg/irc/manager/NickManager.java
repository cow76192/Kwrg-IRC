package me.cookie.kwrg.irc.manager;

import com.mojang.authlib.GameProfile;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NickManager {

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static boolean isBypassed() {
        return BYPASS.get();
    }

    public static void setBypassed(boolean bypass) {
        BYPASS.set(bypass);
    }

    public static boolean isEnabled() {
        if (isBypassed()) return false;
        return IrcConfig.get().nickEnabled;
    }

    public static String getEffectiveNick() {
        String custom = getCustomNick();
        return (custom == null || custom.isBlank()) ? getRealPlayerName() : custom;
    }

    public static void setEnabled(boolean state) {
        IrcConfig.get().nickEnabled = state;
        IrcConfig.saveDelayed(300);
    }

    public static String getCustomNick() {
        return IrcConfig.get().customNick != null ? IrcConfig.get().customNick : "";
    }

    public static void setCustomNick(String nick) {
        IrcConfig.get().customNick = nick != null ? nick.trim() : "";
        IrcConfig.saveDelayed(500);
    }

    public static HypixelRank getRank() {
        try {
            return HypixelRank.valueOf(IrcConfig.get().nickRank);
        } catch (RuntimeException e) {
            return HypixelRank.MVP_PLUS;
        }
    }

    public static void setRank(HypixelRank rank) {
        IrcConfig.get().nickRank = rank.name();
        IrcConfig.saveDelayed(300);
    }

    public static HypixelRank.PlusColor getPlusColor() {
        try {
            if ("GRAY".equals(IrcConfig.get().nickPlusColor)) return HypixelRank.PlusColor.DARK_GRAY;
            return HypixelRank.PlusColor.valueOf(IrcConfig.get().nickPlusColor);
        } catch (RuntimeException e) {
            return HypixelRank.PlusColor.RED;
        }
    }

    public static void setPlusColor(HypixelRank.PlusColor color) {
        IrcConfig.get().nickPlusColor = color.name();
        IrcConfig.saveDelayed(300);
    }

    public static HypixelRank.TagColor getTagColor() {
        try {
            return HypixelRank.TagColor.valueOf(IrcConfig.get().nickTagColor);
        } catch (RuntimeException e) {
            return HypixelRank.TagColor.GOLD;
        }
    }

    public static void setTagColor(HypixelRank.TagColor color) {
        IrcConfig.get().nickTagColor = color.name();
        IrcConfig.saveDelayed(300);
    }

    public static int getRankNameColorRgb() {
        return getRank().getNameColorRgb(getTagColor());
    }

    public static String getRealPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            GameProfile profile = mc.player.getGameProfile();
            return profile.name();
        }
        return mc.getUser().getName();
    }

    public static String protectText(String text) {
        if (text == null || text.isEmpty()) return text;
        String realName = getRealPlayerName();
        if (realName.isEmpty() || !text.contains(realName)) return text;
        return text.replace(realName, getProtectedRealName());
    }

    public static net.minecraft.network.chat.Component protectComponent(net.minecraft.network.chat.Component component) {
        if (component == null) return null;
        String fullStr = component.getString();
        String realName = getRealPlayerName();
        if (realName.isEmpty() || !fullStr.contains(realName)) return component;

        net.minecraft.network.chat.MutableComponent root = net.minecraft.network.chat.Component.empty();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                root.append(net.minecraft.network.chat.Component.literal(protectText(text)).setStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return root;
    }

    public static String getProtectedRealName() {
        String real = getRealPlayerName();
        if (real.length() <= 1) return real;
        return real.charAt(0) + "\u200C" + real.substring(1);
    }

    public static String getFormattedPrefix() {
        return getRank().getFormattedPrefix(getPlusColor(), getTagColor());
    }

    public static boolean isHypixelRankContent(String content) {
        if (content == null || content.isBlank()) return false;
        String clean = ChatNotifier.FORMATTING_PATTERN.matcher(content).replaceAll("").trim().toUpperCase(Locale.ROOT);
        return clean.equals("VIP") || clean.equals("VIP+")
                || clean.equals("MVP") || clean.equals("MVP+") || clean.equals("MVP++")
                || clean.equals("ADMIN") || clean.equals("OWNER") || clean.equals("YOUTUBE")
                || clean.equals("MOD") || clean.equals("HELPER") || clean.equals("GM")
                || clean.startsWith("PIG");
    }

    public static String replaceNick(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) return input;
        String realName = getRealPlayerName();
        if (realName.isEmpty() || !input.toLowerCase(Locale.ROOT).contains(realName.toLowerCase(Locale.ROOT))) {
            return input;
        }

        Pattern pattern = Pattern.compile("(\\[[a-zA-Z0-9+_§ ]{2,15}\\]\\s*)?" + Pattern.quote(realName), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String oldRankGroup = matcher.group(1);
            boolean hasOldRank = false;

            if (oldRankGroup != null) {
                String inside = oldRankGroup.replace("[", "").replace("]", "").trim();
                if (isHypixelRankContent(inside)) {
                    hasOldRank = true;
                }
            }

            String replacement;
            if (hasOldRank) {
                replacement = getFormattedPrefix() + getRankNameColorCode() + getEffectiveNick();
            } else {
                String prefixToKeep = oldRankGroup != null ? oldRankGroup : "";
                replacement = prefixToKeep + getRankNameColorCode() + getEffectiveNick();
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String getRankNameColorCode() {
        return getRank().getNameColorCode(getTagColor());
    }

    private record StyledChar(int codePoint, Style style) {}

    public static FormattedCharSequence wrap(FormattedCharSequence original) {
        if (!isEnabled() || original == null) return original;

        String realName = getRealPlayerName();
        if (realName.isEmpty()) return original;

        return sink -> {
            List<StyledChar> chars = new ArrayList<>();
            original.accept((pos, style, cp) -> {
                chars.add(new StyledChar(cp, style));
                return true;
            });

            if (chars.isEmpty()) return true;

            int[] targetCps = realName.codePoints().toArray();
            int targetLen = targetCps.length;
            List<StyledChar> result = new ArrayList<>();
            int i = 0;
            int size = chars.size();
            boolean hasReplaced = false;

            while (i < size) {
                if (i <= size - targetLen && matches(chars, i, targetCps)) {
                    hasReplaced = true;

                    int eraseStart = i;
                    boolean hasOldRank = false;

                    int searchIdx = i - 1;
                    while (searchIdx >= 0 && chars.get(searchIdx).codePoint == ' ') {
                        searchIdx--;
                    }

                    if (searchIdx >= 0 && chars.get(searchIdx).codePoint == ']') {
                        int closeBracket = searchIdx;
                        int openBracket = -1;
                        for (int k = closeBracket - 1; k >= Math.max(0, closeBracket - 15); k--) {
                            if (chars.get(k).codePoint == '[') {
                                openBracket = k;
                                break;
                            }
                        }

                        if (openBracket != -1) {
                            StringBuilder rankContent = new StringBuilder();
                            for (int k = openBracket + 1; k < closeBracket; k++) {
                                int cp = chars.get(k).codePoint;
                                if (Character.isLetter(cp) || cp == '+') {
                                    rankContent.appendCodePoint(cp);
                                }
                            }
                            if (isHypixelRankContent(rankContent.toString())) {
                                eraseStart = openBracket;
                                hasOldRank = true;
                            }
                        }
                    }

                    if (hasOldRank) {
                        int removeCount = i - eraseStart;
                        while (removeCount > 0 && !result.isEmpty()) {
                            result.remove(result.size() - 1);
                            removeCount--;
                        }

                        buildRankPrefixChars(result);

                        int nameColor = getRankNameColorRgb();
                        Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(nameColor));
                        for (int cp : getEffectiveNick().codePoints().toArray()) {
                            result.add(new StyledChar(cp, nameStyle));
                        }
                    } else {
                        int nameColor = getRankNameColorRgb();
                        Style originalStyle = chars.get(i).style;
                        Style coloredStyle = originalStyle.withColor(TextColor.fromRgb(nameColor));

                        for (int cp : getEffectiveNick().codePoints().toArray()) {
                            result.add(new StyledChar(cp, coloredStyle));
                        }
                    }

                    i += targetLen;
                } else {
                    result.add(chars.get(i));
                    i++;
                }
            }

            List<StyledChar> output = hasReplaced ? result : chars;
            int pos = 0;
            for (StyledChar sc : output) {
                if (!sink.accept(pos++, sc.style, sc.codePoint)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static void buildRankPrefixChars(List<StyledChar> list) {
        HypixelRank rank = getRank();
        if (rank == HypixelRank.NONE) return;

        list.addAll(parseRankPrefix(rank.getFormattedPrefix(getPlusColor(), getTagColor()), rank.getNameColorRgb(getTagColor())));
    }

    private static List<StyledChar> parseRankPrefix(String formatted, int baseRgb) {
        List<StyledChar> chars = new ArrayList<>();
        int currentRgb = baseRgb;
        int i = 0;
        while (i < formatted.length()) {
            char c = formatted.charAt(i);
            if (c == '§' && i + 1 < formatted.length()) {
                int rgb = rankCodeRgb(formatted.charAt(i + 1));
                currentRgb = rgb >= 0 ? rgb : baseRgb;
                i += 2;
                continue;
            }
            chars.add(new StyledChar(c, Style.EMPTY.withColor(TextColor.fromRgb(currentRgb))));
            i++;
        }
        return chars;
    }

    private static int rankCodeRgb(char code) {
        String key = "§" + Character.toLowerCase(code);
        for (HypixelRank.PlusColor plus : HypixelRank.PlusColor.values()) {
            if (plus.code.equals(key)) return plus.rgb;
        }
        for (HypixelRank.TagColor tag : HypixelRank.TagColor.values()) {
            if (tag.code.equals(key)) return tag.rgb;
        }
        return -1;
    }

    private static boolean matches(List<StyledChar> chars, int start, int[] target) {
        for (int j = 0; j < target.length; j++) {
            int cp1 = Character.toLowerCase(chars.get(start + j).codePoint);
            int cp2 = Character.toLowerCase(target[j]);
            if (cp1 != cp2) return false;
        }
        return true;
    }
}
