package me.cookie.kwrg.gui;

import me.cookie.kwrg.irc.manager.RouteManager;
import me.cookie.kwrg.util.PointEntry;
import java.nio.file.Path;
import java.util.List;

final class RouteCollectionStore implements PointCollectionStore {
    public String noun() { return "路线"; }

    public boolean supportsLines() { return true; }

    public Path getDir() { return RouteManager.getDir(); }

    public List<Path> listFiles() { return RouteManager.listFiles(); }

    public Path currentFile() { return RouteManager.currentFile(); }

    public List<? extends PointEntry> entries() { return RouteManager.entries(); }

    public boolean importFile(Path path) { return RouteManager.importFile(path); }

    public boolean saveCurrent() { return RouteManager.saveCurrent(); }

    public void discardCurrentEdits() { RouteManager.discardCurrentEdits(); }

    public Path createRoute(String name) throws Exception { return RouteManager.createRoute(name); }

    public Path renameRoute(Path path, String name) throws Exception { return RouteManager.renameRoute(path, name); }

    public boolean deleteFile(Path path) { return RouteManager.deleteFile(path); }

    public FileOptions fileOptions(Path path) {
        var flags = RouteManager.fileOptions(path);
        return new FileOptions(flags.visible(), flags.showLine(), true);
    }

    public boolean setFileOptions(Path path, boolean visible, boolean line) { return RouteManager.setFileOptions(path, visible, line); }

    public void addEmpty(int position) { RouteManager.addEmpty(position); }

    public boolean deleteAt(int index) { return RouteManager.deleteAt(index); }
}
