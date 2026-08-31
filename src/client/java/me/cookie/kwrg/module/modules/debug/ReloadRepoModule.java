package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.SkyblockItemHelper;

public class ReloadRepoModule extends Module {

    public ReloadRepoModule() {
        super("reloadRepo", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        try {
            SkyblockItemHelper.reloadRepo();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}