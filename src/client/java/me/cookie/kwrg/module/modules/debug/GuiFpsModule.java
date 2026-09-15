package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class GuiFpsModule extends Module {

    public GuiFpsModule() {
        super("FPSLimit", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
    }

    private void saveConfig(int fpsLimit) {
        IrcConfig config = IrcConfig.get();
        config.guiFpsLimit = fpsLimit;
        IrcConfig.save();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        IrcConfig config = IrcConfig.get();

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