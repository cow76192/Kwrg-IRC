package me.cookie.kwrg.gui;

import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.util.WaypointFiles;
import me.cookie.kwrg.util.WaypointRouteUpdate;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class WaypointCollectionStore implements PointCollectionStore {
    public String noun() { return "点位集"; }
    public Path getDir() { return WaypointFiles.getDir(); }
    public List<Path> listFiles() { return WaypointFiles.listFiles(); }
    public Path currentFile() { return WaypointFiles.currentFile(); }
    public List<PointEntry> entries() { return WaypointFiles.entries(); }
    public boolean importFile(Path path) { return WaypointFiles.select(path); }
    public boolean saveCurrent() { return WaypointFiles.saveCurrent(); }
    public Path createRoute(String name) throws Exception { return WaypointFiles.create(name); }
    public Path renameRoute(Path path, String name) throws Exception { return WaypointFiles.rename(path, name); }
    public boolean deleteFile(Path path) { return WaypointFiles.delete(path); }
    public FileOptions fileOptions(Path path) {
        var file = WaypointFiles.collection(path);
        return file == null ? new FileOptions(false, false, false) : new FileOptions(file.visible, false, file.enabled);
    }
    public boolean setFileOptions(Path path, boolean visible, boolean unused) {
        var file = WaypointFiles.collection(path);
        if (file == null) return false;
        file.visible = visible;
        return WaypointFiles.save(path);
    }
    public void addEmpty(int position) { WaypointFiles.addEmpty(); }
    public boolean deleteAt(int index) { return WaypointFiles.deleteAt(index); }
    public boolean externalFile(Path path) { return WaypointFiles.external(path); }
    public boolean protectedFile(Path path) { return WaypointFiles.isDefault(path); }
    public void setEnabled(Path path, boolean enabled) { var file = WaypointFiles.collection(path); if (file != null) { file.enabled = enabled; WaypointFiles.save(path); } }
    public Set<String> regions(Path path) { return Set.copyOf(WaypointFiles.collection(path).regions); }
    public void setRegions(Path path, Set<String> regions) { var file = WaypointFiles.collection(path); file.regions.clear(); file.regions.addAll(regions); WaypointFiles.save(path); }
    public void importExternal(Path path) throws Exception { WaypointFiles.importRoute(path); }
    public String externalActionLabel(Path path) { return WaypointFiles.importedFile(path) == null ? "导入" : "更新"; }
    public WaypointRouteUpdate prepareExternalUpdate(Path path) throws Exception { return WaypointFiles.prepareUpdate(path); }
}
