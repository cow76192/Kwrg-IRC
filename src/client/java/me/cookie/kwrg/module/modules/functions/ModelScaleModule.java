package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.*;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
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
        panel.child(new TextInfoPaneComponent("当前模型大小", () -> Math.round(PlayerCustomizationManager.getMyScale() * 100) + "%", false, false));

        panel.child(new SlotInputPaneComponent(
                "自定义大小",
                new String[]{String.valueOf(Math.round(PlayerCustomizationManager.getMyScale() * 100))},
                new String[]{"%"},
                values -> {
                    try {
                        int v = Integer.parseInt(values[0].trim());
                        v = Math.max(10, Math.min(500, v));
                        float scale = v / 100.0f;
                        PlayerCustomizationManager.setMyScale(scale);
                        ChatNotifier.send("§a[Scale] 模型大小已设为: §f" + v + "%", NotificationType.SUCCESS);
                        renderScaleUI(panel);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c[Scale] 请输入有效的数字", NotificationType.ERR);
                    } catch (Exception ignored) {}
                },
                null
        ).bounds(new int[][]{{10, 500}}).dragStep(5).precision(0).dragSensitivity(8.0));

        panel.child(TextInfoPaneComponent.header("挡位预设 (左键应用)"));
        for (float step : PlayerCustomizationManager.SCALE_STEPS) {
            String label = Math.round(step * 100) + "%";
            panel.child(new PanelItemComponent(
                    label,
                    PanelItemComponent.ItemType.TOGGLE,
                    () -> {
                        PlayerCustomizationManager.setMyScale(step);
                        ChatNotifier.send("§a[Scale] 模型大小已设为: §f" + label, NotificationType.SUCCESS);
                        renderScaleUI(panel);
                    },
                    null,
                    () -> Math.abs(PlayerCustomizationManager.getMyScale() - step) < 0.001f
            ));
        }
    }
}