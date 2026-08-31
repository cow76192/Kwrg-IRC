package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

public class ConnectModule extends Module {

    public ConnectModule() {
        super("connect", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        try {
            IrcClient.connect();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}