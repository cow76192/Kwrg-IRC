package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class ModelScaleModule extends Module {

    public ModelScaleModule() {
        super("modelScale", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderScaleUI(panel);
    }

    private void renderScaleUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        panel.child(new TextInfoPaneComponent("当前我的模型大小", () -> Math.round(PlayerCustomizationManager.getMyScale() * 100) + "%", false, false));
        panel.child(TextInfoPaneComponent.header("挡位预设 (左键应用)"));
        for (float step : PlayerCustomizationManager.SCALE_STEPS) {
            String label = Math.round(step * 100) + "%";
            panel.child(new PanelItemComponent(
                    label,
                    PanelItemComponent.ItemType.TOGGLE,
                    () -> {
                        PlayerCustomizationManager.setMyScale(step);
                        ChatNotifier.send("§a[Scale] 我的模型大小已设为: §f" + label, NotificationType.SUCCESS);
                        renderScaleUI(panel);
                    },
                    null,
                    () -> Math.abs(PlayerCustomizationManager.getMyScale() - step) < 0.001f
            ));
        }
        panel.child(new PanelItemComponent(
                "循环切换挡位",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    float next = PlayerCustomizationManager.cycleMyScale();
                    ChatNotifier.send("§a[Scale] 我的模型大小已设为: §f" + Math.round(next * 100) + "%", NotificationType.SUCCESS);
                    renderScaleUI(panel);
                },
                null,
                () -> false
        ));
    }
}