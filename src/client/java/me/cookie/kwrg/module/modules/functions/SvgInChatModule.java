package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class SvgInChatModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean svgInChat = false;
    }

    public SvgInChatModule() {
        super("svgInChat", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> CONFIG.get().svgInChat, CONFIG);
    }

    @Override
    public void onExecute() {
        CONFIG.update(config -> config.svgInChat = !config.svgInChat);
    }

    public static boolean enabled() {
        return CONFIG.get().svgInChat;
    }
}
