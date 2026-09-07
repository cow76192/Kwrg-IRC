package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class DynamicIslandModule extends Module {

    public DynamicIslandModule() {
        super("dynamicIsland", Category.STYLE, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().dynamicIsland);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.dynamicIsland = !config.dynamicIsland;
        IrcConfig.save();

        if (config.dynamicIsland) {
            ChatNotifier.send("§adynamicIsland 已: §a开启");
        } else {
            ChatNotifier.send("§edynamicIsland 已: §c关闭");
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("基础数据"));

        // 1. FPS 配置项
        panel.child(new PanelItemComponent(
                "FPS",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowFps = !config.islandShowFps;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowFps
        ));

        // 2. Ping 配置项
        panel.child(new PanelItemComponent(
                "Ping (IRC)",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowPing = !config.islandShowPing;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowPing
        ));

        // 3. TPS 配置项
        panel.child(new PanelItemComponent(
                "TPS",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowTps = !config.islandShowTps;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowTps
        ));

        panel.child(TextInfoPaneComponent.header("硬件监控"));

        // 4. RAM 配置项
        panel.child(new PanelItemComponent(
                "RAM",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowRam = !config.islandShowRam;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowRam
        ));

        // 5. GPU 配置项
        panel.child(new PanelItemComponent(
                "GPU",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowGpu = !config.islandShowGpu;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowGpu
        ));

        // 6. CPU 配置项
        panel.child(new PanelItemComponent(
                "CPU",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.islandShowCpu = !config.islandShowCpu;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().islandShowCpu
        ));
    }
}