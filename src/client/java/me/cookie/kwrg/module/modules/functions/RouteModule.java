package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.RouteScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.function.Consumer;

public class RouteModule extends Module {

    public RouteModule() {
        super("route", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
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
        panel.child(new PanelItemComponent("打开路线合集管理器", PanelItemComponent.ItemType.TRIGGER,
                (item, click) -> RouteScreen.openFrom(item), null));
    }
}
