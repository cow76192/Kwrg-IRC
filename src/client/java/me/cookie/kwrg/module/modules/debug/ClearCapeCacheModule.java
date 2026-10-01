package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class ClearCapeCacheModule extends Module {

    public ClearCapeCacheModule() {
        super("clearCapeCache", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        PlayerCustomizationManager.clearCapeCache();
        ChatNotifier.send("§a[Debug] 已清理所有披风显存缓存", NotificationType.SUCCESS);
    }
}