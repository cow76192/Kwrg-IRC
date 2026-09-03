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

        // ① 顶部输入框：纯内存添加，不存入 config (回车添加)
        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入寻找玩家 (回车添加)...", player -> {
            String clean = player.trim();
            if (!clean.isEmpty()) {
                PlayerFinderManager.addPlayer(clean);
                ChatNotifier.send("§a[Finder] 已将 §f" + clean + " §a加入寻找名单！");
                this.currentInputText = "";
                renderFinderUI(panel, clean); // 播放弹出动效
            }
        });

        inputPane.setText(this.currentInputText);
        inputPane.onTextChanged(text -> this.currentInputText = text);

        panel.child(inputPane);

        // ② 色板 (存入 config，不显示底部 HEX 和 RGB 面板)
        panel.child(new ColorSettingComponent(IrcConfig.get().playerFinderColor, PlayerFinderModule::setFinderColor, false));

        // ③ 居中提示标题
        panel.child(TextInfoPaneComponent.header("当前寻找玩家 (Shift+左键移除)"));

        // ④ 寻找名单列表 (Shift + 左键移除平滑收起)
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
                        () -> true // 保持激活高亮
                );

                if (newlyAddedPlayer != null && p.equalsIgnoreCase(newlyAddedPlayer)) {
                    item.withSpawnAnimation();
                }

                panel.child(item);
            }
        }
    }

    /**
     * 颜色保存至配置 (带 800ms 防抖)
     */
    public static int setFinderColor(String colorStr) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        if (c == null) return 0;

        IrcConfig config = IrcConfig.get();
        config.playerFinderColor = colorStr;
        IrcConfig.saveDelayed(800L);
        return 1;
    }
}