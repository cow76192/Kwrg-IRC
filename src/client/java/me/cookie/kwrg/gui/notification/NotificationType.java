package me.cookie.kwrg.gui.notification;

public enum NotificationType {
    INFO("ⓘ", 0x38BDF8, 0x101722),    // 天蓝 #38BDF8
    SUCCESS("✔", 0x34D399, 0x0D1F18), // 【新增】翡翠绿 #34D399
    WARN("⚠", 0xFBBF24, 0x1C1810),    // 暖金 #FBBF24
    ERR("✕", 0xF87171, 0x1F1214);     // 珊瑚红 #F87171

    private final String icon;
    private final int accentRgb;
    private final int bgRgb;

    NotificationType(String icon, int accentRgb, int bgRgb) {
        this.icon = icon;
        this.accentRgb = accentRgb;
        this.bgRgb = bgRgb;
    }

    public String getIcon() {
        return this.icon;
    }

    public int getAccentRgb() {
        return this.accentRgb;
    }

    public int getBgRgb() {
        return this.bgRgb;
    }
}