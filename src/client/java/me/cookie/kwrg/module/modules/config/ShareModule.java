package me.cookie.kwrg.module.modules.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ConfigMigrator;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.module.ModuleManager;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ShareModule extends Module {

    private static final Gson GSON = new Gson();

    public static class PendingShare {
        public String sender;
        public String type;       // "share" 或 "invite"
        public String shareType;  // "config", "ns", "ms", "channel"
        public String rawData;    // 样式文本、频道名或配置 json
    }

    public static PendingShare currentPending = null;

    public ShareModule() {
        super("share", Category.CONFIG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    public static void openShareGui() {
        me.cookie.kwrg.gui.IrcScreen.openDirectToModule("config", "share");
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        ModuleManager.setPendingGuiQuery(true);
        try {
            IrcClient.queryOnline();
        } catch (Throwable ignored) {}

        if (currentPending != null) {
            renderImportConfirmUI(panel, currentPending);
        } else {
            renderSenderPipelineUI(panel);
        }
    }

    private static class SenderState {
        String selectedCategory = "";
        boolean isCatOpen = false;

        String payload = "";
        boolean isPayloadOpen = false;

        String targetPlayer = "";
        boolean isPlayerPickerOpen = false;
    }

    private void renderSenderPipelineUI(TertiaryPanelComponent panel) {
        renderSenderStep(panel, new SenderState());
    }

    private void renderSenderStep(TertiaryPanelComponent panel, SenderState state) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("分享与邀请"));

        String catText = state.selectedCategory.isEmpty() ? "选择分享类型 (点击展开)..." : "类型: " + state.selectedCategory.toUpperCase();
        TextInputPaneComponent catPane = new TextInputPaneComponent(catText, null).asButton(catText, () -> {
            state.isCatOpen = !state.isCatOpen;
            renderSenderStep(panel, state);
        });
        panel.child(catPane);

        if (state.isCatOpen) {
            panel.child(new PanelItemComponent("config", () -> {
                state.selectedCategory = "config";
                state.payload = "";
                state.targetPlayer = "";
                state.isCatOpen = false;
                state.isPayloadOpen = true;
                renderSenderStep(panel, state);
            }).withSpawnAnimation());

            panel.child(new PanelItemComponent("NameStyle", () -> {
                state.selectedCategory = "ns";
                state.payload = IrcConfig.get().namestyle;
                state.targetPlayer = "";
                state.isCatOpen = false;
                state.isPlayerPickerOpen = true;
                renderSenderStep(panel, state);
            }).withSpawnAnimation());

            panel.child(new PanelItemComponent("MessageStyle", () -> {
                state.selectedCategory = "ms";
                state.payload = IrcConfig.get().chatstyle;
                state.targetPlayer = "";
                state.isCatOpen = false;
                state.isPlayerPickerOpen = true;
                renderSenderStep(panel, state);
            }).withSpawnAnimation());

            panel.child(new PanelItemComponent("channel", () -> {
                state.selectedCategory = "channel";
                state.payload = "";
                state.targetPlayer = "";
                state.isCatOpen = false;
                state.isPayloadOpen = true;
                renderSenderStep(panel, state);
            }).withSpawnAnimation());
            return;
        }

        // 选择载荷
        if ("config".equals(state.selectedCategory) || "channel".equals(state.selectedCategory)) {
            String payloadLabel = "config".equals(state.selectedCategory) ? "选择配置文件" : "选择邀请频道";
            String pText = state.payload.isEmpty() ? payloadLabel + " (点击展开)..." : "目标: " + state.payload;
            TextInputPaneComponent payloadPane = new TextInputPaneComponent(pText, null).asButton(pText, () -> {
                state.isPayloadOpen = !state.isPayloadOpen;
                renderSenderStep(panel, state);
            });
            panel.child(payloadPane);

            if (state.isPayloadOpen) {
                if ("config".equals(state.selectedCategory)) {
                    List<String> configs = IrcConfig.listExistingProfiles();
                    // 过滤 default
                    configs.removeIf(cfg -> "default".equalsIgnoreCase(cfg));

                    if (configs.isEmpty()) {
                        panel.child(new PanelItemComponent("暂无自定义配置").withSpawnAnimation());
                    } else {
                        for (String cfg : configs) {
                            panel.child(new PanelItemComponent(cfg, () -> {
                                state.payload = cfg;
                                state.isPayloadOpen = false;
                                state.isPlayerPickerOpen = true;
                                renderSenderStep(panel, state);
                            }).withSpawnAnimation());
                        }
                    }
                } else {
                    List<String> channels = IrcConfig.get().savedChannels;
                    if (channels != null && !channels.isEmpty()) {
                        for (String ch : channels) {
                            panel.child(new PanelItemComponent("#" + ch, () -> {
                                state.payload = ch;
                                state.isPayloadOpen = false;
                                state.isPlayerPickerOpen = true;
                                renderSenderStep(panel, state);
                            }).withSpawnAnimation());
                        }
                    } else {
                        panel.child(new PanelItemComponent("暂无已保存频道").withSpawnAnimation());
                    }
                }
                return;
            }
        }

        // 选择玩家
        if (!state.selectedCategory.isEmpty() && (!state.payload.isEmpty() || "ns".equals(state.selectedCategory) || "ms".equals(state.selectedCategory))) {
            String playerDisplayText = !state.targetPlayer.isEmpty() ? "目标玩家: " + state.targetPlayer : "点击以选择玩家";
            TextInputPaneComponent playerPickerPane = new TextInputPaneComponent(playerDisplayText, null).asButton(playerDisplayText, () -> {
                state.isPlayerPickerOpen = !state.isPlayerPickerOpen;
                renderSenderStep(panel, state);
            });
            panel.child(playerPickerPane);

            if (state.isPlayerPickerOpen) {
                List<String> onlineList = ModuleManager.getCachedOnlinePlayers();
                if (onlineList == null || onlineList.isEmpty()) {
                    panel.child(new PanelItemComponent("暂无在线玩家").withSpawnAnimation());
                } else {
                    for (String player : onlineList) {
                        if (player.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) continue;
                        panel.child(new PanelItemComponent(
                                player,
                                PanelItemComponent.ItemType.TRIGGER,
                                (btn, click) -> {
                                    state.targetPlayer = player;
                                    state.isPlayerPickerOpen = false;
                                    renderSenderStep(panel, state);
                                },
                                null,
                                () -> player.equalsIgnoreCase(state.targetPlayer)
                        ).withSpawnAnimation());
                    }
                }
                return;
            }

            // 确认发送
            if (!state.targetPlayer.isEmpty() && !state.isPlayerPickerOpen) {
                panel.child(new PanelItemComponent(
                        "✔ 发送给: " + state.targetPlayer,
                        () -> executeSend(state, state.targetPlayer)
                ).withSpawnAnimation());
            }
        }
    }

    private static void executeSend(SenderState state, String targetPlayer) {
        if (targetPlayer == null || targetPlayer.isBlank()) {
            ChatNotifier.send("§c[Share] 请先指定目标接收玩家！", NotificationType.WARN);
            return;
        }

        switch (state.selectedCategory) {
            case "config" -> {
                if ("default".equalsIgnoreCase(state.payload)) {
                    ChatNotifier.send("§c[Share] default 默认配置不可分享！",NotificationType.WARN);
                    return;
                }
                try {
                    var path = IrcConfig.getKwrgDir().resolve(state.payload + ".json");
                    if (Files.exists(path)) {
                        String jsonStr = Files.readString(path);
                        JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
                        obj.addProperty("__profileName", state.payload);

                        if (obj.has("guiFont")) {
                            String fontFile = obj.get("guiFont").getAsString();
                            if (fontFile != null && !fontFile.isBlank() && !FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(fontFile)) {
                                Path fontPath = IrcConfig.getFontDir().resolve(fontFile);
                                String fontHash = FontManagerHelper.calculateFileHash(fontPath);
                                if (!fontHash.isEmpty()) {
                                    obj.addProperty("__fontHash", fontHash);
                                }
                            }
                        }

                        IrcClient.sendShare(targetPlayer, "share", "config", obj);
                        ChatNotifier.send("§a[Share] 已向 §f" + targetPlayer + " §a发送配置: §e" + state.payload, NotificationType.SUCCESS);
                    }
                } catch (Exception e) {
                    ChatNotifier.send("§c[Share] 读取配置文件失败: " + e.getMessage(), NotificationType.ERR);
                }
            }
            case "ns" -> {
                String ns = IrcConfig.get().namestyle;
                IrcClient.sendShare(targetPlayer, "share", "ns", ns);
                ChatNotifier.send("§a[Share] 已向 §f" + targetPlayer + " §a发送 NameStyle 样式", NotificationType.SUCCESS);
            }
            case "ms" -> {
                String ms = IrcConfig.get().chatstyle;
                IrcClient.sendShare(targetPlayer, "share", "ms", ms);
                ChatNotifier.send("§a[Share] 已向 §f" + targetPlayer + " §a发送 MessageStyle 样式", NotificationType.SUCCESS);
            }
            case "channel" -> {
                IrcClient.sendInvite(targetPlayer, "channel", state.payload);
                ChatNotifier.send("§a[Share] 已向 §f" + targetPlayer + " §a发送频道邀请: §b#" + state.payload, NotificationType.SUCCESS);
            }
        }
    }

    public static void renderImportConfirmUI(TertiaryPanelComponent panel, PendingShare pending) {
        panel.clearChildren();

        String typeDesc = switch (pending.shareType.toLowerCase()) {
            case "config" -> "Config";
            case "ns" -> "NameStyle";
            case "ms" -> "MessageStyle";
            case "channel" -> "Channel invite";
            default -> "分享内容";
        };

        panel.child(TextInfoPaneComponent.header("确认导入请求"));

        panel.child(new TextInfoPaneComponent(
                "来自玩家",
                () -> Component.literal("§e" + pending.sender),
                false
        ));

        panel.child(new TextInfoPaneComponent(
                "分享类型",
                () -> Component.literal("§b" + typeDesc),
                false
        ));

        String cleanData = unwrapDataString(pending.rawData);

        if ("ns".equalsIgnoreCase(pending.shareType) || "ms".equalsIgnoreCase(pending.shareType)) {
            String sample = "ns".equalsIgnoreCase(pending.shareType) ? pending.sender : "Hello Kwrg IRC~";
            panel.child(new TextInfoPaneComponent(
                    "效果 (点击复制)",
                    () -> ColorUtil.applyStyle(sample, cleanData),
                    () -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(cleanData);
                        ChatNotifier.send("§a[IRC] 已复制该样式: §f" + cleanData, NotificationType.SUCCESS);
                    }
            ));
        } else if ("channel".equalsIgnoreCase(pending.shareType)) {
            panel.child(new TextInfoPaneComponent(
                    "目标频道",
                    () -> Component.literal("§b#" + cleanData),
                    false
            ));
        } else if ("config".equalsIgnoreCase(pending.shareType)) {
            String profileName = "分享配置";
            String fontStatus = "默认字体";
            try {
                JsonObject obj = JsonParser.parseString(pending.rawData).getAsJsonObject();
                if (obj.has("__profileName")) {
                    profileName = obj.get("__profileName").getAsString();
                }
                if (obj.has("__fontHash")) {
                    String hash = obj.get("__fontHash").getAsString();
                    String matched = FontManagerHelper.findFontByHash(hash);
                    fontStatus = (matched != null) ? "§a匹配成功 (" + matched + ")" : "§c本地缺失 (跳过)";
                }
            } catch (Exception ignored) {}

            String finalProfileName = profileName;
            String finalFontStatus = fontStatus;

            panel.child(new TextInfoPaneComponent(
                    "配置名称",
                    () -> Component.literal("§6" + finalProfileName),
                    false
            ));
            panel.child(new TextInfoPaneComponent(
                    "字体检测",
                    () -> Component.literal(finalFontStatus),
                    false
            ));
        }

        panel.child(new PanelItemComponent(
                "✔ 确认导入 / 加入",
                () -> {
                    executeImport(pending);
                    currentPending = null;
                    panel.clearChildren();
                    ((ShareModule) ModuleManager.getModule("share")).renderSenderPipelineUI(panel);
                }
        ).withSpawnAnimation());

        panel.child(new PanelItemComponent(
                "✖ 忽略 / 拒绝",
                () -> {
                    ChatNotifier.send("§e[Share] 已忽略来自 §f" + pending.sender + " §e的分享", NotificationType.INFO);
                    currentPending = null;
                    panel.clearChildren();
                    ((ShareModule) ModuleManager.getModule("share")).renderSenderPipelineUI(panel);
                }
        ).withSpawnAnimation());
    }

    private static String unwrapDataString(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("{") && clean.contains("\"content\"")) {
            try {
                JsonObject obj = JsonParser.parseString(clean).getAsJsonObject();
                if (obj.has("content")) {
                    return obj.get("content").getAsString();
                }
            } catch (Exception ignored) {}
        }
        return clean;
    }

    private static void executeImport(PendingShare pending) {
        String cleanData = unwrapDataString(pending.rawData);

        switch (pending.shareType.toLowerCase()) {
            case "ns" -> {
                IrcConfig.get().namestyle = cleanData;
                IrcConfig.save();
                ChatNotifier.send("§a[Share] 已成功导入 NameStyle: §f" + cleanData, NotificationType.SUCCESS);
            }
            case "ms" -> {
                IrcConfig.get().chatstyle = cleanData;
                IrcConfig.save();
                ChatNotifier.send("§a[Share] 已成功导入 MessageStyle: §f" + cleanData, NotificationType.SUCCESS);
            }
            case "channel" -> {
                String ch = cleanData.replace("#", "").trim();
                IrcConfig cfg = IrcConfig.get();
                if (!cfg.savedChannels.contains(ch)) {
                    cfg.savedChannels.add(ch);
                }
                cfg.currentChannel = ch;
                cfg.channelEnabled = true;
                IrcConfig.save();
                IrcClient.sendChannelSync(ch, true, cfg.savedChannels);
                ChatNotifier.send("§a[Share] 已成功接受邀请并加入频道: §b#" + ch, NotificationType.SUCCESS);
            }
            case "config" -> {
                try {
                    JsonObject obj = JsonParser.parseString(pending.rawData).getAsJsonObject();
                    String name = obj.has("__profileName") ? obj.get("__profileName").getAsString() : "shared_" + pending.sender;
                    obj.remove("__profileName");

                    String fontHash = obj.has("__fontHash") ? obj.get("__fontHash").getAsString() : null;
                    obj.remove("__fontHash");

                    ConfigMigrator.migrate(obj);
                    IrcConfig imported = GSON.fromJson(obj, IrcConfig.class);

                    boolean fontMatched = true;
                    String originalFont = imported.guiFont;

                    if (originalFont != null && !originalFont.isBlank() && !FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(originalFont)) {
                        if (fontHash != null && !fontHash.isBlank()) {
                            String localMatchedFont = FontManagerHelper.findFontByHash(fontHash);
                            if (localMatchedFont != null) {
                                imported.guiFont = localMatchedFont;
                            } else {
                                fontMatched = false;
                                imported.guiFont = IrcConfig.get().guiFont;
                            }
                        } else {
                            Path localPath = IrcConfig.getFontDir().resolve(originalFont);
                            if (!Files.exists(localPath)) {
                                fontMatched = false;
                                imported.guiFont = IrcConfig.get().guiFont;
                            }
                        }
                    }

                    IrcConfig.getKwrgDir().toFile().mkdirs();
                    Files.writeString(IrcConfig.getKwrgDir().resolve(name + ".json"), GSON.toJson(imported));
                    IrcConfig.loadProfile(name, false);

                    if (fontMatched) {
                        if (imported.guiFont != null && !FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(imported.guiFont)) {
                            FontManagerHelper.applyFont(imported.guiFont);
                        }
                        ChatNotifier.send("§a[Share] 配置文件 §e" + name + ".json §a导入并加载成功！", NotificationType.SUCCESS);
                    } else {
                        ChatNotifier.send("§e[Share] 配置已导入，但未在本地 font 文件夹找到匹配的字体文件 (哈希不匹配)，已跳过字体加载！", NotificationType.WARN);
                    }
                } catch (Exception e) {
                    ChatNotifier.send("§c[Share] 导入配置文件失败: " + e.getMessage(), NotificationType.ERR);
                }
            }
        }
    }
}
