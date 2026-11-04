package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.Navigator;

public class NavigatorModule extends Module {

    public NavigatorModule() {
        super("navigator", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("寻路动作配置"));

        panel.child(new PanelItemComponent(
                "允许游泳",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.navigatorAllowSwimming = !config.navigatorAllowSwimming;
                    Navigator.setAllowSwimming(config.navigatorAllowSwimming);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> IrcConfig.get().navigatorAllowSwimming
        ));

        panel.child(new PanelItemComponent(
                "允许跳跃",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.navigatorAllowJumping = !config.navigatorAllowJumping;
                    Navigator.setAllowJumping(config.navigatorAllowJumping);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> IrcConfig.get().navigatorAllowJumping
        ));

        panel.child(new PanelItemComponent(
                "允许 AOTV",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.navigatorAllowTeleport = !config.navigatorAllowTeleport;
                    Navigator.setAllowTeleport(config.navigatorAllowTeleport);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> IrcConfig.get().navigatorAllowTeleport
        ));

        panel.child(new PanelItemComponent(
                "允许攀爬",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    IrcConfig config = IrcConfig.get();
                    config.navigatorAllowClimbing = !config.navigatorAllowClimbing;
                    Navigator.setAllowClimbing(config.navigatorAllowClimbing);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> IrcConfig.get().navigatorAllowClimbing
        ));
    }
}