package me.cookie.kwrg.gui;

import java.nio.file.Path;

public final class PointCollectionActivationCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void run(Path first, Path second) {
        PointCollectionStore store = new WaypointCollectionStore();
        store.importFile(second);
        check(store.active(first) && store.active(second), "All enabled waypoint files must be highlighted, independently of the edited file");
        store.setEnabled(first, false);
        check(!store.active(first) && store.active(second), "Disabling one file must leave another file highlighted");
        store.importFile(first);
        check(!store.active(first) && store.active(second), "Right-click editing a disabled file must not mark it active");
        store.setEnabled(first, true);
        check(store.active(first) && store.active(second), "Enabling another file must preserve multiple active highlights");
    }
}
