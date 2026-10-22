package me.cookie.kwrg.gui.manager;

import me.cookie.kwrg.gui.component.BaseGuiComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;

public class GuiColorManager {

    public static final String DEFAULT_COLOR = String.format("#%06X", BaseGuiComponent.DEFAULT_PANEL_BG);

    static { refresh(); }

    public static void refresh() {
        IrcConfig config = IrcConfig.get();
        if (config.guiColor == null || config.guiColor.isEmpty()) config.guiColor = DEFAULT_COLOR;

        java.awt.Color c = ColorUtil.parseSingleColor(config.guiColor);
        BaseGuiComponent.setPanelBg(c != null
                ? (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue()
                : BaseGuiComponent.DEFAULT_PANEL_BG);
        BaseGuiComponent.setOpacity(config.guiOpacity);
    }

    public static String getGuiColorStr() {
        IrcConfig config = IrcConfig.get();
        return (config.guiColor != null && !config.guiColor.isEmpty()) ? config.guiColor : DEFAULT_COLOR;
    }

    public static int setGuiColor(String colorStr) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        if (c == null) {
            ChatNotifier.sendInChatScreen("§c[IRC] 无法解析的颜色格式！支持 #HEX, rgb(...), hsl(...)");
            return 0;
        }
        BaseGuiComponent.setPanelBg((c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());

        IrcConfig config = IrcConfig.get();
        config.guiColor = colorStr;
        IrcConfig.saveDelayed(800L);
        return 1;
    }

    public static void setGuiOpacity(float opacity) {
        BaseGuiComponent.setOpacity(opacity);
        IrcConfig.get().guiOpacity = BaseGuiComponent.opacity();
        IrcConfig.saveDelayed(800L);
    }

    public static void resetDefault() {
        BaseGuiComponent.setPanelBg(BaseGuiComponent.DEFAULT_PANEL_BG);
        BaseGuiComponent.setOpacity(1.0f);

        IrcConfig config = IrcConfig.get();
        config.guiColor = DEFAULT_COLOR;
        config.guiOpacity = 1.0f;
        IrcConfig.save();
        ChatNotifier.send("§a[IRC] GUI 主题颜色与透明度已重置为默认值: §f" + DEFAULT_COLOR + " §7/ §f100%", NotificationType.INFO);
    }
}
