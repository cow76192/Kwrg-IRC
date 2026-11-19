package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ColorUtil;

public final class ESPModule extends Module {
    private static final String DEFAULT_ENTITY_OUTLINE_COLOR = "#00E5FF";
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean espEnabled;
        public String espEntityOutlineColor = DEFAULT_ENTITY_OUTLINE_COLOR;

        private void sanitize() {
            if (ColorUtil.parseSingleColor(espEntityOutlineColor) == null) {
                espEntityOutlineColor = DEFAULT_ENTITY_OUTLINE_COLOR;
            }
        }
    }

    public ESPModule() {
        super("ESP", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, ESPModule::isEnabled, CONFIG);
    }

    public static boolean isEnabled() {
        return CONFIG.get().espEnabled;
    }

    public static int entityOutlineColor() {
        return ColorUtil.parseSingleColor(CONFIG.get().espEntityOutlineColor).getRGB();
    }

    @Override
    public void onExecute() {
        CONFIG.updateDelayed(400, settings -> settings.espEnabled = !settings.espEnabled);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new ColorSettingComponent(CONFIG.get().espEntityOutlineColor,
                value -> CONFIG.updateDelayed(400, settings -> settings.espEntityOutlineColor = value)));
    }
}
