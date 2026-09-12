package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
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
            ChatNotifier.send("§adynamicIsland 已: §a开启");
        } else {
            ChatNotifier.send("§edynamicIsland 已: §c关闭");
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        // ================= 外观样式配置 =================
        panel.child(TextInfoPaneComponent.header("外观设置"));

        // 创建引用数组以便在回车回调中能够调用 setText 刷新为标准百分比
        final TextInputPaneComponent[] opacityInputHolder = new TextInputPaneComponent[1];
        opacityInputHolder[0] = new TextInputPaneComponent("透明度 (例如: 80%)", input -> {
            IrcConfig config = IrcConfig.get();
            if (input == null || input.isBlank()) return;

            try {
                // 去除空格与百分号
                String clean = input.trim().replace("%", "");
                float val = Float.parseFloat(clean);

                // 兼容小数输入 (如 0.8 自动转为 80%)
                if (clean.contains(".") && val <= 1.0f) {
                    val *= 100.0f;
                }

                // 限制在 0% ~ 100%
                int percent = Math.round(Mth.clamp(val, 0.0f, 100.0f));

                // 保存为 0.0f ~ 1.0f 的浮点数 (或者直接存百分比整数)
                config.islandOpacity = percent / 100.0f;
                IrcConfig.save();

                // 自动刷新输入框为规范的百分比显示
                opacityInputHolder[0].setText(percent + "%");
                ChatNotifier.send("§a灵动岛透明度已设置为: §e" + percent + "%");
            } catch (NumberFormatException e) {
                ChatNotifier.send("§c请输入有效的数字或百分比 (例如: 80 或 80%)");
                // 恢复为当前已配置的值
                int currentPercent = Math.round(config.islandOpacity * 100.0f);
                opacityInputHolder[0].setText(currentPercent + "%");
            }
        });

        // 初始化当前配置的回显文本 (例如 "100%")
        int currentPercent = Math.round(IrcConfig.get().islandOpacity * 100.0f);
        opacityInputHolder[0].setText(currentPercent + "%");

        panel.child(opacityInputHolder[0]);

        // ================= 基础数据 =================
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

        // ================= 硬件监控 =================
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