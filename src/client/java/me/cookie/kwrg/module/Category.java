package me.cookie.kwrg.module;

import java.util.Locale;

public enum Category {
    STYLE("style"),
    CONFIG("config"),
    FUNCTIONS("functions"),
    DEBUG("debug"),
    PLAY("play");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static Category fromName(String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        for (Category cat : values()) {
            if (cat.name.equalsIgnoreCase(clean)) {
                return cat;
            }
        }
        return null;
    }
}