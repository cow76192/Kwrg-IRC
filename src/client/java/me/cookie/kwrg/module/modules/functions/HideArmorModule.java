package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;

public class HideArmorModule extends Module {

    public HideArmorModule() {
        super("hideArmor", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().hideArmor);
    }

    @Override
    public void onExecute() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            boolean newState = !IrcConfig.get().hideArmor;
            HideArmorManager.setPlayerHideArmor(mc.getUser().getName(), newState);
        }
    }

    public static int setHideArmor(boolean enabled) {
        IrcConfig config = IrcConfig.get();
        config.hideArmor = enabled;
        IrcConfig.save();
        IrcClient.sendFunctionSync("hideArmor", enabled);
        ChatNotifier.sendInChatScreen("§a[IRC] hideArmor 功能已设置为: " + (enabled ? "§a开启" : "§c关闭"));
        return 1;
    }

    public static int showHideArmor() {
        IrcConfig config = IrcConfig.get();
        ChatNotifier.sendInChatScreen("§6[IRC] 当前 hideArmor 状态: " + (config.hideArmor ? "§a开启" : "§c关闭") + " §7(用法: /irc functions hideArmor true/false)");
        return 1;
    }
}