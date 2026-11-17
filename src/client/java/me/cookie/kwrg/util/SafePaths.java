package me.cookie.kwrg.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class SafePaths {

    private static final String ILLEGAL = "<>:\"|?*";

    private static final Set<String> RESERVED_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private SafePaths() {}

    public static boolean isReservedDeviceName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int dot = name.indexOf('.');
        String stem = (dot >= 0 ? name.substring(0, dot) : name).strip();
        return RESERVED_DEVICE_NAMES.contains(stem.toUpperCase(Locale.ROOT));
    }

    public static Path resolveUnder(Path base, String name) {
        if (base == null) {
            return null;
        }
        String clean = sanitizeFileName(name);
        if (clean.isEmpty()) {
            return null;
        }
        Path resolved = base.resolve(clean).normalize();
        Path root = base.toAbsolutePath().normalize();
        return resolved.toAbsolutePath().normalize().startsWith(root) ? resolved : null;
    }

    public static Path resolveInside(Path base, Path candidate) {
        if (base == null || candidate == null) {
            return null;
        }
        Path root = base.toAbsolutePath().normalize();
        return candidate.toAbsolutePath().normalize().startsWith(root) ? candidate : null;
    }

    public static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20 || ILLEGAL.indexOf(c) >= 0) {
                clean.append('_');
            } else {
                clean.append(c);
            }
        }
        String result = clean.toString().strip();
        int start = 0;
        while (start < result.length() && result.charAt(start) == '.') {
            start++;
        }
        String sanitized = result.substring(start).strip();
        return isReservedDeviceName(sanitized) ? "" : sanitized;
    }

    public static boolean isHexId(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
