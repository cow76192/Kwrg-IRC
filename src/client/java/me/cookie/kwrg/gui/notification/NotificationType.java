package me.cookie.kwrg.gui.notification;

import net.minecraft.resources.Identifier;

public enum NotificationType {
    INFO("info", 0x38BDF8, 0x101722),       // 天蓝 #38BDF8
    SUCCESS("success", 0x34D399, 0x0D1F18), // 翡翠绿 #34D399
    WARN("warn", 0xFBBF24, 0x1C1810),       // 暖金 #FBBF24
    ERR("err", 0xF87171, 0x1F1214);         // 珊瑚红 #F87171

    private final Identifier icon;
    private final int accentRgb;
    private final int bgRgb;

    NotificationType(String iconName, int accentRgb, int bgRgb) {
        this.icon = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/notification/" + iconName + ".png");
        this.accentRgb = accentRgb;
        this.bgRgb = bgRgb;
    }

    public Identifier getIcon() {
        return this.icon;
    }

    public int getAccentRgb() {
        return this.accentRgb;
    }

    public int getBgRgb() {
        return this.bgRgb;
    }
}