package me.cookie.kwrg.module.modules.play;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.module.ModuleManager;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.SkyblockItemHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayModule extends Module {

    private String selectedPlayer = null;
    private boolean isPlayerPickerOpen = false;
    private String searchKeyword = "";

    public PlayModule() {
        super("play", Category.PLAY, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        ModuleManager.setPendingGuiQuery(true);
        try {
            IrcClient.queryOnline();
        } catch (Throwable ignored) {}

        renderPlayUI(panel);
    }

    private void renderPlayUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String playerDisplayText = this.selectedPlayer != null ? "玩家: " + this.selectedPlayer : "点击以选择玩家";
        TextInputPaneComponent playerPickerPane = new TextInputPaneComponent(playerDisplayText, null)
                .asButton(playerDisplayText, () -> {
                    this.isPlayerPickerOpen = !this.isPlayerPickerOpen;
                    renderPlayUI(panel);
                });

        panel.child(playerPickerPane);

        // 展开在线玩家列表
        if (this.isPlayerPickerOpen) {
            List<String> onlineList = ModuleManager.getCachedOnlinePlayers();
            if (onlineList == null || onlineList.isEmpty()) {
                panel.child(new PanelItemComponent("暂无在线玩家").withSpawnAnimation());
            } else {
                for (String player : onlineList) {
                    panel.child(new PanelItemComponent(
                            player,
                            PanelItemComponent.ItemType.TRIGGER,
                            () -> {
                                this.selectedPlayer = player;
                                this.isPlayerPickerOpen = false;
                                renderPlayUI(panel);
                            },
                            null,
                            () -> player.equalsIgnoreCase(this.selectedPlayer)
                    ).withSpawnAnimation());
                }
            }
        }

        TextInputPaneComponent searchInput = new TextInputPaneComponent("在此输入以搜索...", query -> {
            this.searchKeyword = query.trim();
            renderPlayUI(panel);
        });
        searchInput.setText(this.searchKeyword);
        searchInput.onTextChanged(query -> {
            this.searchKeyword = query.trim();
            renderPlayUI(panel);
        });

        panel.child(searchInput);

        // 搜索结果
        if (!this.searchKeyword.isEmpty()) {
            List<String> results = searchMatchingItems(this.searchKeyword);

            if (results.isEmpty()) {
                panel.child(TextInfoPaneComponent.header("无匹配项"));
            } else {
                for (String itemResult : results) {
                    panel.child(new PanelItemComponent(itemResult, () -> {
                        PlayModule.handlePlayCommandWithGui(this.selectedPlayer, itemResult);
                    }));
                }
            }
        }
    }

    private static int handlePlayCommandWithGui(String targetPlayer, String itemId) {
        if (targetPlayer == null || targetPlayer.isBlank()) {
            ChatNotifier.send("§c[IRC] 请先点击上方选择目标玩家！", NotificationType.WARN);
            return 0;
        }

        try {
            IrcClient.sendPlayEffect(targetPlayer, itemId);
            ChatNotifier.send("§a[IRC] 已向玩家 §f" + targetPlayer + " §a发送动画效果 (§e" + itemId + "§a)！", NotificationType.SUCCESS);
            return 1;
        } catch (Throwable t) {
            ChatNotifier.send("§c[IRC] 发送动画效果失败: " + t.getMessage(), NotificationType.ERR);
            t.printStackTrace();
            return 0;
        }
    }

    public static int handlePlayCommand(String targetPlayer, String itemId) {
        IrcClient.sendPlayEffect(targetPlayer, itemId);
        IrcMessageHandler.printMessage("§a[IRC] 已向玩家 §f" + targetPlayer + " §a发送动画效果 (§e" + itemId + "§a)！");
        return 1;
    }

    private static List<String> searchMatchingItems(String keyword) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        for (String sbItem : SkyblockItemHelper.getSuggestedIds()) {
            if (sbItem.toLowerCase(Locale.ROOT).startsWith(query)) {
                matches.add(sbItem);
                if (matches.size() >= 6) return matches;
            }
        }

        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            String fullId = id.toString();
            String path = id.getPath();

            if (fullId.toLowerCase(Locale.ROOT).startsWith(query) || path.toLowerCase(Locale.ROOT).startsWith(query)) {
                matches.add(fullId);
                if (matches.size() >= 6) return matches;
            }
        }

        if (matches.size() < 6) {
            for (String sbItem : SkyblockItemHelper.getSuggestedIds()) {
                if (!matches.contains(sbItem) && sbItem.toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(sbItem);
                    if (matches.size() >= 6) return matches;
                }
            }
        }

        return matches;
    }
}