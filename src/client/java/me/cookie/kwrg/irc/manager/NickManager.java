package me.cookie.kwrg.irc.manager;

import com.mojang.authlib.GameProfile;
import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NickManager {

    private static Pattern cachedPattern = null;
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static boolean isBypassed() {
        return BYPASS.get();
    }

    public static void setBypassed(boolean bypass) {
        BYPASS.set(bypass);
    }

    public static String getProtectedRealName() {
        String real = getRealPlayerName();
        if (real.length() <= 1) {
            return real;
        }
        return real.charAt(0) + "\u200C" + real.substring(1);
    }

    public static boolean isEnabled() {
        if (isBypassed()) {
            return false;
        }
        IrcConfig config = IrcConfig.get();
        return config.nickEnabled && config.customNick != null && !config.customNick.isBlank();
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
        cachedPattern = null;
        IrcConfig.saveDelayed(500);
    }

    public static String getRealPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            GameProfile profile = mc.player.getGameProfile();
            return profile.name();
        }
        if (mc.getUser() != null) {
            return mc.getUser().getName();
        }
        return "";
    }

    public static String replaceNick(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) {
            return input;
        }

        String realName = getRealPlayerName();
        if (realName.isEmpty() || !input.contains(realName)) {
            return input;
        }

        if (cachedPattern == null) {
            cachedPattern = Pattern.compile(Pattern.quote(realName), Pattern.CASE_INSENSITIVE);
        }

        return cachedPattern.matcher(input).replaceAll(getCustomNick());
    }

    private record StyledChar(int codePoint, Style style) {}

    /**
     * 核心：底层字符流劫持包装
     * 将 FormattedCharSequence 中出现的真实名字无缝替换为自定义昵称，并保留原始样式
     */
    public static FormattedCharSequence wrap(FormattedCharSequence original) {
        if (!isEnabled() || original == null) {
            return original;
        }

        String realName = getRealPlayerName();
        if (realName.isEmpty()) {
            return original;
        }

        return sink -> {
            List<StyledChar> chars = new ArrayList<>();
            original.accept((pos, style, cp) -> {
                chars.add(new StyledChar(cp, style));
                return true;
            });

            if (chars.isEmpty()) {
                return true;
            }

            int[] targetCps = realName.codePoints().toArray();
            int targetLen = targetCps.length;
            String nick = getCustomNick();
            int[] nickCps = nick.codePoints().toArray();

            List<StyledChar> modified = new ArrayList<>();
            int i = 0;
            int size = chars.size();
            boolean hasReplaced = false;

            while (i < size) {
                if (i <= size - targetLen && matches(chars, i, targetCps)) {
                    hasReplaced = true;
                    Style inheritStyle = chars.get(i).style; // 继承原名字的颜色、粗体等样式
                    for (int ncp : nickCps) {
                        modified.add(new StyledChar(ncp, inheritStyle));
                    }
                    i += targetLen;
                } else {
                    modified.add(chars.get(i));
                    i++;
                }
            }

            // 如果没有命中原名，零开销顺序回放
            List<StyledChar> output = hasReplaced ? modified : chars;
            int pos = 0;
            for (StyledChar sc : output) {
                if (!sink.accept(pos++, sc.style, sc.codePoint)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static boolean matches(List<StyledChar> chars, int start, int[] target) {
        for (int j = 0; j < target.length; j++) {
            int cp1 = Character.toLowerCase(chars.get(start + j).codePoint);
            int cp2 = Character.toLowerCase(target[j]);
            if (cp1 != cp2) {
                return false;
            }
        }
        return true;
    }
}