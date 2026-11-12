package me.cookie.kwrg.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class AntiNickDetector {
    private AntiNickDetector() {}

    public record Skin(UUID id, String name, String hash) {}

    public record Result(List<String> reasons, Skin candidate, boolean nickSkin) {
        public boolean suspected() {
            return !reasons.isEmpty();
        }
    }

    public static Result inspect(UUID id, String textureValue, Set<String> nickSkins) {
        if (id.version() == 2) return new Result(List.of(), null, false);
        List<String> reasons = new ArrayList<>();

        if (id.version() == 1) reasons.add("Hypixel 常见 nick UUID 特征 (v1)");
        Skin skin = decodeSkin(textureValue);

        boolean mismatch = skin != null && !id.equals(skin.id());
        boolean preset = skin != null && nickSkins.contains(skin.hash());
        if (mismatch) reasons.add("玩家 UUID 与皮肤资料 UUID 不一致");
        if (preset && !reasons.isEmpty()) reasons.add("使用已知 nick 预设皮肤");
        Skin candidate = mismatch && !preset && skin.id().version() == 4 && skin.name() != null ? skin : null;
        return new Result(List.copyOf(reasons), candidate, preset);
    }

    public static Skin decodeSkin(String value) {
        if (value == null || value.isEmpty() || value.length() > 32767) return null;
        try {
            JsonObject json = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String rawId = json.get("profileId").getAsString().replace("-", "");
            if (!rawId.matches("[a-fA-F0-9]{32}")) return null;

            UUID id = UUID.fromString(rawId.substring(0, 8) + "-" + rawId.substring(8, 12) + "-"
                    + rawId.substring(12, 16) + "-" + rawId.substring(16, 20) + "-" + rawId.substring(20));
            String name = json.has("profileName") ? json.get("profileName").getAsString() : null;
            if (name != null && !name.matches("[A-Za-z0-9_]{1,16}")) name = null;

            String hash = textureHash(json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
            return hash.isEmpty() ? null : new Skin(id, name, hash);
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException
                 | UnsupportedOperationException | ClassCastException | NullPointerException e) {
            return null;
        }
    }

    public static String textureHash(String url) {
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) return "";
            String path = uri.getPath();
            if (path == null || !path.matches("/texture/[a-fA-F0-9]{1,64}")) return "";
            return path.substring("/texture/".length()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException | NullPointerException e) {
            return "";
        }
    }
}
