package me.cookie.kwrg.irc.manager;

public enum HypixelRank {
    NONE("NONE (普通玩家)", "", 0xAAAAAA, false),
    VIP("VIP", "§a[VIP] ", 0x55FF55, false),
    VIP_PLUS("VIP+", "§a[VIP§6+§a] ", 0x55FF55, false),
    MVP("MVP", "§b[MVP] ", 0x55FFFF, false),
    MVP_PLUS("MVP+", "", 0x55FFFF, true),
    MVP_PLUS_PLUS("MVP++", "", 0xFFAA00, true),
    ADMIN("ADMIN", "§c[ADMIN] ", 0xFF5555, false),
    OWNER("OWNER", "§c[OWNER] ", 0xFF5555, false),
    YOUTUBE("YOUTUBE","§c[YOUTUBE] ", 0xFF5555, false);

    private final String displayName;
    private final String staticPrefix;
    private final int nameColorRgb;
    private final boolean supportsPlusColor;

    HypixelRank(String displayName, String staticPrefix, int nameColorRgb, boolean supportsPlusColor) {
        this.displayName = displayName;
        this.staticPrefix = staticPrefix;
        this.nameColorRgb = nameColorRgb;
        this.supportsPlusColor = supportsPlusColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNameColorRgb() {
        return nameColorRgb;
    }

    public boolean supportsPlusColor() {
        return supportsPlusColor;
    }

    public String getFormattedPrefix(PlusColor plusColor) {
        if (this == NONE) return "";
        if (!supportsPlusColor) return staticPrefix;

        String plus = plusColor != null ? plusColor.code : "§c";
        if (this == MVP_PLUS) {
            return "§b[MVP" + plus + "+§b] ";
        } else if (this == MVP_PLUS_PLUS) {
            return "§6[MVP" + plus + "++§6] ";
        }
        return staticPrefix;
    }

    public enum PlusColor {
        RED("红色", "§c", 0xFF5555),
        GOLD("金色", "§6", 0xFFAA00),
        GREEN("绿色", "§a", 0x55FF55),
        YELLOW("黄色", "§e", 0xFFFF55),
        PINK("粉红", "§d", 0xFF55FF),
        WHITE("白色", "§f", 0xFFFFFF),
        BLUE("蓝色", "§9", 0x5555FF),
        DARK_GREEN("深绿", "§2", 0x00AA00),
        DARK_RED("深红", "§4", 0xAA0000),
        DARK_BLUE("深蓝", "§3", 0x00AAAA),
        DARK_PURPLE("暗紫", "§5", 0xAA00AA),
        GRAY("灰色", "§7", 0xAAAAAA),
        BLACK("黑色", "§0", 0x000000);

        public final String name;
        public final String code;
        public final int rgb;

        PlusColor(String name, String code, int rgb) {
            this.name = name;
            this.code = code;
            this.rgb = rgb;
        }
    }
}