package me.cookie.kwrg.gui;

import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.util.WaypointRouteUpdate;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface PointCollectionStore {
    record FileOptions(boolean visible, boolean showLine, boolean enabled) {}
    String noun();
    default boolean supportsLines() { return false; }
    Path getDir();
    List<Path> listFiles();
    Path currentFile();
    default String currentFileName() { return currentFile() == null ? "" : currentFile().getFileName().toString(); }
    List<? extends PointEntry> entries();
    boolean importFile(Path path);
    boolean saveCurrent();
    default void discardCurrentEdits() {}
    Path createRoute(String name) throws Exception;
    Path renameRoute(Path path, String name) throws Exception;
    boolean deleteFile(Path path);
    FileOptions fileOptions(Path path);
    boolean setFileOptions(Path path, boolean visible, boolean showLine);
    void addEmpty(int position);
    boolean deleteAt(int index);
    default void updateName(int index, String name) { entries().get(index).name = name; }
    default void updateCoordinate(int index, int axis, String value) {
        PointEntry entry = entries().get(index);
        switch (axis) { case 0 -> entry.x = value; case 1 -> entry.y = value; case 2 -> entry.z = value; default -> throw new IllegalArgumentException("axis"); }
    }
    default void setEntryVisible(int index, boolean visible) { entries().get(index).visible = visible; saveCurrent(); }
    default boolean externalFile(Path path) { return false; }
    default boolean protectedFile(Path path) { return false; }
    default boolean enabled(Path path) { return fileOptions(path).enabled(); }
    default boolean active(Path path) { return supportsLines() ? path.equals(currentFile()) : enabled(path); }
    default boolean setEnabled(Path path, boolean enabled) { return false; }
    default Set<String> regions(Path path) { return Set.of("all"); }
    default boolean setRegions(Path path, Set<String> regions) { return false; }
    default void importExternal(Path path) throws Exception {}
    default String externalActionLabel(Path path) { return "导入"; }
    default WaypointRouteUpdate prepareExternalUpdate(Path path) throws Exception { return null; }
}
