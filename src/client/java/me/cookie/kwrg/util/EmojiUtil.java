package me.cookie.kwrg.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiUtil {
    private static final String CONFIG_PATH = "/assets/kwrg/emoji/emoji_shortcode.json";
    private static final Map<String, String> EMOJI_MAP;

    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":([a-zA-Z0-9_+-]+):");

    static {
        Map<String, String> map;
        try (InputStream is = EmojiUtil.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                map = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
            } else {
                System.err.println("[KWRG] 无法找到资源文件: " + CONFIG_PATH);
                map = Collections.emptyMap();
            }
        } catch (Exception e) {
            e.printStackTrace();
            map = Collections.emptyMap();
        }
        EMOJI_MAP = map != null ? map : Collections.emptyMap();
    }

    public static String replaceShortcodes(String input) {
        if (input == null || input.isEmpty() || !input.contains(":")) {
            return input;
        }

        Matcher matcher = SHORTCODE_PATTERN.matcher(input);
        return matcher.replaceAll(result -> {
            String code = result.group(1);
            return EMOJI_MAP.getOrDefault(code, result.group(0));
        });
    }
}