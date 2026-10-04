package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.*;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
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
        renderScaleUI(panel);
    }

    private void renderScaleUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(new TextInfoPaneComponent("当前缩放", () -> (int) (IrcConfig.get().guiScale * 100) + "%"));

        panel.child(new SlotInputPaneComponent(
                "自定义缩放",
                new String[]{String.valueOf((int) (IrcConfig.get().guiScale * 100))},
                new String[]{"%"},
                values -> {
                    try {
                        int val = Integer.parseInt(values[0].trim());
                        setScale(panel, val / 100.0f);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c请输入合法的整数！", NotificationType.ERR);
                    }
                },
                null
        ).bounds(new int[][]{{50, 200}})
                .dragStep(5)
                .precision(0)
                .dragSensitivity(8.0));

        panel.child(TextInfoPaneComponent.header("快速缩放预设"));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("75%", () -> setScale(panel, 0.75f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("100%", () -> setScale(panel, 1.0f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("125%", () -> setScale(panel, 1.25f)));
        panel.child(new TextInputPaneComponent("", str -> {}).asButton("150%", () -> setScale(panel, 1.5f)));
    }

    private void setScale(TertiaryPanelComponent panel, float scale) {
        IrcConfig.get().guiScale = Mth.clamp(scale, 0.5f, 2.0f);
        IrcConfig.save();
        ChatNotifier.send("§aGUI 缩放已设为: §f" + (int) (IrcConfig.get().guiScale * 100) + "%", NotificationType.SUCCESS);
        renderScaleUI(panel);
    }
}