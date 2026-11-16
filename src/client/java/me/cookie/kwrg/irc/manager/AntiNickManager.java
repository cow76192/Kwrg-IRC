package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.module.modules.functions.AntiNickModule;
import me.cookie.kwrg.util.AntiNickDetector;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.skyblock.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntiNickManager {
    private static final Set<String> NICK_SKINS = loadNickSkins();
    private static final Map<UUID, ProfileState> SEEN = new HashMap<>();
    private static ClientLevel world;
    private static String server = "";
    private static int ticks;

    private record ProfileState(String name, String texture, AntiNickDetector.Result result) {}

    private AntiNickManager() {}

    public static void reset() {
        SEEN.clear();
        world = null;
        server = "";
        ticks = 0;
    }

    public static void onTick(Minecraft mc) {
        if (!AntiNickModule.CONFIG.get().antiNickEnabled || mc.level == null || mc.getConnection() == null || !isHypixel(mc)) {
            if (world != null) reset();
            return;
        }

        String currentServer = SkyblockLocation.server();
        if (world != mc.level || !server.equals(currentServer)) {
            reset();
            world = mc.level;
            server = currentServer;
        }

        if (ticks++ % 10 == 0) scan(mc, false);
    }

    public static int scanNow() {
        Minecraft mc = Minecraft.getInstance();
        if (!AntiNickModule.CONFIG.get().antiNickEnabled) {
            ChatNotifier.sendInChatScreen("§e[AntiNick] 请先开启 antiNick。");
            return 0;
        }

        if (mc.level == null || mc.getConnection() == null || !isHypixel(mc)) {
            ChatNotifier.sendInChatScreen("§e[AntiNick] 请先进入 Hypixel 大厅或游戏。");
            return 0;
        }

        int count = scan(mc, true);
        ChatNotifier.sendInChatScreen("§b[AntiNick] 扫描完成，发现 " + count + " 名疑似 nick 玩家。无提示不代表未 nick。");
        return 1;
    }

    private static int scan(Minecraft mc, boolean announceAll) {
        Set<UUID> present = new HashSet<>();
        int count = 0;
        for (var info : mc.getConnection().getListedOnlinePlayers()) {
            var profile = info.getProfile();
            UUID id = profile.id();
            if (id.version() == 2 || !profile.name().matches("[A-Za-z0-9_]{1,16}")) continue;
            present.add(id);

            var textures = profile.properties().get("textures");
            String value = textures.isEmpty() ? "" : textures.iterator().next().value();
            ProfileState previous = SEEN.get(id);
            if (previous != null && previous.name().equals(profile.name()) && previous.texture().equals(value)) {
                if (previous.result().suspected()) {
                    count++;
                    if (announceAll) announce(profile.name(), id, previous.result());
                }
                continue;
            }

            var result = AntiNickDetector.inspect(id, value, NICK_SKINS);
            SEEN.put(id, new ProfileState(profile.name(), value, result));
            if (result.suspected()) {
                count++;
                if (announceAll || previous == null || !previous.name().equals(profile.name()) || !previous.result().equals(result)) {
                    announce(profile.name(), id, result);
                }
            }
        }
        SEEN.keySet().retainAll(present);
        return count;
    }

    private static void announce(String name, UUID id, AntiNickDetector.Result result) {
        StringBuilder details = new StringBuilder("展示 UUID: ").append(id)
                .append("\n依据: ").append(String.join("；", result.reasons()));
        String message = "§e[AntiNick] §f" + name + " §e疑似 nick";

        var candidate = result.candidate();
        if (candidate != null) {
            message += " §7| 推测账号: §b" + candidate.name() + " §7（皮肤来源，未证实）";
            details.append("\n候选 UUID: ").append(candidate.id())
                    .append("\n仅依据下发的皮肤资料；可能是借用皮肤，名字也可能已过期。");
        } else {
            message += result.nickSkin() ? " §7| 预设皮肤，无法推测真实账号" : " §7| 无可用真实账号线索";
        }

        ChatNotifier.sendInChatScreen(Component.literal(message).withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal(details.toString())))));
    }

    private static boolean isHypixel(Minecraft mc) {
        var current = mc.getCurrentServer();
        if (current == null) return false;

        String host = current.ip.toLowerCase(Locale.ROOT).split(":", 2)[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || host.equals("hypixel.io") || host.endsWith(".hypixel.io")
                || !SkyblockLocation.server().isEmpty();
    }

    private static Set<String> loadNickSkins() {
        try (var stream = AntiNickManager.class.getResourceAsStream("/assets/kwrg/antinick/nick-skins.txt")) {
            if (stream == null) throw new IOException("Missing nick skin list");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Set<String> hashes = new HashSet<>();
                reader.lines().filter(line -> line.matches("[a-f0-9]{1,64}")).forEach(hashes::add);
                return Set.copyOf(hashes);
            }
        } catch (IOException e) {
            CookieMain.LOGGER.warn("[AntiNick] 无法加载 nick 皮肤列表，仍使用 UUID 检测", e);
            return Set.of();
        }
    }
}
