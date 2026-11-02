package me.cookie.kwrg.gui;

import me.cookie.kwrg.gui.component.route.RoutePointComponent;
import me.cookie.kwrg.util.PointEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class PointListModel {

    private PointListModel() {
    }

    static List<? extends PointEntry> orderedEntries(List<? extends PointEntry> entries, List<String> order, boolean persistsPointOrder) {
        var ordered = new ArrayList<>(entries);
        if (!persistsPointOrder) {
            if (order != null) {
                Map<String, Integer> positions = new HashMap<>();
                for (int i = 0; i < order.size(); i++) positions.put(order.get(i), i);
                // Stable sorting appends new nodes while preserving the surviving nodes' positions.
                ordered.sort(Comparator.comparingInt(entry -> positions.getOrDefault(entry.id, Integer.MAX_VALUE)));
            }
        }
        return ordered;
    }

    static List<String> displayOrderIds(List<RoutePointComponent> points) {
        return points.stream().map(point -> point.entry().id).toList();
    }

    static boolean sameEntries(List<? extends PointEntry> entries, List<RoutePointComponent> points, boolean persistsPointOrder) {
        if (!persistsPointOrder) {
            var identities = java.util.Collections.newSetFromMap(new IdentityHashMap<PointEntry, Boolean>());
            identities.addAll(entries);
            return points.stream().allMatch(point -> identities.contains(point.entry()));
        }

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) != points.get(i).entry()) {
                return false;
            }
        }

        return true;
    }
}
