package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

public class GuiScaleModule extends Module {

    public GuiScaleModule() {
        super("GuiScale", Category.STYLE, PanelItemComponent.ItemType.TRIGGER, () -> true);
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new TextInfoPaneComponent("当前缩放", () -> (int) (IrcConfig.get().guiScale * 100) + "%"));

        panel.child(TextInfoPaneComponent.header("快速缩放预设"));

        panel.child(new TextInputPaneComponent("", str -> {}).asButton("75%", () -> setScale(0.75f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("100%", () -> setScale(1.0f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("125%", () -> setScale(1.25f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("150%", () -> setScale(1.5f)));

        panel.child(TextInfoPaneComponent.header("自定义数值 (50 ~ 200)"));
        TextInputPaneComponent customInput = new TextInputPaneComponent("输入 50 - 200 后回车", input -> {
            try {
                int val = Integer.parseInt(input.trim().replace("%", ""));
                if (val < 50 || val > 200) {
                    ChatNotifier.send("§c缩放范围必须在 50% ~ 200% 之间！", NotificationType.ERR);
                    return;
                }
                setScale(val / 100.0f);
            } catch (NumberFormatException e) {
                ChatNotifier.send("§c请输入合法的整数！", NotificationType.ERR);
            }
        });
        panel.child(customInput);
    }

    private void setScale(float scale) {
        IrcConfig.get().guiScale = Mth.clamp(scale, 0.5f, 2.0f);
        IrcConfig.save();
        ChatNotifier.send("§aGUI 缩放已设为: §f" + (int)(IrcConfig.get().guiScale * 100) + "%", NotificationType.SUCCESS);
    }
}