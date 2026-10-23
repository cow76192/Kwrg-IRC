package me.cookie.kwrg.util;

import com.google.gson.stream.JsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmojiUtil {
    private static final String RESOURCE_PATH = "/assets/kwrg/emoji/emoji_shortcode.json";
    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":([a-zA-Z0-9_+-]+):");
    public static final Logger LOGGER = LoggerFactory.getLogger("EmojiUtil");

    private static volatile Map<String, String> emojiMap = Collections.emptyMap();

    private EmojiUtil() {}

    public static synchronized void reload() {
        Map<String, String> tempMap = new HashMap<>(64);

        try (InputStream is = EmojiUtil.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                System.err.println("[KWRG] 未找到默认 Emoji 资源: " + RESOURCE_PATH);
                return;
            }

            try (JsonReader reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    String emoji = reader.nextString();
                    tempMap.put(name, emoji);
                }
                reader.endObject();
            }

            emojiMap = Map.copyOf(tempMap);
            LOGGER.info("[KWRG] 成功加载 Emoji 映射，共 " + emojiMap.size() + " 项");

        } catch (Exception e) {
            LOGGER.error("[KWRG] 解析 emoji_shortcode.json 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String replaceShortcodes(String input) {
        if (input == null || input.isEmpty() || !input.contains(":")) {
            return input;
        }

        Map<String, String> map = emojiMap;
        Matcher matcher = SHORTCODE_PATTERN.matcher(input);

        return matcher.replaceAll(result -> {
            String code = result.group(1);
            return map.getOrDefault(code, result.group(0));
        });
    }

    static {
        reload();
    }
}