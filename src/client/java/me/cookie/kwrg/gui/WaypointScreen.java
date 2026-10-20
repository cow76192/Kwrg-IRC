package me.cookie.kwrg.gui;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import net.minecraft.client.Minecraft;

public final class WaypointScreen extends PointCollectionScreen {
    WaypointScreen(IrcScreen host, PanelItemComponent source, float x, float y) {
        super(host, source, x, y, new WaypointCollectionStore());
    }
    public static void open() { openFrom(null); }
    public static void openFrom(PanelItemComponent source) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            IrcScreen host = IrcScreen.getCurrentScreen();
            if (host == null || mc.screen != host) {
                host = new IrcScreen();
                host.setDirectOpenTarget("functions", "waypoint");
                mc.setScreen(host);
            }
            float cx = host.width / 2f, cy = host.height / 2f;
            float x = source == null ? cx : source.x() + source.width() / 2f;
            float y = source == null ? cy : source.y() + source.height() / 2f;
            host.openWaypointOverlay(cx + (x - cx) * host.getAnimatedScale(), cy + (y - cy) * host.getAnimatedScale(), source);
        });
    }
}
