package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.TotemAnimationManager;

public class ClearIndexCacheModule extends Module {

    public ClearIndexCacheModule() {
        super("clearIndexCache", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        try {
            ClearIndexCacheModule.handleClearImgCache();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static int handleClearImgCache() {
        int count = TotemAnimationManager.getCacheCount();
        double freedMb = TotemAnimationManager.clearCache();
        IrcMessageHandler.printMessage(String.format("§a[IRC] 已清理图片缓存 (%d 张)，共释放 RAM %.2f MB", count, freedMb));
        ChatNotifier.send(String.format("§a[IRC] 已清理图片缓存 (%d 张)，共释放 RAM %.2f MB", count, freedMb), NotificationType.SUCCESS);
        return 1;
    }
}