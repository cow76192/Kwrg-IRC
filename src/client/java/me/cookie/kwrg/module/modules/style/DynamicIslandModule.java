package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

public class DynamicIslandModule extends DynamicModule {

    public DynamicIslandModule() {
        super("dynamicIsland", HudElementRegistry.ISLAND);
    }

    @Override
    public void onExecute() {
        boolean enabled = toggleElement();

        ChatNotifier.send(enabled ? "§adynamicIsland 已: §a开启" : "§edynamicIsland 已: §c关闭",
                enabled ? NotificationType.SUCCESS : NotificationType.ERR);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(header("外观设置"));
        panel.child(new SlotInputPaneComponent(
                "灵动岛透明度",
                new String[]{String.valueOf(Math.round(config().islandOpacity * 100.0f))},
                new String[]{"%"},
                values -> {
                    try {
                        int percent = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f));
                        config().islandOpacity = percent / 100.0f;
                        save();
                        ChatNotifier.send("§a灵动岛透明度: §e" + percent + "%", NotificationType.SUCCESS);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c请输入有效的数字 (例如: 80，0 = 完全透明)", NotificationType.WARN);
                    }
                },
                values -> {
                    try {
                        config().islandOpacity = Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f) / 100.0f;
                        saveDelayed();
                    } catch (NumberFormatException ignored) {
                    }
                }
        ).bounds(new int[][]{{0, 100}}).dragStep(5).precision(0).dragSensitivity(6.0));

        panel.child(toggle("shadow", () -> config().isShadowEnabled, () -> {
            config().isShadowEnabled = !config().isShadowEnabled;
            save();
        }));

        panel.child(header("基础数据"));
        panel.child(toggle("FPS", () -> config().islandShowFps, () -> {
            config().islandShowFps = !config().islandShowFps;
            save();
        }));
        panel.child(toggle("Ping (IRC)", () -> config().islandShowPing, () -> {
            config().islandShowPing = !config().islandShowPing;
            save();
        }));
        panel.child(toggle("TPS", () -> config().islandShowTps, () -> {
            config().islandShowTps = !config().islandShowTps;
            save();
        }));

        panel.child(header("硬件监控"));
        panel.child(toggle("RAM", () -> config().islandShowRam, () -> {
            config().islandShowRam = !config().islandShowRam;
            save();
        }));
        panel.child(toggle("GPU", () -> config().islandShowGpu, () -> {
            config().islandShowGpu = !config().islandShowGpu;
            save();
        }));
        panel.child(toggle("CPU", () -> config().islandShowCpu, () -> {
            config().islandShowCpu = !config().islandShowCpu;
            save();
        }));

        panel.child(header("Position"));
        panel.child(row("Link to HudEditor", this::openEditor));
    }
}
