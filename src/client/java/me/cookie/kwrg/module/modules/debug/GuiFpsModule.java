package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class GuiFpsModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public int guiFpsLimit = 60;
    }

    public GuiFpsModule() {
        super("FPSLimit", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    public void onExecute() {
    }

    private void saveConfig(int fpsLimit) {
        Settings config = CONFIG.get();
        config.guiFpsLimit = fpsLimit;
        IrcConfig.save();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        Settings config = CONFIG.get();

        panel.child(new TextInfoPaneComponent("当前 GUI 帧率限制", () -> {
            int limit = config.guiFpsLimit;
            return limit <= 0 ? "无限制 (OFF)" : limit + " FPS";
        }, false, false));

        panel.child(new TextInputPaneComponent("", null)
                .asButton("30 FPS", () -> {
                    saveConfig(30);
                    NotificationManager.send("GUI FPS 限制已设为 30");
                }));

        panel.child(new TextInputPaneComponent("", null)
                .asButton("60 FPS", () -> {
                    saveConfig(60);
                    NotificationManager.send("GUI FPS 限制已设为 60");
                }));

        panel.child(new TextInputPaneComponent("", null)
                .asButton("120 FPS", () -> {
                    saveConfig(120);
                    IrcConfig.save();
                    NotificationManager.send("GUI FPS 限制已设为 120");
                }));

        panel.child(new TextInputPaneComponent("", null)
                .asButton("infinite", () -> {
                    saveConfig(0);
                    NotificationManager.send("GUI FPS 限制已关闭");
                }));

        panel.child(new TextInputPaneComponent("输入自定义 FPS (0 为不限制)...", input -> {
            try {
                int customFps = Integer.parseInt(input.trim());
                if (customFps < 0) customFps = 0;
                if (customFps > 1000) customFps = 1000;
                config.guiFpsLimit = customFps;
                NotificationManager.send("GUI FPS 限制已更新为: " + (customFps == 0 ? "无限制" : customFps + " FPS"));
            } catch (NumberFormatException e) {
                NotificationManager.send("§c请输入合法的整数数字！");
            }
        }));
    }
}