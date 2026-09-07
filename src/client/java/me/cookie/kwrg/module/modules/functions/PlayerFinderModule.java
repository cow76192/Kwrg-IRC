package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.*;
import me.cookie.kwrg.irc.IrcConfig;
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

    public PlayerFinderModule() {
        super("playerFinder", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderFinderUI(panel, null);
    }

    private void renderFinderUI(TertiaryPanelComponent panel, String newlyAddedPlayer) {
        panel.clearChildren();

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入寻找玩家 (回车添加)...", player -> {
            String clean = player.trim();
            if (!clean.isEmpty()) {
                PlayerFinderManager.addPlayer(clean);
                ChatNotifier.send("§a[Finder] 已将 §f" + clean + " §a加入寻找名单！");
                this.currentInputText = "";
                renderFinderUI(panel, clean);
            }
        });

        inputPane.setText(this.currentInputText);
        inputPane.onTextChanged(text -> this.currentInputText = text);

        panel.child(inputPane);

        panel.child(new ColorSettingComponent(IrcConfig.get().playerFinderColor, PlayerFinderModule::setFinderColor, false));

        panel.child(TextInfoPaneComponent.header("当前寻找玩家 (Shift+左键移除)"));

        List<String> targetList = PlayerFinderManager.getPlayers();
        if (targetList.isEmpty()) {
            panel.child(new PanelItemComponent("暂无寻找目标"));
        } else {
            for (String p : targetList) {
                PanelItemComponent item = new PanelItemComponent(
                        p,
                        PanelItemComponent.ItemType.TOGGLE,
                        (btn, click) -> {
                            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
                            if (isShiftDown) {
                                btn.dismiss(() -> {
                                    PlayerFinderManager.removePlayer(p);
                                    ChatNotifier.send("§e[Finder] 已将 §f" + p + " §e从寻找名单移除。");
                                    renderFinderUI(panel, null);
                                });
                            } else {
                                Minecraft.getInstance().keyboardHandler.setClipboard(p);
                                ChatNotifier.send("§e[Finder] 已复制 §f" + p + " §e");
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