package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class RankModule extends Module {

    public RankModule() {
        super("rank", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String initialRank = (IrcConfig.get().rank != null && !IrcConfig.get().rank.isBlank())
                ? IrcConfig.get().rank
                : "&7[Member&7]";

        String[] lastValidDisplay = new String[]{ formatRankSafe(initialRank) };
        boolean[] isParseError = new boolean[]{ false };

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入 Rank (如 &7[Member&7])...", newRankStr -> {
            String clean = newRankStr.trim();
            if (clean.isEmpty()) {
                ChatNotifier.send("§c[IRC] Rank 不能为空！", NotificationType.WARN);
                return;
            }

            try {
                String formatted = formatRankSafe(clean);
                if (formatted != null && !formatted.isEmpty()) {
                    IrcConfig.get().rank = clean;
                    IrcConfig.save();
                    ChatNotifier.send("§a[IRC] 已成功保存 Rank: §r" + formatted, NotificationType.SUCCESS);
                } else {
                    ChatNotifier.send("§c[IRC] 无法解析当前配置！", NotificationType.WARN);
                }
            } catch (Exception e) {
                ChatNotifier.send("§c[IRC] 保存 Rank 失败: " + e.getMessage(), NotificationType.ERR);
            }
        });

        inputPane.setText(initialRank);

        inputPane.onTextChanged(typingText -> {
            String candidate = typingText.trim();
            if (candidate.isEmpty()) {
                isParseError[0] = true;
                return;
            }

            try {
                String formatted = formatRankSafe(candidate);
                if (formatted != null && !formatted.isEmpty()) {
                    lastValidDisplay[0] = formatted;
                    isParseError[0] = false;
                } else {
                    isParseError[0] = true;
                }
            } catch (Exception e) {
                isParseError[0] = true;
            }
        });

        panel.child(inputPane);

        panel.child(new TextInfoPaneComponent(
                "预览",
                () -> {
                    if (isParseError[0]) {
                        return "§c无法解析当前配置 §7(" + lastValidDisplay[0] + "§7)";
                    }
                    return lastValidDisplay[0];
                },
                false,
                false
        ));
    }

    public static int setRank(String newRank) {
        IrcConfig config = IrcConfig.get();
        config.rank = newRank;
        IrcConfig.save();
        ChatNotifier.sendInChatScreen("§a[IRC] Rank 称号已更新为: " + IrcMessageHandler.formatRankWithBrackets(newRank));
        return 1;
    }

    private static String formatRankSafe(String rawRank) {
        if (rawRank == null || rawRank.isBlank()) {
            return "§7[Member§7]";
        }

        try {
            return IrcMessageHandler.formatRankWithBrackets(rawRank);
        } catch (Throwable t) {
            String legacy = rawRank.replace('&', '§');
            if (!legacy.contains("[") && !legacy.contains("]")) {
                legacy = "§7[" + legacy + "§7]";
            }
            return legacy;
        }
    }

    public static int showRank() {
        IrcConfig config = IrcConfig.get();
        ChatNotifier.sendInChatScreen("§c[IRC] 当前 Rank: " + IrcMessageHandler.formatRankWithBrackets(config.rank) + "§c, 用法: /irc r <称号>");
        return 0;
    }
}