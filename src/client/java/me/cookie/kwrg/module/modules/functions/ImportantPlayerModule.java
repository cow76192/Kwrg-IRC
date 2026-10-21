package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.module.ModuleManager;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ImportantPlayerModule extends Module {

    private String currentInputText = "";
    private boolean isOnlinePickerOpen = false;

    public ImportantPlayerModule() {
        super("importantPlayer", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().importantPlayer);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.importantPlayer = !config.importantPlayer;
        IrcConfig.save();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        ModuleManager.setPendingGuiQuery(true);
        try {
            IrcClient.queryOnline();
        } catch (Throwable ignored) {}

        renderImportantPlayersUI(panel, null);
    }

    public void renderImportantPlayersUI(TertiaryPanelComponent panel, String newlyAddedPlayer) {
        panel.clearChildren();

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入名称 (回车添加 / 右键选在线)...", player -> {
            String clean = player.trim();
            if (!clean.isEmpty()) {
                toggleImportantWithGui(clean);
                this.currentInputText = "";
                this.isOnlinePickerOpen = false;
                renderImportantPlayersUI(panel, clean);
            }
        });

        inputPane.setText(this.currentInputText);
        inputPane.onTextChanged(text -> this.currentInputText = text);

        inputPane.onRightClick(() -> {
            this.isOnlinePickerOpen = !this.isOnlinePickerOpen;
            renderImportantPlayersUI(panel, null);
        });

        panel.child(inputPane);

        if (this.isOnlinePickerOpen) {
            List<String> onlineList = ModuleManager.getCachedOnlinePlayers();
            if (onlineList == null || onlineList.isEmpty()) {
                panel.child(new PanelItemComponent("暂无在线玩家").withSpawnAnimation());
            } else {
                for (String onlinePlayer : onlineList) {
                    panel.child(new PanelItemComponent(
                            onlinePlayer,
                            PanelItemComponent.ItemType.TRIGGER,
                            () -> {
                                this.currentInputText = onlinePlayer;
                                this.isOnlinePickerOpen = false;
                                toggleImportantWithGui(onlinePlayer);
                                renderImportantPlayersUI(panel, onlinePlayer);
                            },
                            null,
                            () -> onlinePlayer.equalsIgnoreCase(this.currentInputText)
                    ).withSpawnAnimation());
                }
            }
        }

        panel.child(TextInfoPaneComponent.header("当前关注玩家 (Shift+左键取关)"));

        List<String> list = IrcConfig.get().importantPlayers;
        if (list == null || list.isEmpty()) {
            panel.child(new PanelItemComponent("暂无关注玩家"));
        } else {
            for (String p : new ArrayList<>(list)) {
                PanelItemComponent item = new PanelItemComponent(
                        p,
                        PanelItemComponent.ItemType.TOGGLE,
                        (btn, click) -> {
                            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
                            if (isShiftDown) {
                                btn.dismiss(() -> {
                                    toggleImportantWithGui(p);
                                    renderImportantPlayersUI(panel, null);
                                });
                            } else {
                                Minecraft.getInstance().keyboardHandler.setClipboard(p);
                                ChatNotifier.send("§e[IRC] 已复制 §f" + p + " §e| 按住 §cShift+左键 §e可取消关注", NotificationType.SUCCESS);
                            }
                        },
                        null,
                        () -> true
                );

                if (newlyAddedPlayer != null && p.equalsIgnoreCase(newlyAddedPlayer)) {
                    item.withSpawnAnimation();
                }

                panel.child(item);
            }
        }
    }

    private static void toggleImportantWithGui(String player) {
        IrcConfig config = IrcConfig.get();
        if (config.importantPlayers == null) config.importantPlayers = new ArrayList<>();

        boolean removed = config.importantPlayers.removeIf(p -> p.equalsIgnoreCase(player));
        if (removed) {
            IrcConfig.save();
            PlayerFinderManager.onImportantPlayerChanged(player, false);
            ChatNotifier.send("§e[IRC] 已将玩家 §f" + player + " §e从关注名单中移除。", NotificationType.INFO);
        } else {
            config.importantPlayers.add(player);
            IrcConfig.save();
            PlayerFinderManager.onImportantPlayerChanged(player, true);
            ChatNotifier.send("§a[IRC] 已将玩家 §f" + player + " §a添加到关注名单!", NotificationType.INFO);
        }
    }

    public static int toggleImportant(String player) {
        IrcConfig config = IrcConfig.get();
        boolean removed = config.importantPlayers.removeIf(p -> p.equalsIgnoreCase(player));
        if (removed) {
            IrcConfig.save();
            PlayerFinderManager.onImportantPlayerChanged(player, false);
            ChatNotifier.sendInChatScreen("§e[IRC] 已将玩家 §f" + player + " §e从关注名单中移除。");
        } else {
            config.importantPlayers.add(player);
            IrcConfig.save();
            PlayerFinderManager.onImportantPlayerChanged(player, true);
            ChatNotifier.sendInChatScreen("§a[IRC] 已将玩家 §f" + player + " §a添加到关注名单!");
        }
        return 1;
    }

    public static int listImportant() {
        IrcConfig config = IrcConfig.get();
        if (config.importantPlayers == null || config.importantPlayers.isEmpty()) {
            ChatNotifier.sendInChatScreen("§c[IRC] 当前关注名单为空。用法: /irc important <玩家名>");
        } else {
            ChatNotifier.sendInChatScreen("§6[IRC] 当前关注名单: §f" + String.join("§7, §f", config.importantPlayers));
        }
        return 0;
    }
}