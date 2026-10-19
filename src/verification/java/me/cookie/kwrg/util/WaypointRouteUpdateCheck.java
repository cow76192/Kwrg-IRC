package me.cookie.kwrg.util;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Real-file regression cases for non-destructive route updates. */
final class WaypointRouteUpdateCheck {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static PointEntry point(String name, String x) { return new PointEntry(name, x, "70", "0"); }
    private static PointEntry named(Path file, String name) { return WaypointFiles.collection(file).points.stream().filter(point -> point.name.equals(name)).findFirst().orElseThrow(); }
    private static void route(Path file, PointEntry... points) throws IOException {
        var collection = new WaypointFiles.Collection("source"); collection.points.addAll(List.of(points));
        WaypointFiles.write(file, collection);
        // Most existing route files do not carry stable IDs.
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (JsonElement node : json.getAsJsonArray("waypoints")) node.getAsJsonObject().remove("id");
        Files.writeString(file, new Gson().toJson(json));
    }
    private static Map<Integer, Boolean> choices(WaypointRouteUpdate plan, boolean route) {
        Map<Integer, Boolean> choices = new HashMap<>();
        for (int i = 0; i < plan.issues().size(); i++) choices.put(i, route);
        return choices;
    }

    static void run(Path root) throws Exception {
        Path points = root.resolve("wayPoints"), routes = root.resolve("routes");
        Files.createDirectories(routes);
        WaypointFiles.initialize(points, routes, Map.of());
        Path source = routes.resolve("route.json");
        route(source, point("A", "1"), point("B", "2"), point("C", "3"));
        Path imported = WaypointFiles.importRoute(source);
        check(WaypointFiles.importedFile(source).equals(imported), "Imported route is tracked immediately");
        var local = WaypointFiles.collection(imported);
        local.enabled = false; local.visible = false; local.regions.clear(); local.regions.add("garden");
        named(imported, "A").visible = false;
        local.points.add(point("local-only", "99"));
        WaypointFiles.save(imported);
        imported = WaypointFiles.rename(imported, "renamed-waypoint");
        WaypointFiles.initialize(points, routes, Map.of());
        check(WaypointFiles.importedFile(source).equals(imported), "Source association survives rename and reload");

        route(source, point("A", "1.0"), point("B", "2"), point("C", "3"), point("D", "4"));
        var addition = WaypointFiles.prepareUpdate(source);
        check(!addition.needsConfirmation() && addition.additions() == 1, "Additions and equivalent numeric formatting require no confirmation");
        var addedSummary = addition.apply(Map.of());
        check(addedSummary.equals(new WaypointRouteUpdate.Summary(1, 0, 0)), "Automatic update reports the actual added count");
        check(WaypointFiles.collection(imported).points.size() == 5 && named(imported, "local-only").x.equals("99"), "Automatic update preserves manually added points");
        check(!WaypointFiles.collection(imported).enabled && !WaypointFiles.collection(imported).visible && WaypointFiles.collection(imported).regions.equals(Set.of("garden")) && !named(imported, "A").visible, "Update preserves file flags, regions and node visibility");
        var again = WaypointFiles.prepareUpdate(source);
        check(!again.needsConfirmation() && again.additions() == 0, "Repeated updates do not duplicate additions");
        check(again.apply(Map.of()).equals(new WaypointRouteUpdate.Summary(0, 0, 0)), "No-op update reports zero actual changes");

        route(source, point("A", "10"), point("C", "3"), point("D", "4"), point("E", "5"));
        var mixed = WaypointFiles.prepareUpdate(source);
        check(mixed.issues().size() == 2 && mixed.additions() == 1, "Deletion, conflict and addition are classified independently");
        check(mixed.issues().stream().anyMatch(issue -> issue.kind() == WaypointRouteUpdate.Kind.DELETED && issue.local().name.equals("B")), "Only a previously imported deleted source node is proposed for deletion");
        String before = Files.readString(imported);
        try { mixed.apply(Map.of()); throw new AssertionError("Missing decisions must fail"); }
        catch (IllegalArgumentException expected) { check(Files.readString(imported).equals(before), "Unresolved or canceled update must not mutate the file"); }
        Map<Integer, Boolean> decisions = new HashMap<>();
        for (int i = 0; i < mixed.issues().size(); i++) decisions.put(i, mixed.issues().get(i).kind() == WaypointRouteUpdate.Kind.DELETED);
        check(mixed.apply(decisions).equals(new WaypointRouteUpdate.Summary(1, 1, 0)), "Kept conflict is excluded from modification count");
        check(named(imported, "A").x.equals("1") && WaypointFiles.collection(imported).points.stream().noneMatch(point -> point.name.equals("B")) && named(imported, "E") != null, "Individual decisions delete B, keep local A and still add E");
        WaypointFiles.initialize(points, routes, Map.of());
        check(!WaypointFiles.prepareUpdate(source).needsConfirmation(), "Accepted local conflict is remembered after reload");

        route(source, point("A", "20"), point("D", "4"), point("E", "5"));
        var replace = WaypointFiles.prepareUpdate(source);
        check(replace.issues().size() == 2, "Changed source triggers a fresh conflict and deletion prompt");
        check(replace.apply(choices(replace, true)).equals(new WaypointRouteUpdate.Summary(0, 1, 1)), "Batch update reports accepted deletion and coordinate edit");
        check(named(imported, "A").x.equals("20") && !named(imported, "A").visible && WaypointFiles.collection(imported).points.stream().noneMatch(point -> point.name.equals("C")), "Batch follow-route applies coordinates and deletions while preserving visibility");
        check(named(imported, "local-only") != null, "Batch deletion never removes local-only nodes");

        route(source, point("A", "20"), point("E", "5"));
        var keepDeletion = WaypointFiles.prepareUpdate(source);
        check(keepDeletion.apply(choices(keepDeletion, false)).equals(new WaypointRouteUpdate.Summary(0, 0, 0)), "Kept deleted node is excluded from deletion count");
        check(named(imported, "D") != null && !WaypointFiles.prepareUpdate(source).needsConfirmation(), "Keeping removed node preserves it locally without repeatedly asking");
        named(imported, "A").x = "30"; WaypointFiles.save(imported);
        var localConflict = WaypointFiles.prepareUpdate(source);
        check(localConflict.issues().size() == 1 && localConflict.issues().getFirst().kind() == WaypointRouteUpdate.Kind.CONFLICT, "New local coordinate edits are detected as conflicts");

        String unchanged = Files.readString(imported);
        route(source, point("A", "40"), point("E", "5"));
        try { localConflict.apply(choices(localConflict, true)); throw new AssertionError("Stale source must be rejected"); }
        catch (IOException expected) { check(Files.readString(imported).equals(unchanged), "Source changes during confirmation cannot overwrite local points"); }
        var staleTarget = WaypointFiles.prepareUpdate(source);
        Files.writeString(imported, Files.readString(imported) + "\n");
        try { staleTarget.apply(choices(staleTarget, true)); throw new AssertionError("Stale target must be rejected"); }
        catch (IOException expected) { /* external edits protected */ }

        Path twinsSource = routes.resolve("twins.json");
        route(twinsSource, point("same", "1"), point("same", "2"));
        Path twins = WaypointFiles.importRoute(twinsSource);
        route(twinsSource, point("same", "2.0"), point("same", "1.0"), point("new", "3"));
        var twinsUpdate = WaypointFiles.prepareUpdate(twinsSource);
        check(!twinsUpdate.needsConfirmation() && twinsUpdate.additions() == 1, "Duplicate names match exact coordinates before falling back to names");
        twinsUpdate.apply(Map.of());
        check(WaypointFiles.collection(twins).points.size() == 3, "Duplicate names and route reorder do not duplicate existing nodes");

        Path oldSource = routes.resolve("legacy.json");
        route(oldSource, point("old-A", "1"), point("old-B", "2"));
        Path oldTarget = WaypointFiles.importRoute(oldSource);
        JsonObject historical = JsonParser.parseString(Files.readString(oldTarget)).getAsJsonObject();
        historical.remove("sourceRoute"); historical.remove("sourceNodes"); Files.writeString(oldTarget, historical.toString());
        WaypointFiles.initialize(points, routes, Map.of());
        route(oldSource, point("old-A", "10"));
        check(WaypointFiles.importedFile(oldSource).equals(oldTarget), "Historical same-name import can be adopted");
        var legacyUpdate = WaypointFiles.prepareUpdate(oldSource);
        check(legacyUpdate.issues().size() == 2, "Historical imports still confirm deletions and coordinate conflicts");
        legacyUpdate.apply(choices(legacyUpdate, false));
        check(!WaypointFiles.collection(oldTarget).sourceRoute.isEmpty(), "Historical import gains a persistent source association after confirmation");

        Path idSource = routes.resolve("ids.json");
        var idFile = new WaypointFiles.Collection("ids");
        PointEntry idPoint = point("before rename", "10"); idPoint.id = "stable-source"; idFile.points.add(idPoint); WaypointFiles.write(idSource, idFile);
        Path idTarget = WaypointFiles.importRoute(idSource);
        idPoint.name = "after rename"; WaypointFiles.write(idSource, idFile);
        var renamed = WaypointFiles.prepareUpdate(idSource);
        check(!renamed.needsConfirmation() && renamed.additions() == 0, "Stable source IDs recognize renames");
        check(renamed.apply(Map.of()).equals(new WaypointRouteUpdate.Summary(0, 0, 1)), "Stable-ID rename counts as one modified point");
        check(named(idTarget, "after rename") != null, "Stable-ID rename updates the existing point");
        System.out.println("Route update checks passed: additions, deletion and conflict decisions, local edits, duplicate names, reload, legacy adoption and stale-file protection.");
    }
}
