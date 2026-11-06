package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.Navigator;

public class NavigatorModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean navigatorAllowSwimming = true;
        public boolean navigatorAllowJumping = true;
        public boolean navigatorAllowTeleport = true;
        public boolean navigatorAllowClimbing = true;
    }

    public NavigatorModule() {
        super("navigator", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    protected void onConfigLoaded() {
        Settings config = CONFIG.get();
        Navigator.setAllowSwimming(config.navigatorAllowSwimming);
        Navigator.setAllowJumping(config.navigatorAllowJumping);
        Navigator.setAllowTeleport(config.navigatorAllowTeleport);
        Navigator.setAllowClimbing(config.navigatorAllowClimbing);
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
                    Settings config = CONFIG.get();
                    config.navigatorAllowSwimming = !config.navigatorAllowSwimming;
                    Navigator.setAllowSwimming(config.navigatorAllowSwimming);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> CONFIG.get().navigatorAllowSwimming
        ));

        panel.child(new PanelItemComponent(
                "允许跳跃",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    Settings config = CONFIG.get();
                    config.navigatorAllowJumping = !config.navigatorAllowJumping;
                    Navigator.setAllowJumping(config.navigatorAllowJumping);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> CONFIG.get().navigatorAllowJumping
        ));

        panel.child(new PanelItemComponent(
                "允许 AOTV",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    Settings config = CONFIG.get();
                    config.navigatorAllowTeleport = !config.navigatorAllowTeleport;
                    Navigator.setAllowTeleport(config.navigatorAllowTeleport);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> CONFIG.get().navigatorAllowTeleport
        ));

        panel.child(new PanelItemComponent(
                "允许攀爬",
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    Settings config = CONFIG.get();
                    config.navigatorAllowClimbing = !config.navigatorAllowClimbing;
                    Navigator.setAllowClimbing(config.navigatorAllowClimbing);
                    IrcConfig.saveDelayed(800L);
                },
                null,
                () -> CONFIG.get().navigatorAllowClimbing
        ));
    }
}