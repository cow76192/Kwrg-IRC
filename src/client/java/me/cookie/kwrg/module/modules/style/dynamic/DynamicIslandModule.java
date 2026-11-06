package me.cookie.kwrg.module.modules.style.dynamic;

import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

public class DynamicIslandModule extends DynamicModule {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean islandShowFps = true;
        public boolean islandShowPing = true;
        public boolean islandShowTps = true;
        public boolean islandShowRam = false;
        public boolean islandShowGpu = false;
        public boolean islandShowCpu = false;
        public boolean islandShowServerPing = true;
    }

    public DynamicIslandModule() {
        super("dynamicIsland", HudElementRegistry.ISLAND, CONFIG);
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
                new String[]{String.valueOf(Math.round(elementState().opacity * 100.0f))},
                new String[]{"%"},
                values -> {
                    try {
                        int percent = Math.round(Mth.clamp(Float.parseFloat(values[0].trim()), 0.0f, 100.0f));
                        elementState().opacity = percent / 100.0f;
                        save();
                        ChatNotifier.send("§a灵动岛透明度: §e" + percent + "%", NotificationType.SUCCESS);
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

        panel.child(toggle("shadow", () -> elementState().shadow, () -> {
            elementState().shadow = !elementState().shadow;
            save();
        }));

        panel.child(header("基础数据"));
        panel.child(toggle("FPS", () -> CONFIG.get().islandShowFps, () -> {
            CONFIG.get().islandShowFps = !CONFIG.get().islandShowFps;
            save();
        }));
        panel.child(toggle("Ping (IRC)", () -> CONFIG.get().islandShowPing, () -> {
            CONFIG.get().islandShowPing = !CONFIG.get().islandShowPing;
            save();
        }));
        panel.child(toggle("Ping (Server)", () -> CONFIG.get().islandShowServerPing, () -> {
            CONFIG.get().islandShowServerPing = !CONFIG.get().islandShowServerPing;
            save();
        }));
        panel.child(toggle("TPS", () -> CONFIG.get().islandShowTps, () -> {
            CONFIG.get().islandShowTps = !CONFIG.get().islandShowTps;
            save();
        }));

        panel.child(header("硬件监控"));
        panel.child(toggle("RAM", () -> CONFIG.get().islandShowRam, () -> {
            CONFIG.get().islandShowRam = !CONFIG.get().islandShowRam;
            save();
        }));
        panel.child(toggle("GPU", () -> CONFIG.get().islandShowGpu, () -> {
            CONFIG.get().islandShowGpu = !CONFIG.get().islandShowGpu;
            save();
        }));
        panel.child(toggle("CPU", () -> CONFIG.get().islandShowCpu, () -> {
            CONFIG.get().islandShowCpu = !CONFIG.get().islandShowCpu;
            save();
        }));

        panel.child(header("Position"));
        panel.child(row("Link to HudEditor", this::openEditor));
    }
}
