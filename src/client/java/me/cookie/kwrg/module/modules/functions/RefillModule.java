package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.RefillScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.RefillManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RefillModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public List<Entry> refillEntries = new ArrayList<>();
    }

    public static final class Entry {
        public String item = "";
        public int target = 16;
        public boolean auto = false;
        public int autoSeconds = 0;
        public String key = "";
        public List<String> islands = new ArrayList<>(List.of("all"));
    }

    public RefillModule() {
        super("refill", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    public void onExecute() {
        RefillScreen.open();
    }

    @Override
    public PanelItemComponent createPanelItem(Consumer<PanelItemComponent> openTertiaryCallback) {
        return new PanelItemComponent(getName(), getType(), (Runnable) null,
                item -> RefillScreen.openFrom(item), this::isActive);
    }

    @Override
    protected void onConfigLoaded() {
        RefillManager.reset();
    }
}
