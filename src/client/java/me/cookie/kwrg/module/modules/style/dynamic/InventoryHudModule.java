package me.cookie.kwrg.module.modules.style.dynamic;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.SliderPaneComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.hud.HudEditorScreen;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

import java.util.List;

public class InventoryHudModule extends Module {

    private static final float DEFAULT_OPACITY = 0.75f;
    private static final float DEFAULT_SLOT_OPACITY = 0.65f;
    private static final float DEFAULT_SCALE = 1.0f;
    private static final int DEFAULT_PADDING = 6;
    private static final int DEFAULT_SLOT_SPACING = 2;
    private static final float DEFAULT_CORNER_RADIUS = 10.0f;

    public InventoryHudModule() {
        super(
                "inventoryHud",
                Category.STYLE,
                PanelItemComponent.ItemType.TOGGLE,
                () -> HudElementRegistry.isEnabled(HudElementRegistry.INVENTORY)
        );
    }

    @Override
    public void onExecute() {
        HudElement.State state = HudElementRegistry.state(HudElementRegistry.INVENTORY);
        state.enabled = !state.enabled;
        IrcConfig.saveDelayed(400L);

        if (state.enabled) {
            ChatNotifier.send("§a[HUD] 背包显示已开启", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[HUD] 背包显示已关闭", NotificationType.INFO);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("透明度"));

        PanelItemComponent followGlobalToggle = new PanelItemComponent(
                "沿用全局 HUD 配置",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudFollowGlobal = !config.inventoryHudFollowGlobal;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.inventoryHudFollowGlobal);
                    ChatNotifier.send(
                            config.inventoryHudFollowGlobal
                                    ? "§a[HUD] 背包显示已设为沿用全局 HUD 配置"
                                    : "§e[HUD] 背包显示已切换为独立透明度与样式配置",
                            NotificationType.INFO
                    );
                },
                null,
                () -> IrcConfig.get().inventoryHudFollowGlobal
        );
        panel.child(followGlobalToggle);

        SliderPaneComponent opacitySlider = new SliderPaneComponent("独立背景透明度")
                .range(0.0f, 1.0f)
                .defaultValue(DEFAULT_OPACITY)
                .step(0.01f)
                .getter(() -> IrcConfig.get().inventoryHudOpacity)
                .setter(val -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudOpacity = val;
                    HudElementRegistry.state(HudElementRegistry.INVENTORY).opacity = val;
                    if (config.inventoryHudFollowGlobal) {
                        config.inventoryHudFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                })
                .formatter(val -> Math.round(val * 100.0f) + "%");
        panel.child(opacitySlider);

        SliderPaneComponent slotOpacitySlider = new SliderPaneComponent("槽位透明度")
                .range(0.0f, 1.0f)
                .defaultValue(DEFAULT_SLOT_OPACITY)
                .step(0.01f)
                .getter(() -> IrcConfig.get().inventoryHudSlotOpacity)
                .setter(val -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudSlotOpacity = val;
                    if (config.inventoryHudFollowGlobal) {
                        config.inventoryHudFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                })
                .formatter(val -> Math.round(val * 100.0f) + "%");
        panel.child(slotOpacitySlider);

        panel.child(TextInfoPaneComponent.header("尺寸与样式 (右键滑块可重置)"));

        SliderPaneComponent scaleSlider = new SliderPaneComponent("缩放大小")
                .range(0.5f, 2.0f)
                .defaultValue(DEFAULT_SCALE)
                .step(0.05f)
                .getter(() -> IrcConfig.get().inventoryHudScale)
                .setter(val -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudScale = val;
                    if (config.inventoryHudFollowGlobal) {
                        config.inventoryHudFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                })
                .formatter(val -> String.format("%.2fx", val));

        SliderPaneComponent paddingSlider = SliderPaneComponent.ofInt(
                "面板内边距",
                2, 16, DEFAULT_PADDING,
                () -> HudElementRegistry.state(HudElementRegistry.INVENTORY).padding,
                val -> {
                    HudElementRegistry.state(HudElementRegistry.INVENTORY).padding = val;
                    if (IrcConfig.get().inventoryHudFollowGlobal) {
                        IrcConfig.get().inventoryHudFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                },
                val -> val + " px"
        );

        SliderPaneComponent spacingSlider = SliderPaneComponent.ofInt(
                "槽位格间距",
                0, 6, DEFAULT_SLOT_SPACING,
                () -> IrcConfig.get().inventoryHudSlotSpacing,
                val -> IrcConfig.get().inventoryHudSlotSpacing = val,
                val -> val + " px"
        );

        SliderPaneComponent radiusSlider = new SliderPaneComponent("外框圆角")
                .range(2.0f, 16.0f)
                .defaultValue(DEFAULT_CORNER_RADIUS)
                .step(0.5f)
                .getter(() -> IrcConfig.get().inventoryHudCornerRadius)
                .setter(val -> IrcConfig.get().inventoryHudCornerRadius = val)
                .formatter(val -> String.format("%.1f px", val));

        List<SliderPaneComponent> layoutSliders = List.of(
                scaleSlider, paddingSlider, spacingSlider, radiusSlider
        );
        for (SliderPaneComponent slider : layoutSliders) {
            panel.child(slider);
        }

        panel.child(TextInfoPaneComponent.header("显示选项"));

        panel.child(new PanelItemComponent(
                "显示槽位背景格",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudShowSlotBg = !config.inventoryHudShowSlotBg;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.inventoryHudShowSlotBg);
                },
                null,
                () -> IrcConfig.get().inventoryHudShowSlotBg
        ));

        panel.child(new PanelItemComponent(
                "跟随 GUI 配色",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudFollowTheme = !config.inventoryHudFollowTheme;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.inventoryHudFollowTheme);
                },
                null,
                () -> IrcConfig.get().inventoryHudFollowTheme
        ));

        panel.child(new PanelItemComponent(
                "包含快捷栏",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudShowHotbar = !config.inventoryHudShowHotbar;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.inventoryHudShowHotbar);
                },
                null,
                () -> IrcConfig.get().inventoryHudShowHotbar
        ));

        panel.child(new PanelItemComponent(
                "背包为空时自动隐藏",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.inventoryHudHideEmpty = !config.inventoryHudHideEmpty;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.inventoryHudHideEmpty);
                },
                null,
                () -> IrcConfig.get().inventoryHudHideEmpty
        ));

        panel.child(TextInfoPaneComponent.header("布局管理"));

        panel.child(new PanelItemComponent(
                "Link to HudEditor",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> HudEditorScreen.open(HudElementRegistry.INVENTORY),
                null
        ));

        panel.child(new PanelItemComponent(
                "reset",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    HudElementRegistry.reset(HudElementRegistry.INVENTORY);
                    opacitySlider.resetToDefault();
                    slotOpacitySlider.resetToDefault();
                    for (SliderPaneComponent slider : layoutSliders) {
                        slider.resetToDefault();
                    }
                    ChatNotifier.send("§a[HUD] 背包显示已恢复默认位置与样式", NotificationType.SUCCESS);
                },
                null
        ));
    }
}