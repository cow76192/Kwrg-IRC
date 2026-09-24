package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.ColorSettingComponent;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.manager.GuiColorManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

public class GuiColorModule extends Module {

    public GuiColorModule() {
        super("guiColor", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        ChatNotifier.sendInChatScreen("§6[IRC] 当前 GUI 颜色: §f" + GuiColorManager.getGuiColorStr() + " §7(右键打开调色盘)");
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new ColorSettingComponent(GuiColorManager.getGuiColorStr(), GuiColorManager::setGuiColor));

        TextInfoPaneComponent resetPane = new TextInfoPaneComponent(
                "重置默认",
                () -> {
                    String current = GuiColorManager.getGuiColorStr();
                    return ColorUtil.applyStyle(current, current);
                },
                (pane, click) -> {
                    boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();

                    if (isShiftDown) {
                        GuiColorManager.resetDefault();
                        panel.clearChildren();
                        this.buildTertiaryUI(panel);
                    } else {
                        ChatNotifier.send("§e[IRC] 提示: 请按住 §fShift + 左键 §e点击以重置为默认主题颜色！", NotificationType.INFO);
                    }
                }
        );

        panel.child(resetPane);
    }
}