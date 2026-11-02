package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class ConnectModule extends Module {

    public ConnectModule() {
        super("connect", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        try {
            IrcClient.connect();
        } catch (RuntimeException t) {
            ChatNotifier.send("§c[IRC] 连接失败: " + t.getMessage(), NotificationType.ERR);
            t.printStackTrace();
        }
    }
}