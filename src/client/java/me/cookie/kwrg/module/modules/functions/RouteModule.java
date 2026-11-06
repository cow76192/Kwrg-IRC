package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.RouteScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.RouteManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.function.Consumer;

public class RouteModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public String currentRouteFileName = "";
    }

    public RouteModule() {
        super("route", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    protected void onConfigLoaded() {
        RouteManager.reloadFromConfig();
    }

    @Override
    public void onExecute() {
        RouteScreen.open();
    }

    @Override
    public PanelItemComponent createPanelItem(Consumer<PanelItemComponent> openTertiaryCallback) {
        return new PanelItemComponent(getName(), getType(),
                (item, click) -> RouteScreen.openFrom(item),
                openTertiaryCallback, this::isActive);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new PanelItemComponent("打开 route 管理器", PanelItemComponent.ItemType.TRIGGER,
                (item, click) -> RouteScreen.openFrom(item), null));
    }
}
