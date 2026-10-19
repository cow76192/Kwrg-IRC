package me.cookie.kwrg.util;

import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.RouteManager;
import java.nio.file.*;
import java.util.concurrent.*;

/** A startup restore must not go through the profile-save path or cancel pending saves. */
final class RouteRestoreCheck {
    static void run(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path route = directory.resolve("startup-route.json");
        String json = "{\"visible\":false,\"showLine\":true,\"waypoints\":[{\"name\":\"restored\",\"x\":1,\"y\":2,\"z\":3}]}";
        Files.writeString(route, json);
        var taskField = IrcConfig.class.getDeclaredField("pendingSaveTask"); taskField.setAccessible(true);
        Object oldTask = taskField.get(null);
        String oldSelection = IrcConfig.get().currentRouteFileName;
        var executor = Executors.newSingleThreadScheduledExecutor();
        var pending = executor.schedule(() -> {}, 1, TimeUnit.DAYS);
        try {
            taskField.set(null, pending);
            IrcConfig.get().currentRouteFileName = route.getFileName().toString();
            var restore = RouteManager.class.getDeclaredMethod("loadFile", Path.class, boolean.class); restore.setAccessible(true);
            if (!(boolean) restore.invoke(null, route, false)) throw new AssertionError("Existing route must restore");
            if (pending.isCancelled()) throw new AssertionError("Restoring a route must not invoke IrcConfig.save");
            if (!Files.readString(route).equals(json)) throw new AssertionError("Restoring JSON must not rewrite the route");
            if (RouteManager.entries().size() != 1 || RouteManager.isVisible() || !RouteManager.isShowLine()) throw new AssertionError("Read-only restore must recover coordinates and file flags");
            if (!(boolean) restore.invoke(null, route, false) || pending.isCancelled()) throw new AssertionError("Repeated restore must remain free of profile-save side effects");
            System.out.println("Route restore checks passed: saved route loads without profile save or file rewrite.");
        } finally {
            taskField.set(null, oldTask); IrcConfig.get().currentRouteFileName = oldSelection; executor.shutdownNow();
        }
    }
}
