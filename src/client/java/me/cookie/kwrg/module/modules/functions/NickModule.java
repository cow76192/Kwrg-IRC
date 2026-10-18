package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.HypixelRank;
import me.cookie.kwrg.irc.manager.NickManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;

public class NickModule extends Module {

    public NickModule() {
        super(
                "Nick",
                Category.FUNCTIONS,
                PanelItemComponent.ItemType.TOGGLE,
                NickManager::isEnabled
        );
    }

    @Override
    public void onExecute() {
        boolean newState = !NickManager.isEnabled();
        NickManager.setEnabled(newState);

        String currentRank = NickManager.getFormattedPrefix();
        boolean hasCustomNick = !NickManager.getCustomNick().isEmpty();

        String statusMsg;
        if (newState) {
            if (hasCustomNick) {
                statusMsg = "§a[Nick] 已开启: " + currentRank + NickManager.getCustomNick();
            } else {
                statusMsg = "§a[Nick] 已开启 (仅伪装 Rank): " + currentRank + NickManager.getRealPlayerName();
            }
            ChatNotifier.send(statusMsg, NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§c[Nick] 已关闭", NotificationType.INFO);
        }
    }
    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("§b§l自定义昵称与 Rank (Hypixel)"));

        panel.child(new TextInfoPaneComponent(
                "原名",
                () -> Component.literal(NickManager.getProtectedRealName()),
                (pane, click) -> {
                    String realName = NickManager.getRealPlayerName();
                    if (!realName.isEmpty()) {
                        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(realName);
                        ChatNotifier.send("§a已复制 原名: §f" + NickManager.getProtectedRealName(), NotificationType.SUCCESS);
                    }
                }
        ));

        panel.child(new TextInfoPaneComponent(
                "效果预览",
                () -> {
                    String effective = NickManager.getEffectiveNick();
                    String tag = NickManager.getCustomNick().isEmpty() ? " §8(原名)" : "";
                    return Component.literal(NickManager.getFormattedPrefix() + NickManager.getRankNameColorCode() + effective + tag);
                },
                false
        ));
        TextInputPaneComponent inputPane = new TextInputPaneComponent(
                "自定义昵称 (留空则保留原名)...",
                newNick -> {
                    String clean = newNick != null ? newNick.trim() : "";
                    NickManager.setCustomNick(clean);
                    NickManager.setEnabled(true);
                    if (clean.isEmpty()) {
                        ChatNotifier.send("§a[Nick] 已清空昵称，当前仅伪装 Rank", NotificationType.INFO);
                    } else {
                        ChatNotifier.send("§a[Nick] 昵称已更新: §f" + clean, NotificationType.SUCCESS);
                    }
                },
                "Enter保存"
        );

        if (!NickManager.getCustomNick().isEmpty()) {
            inputPane.setText(NickManager.getCustomNick());
        }
        inputPane.onTextChanged(NickManager::setCustomNick);
        panel.child(inputPane);

        panel.child(TextInfoPaneComponent.header("§eHypixel Rank 预设"));

        for (HypixelRank r : HypixelRank.values()) {
            String title = r.getDisplayName();
            panel.child(new PanelItemComponent(
                    title,
                    PanelItemComponent.ItemType.TRIGGER,
                    (item, click) -> {
                        NickManager.setRank(r);
                        ChatNotifier.send("§a[Nick] 已应用 Rank: §f" + r.getDisplayName(), NotificationType.SUCCESS);
                        buildTertiaryUI(panel);
                    },
                    item -> {}
            ));
        }

        if (NickManager.getRank() == HypixelRank.MVP_PLUS_PLUS) {
            panel.child(TextInfoPaneComponent.header("§eMVP++ Tag 颜色 (" + NickManager.getTagColor().name + ")"));
            for (HypixelRank.TagColor color : HypixelRank.TagColor.values()) {
                panel.child(new PanelItemComponent(
                        color.code + color.name,
                        PanelItemComponent.ItemType.TRIGGER,
                        (item, click) -> {
                            NickManager.setTagColor(color);
                            ChatNotifier.send("§a[Nick] Tag 颜色已更新为: " + color.code + color.name, NotificationType.SUCCESS);
                            buildTertiaryUI(panel);
                        },
                        item -> {}
                ));
            }
        }

        if (NickManager.getRank().supportsPlusColor()) {
            panel.child(TextInfoPaneComponent.header("§d+ 号颜色 (" + NickManager.getPlusColor().name + ")"));

            for (HypixelRank.PlusColor pc : HypixelRank.PlusColor.values()) {
                panel.child(new PanelItemComponent(
                        pc.code + "+ " + pc.name,
                        PanelItemComponent.ItemType.TRIGGER,
                        (item, click) -> {
                            NickManager.setPlusColor(pc);
                            ChatNotifier.send("§a[Nick] +号颜色已更新为: " + pc.code + pc.name, NotificationType.SUCCESS);
                            buildTertiaryUI(panel);
                        },
                        item -> {}
                ));
            }
        }

        panel.child(new TextInfoPaneComponent(
                "§c重置 Nick 与 Rank",
                () -> Component.literal("§7[点击恢复默认]"),
                () -> {
                    NickManager.setCustomNick("");
                    NickManager.setRank(HypixelRank.MVP_PLUS);
                    NickManager.setPlusColor(HypixelRank.PlusColor.RED);
                    NickManager.setTagColor(HypixelRank.TagColor.GOLD);
                    NickManager.setEnabled(false);
                    inputPane.setText("");
                    ChatNotifier.send("§e[Nick] 已重置所有配置", NotificationType.INFO);
                    buildTertiaryUI(panel);
                }
        ));
    }

}
