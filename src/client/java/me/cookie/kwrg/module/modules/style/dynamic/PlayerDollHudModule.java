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

public class PlayerDollHudModule extends Module {

    private static final float DEFAULT_OPACITY = 1.0f;
    private static final float DEFAULT_SCALE = 1.0f;

    public PlayerDollHudModule() {
        super(
                "playerDollHud",
                Category.STYLE,
                PanelItemComponent.ItemType.TOGGLE,
                () -> HudElementRegistry.isEnabled(HudElementRegistry.PLAYER_DOLL)
        );
    }

    @Override
    public void onExecute() {
        HudElement.State state = HudElementRegistry.state(HudElementRegistry.PLAYER_DOLL);
        state.enabled = !state.enabled;
        IrcConfig.saveDelayed(400L);

        if (state.enabled) {
            ChatNotifier.send("§a[HUD] 玩家模型显示已开启", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[HUD] 玩家模型显示已关闭", NotificationType.INFO);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("全局与透明度设置 (右键滑块可重置)"));

        PanelItemComponent followGlobalToggle = new PanelItemComponent(
                "沿用全局 HUD 配置",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.playerDollFollowGlobal = !config.playerDollFollowGlobal;
                    IrcConfig.saveDelayed(400L);
                    btn.setToggled(config.playerDollFollowGlobal);
                    ChatNotifier.send(
                            config.playerDollFollowGlobal
                                    ? "§a[HUD] 玩家模型已设为沿用全局 HUD 配置"
                                    : "§e[HUD] 玩家模型已切换为独立透明度与大小配置",
                            NotificationType.INFO
                    );
                },
                null,
                () -> IrcConfig.get().playerDollFollowGlobal
        );
        panel.child(followGlobalToggle);

        SliderPaneComponent opacitySlider = new SliderPaneComponent("模型透明度")
                .range(0.05f, 1.0f)
                .defaultValue(DEFAULT_OPACITY)
                .step(0.01f)
                .getter(() -> IrcConfig.get().playerDollOpacity)
                .setter(val -> {
                    IrcConfig config = IrcConfig.get();
                    config.playerDollOpacity = val;
                    HudElementRegistry.state(HudElementRegistry.PLAYER_DOLL).opacity = val;
                    if (config.playerDollFollowGlobal) {
                        config.playerDollFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                })
                .formatter(val -> Math.round(val * 100.0f) + "%");
        panel.child(opacitySlider);

        SliderPaneComponent scaleSlider = new SliderPaneComponent("HUD 显示大小")
                .range(HudElement.State.MIN_SCALE, HudElement.State.MAX_SCALE)
                .defaultValue(DEFAULT_SCALE)
                .step(0.05f)
                .getter(() -> HudElementRegistry.state(HudElementRegistry.PLAYER_DOLL).scale)
                .setter(val -> {
                    HudElement.State state = HudElementRegistry.state(HudElementRegistry.PLAYER_DOLL);
                    state.scale = val;
                    state.sanitize();

                    IrcConfig config = IrcConfig.get();
                    if (config.playerDollFollowGlobal) {
                        config.playerDollFollowGlobal = false;
                        followGlobalToggle.setToggled(false);
                    }
                })
                .formatter(val -> String.format("%.2fx", val));

        panel.child(scaleSlider);

        panel.child(TextInfoPaneComponent.header("布局管理"));

        panel.child(new PanelItemComponent(
                "Link To HudEditor",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> HudEditorScreen.open(HudElementRegistry.PLAYER_DOLL),
                null
        ));

        panel.child(new PanelItemComponent(
                "reset",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    HudElementRegistry.reset(HudElementRegistry.PLAYER_DOLL);
                    opacitySlider.resetToDefault();
                    scaleSlider.resetToDefault();
                    ChatNotifier.send("§a[HUD] 玩家模型已恢复默认位置与样式", NotificationType.SUCCESS);
                },
                null
        ));
    }
}