package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public final class GlaciteAdventurerModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean glaciteAdventurerEnabled;
    }

    public GlaciteAdventurerModule() {
        super("Glacite Adventurer", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE,
                GlaciteAdventurerModule::isEnabled, CONFIG);
    }

    public static boolean isEnabled() {
        return CONFIG.get().glaciteAdventurerEnabled;
    }

    @Override
    public void onExecute() {
        CONFIG.updateDelayed(400, settings -> settings.glaciteAdventurerEnabled = !settings.glaciteAdventurerEnabled);
    }
}
