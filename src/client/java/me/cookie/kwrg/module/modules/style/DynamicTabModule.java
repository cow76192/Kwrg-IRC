package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

public class DynamicTabModule extends DynamicModule {

    public DynamicTabModule() {
        super("dynamicTab", HudElementRegistry.ISLAND);
    }

    @Override
    public boolean isActive() {
        return config().dynamicTab;
    }

    @Override
    public void onExecute() {
        config().dynamicTab = !config().dynamicTab;
        save();

        ChatNotifier.send(config().dynamicTab ? "§adynamicTab 已: §a开启" : "§edynamicTab 已: §c关闭",
                config().dynamicTab ? NotificationType.SUCCESS : NotificationType.ERR);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(header("Tab 面板透明度 (0 = 完全透明)"));
        panel.child(new SlotInputPaneComponent(
                "透明度",
                new String[]{String.valueOf(Math.round(config().tabOpacity * 100.0f))},
                new String[]{"%"},
                values -> {
                    try {
                        int percent = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f));
                        config().tabOpacity = percent / 100.0f;
                        save();
                        ChatNotifier.send("§aTab 面板透明度: §e" + percent + "%", NotificationType.SUCCESS);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c请输入有效的数字 (例如: 80，0 = 完全透明)", NotificationType.WARN);
                    }
                },
                values -> {
                    try {
                        config().tabOpacity = Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f) / 100.0f;
                        saveDelayed();
                    } catch (NumberFormatException ignored) {
                    }
                }
        ).bounds(new int[][]{{0, 100}}).dragStep(5).precision(0).dragSensitivity(6.0));

        panel.child(header("展开行为"));
        panel.child(toggle("Follow Island Position", () -> config().islandPosAffectsTab, this::toggleFollowIslandPosition));

        panel.child(header("Position"));
        panel.child(row("Link to HudEditor", this::openEditor));
    }

    private void toggleFollowIslandPosition() {
        if (config().islandPosAffectsTab) {
            config().islandPosAffectsTab = false;
            save();
            return;
        }

        if (!HudElementRegistry.isEnabled(HudElementRegistry.ISLAND)) {
            ChatNotifier.send("§cDynamicIsland 未开启，Follow Island Position 不会生效。§7请先开启 dynamicIsland。",
                    NotificationType.WARN);
            return;
        }

        config().islandPosAffectsTab = true;
        save();
    }
}
