package me.cookie.kwrg.util;

import me.cookie.kwrg.irc.manager.WaypointManager;

import net.minecraft.core.BlockPos;

/** Editable coordinates shared by route and waypoint files. */
public class PointEntry {
    public String name, x, y, z;
    public boolean visible = true;
    public String id = java.util.UUID.randomUUID().toString();
    public transient boolean explicitId;

    public PointEntry(String name, String x, String y, String z) {
        this.name = name == null ? "" : name;
        this.x = x == null ? "" : x;
        this.y = y == null ? "" : y;
        this.z = z == null ? "" : z;
    }

    public boolean valid() {
        try {
            return WaypointManager.isValidCoord(Double.parseDouble(x.trim()), Double.parseDouble(y.trim()), Double.parseDouble(z.trim()));
        } catch (RuntimeException e) { return false; }
    }

    public BlockPos blockPos() {
        return valid() ? BlockPos.containing(Double.parseDouble(x.trim()), Double.parseDouble(y.trim()), Double.parseDouble(z.trim())) : null;
    }
}
