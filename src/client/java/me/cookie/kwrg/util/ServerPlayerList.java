package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ServerPlayerList {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private ServerPlayerList() {}

    public record Entry(UUID id, String name, String textureValue) {
        public boolean hasValidName() {
            return name != null && VALID_NAME.matcher(name).matches();
        }

        public boolean isNpcUuid() {
            return id.version() == 2;
        }

        public boolean isRealPlayer() {
            return !isNpcUuid() && hasValidName();
        }
    }

    public static List<Entry> getListedPlayers() {
        return getListedPlayers(Minecraft.getInstance());
    }

    public static List<Entry> getListedPlayers(Minecraft mc) {
        if (mc == null || mc.level == null || mc.getConnection() == null) {
            return List.of();
        }

        var onlinePlayers = mc.getConnection().getListedOnlinePlayers();
        List<Entry> entries = new ArrayList<>(onlinePlayers.size());
        for (var info : onlinePlayers) {
            var profile = info.getProfile();
            if (profile == null || profile.id() == null || profile.name() == null) continue;

            var textures = profile.properties().get("textures");
            String value = textures.isEmpty() ? "" : textures.iterator().next().value();
            entries.add(new Entry(profile.id(), profile.name(), value == null ? "" : value));
        }
        return List.copyOf(entries);
    }

    public static List<Entry> getRealListedPlayers() {
        return getRealListedPlayers(Minecraft.getInstance());
    }

    public static List<Entry> getRealListedPlayers(Minecraft mc) {
        return getListedPlayers(mc).stream()
                .filter(Entry::isRealPlayer)
                .toList();
    }
}