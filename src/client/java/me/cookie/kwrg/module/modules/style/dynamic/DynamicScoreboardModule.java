package me.cookie.kwrg.module.modules.style.dynamic;

import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

public class DynamicScoreboardModule extends DynamicModule {

    public DynamicScoreboardModule() {
        super("dynamicScoreboard", HudElementRegistry.SCOREBOARD);
    }

    @Override
    public void onExecute() {
        boolean enabled = toggleElement();

        ChatNotifier.send(enabled ? "§aDynamicScoreboard 已: §a开启" : "§eDynamicScoreboard 已: §c关闭",
                enabled ? NotificationType.SUCCESS : NotificationType.ERR);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(header("显示"));
        panel.child(toggle("隐藏分数数字", () -> config().scoreboardHideScores, () -> {
            config().scoreboardHideScores = !config().scoreboardHideScores;
            save();
        }));

        panel.child(header("面板透明度 (0 = 完全透明)"));
        panel.child(new SlotInputPaneComponent(
                "透明度",
                new String[]{String.valueOf(Math.round(elementState().opacity * 100.0f))},
                new String[]{"%"},
                values -> {
                    try {
                        int percent = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f));
                        elementState().opacity = percent / 100.0f;
                        save();
                        ChatNotifier.send("§aDynamicScoreboard 面板透明度: §e" + percent + "%", NotificationType.SUCCESS);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c请输入有效的数字 (例如: 80，0 = 完全透明)", NotificationType.WARN);
                    }
                },
                values -> {
                    try {
                        elementState().opacity = Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f) / 100.0f;
                        saveDelayed();
                    } catch (NumberFormatException ignored) {
                    }
                }
        ).bounds(new int[][]{{0, 100}}).dragStep(5).precision(0).dragSensitivity(6.0));

        panel.child(header("边框与文字距离"));
        panel.child(new SlotInputPaneComponent(
                "间距",
                new String[]{String.valueOf(elementState().padding)},
                new String[]{"px"},
                values -> {
                    try {
                        int padding = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 24.0f));
                        elementState().padding = padding;
                        save();
                        ChatNotifier.send("§aDynamicScoreboard 边框间距: §e" + padding + "px", NotificationType.SUCCESS);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c请输入有效的数字 (例如: 6)", NotificationType.WARN);
                    }
                },
                values -> {
                    try {
                        elementState().padding = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 24.0f));
                        saveDelayed();
                    } catch (NumberFormatException ignored) {
                    }
                }
        ).bounds(new int[][]{{0, 24}}).dragStep(1).precision(0).dragSensitivity(4.0));

        panel.child(header("Position"));
        panel.child(row("Link to HudEditor", this::openEditor));
    }
}
