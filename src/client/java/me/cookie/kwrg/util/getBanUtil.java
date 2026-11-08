package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class getBanUtil {

    public record BanTemplate(String key, String name, List<String> lines) {}

    private static final Map<String, BanTemplate> BAN_TEMPLATES = new LinkedHashMap<>();

    static {
        register("cheating", "Cheating", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fCheating through the use of unfair game advantages.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("cheating_backforward", "Cheating_Backforward", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fCheating through the use of unfair game advantages.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("chatban", "Chatban", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fExtreme Chat Infraction",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("badname", "Badname", Arrays.asList(
                "&cYou are currently blocked from joining this server!",
                "&7",
                "&7Reason: &r&fYour username, %REASON%, is not allowed on the server and is breaking our rules.",
                "&cFind out more: &bhttps://www.hypixel.net/rules",
                "&7",
                "&cPlease change your Minecraft username before trying to join again.",
                "&cIf you believe your name has been falsely blocked, contact &bhttps://www.hypixel.net/appeal"
        ));

        register("wipeban", "Wipeban", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fBoosting detected on one or multiple SkyBlock profiles.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("bug", "Bug", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fExploiting a bug or issue within the server and using it to your advantage.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("security", "Security", Arrays.asList(
                "&cYour account has been blocked.",
                "&7",
                "&7Reason: &r&fSuspicious activity has been detected on your account.",
                "&7Find out more: &b&nhttps://www.hypixel.net/security-block",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("security_old", "Security_old", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fYour account has a security alert, please secure it and contact appeals.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("security_accept", "Security_accept", Arrays.asList(
                "&cYour account is temporarily blocked for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fYour accounts security appeal was processed and the account has entered a recovery phase and will be able to access the server again afterwards.",
                "&7Find out more: &b&nhttps://www.hypixel.net/security-block",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("badbuilding", "Badbuilding", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fCreating a build or drawing which is not appropriate on the server.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("watchdogban", "Watchdogban", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fWATCHDOG CHEAT DETECTION &7&o[AB-328572]",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("negative_behaviour", "Negative_Behaviour", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fExtreme Negative Behaviour[F]",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("chargeback", "Chargeback", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fChargeback: for more info and appeal, pleace visit support.hypixel.net.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("cross_teaming", "Cross_teaming", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fCross teaming with an enemy team or player.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("ytban", "YtBan", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fPlease contact creators@hypixel.net for assistance.",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("fraud", "Fraud", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fFraud: Please visit support.hypixel.net for clarifications or questions.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("badskin", "BadSkin", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fUsing inappropriate skins or capes on the server.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("boosting", "Boosting", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fBoosting your account to improve your stats.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("acc_del", "acc_del", Arrays.asList(
                "&cYou are permanently banned from this server!",
                "&7",
                "&7Reason: &r&fUpon request, data for this user has been deleted.",
                "&7Find out more: &b&nhttps://support.hypixel.net",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));

        register("sabotaging", "sabotaging", Arrays.asList(
                "&cYou are temporarily banned for &f%DURATION% &cfrom this server!",
                "&7",
                "&7Reason: &r&fSabotaging the gameplay of other players on your team.",
                "&7Find out more: &b&nhttps://www.hypixel.net/appeal",
                "&7",
                "&7Ban ID: &f#%banid%",
                "&7Sharing your Ban ID may affect the processing of your appeal!"
        ));
    }

    private static void register(String key, String name, List<String> lines) {
        BAN_TEMPLATES.put(key.toLowerCase(), new BanTemplate(key, name, lines));
    }

    public static Set<String> getBanTypes() {
        return BAN_TEMPLATES.keySet();
    }

    public static int triggerBan(String type, double extraDelaySec, String durationArg) {
        BanTemplate template = BAN_TEMPLATES.get(type.toLowerCase());
        if (template == null) {
            ChatNotifier.sendInChatScreen("§c[IRC] 未知的封禁类型: " + type);
            return 0;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener initialListener = mc.getConnection();
        if (mc.player == null || initialListener == null) {
            ChatNotifier.sendInChatScreen("§c[IRC] 当前不在服务器或世界中！");
            return 0;
        }

        final Connection targetConnection = initialListener.getConnection();
        if (!targetConnection.isConnected()) {
            ChatNotifier.sendInChatScreen("§c[IRC] 当前网络连接已断开！");
            return 0;
        }

        String duration = (durationArg == null || durationArg.isBlank()) ? "29d 23h 59m 59s" : durationArg;
        String playerName = mc.player.getName().getString();
        String reason = (durationArg == null || durationArg.isBlank()) ? playerName : durationArg;
        String banId = generateBanId();

        StringBuilder sb = new StringBuilder();
        List<String> lines = template.lines();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                    .replace("&", "§")
                    .replace("%DURATION%", duration)
                    .replace("%banid%", banId)
                    .replace("%REASON%", reason);
            sb.append(line);
            if (i < lines.size() - 1) {
                sb.append("\n");
            }
        }
        Component kickMessage = Component.literal(sb.toString());

        long initialDelayMs = Math.max(0, (long) (extraDelaySec * 1000));
        long limboToKickDelayMs = ThreadLocalRandom.current().nextLong(1300, 2501);

        if (initialDelayMs > 0) {
            ChatNotifier.sendInChatScreen(String.format("§a[IRC] 将在 %.1f 秒后触发 §e%s §a封禁效果...", extraDelaySec, template.name()));
        }

        // 第一阶段：等待设定的触发延迟 -> 在主线程检查连接并发送 /limbo
        CompletableFuture.delayedExecutor(initialDelayMs, TimeUnit.MILLISECONDS).execute(() -> {
            mc.execute(() -> {
                ClientPacketListener currentListener = mc.getConnection();
                if (mc.player == null || currentListener == null
                        || currentListener.getConnection() != targetConnection
                        || !targetConnection.isConnected()) {
                    return;
                }

                currentListener.sendCommand("limbo");

                // 第二阶段：确认第一阶段成功后，再启动 1.3s ~ 2.5s 计时 -> 断开原始连接并显示 Ban 界面
                CompletableFuture.delayedExecutor(limboToKickDelayMs, TimeUnit.MILLISECONDS).execute(() -> {
                    mc.execute(() -> {
                        ClientPacketListener latestListener = mc.getConnection();
                        if (latestListener != null
                                && latestListener.getConnection() == targetConnection
                                && targetConnection.isConnected()) {
                            targetConnection.disconnect(kickMessage);
                        }
                    });
                });
            });
        });

        return 1;
    }
    private static String generateBanId() {
        String chars = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}