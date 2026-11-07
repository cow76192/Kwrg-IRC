package me.cookie.kwrg.irc.manager;

import java.util.List;

public enum HypixelRank {
    NONE("NONE (普通玩家)", "", 0xAAAAAA, false),
    VIP("VIP", "[VIP] ", 0x55FF55, false),
    VIP_PLUS("VIP+", "[VIP+] ", 0x55FF55, false),
    MVP("MVP", "[MVP] ", 0x55FFFF, false),
    MVP_PLUS("MVP+", "", 0x55FFFF, true),
    MVP_PLUS_PLUS("MVP++", "", 0xFFAA00, true),
    ADMIN("ADMIN", "[ADMIN] ", 0xFF5555, false),
    OWNER("OWNER", "[OWNER] ", 0xFF5555, false),
    YOUTUBE("YOUTUBE","[YOUTUBE] ", 0xFF5555, false);

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
        return getNameColorRgb(TagColor.GOLD);
    }

    public int getNameColorRgb(TagColor tagColor) {
        return this == MVP_PLUS_PLUS ? effectiveTagColor(tagColor).rgb : nameColorRgb;
    }

    public String getNameColorCode(TagColor tagColor) {
        return switch (this) {
            case NONE -> "§7";
            case VIP, VIP_PLUS -> "§a";
            case MVP, MVP_PLUS -> "§b";
            case MVP_PLUS_PLUS -> effectiveTagColor(tagColor).code;
            case ADMIN, OWNER, YOUTUBE -> "§c";
        };
    }

    private static TagColor effectiveTagColor(TagColor tagColor) {
        return tagColor != null ? tagColor : TagColor.GOLD;
    }

    public boolean supportsPlusColor() {
        return supportsPlusColor;
    }

    public String getFormattedPrefix(PlusColor plusColor) {
        return getFormattedPrefix(plusColor, TagColor.GOLD);
    }

    public String getFormattedPrefix(PlusColor plusColor, TagColor tagColor) {
        List<PrefixPart> parts = getPrefixParts(plusColor, tagColor);
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (PrefixPart part : parts) {
            out.append(codeFor(part.rgb())).append(part.text());
        }
        return out.toString();
    }

    private static String codeFor(int rgb) {
        for (PlusColor color : PlusColor.values()) {
            if (color.rgb == rgb) {
                return color.code;
            }
        }
        for (TagColor color : TagColor.values()) {
            if (color.rgb == rgb) {
                return color.code;
            }
        }
        return "§f";
    }

    public record PrefixPart(String text, int rgb) {
    }

    public List<PrefixPart> getPrefixParts(PlusColor plusColor, TagColor tagColor) {
        if (this == NONE) {
            return List.of();
        }
        if (!supportsPlusColor) {
            return staticParts();
        }
        int plusRgb = plusColor != null ? plusColor.rgb : PlusColor.RED.rgb;
        if (this == MVP_PLUS) {
            return List.of(new PrefixPart("[MVP", nameColorRgb), new PrefixPart("+", plusRgb), new PrefixPart("] ", nameColorRgb));
        }
        if (this == MVP_PLUS_PLUS) {
            TagColor tag = effectiveTagColor(tagColor);
            return List.of(new PrefixPart("[MVP", tag.rgb), new PrefixPart("++", plusRgb), new PrefixPart("] ", tag.rgb));
        }
        return staticParts();
    }

    private List<PrefixPart> staticPartsCache;

    private List<PrefixPart> staticParts() {
        if (staticPartsCache == null) {
            staticPartsCache = buildStaticParts();
        }
        return staticPartsCache;
    }

    private List<PrefixPart> buildStaticParts() {
        String text = staticPrefix;
        if (text.isEmpty()) {
            return List.of();
        }
        if (this == VIP_PLUS) {
            int plus = text.indexOf('+');
            if (plus >= 0) {
                return List.of(
                        new PrefixPart(text.substring(0, plus), nameColorRgb),
                        new PrefixPart("+", TagColor.GOLD.rgb),
                        new PrefixPart(text.substring(plus + 1), nameColorRgb));
            }
        }
        return List.of(new PrefixPart(text, nameColorRgb));
    }

    public enum TagColor {
        GOLD("Gold Tag", "§6", 0xFFAA00),
        AQUA("Aqua Tag", "§b", 0x55FFFF);

        public final String name;
        public final String code;
        public final int rgb;

        TagColor(String name, String code, int rgb) {
            this.name = name;
            this.code = code;
            this.rgb = rgb;
        }
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
        DARK_AQUA("深青", "§3", 0x00AAAA),
        DARK_BLUE("深蓝", "§1", 0x0000AA),
        DARK_PURPLE("暗紫", "§5", 0xAA00AA),
        DARK_GRAY("深灰", "§8", 0x555555),
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
