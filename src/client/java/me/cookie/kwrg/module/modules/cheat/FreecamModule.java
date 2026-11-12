package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.FreecamManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public final class FreecamModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public double freecamSpeed = 10.0;

        private void sanitize() {
            freecamSpeed = Double.isFinite(freecamSpeed) ? Math.clamp(freecamSpeed, 1.0, 100.0) : 10.0;
        }
    }

    public FreecamModule() {
        super("freecam", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, FreecamManager::isActive, CONFIG);
    }

    @Override
    public void onExecute() {
        setEnabled(!FreecamManager.isActive());
    }

    public static int setEnabled(boolean enabled) {
        if (enabled && !FreecamManager.enable()) {
            ChatNotifier.send("§c[Freecam] 请先进入世界，并使用玩家视角", NotificationType.ERR);
            return 0;
        }
        if (!enabled) FreecamManager.disable();
        ChatNotifier.send("§a[Freecam] Freecam 已" + (enabled ? "开启" : "关闭"), NotificationType.INFO);
        return 1;
    }

    @Override
    protected void onConfigLoaded() {
        FreecamManager.disable();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(new SlotInputPaneComponent("移动速度",
                new String[]{String.valueOf(CONFIG.get().freecamSpeed)}, new String[]{"格/秒"},
                values -> {
                    try {
                        double speed = Double.parseDouble(values[0].trim());
                        if (!Double.isFinite(speed)) throw new NumberFormatException();
                        CONFIG.update(settings -> settings.freecamSpeed = Math.clamp(speed, 1.0, 100.0));
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c[Freecam] 请输入有效的速度", NotificationType.ERR);
                    }
                }, null).bounds(new int[][]{{1, 100}}).dragStep(1).precision(1).dragSensitivity(8.0));
    }
}
