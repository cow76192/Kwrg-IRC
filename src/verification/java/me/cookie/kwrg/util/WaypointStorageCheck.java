package me.cookie.kwrg.util;

import me.cookie.kwrg.irc.manager.WaypointManager;

import com.google.gson.JsonParser;
import me.cookie.kwrg.irc.IrcConfig;
import java.nio.file.*;
import java.util.*;

/** Standalone verification: exercises real point files without a game window. */
public final class WaypointStorageCheck {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejected(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected invalid operation to fail"); }
        catch (IllegalArgumentException expected) { /* validated */ }
    }
    private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kwrg-waypoints-");
        try {
            Path points = root.resolve("wayPoints"), routes = root.resolve("routes");
            Files.createDirectories(routes);
            var legacy = new IrcConfig.WaypointEntry("manual", 1.5, 80, -3);
            legacy.enabled = false;
            WaypointFiles.initialize(points, routes, Map.of("stable-id", legacy));
            Path defaults = points.resolve("default.json");
            check(Files.exists(defaults), "Legacy points migrate to default.json");
            check(WaypointManager.savedEntries().size() == 1 && !WaypointManager.savedEntries().getFirst().enabled, "Migration preserves manual points and visibility");
            check(WaypointFiles.collection(defaults).points.getFirst().id.equals("stable-id"), "Migration preserves point IDs");
            WaypointFiles.initialize(points, routes, Map.of("old", new IrcConfig.WaypointEntry("do-not-duplicate", 0, 0, 0)));
            check(WaypointFiles.collection(defaults).points.size() == 1, "Reload must not duplicate legacy points");
            check(!WaypointFiles.delete(defaults), "Default collection cannot be deleted");
            rejected(() -> WaypointFiles.rename(defaults, "renamed"));
            check(WaypointManager.saveEntry(-1, "三级面板", new String[]{"5.5", "81", "-7"}), "Tertiary editor saves into default collection");
            check(WaypointFiles.read(defaults).points.size() == 2, "Tertiary changes persist in the default file");
            WaypointFiles.select(defaults);
            WaypointFiles.entries().getFirst().name = "屏幕编辑";
            check(WaypointFiles.saveCurrent() && WaypointManager.savedEntries().getFirst().name.equals("屏幕编辑"), "Screen edits synchronize back into the tertiary editor");
            check(WaypointManager.removeAt(1) && WaypointFiles.read(defaults).points.size() == 1, "Tertiary deletion updates default JSON");

            Path first = WaypointFiles.create("第一组"), second = WaypointFiles.create("第二组.json");
            rejected(() -> WaypointFiles.create("第一组"));
            rejected(() -> WaypointFiles.create("../outside"));
            check(Files.exists(second) && !second.getFileName().toString().contains(".json.json"), "Names normalize their extension");
            check(WaypointFiles.select(first), "First file can be selected");
            WaypointFiles.addEmpty();
            PointEntry entry = WaypointFiles.entries().getFirst();
            entry.name = "decimal"; entry.x = "-12.5"; entry.y = "80"; entry.z = "40"; entry.visible = false;
            check(WaypointFiles.saveCurrent(), "Edited points save");
            WaypointFiles.Collection collection = WaypointFiles.collection(first);
            collection.regions.clear(); collection.regions.addAll(Set.of("hub", "garden"));
            check(collection.activeIn("hub") && collection.activeIn("garden") && !collection.activeIn("combat_3") && !collection.activeIn(""), "Multiple regions filter rendering and reject unknown location");
            check(WaypointFiles.save(first), "Region selections save");
            check(WaypointFiles.select(second) && WaypointFiles.collection(first).enabled && WaypointFiles.collection(second).enabled, "Selecting another file does not disable loaded files");
            me.cookie.kwrg.gui.PointCollectionActivationCheck.run(first, second);
            var reloaded = WaypointFiles.read(first);
            check(reloaded.regions.equals(Set.of("hub", "garden")) && !reloaded.points.getFirst().visible && reloaded.points.getFirst().x.equals("-12.5"), "Regions, decimals and point visibility round trip");
            collection.enabled = false;
            check(!collection.activeIn("hub"), "Disabled files never render");
            collection.enabled = true; collection.visible = false;
            check(!collection.activeIn("hub"), "Hidden files never render");
            collection.visible = true; collection.regions.clear(); collection.regions.add("all");
            check(collection.activeIn("") && collection.activeIn("combat_3"), "All allows every area");

            Path route = routes.resolve("第一组.json");
            String original = "{\"showLine\":true,\"visible\":false,\"areas\":[{\"name\":\"nested\",\"waypoints\":[{\"label\":\"route point\",\"coords\":[1.25,70,-4],\"visible\":false}]}]}";
            Files.writeString(route, original);
            List<Path> listed = WaypointFiles.listFiles();
            check(listed.getFirst().equals(defaults) && listed.getLast().equals(route), "Default is first and route import sources are last");
            check(!WaypointFiles.select(route) && !WaypointFiles.delete(route), "Route sources cannot be selected or deleted through waypoint management");
            Path imported = WaypointFiles.importRoute(route);
            check(imported.getParent().equals(points) && !imported.equals(first), "Duplicate route name imports under a new safe name");
            check(Files.readString(route).equals(original), "Import preserves the original route");
            var importedJson = JsonParser.parseString(Files.readString(imported)).getAsJsonObject();
            check(!importedJson.has("showLine") && importedJson.has("regions") && importedJson.has("enabled"), "Import converts route-only flags to waypoint schema");
            check(!WaypointFiles.collection(imported).points.getFirst().visible && WaypointFiles.collection(imported).points.getFirst().x.equals("1.25"), "Nested route coordinates and visibility survive conversion");
            PointEntry temporary = new PointEntry("chat-only", "1", "2", "3"); temporary.temporary = true;
            collection.points.add(temporary);
            WaypointFiles.save(first);
            check(!Files.readString(first).contains("chat-only") && !Files.readString(first).contains("temporary"), "Temporary chat points never persist");
            collection.points.remove(temporary);
            Path renamed = WaypointFiles.rename(first, "改名后");
            check(!Files.exists(first) && Files.exists(renamed) && WaypointFiles.collection(renamed).points.size() == 1, "Rename preserves collection contents");
            check(WaypointFiles.select(renamed) && WaypointFiles.deleteAt(0) && WaypointFiles.entries().isEmpty(), "Point deletion persists");
            check(WaypointFiles.delete(renamed) && WaypointFiles.currentFile().equals(defaults), "Deleting edited file returns to default");
            var profileField = IrcConfig.class.getDeclaredField("currentProfileName");
            profileField.setAccessible(true);
            String oldProfile = (String) profileField.get(null);
            try {
                profileField.set(null, "second-profile");
                var secondLegacy = Map.of("stable-id", new IrcConfig.WaypointEntry("another-profile", 10, 80, 10));
                WaypointFiles.initialize(points, routes, secondLegacy);
                check(WaypointFiles.collection(defaults).points.size() == 2 && WaypointFiles.collection(defaults).points.stream().map(point -> point.id).distinct().count() == 2, "Another legacy profile migrates without losing points or duplicating IDs");
                check(WaypointFiles.deleteAt(1), "Migrated point can be removed");
                WaypointFiles.initialize(points, routes, secondLegacy);
                check(WaypointFiles.collection(defaults).points.size() == 1, "Reloading a legacy profile must not resurrect deleted migrated points");
            } finally { profileField.set(null, oldProfile); }
            check(SkyblockIsland.search("花园").stream().anyMatch(island -> island.id().equals("garden")), "Chinese island search works");
            check(SkyblockIsland.search("CRIMSON").stream().anyMatch(island -> island.id().equals("crimson_isle")), "Island ID search ignores case");
            check(SkyblockIsland.search("no-match").size() == 1 && SkyblockIsland.search("no-match").getFirst().id().equals("all"), "All stays first even when search has no island match");
            WaypointRouteUpdateCheck.run(root.resolve("route-update"));
            RouteRestoreCheck.run(root.resolve("route-restore"));
            System.out.println("Waypoint storage checks passed: migration, independent collections, regions, conversion, edits, deletion and temporary-point exclusion.");
        } finally {
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
