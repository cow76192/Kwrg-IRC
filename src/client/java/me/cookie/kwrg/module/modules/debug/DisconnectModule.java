package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class DisconnectModule extends Module {

    public DisconnectModule() {
        super("disconnect", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        try {
            IrcClient.disconnect();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}