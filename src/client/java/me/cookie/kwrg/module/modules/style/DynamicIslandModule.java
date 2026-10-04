package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Mth;

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
            ChatNotifier.send("§adynamicIsland 已: §a开启", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§edynamicIsland 已: §c关闭", NotificationType.ERR);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("外观设置"));

        final TextInputPaneComponent[] opacityInputHolder = new TextInputPaneComponent[1];
        opacityInputHolder[0] = new TextInputPaneComponent("", input -> {
            IrcConfig config = IrcConfig.get();
            if (input == null || input.isBlank()) return;

            try {
                String clean = input.trim().replace("%", "");
                float val = Float.parseFloat(clean);

                if (clean.contains(".") && val <= 1.0f) {
                    val *= 100.0f;
                }

                int percent = Math.round(Mth.clamp(val, 0.0f, 100.0f));

                config.islandOpacity = percent / 100.0f;
                IrcConfig.save();

                opacityInputHolder[0].setText(percent + "%");
                ChatNotifier.send("§a灵动岛透明度已设置为: §e" + percent + "%", NotificationType.INFO);
            } catch (NumberFormatException e) {
                ChatNotifier.send("§c请输入有效的数字或百分比 (例如: 80 或 80%)", NotificationType.WARN);
                int currentPercent = Math.round(config.islandOpacity * 100.0f);
                opacityInputHolder[0].setText(currentPercent + "%");
            }
        }, "透明度 (例如: 80%)");

        int currentPercent = Math.round(IrcConfig.get().islandOpacity * 100.0f);
        opacityInputHolder[0].setText(currentPercent + "%");

        panel.child(opacityInputHolder[0]);

        panel.child(new PanelItemComponent(
                "OnlyApplytoTab",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.onlyApplyToTab = !config.onlyApplyToTab;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().onlyApplyToTab
        ));

        panel.child(new PanelItemComponent(
                "onlyRenderTab (╯▔皿▔)╯",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.onlyRenderTab = !config.onlyRenderTab;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().onlyRenderTab
        ));

        panel.child(new PanelItemComponent(
                "shadow",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.isShadowEnabled = !config.isShadowEnabled;
                    IrcConfig.save();
                },
                null,
                () -> IrcConfig.get().isShadowEnabled
        ));

        panel.child(TextInfoPaneComponent.header("基础数据"));

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