package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.*;
import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PlayerFinderModule extends Module {

    private String currentInputText = "";
    private TertiaryPanelComponent activePanel = null;

    public PlayerFinderModule() {
        super("playerFinder", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
        PlayerFinderManager.setOnTargetsChangedListener(() -> {
            if (this.activePanel != null) {
                renderFinderUI(this.activePanel, null);
            }
        });
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderFinderUI(panel, null);
    }

    private void renderFinderUI(TertiaryPanelComponent panel, String newlyAddedPlayer) {
        this.activePanel = panel;
        panel.clearChildren();

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入寻找玩家 (回车添加，永久保存)...", player -> {
            String clean = player.trim();
            if (!clean.isEmpty()) {
                PlayerFinderManager.addPlayer(clean);
                ChatNotifier.send("§a[Finder] 已将 §f" + clean + " §a加入寻找名单！", NotificationType.SUCCESS);
                this.currentInputText = "";
                renderFinderUI(panel, clean);
            }
        });

        inputPane.setText(this.currentInputText);
        inputPane.onTextChanged(text -> this.currentInputText = text);

        panel.child(inputPane);

        panel.child(TextInfoPaneComponent.header("队伍管理"));

        panel.child(new PanelItemComponent(
                "自动导入并追踪队伍",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    boolean nextState = !PlayerFinderManager.isPartyTracking();
                    PlayerFinderManager.setPartyTracking(nextState);
                    renderFinderUI(panel, null);
                },
                null,
                PlayerFinderManager::isPartyTracking
        ));

        panel.child(new ColorSettingComponent(IrcConfig.get().playerFinderColor, PlayerFinderModule::setFinderColor, false));

        panel.child(TextInfoPaneComponent.header("目标列表\n（Shift左键移除 | Ctrl左键星标）"));

        List<String> targetList = PlayerFinderManager.getPlayers();
        if (targetList.isEmpty()) {
            panel.child(new PanelItemComponent("暂无寻找目标"));
        } else {
            for (String p : targetList) {
                boolean starred = PlayerFinderManager.isStarred(p);
                boolean temp = PlayerFinderManager.isTemporary(p);

                StringBuilder label = new StringBuilder();
                if (starred) label.append("§6★ ");
                if (temp) {
                    label.append("§7[临] §f").append(p);
                } else {
                    label.append("§f").append(p);
                }

                PanelItemComponent item = new PanelItemComponent(
                        label.toString(),
                        PanelItemComponent.ItemType.TOGGLE,
                        (btn, click) -> {
                            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
                            boolean isCtrlDown = (click.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 || click.hasControlDown();

                            if (isShiftDown) {
                                btn.dismiss(() -> {
                                    PlayerFinderManager.removePlayer(p);
                                    ChatNotifier.send("§e已将 §f" + p + " §e从名单移除。", NotificationType.SUCCESS);
                                    renderFinderUI(panel, null);
                                });
                            } else if (isCtrlDown) {
                                PlayerFinderManager.toggleStarred(p);
                                String status = PlayerFinderManager.isStarred(p) ? "§6星标" : "§7普通";
                                ChatNotifier.send("§e已将 §f" + p + " §e设为 " + status + " §e队友。", NotificationType.SUCCESS);
                                renderFinderUI(panel, null);
                            } else {
                                Minecraft.getInstance().keyboardHandler.setClipboard(p);
                                ChatNotifier.send("§e已复制 §f" + p + " §e到剪贴板", NotificationType.SUCCESS);
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

    public static int setFinderColor(String colorStr) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        if (c == null) return 0;

        IrcConfig config = IrcConfig.get();
        config.playerFinderColor = colorStr;
        IrcConfig.saveDelayed(800L);
        return 1;
    }
}