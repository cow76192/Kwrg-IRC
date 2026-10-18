package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.RouteScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class RouteModule extends Module {

    public RouteModule() {
        super("route", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        RouteScreen.open();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new PanelItemComponent("打开路线合集管理器", RouteScreen::open));
    }
}
