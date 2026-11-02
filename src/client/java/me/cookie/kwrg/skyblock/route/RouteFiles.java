package me.cookie.kwrg.skyblock.route;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class RouteFiles {

    static List<Path> listFiles() {
        try {
            Path dir = RouteManager.getDir();
            Files.createDirectories(dir);
            try (Stream<Path> stream = Files.list(dir)) {
                Map<String, Path> selected = new LinkedHashMap<>();
                for (Path path : stream.filter(RouteNames::isRouteFile).toList()) {
                    String base = RouteNames.baseName(path).toLowerCase(Locale.ROOT);
                    Path existing = selected.get(base);
                    if (existing == null || (RouteNames.isJson(path) && !RouteNames.isJson(existing))) {
                        selected.put(base, path);
                    }
                }
                return selected.values().stream()
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    static void ensureRouteNameAvailable(Path directory, String name, Path original) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path file : stream.filter(RouteNames::isRouteFile).toList()) {
                if ((original == null || !file.equals(original)) && RouteNames.baseName(file).equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("已存在同名路线，请换一个名称");
                }
            }
        }
    }

    static String readFile(Path path) {
        try {
            if (Files.size(path) > RouteManager.MAX_ROUTE_FILE_BYTES) return null;
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }
}
