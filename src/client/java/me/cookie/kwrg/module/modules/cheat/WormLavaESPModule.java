package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.gui.component.pane.SliderPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.WormLavaESPManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ColorUtil;

public class WormLavaESPModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean wormLavaEspEnabled = false;
        public int wormLavaEspOpacity = 100;
        public String wormLavaEspColor = "#FF8800";

        private void sanitize() {
            wormLavaEspOpacity = Math.clamp(wormLavaEspOpacity, 0, 100);
            if (wormLavaEspColor == null || ColorUtil.parseSingleColor(wormLavaEspColor) == null) wormLavaEspColor = "#FF8800";
        }
    }

    public WormLavaESPModule() {
        super("wormLavaESP", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, WormLavaESPManager::isEnabled, CONFIG);
    }

    @Override
    protected void onConfigLoaded() { WormLavaESPManager.reset(); }

    @Override
    public void onExecute() {
        WormLavaESPManager.toggle();
        IrcConfig.saveDelayed(400);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        panel.child(TextInfoPaneComponent.header("标记蓝宝石区域内 Y ≥ 64 的流动岩浆"));
        panel.child(SliderPaneComponent.ofInt("ESP 不透明度", 0, 100, 100,
                WormLavaESPManager::getOpacity, value -> {
                    CONFIG.get().wormLavaEspOpacity = value;
                    IrcConfig.saveDelayed(400);
                }, value -> value + "%"));
        panel.child(new ColorSettingComponent(CONFIG.get().wormLavaEspColor, value -> {
            CONFIG.get().wormLavaEspColor = value;
            WormLavaESPManager.reset();
            IrcConfig.saveDelayed(400);
        }));
    }
}
