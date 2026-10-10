package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class PearlCancelModule extends Module {

    public PearlCancelModule() {
        super("PearlCancel", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().pearlCancel);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.pearlCancel = !config.pearlCancel;
        IrcConfig.save();
        ChatNotifier.send("§aPearlCancel 已" + (config.pearlCancel
                ? "§a开启:手持珍珠右键方块只取消交互(箱子/门),珍珠仍会扔出"
                : "§c关闭"), NotificationType.INFO);
    }

    public static int setPearlCancel(boolean enabled) {
        IrcConfig.get().pearlCancel = enabled;
        IrcConfig.save();
        ChatNotifier.sendInChatScreen("§a[Kwrg] PearlCancel 已设置为: " + (enabled ? "§a开启" : "§c关闭"));
        return 1;
    }

    public static int showPearlCancel() {
        ChatNotifier.sendInChatScreen("§6[Kwrg] 当前 PearlCancel 状态: " + (IrcConfig.get().pearlCancel ? "§a开启" : "§c关闭") + " §7(用法: /irc functions pearlCancel true/false)");
        return 1;
    }
}
