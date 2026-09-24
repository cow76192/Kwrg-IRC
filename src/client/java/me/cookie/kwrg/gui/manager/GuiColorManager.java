package me.cookie.kwrg.gui.manager;

import io.wispforest.owo.ui.core.Color;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;

public class GuiColorManager {

    public static final String DEFAULT_COLOR = "#11161F";
    private static final int DEFAULT_BG_RGB = 0x11161F;

    private static int cachedBgRgb = DEFAULT_BG_RGB;
    private static int cachedOutlineRgb = calculateOutlineRgb(DEFAULT_BG_RGB);

    static { refresh(); }

    public static void refresh() {
        IrcConfig config = IrcConfig.get();
        if (config.guiColor == null || config.guiColor.isEmpty()) config.guiColor = DEFAULT_COLOR;

        java.awt.Color c = ColorUtil.parseSingleColor(config.guiColor);
        if (c != null) {
            cachedBgRgb = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
        } else {
            cachedBgRgb = DEFAULT_BG_RGB;
        }
        cachedOutlineRgb = calculateOutlineRgb(cachedBgRgb);
    }

    public static int getBgColorRgb() { return cachedBgRgb; }
    public static int getOutlineColorRgb() { return cachedOutlineRgb; }
    public static Color getPanelBg(float panelAlpha) { return RenderUtil.withAlpha(cachedBgRgb, (int) (255 * panelAlpha)); }
    public static Color getPanelOutline(float panelAlpha) { return RenderUtil.withAlpha(cachedOutlineRgb, (int) (85 * panelAlpha)); }

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
        cachedBgRgb = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
        cachedOutlineRgb = calculateOutlineRgb(cachedBgRgb);

        IrcConfig config = IrcConfig.get();
        config.guiColor = colorStr;
        IrcConfig.saveDelayed(800L);
        return 1;
    }

    public static void resetDefault() {
        setGuiColor(DEFAULT_COLOR);
        ChatNotifier.send("§a[IRC] GUI 主题颜色已重置为默认值: §f" + DEFAULT_COLOR, NotificationType.INFO);
    }

    private static int calculateOutlineRgb(int bgRgb) {
        int r = (bgRgb >> 16) & 0xFF;
        int g = (bgRgb >> 8) & 0xFF;
        int b = bgRgb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0], sat = hsb[1], bri = hsb[2];

        if (bri < 0.5f) {
            sat = Math.max(0.0f, sat * 0.6f);
            bri = Math.min(1.0f, bri + 0.35f + (1.0f - bri) * 0.25f);
        } else {
            sat = Math.min(1.0f, sat * 1.25f);
            bri = Math.max(0.0f, bri * 0.60f);
        }
        return java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
    }
}