package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public final class TitleStyleModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean customTitleButtons = true;
        public boolean customTitleOnStartup = true;
    }

    public TitleStyleModule() {
        super("TitleStyle", Category.DEBUG, PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.get().customTitleButtons, CONFIG);
    }

    @Override
    public void onExecute() {
        CONFIG.update(settings -> settings.customTitleButtons = !settings.customTitleButtons);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new PanelItemComponent("其他界面使用自定义按钮", PanelItemComponent.ItemType.TOGGLE,
                this::onExecute, null, () -> CONFIG.get().customTitleButtons));
        panel.child(new PanelItemComponent("启动时显示自定义主菜单", PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.update(settings -> settings.customTitleOnStartup = !settings.customTitleOnStartup),
                null, () -> CONFIG.get().customTitleOnStartup));
    }
}
