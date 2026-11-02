package me.cookie.kwrg.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class SafePaths {
    private static final int MAX_NAME_LENGTH = 120;
    private static final String FALLBACK_NAME = "unnamed";

    private SafePaths() {}

    public static Path resolveUnder(Path baseDir, String name, String defaultExtension) {
        if (baseDir == null || name == null || name.isBlank()) return null;
        for (int i = 0; i < name.length(); i++) {
            if (!isSafeChar(name.charAt(i))) return null;
        }
        if (name.contains("..") || name.endsWith(".")) return null;
        if (name.length() > MAX_NAME_LENGTH) return null;
        String fileName = defaultExtension == null || defaultExtension.isEmpty() || name.endsWith(defaultExtension)
                ? name : name + defaultExtension;
        try {
            Path base = baseDir.normalize();
            Path target = baseDir.resolve(fileName).normalize();
            return base.equals(target.getParent()) ? target : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static Path resolveInside(Path baseDir, String name) {
        if (baseDir == null || name == null || name.isBlank()) return null;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return null;
        try {
            if (Path.of(name).isAbsolute()) return null;
            Path base = baseDir.normalize();
            Path target = baseDir.resolve(name).normalize();
            return base.equals(target.getParent()) ? target : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static String sanitizeFileName(String raw, int maxLength) {
        String source = raw == null ? "" : raw;
        StringBuilder sanitized = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            sanitized.append(isSafeChar(c) ? c : '_');
        }
        if (maxLength >= 0 && sanitized.length() > maxLength) sanitized.setLength(maxLength);
        return sanitized.length() == 0 ? FALLBACK_NAME : sanitized.toString();
    }

    public static boolean isHexId(String value, int length) {
        if (value == null || value.length() != length) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static boolean isSafeChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '-';
    }
}
