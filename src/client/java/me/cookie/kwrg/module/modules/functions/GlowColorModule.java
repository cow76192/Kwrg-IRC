package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;

public class GlowColorModule extends Module {

    public GlowColorModule() {
        super("glowColor", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcGlowManager.isGlowEnabled());
    }

    @Override
    public void onExecute() {
        boolean newState = !IrcGlowManager.isGlowEnabled();
        IrcGlowManager.setGlowEnabled(newState);
        ChatNotifier.send("§a[IRC] irc玩家发光效果已: " + (newState ? "§a开启" : "§c关闭"), (newState ? NotificationType.SUCCESS : NotificationType.ERR));
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.child(new ColorSettingComponent(IrcConfig.get().glowColor, TertiaryPanelComponent::setGlowColor));
    }

    public static int showGlowColor() {
        IrcConfig config = IrcConfig.get();
        ChatNotifier.sendInChatScreen("§6[IRC] 当前glow颜色: §f" + config.glowColor + " §7(用法: /irc functions glowColor #HEX 或 rgb(...))");
        return 1;
    }

    public static int setGlowColor(String colorStr) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        if (c == null) {
            ChatNotifier.sendInChatScreen("§c[IRC] 无法解析的颜色格式！支持 #HEX, rgb(...), hsl(...)");
            return 0;
        }

        IrcConfig config = IrcConfig.get();
        config.glowColor = colorStr;
        IrcConfig.saveDelayed(800L);

        ChatNotifier.sendInChatScreen("§a[IRC] Irc 在线玩家glow颜色已更新为: §f" + colorStr);
        return 1;
    }
}