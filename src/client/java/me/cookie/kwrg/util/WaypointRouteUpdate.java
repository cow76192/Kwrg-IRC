package me.cookie.kwrg.util;

import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Computes a non-destructive route merge; writes only after every ambiguous change has a decision. */
public final class WaypointRouteUpdate {
    public enum Kind { DELETED, CONFLICT }
    public record Issue(Kind kind, PointEntry local, PointEntry incoming) {}
    public record SourceNode(String sourceId, PointEntry source, PointEntry local) {}
    public record Summary(int added, int deleted, int modified) {
        public String describe() { return "新增 " + added + " / 删除 " + deleted + " / 修改 " + modified + " 个点位"; }
    }
    private record Node(PointEntry source, PointEntry local, PointEntry binding, boolean add, boolean rename, boolean visibility, int issue) {}
    private static final Gson GSON = new Gson();
    private final Path sourcePath, targetPath;
    private final WaypointFiles.Collection target;
    private final String sourceText, targetState, diskState;
    private final List<Issue> issues = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();
    private boolean applied;

    WaypointRouteUpdate(Path sourcePath, Path targetPath, WaypointFiles.Collection target) throws IOException {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.target = target;

        if (Files.size(sourcePath) > 2 * 1024 * 1024) {
            throw new IOException("路线文件过大");
        }
        sourceText = Files.readString(sourcePath);
        diskState = Files.readString(targetPath);
        targetState = GSON.toJson(target);
        WaypointFiles.Collection incoming = WaypointFiles.parse(sourcePath, sourceText);
        List<SourceNode> baseline = target.sourceRoute.isEmpty() ? target.points.stream().map(point -> snapshot(point, point)).toList() : target.sourceNodes;
        Set<SourceNode> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PointEntry> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PointEntry source : incoming.points) {
            SourceNode before = match(source, baseline, matched);
            PointEntry local = null;
            if (before != null) {
                matched.add(before);
                local = target.points.stream().filter(point -> point.id.equals(before.local.id)).findFirst().orElse(null);
            } else {
                local = target.points.stream().filter(point -> !used.contains(point) && point.name.equals(source.name) && coordinates(point, source)).findFirst().orElse(null);
                if (local == null) {
                    local = target.points.stream().filter(point -> !used.contains(point) && point.name.equals(source.name)).findFirst().orElse(null);
                }
            }
            if (local != null) {
                used.add(local);
            }
            int issue = -1;
            boolean alreadyAccepted = before != null && same(source, before.source) && local != null && same(local, before.local);
            boolean competingRename = before != null && local != null
                    && !source.name.equals(before.source.name) && !local.name.equals(before.local.name)
                    && !local.name.equals(source.name);
            boolean competingVisibility = local != null && source.visible != local.visible
                    && (before == null || source.visible != before.source.visible && local.visible != before.local.visible);
            if (local != null && (!coordinates(local, source) || competingRename || competingVisibility) && !alreadyAccepted) {
                issue = issues.size();
                issues.add(new Issue(Kind.CONFLICT, copy(local), copy(source)));
            }
            boolean rename = issue < 0 && !alreadyAccepted && before != null && local != null
                    && coordinates(local, source) && !source.name.equals(before.source.name);
            boolean visibility = issue < 0 && !alreadyAccepted && before != null && local != null
                    && source.visible != before.source.visible;
            nodes.add(new Node(source, local, before == null ? null : before.local, before == null && local == null, rename, visibility, issue));
        }
        for (SourceNode before : baseline) {
            if (!matched.contains(before)) {
                PointEntry local = target.points.stream().filter(point -> point.id.equals(before.local.id) && !used.contains(point)).findFirst().orElse(null);
                if (local != null) {
                    issues.add(new Issue(Kind.DELETED, copy(local), null));
                }
            }
        }
    }

    private static SourceNode match(PointEntry source, List<SourceNode> baseline, Set<SourceNode> used) {
        if (source.explicitId) {
            for (SourceNode node : baseline) {
                if (!used.contains(node) && source.id.equals(node.sourceId)) {
                    return node;
                }
            }
        }

        for (SourceNode node : baseline) {
            if (!used.contains(node) && source.name.equals(node.source.name) && coordinates(source, node.source)) {
                return node;
            }
        }

        for (SourceNode node : baseline) {
            if (!used.contains(node) && source.name.equals(node.source.name)) {
                return node;
            }
        }

        return null;
    }

    public Path targetPath() { return targetPath; }

    public List<Issue> issues() { return List.copyOf(issues); }

    public int additions() { return (int) nodes.stream().filter(Node::add).count(); }

    public boolean needsConfirmation() { return !issues.isEmpty(); }

    /** true follows the route (delete/replace); false keeps the local point. */
    public Summary apply(Map<Integer, Boolean> decisions) throws IOException {
        if (applied) {
            throw new IllegalStateException("此次更新已完成");
        }
        for (int i = 0; i < issues.size(); i++) {
            if (!decisions.containsKey(i)) {
                throw new IllegalArgumentException("请决定每个待处理节点");
            }
        }

        if (!sourceText.equals(Files.readString(sourcePath))
                || !diskState.equals(Files.readString(targetPath))
                || !targetState.equals(GSON.toJson(target))) {
            throw new IOException("文件已变化，请关闭窗口后重新更新");
        }

        WaypointFiles.Collection result = WaypointFiles.copy(target);
        Map<String, PointEntry> byId = new HashMap<>();
        result.points.forEach(point -> byId.put(point.id, point));
        int added = 0, deleted = 0, modified = 0;
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).kind == Kind.DELETED && decisions.get(i) && result.points.remove(byId.get(issues.get(i).local.id))) {
                deleted++;
            }
        }

        result.sourceNodes.clear();
        for (Node node : nodes) {
            PointEntry local = node.local == null ? null : byId.get(node.local.id);
            if (node.add) {
                local = copy(node.source);
                local.id = UUID.randomUUID().toString();
                result.points.add(local);
                added++;
            } else if (local != null) {
                PointEntry previous = copy(local);
                if (node.issue >= 0 && decisions.get(node.issue)) {
                    local.name = node.source.name;
                    local.x = node.source.x;
                    local.y = node.source.y;
                    local.z = node.source.z;
                    local.visible = node.source.visible;
                } else if (node.issue < 0) {
                    if (node.rename) {
                        local.name = node.source.name;
                    }
                    if (node.visibility) {
                        local.visible = node.source.visible;
                    }
                }
                if (!same(previous, local)) {
                    modified++;
                }
            }
            PointEntry binding = local != null ? local : node.binding;
            if (binding != null) {
                result.sourceNodes.add(snapshot(node.source, binding));
            }
        }
        result.sourceRoute = sourcePath.getFileName().toString();
        WaypointFiles.commitUpdate(targetPath, target, result);
        applied = true;
        return new Summary(added, deleted, modified);
    }

    static SourceNode snapshot(PointEntry source, PointEntry local) { return new SourceNode(source.explicitId ? source.id : "", copy(source), copy(local)); }

    static PointEntry copy(PointEntry point) {
        PointEntry copy = new PointEntry(point.name, point.x, point.y, point.z);
        copy.id = point.id;
        copy.visible = point.visible;
        copy.temporary = point.temporary;
        copy.explicitId = point.explicitId;
        return copy;
    }

    private static boolean same(PointEntry a, PointEntry b) { return a.name.equals(b.name) && coordinates(a, b) && a.visible == b.visible; }

    private static boolean coordinates(PointEntry a, PointEntry b) { return number(a.x, b.x) && number(a.y, b.y) && number(a.z, b.z); }

    private static boolean number(String a, String b) {
        try {
            return new BigDecimal(a.strip()).compareTo(new BigDecimal(b.strip())) == 0;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }
}
