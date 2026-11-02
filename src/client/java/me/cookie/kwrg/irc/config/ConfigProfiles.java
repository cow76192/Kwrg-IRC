package me.cookie.kwrg.irc.config;

import me.cookie.kwrg.util.SafePaths;

import java.nio.file.Path;

final class ConfigProfiles {
    private ConfigProfiles() {}

    static String sanitizeProfileName(String name) {
        if (name == null || name.isBlank()) return "default";
        String clean = name.trim();
        if (clean.endsWith(".json")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        return clean.isEmpty() ? "default" : clean;
    }

    static Path getProfilePath(Path kwrgDir, String profileName) {
        Path profilePath = SafePaths.resolveInside(kwrgDir, profileName + ".json");
        return profilePath != null ? profilePath : kwrgDir.resolve("default.json");
    }
}
