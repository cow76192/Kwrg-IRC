package me.cookie.kwrg.gui;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import net.minecraft.client.Minecraft;

public final class RouteScreen extends PointCollectionScreen {
    RouteScreen(IrcScreen host, PanelItemComponent source, float x, float y) {
        super(host, source, x, y, new RouteCollectionStore());
    }
    public static void open() { openAt(Float.NaN, Float.NaN); }

    public static void openAt(float virtualX, float virtualY) {
        openAt(virtualX, virtualY, null);
    }

    public static void openFrom(PanelItemComponent component) {
        openAt(component.x() + component.width() / 2.0f, component.y() + component.height() / 2.0f, component);
    }

    private static void openAt(float virtualX, float virtualY, PanelItemComponent component) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            IrcScreen host = IrcScreen.getCurrentScreen();
            if (host == null || mc.screen != host) {
                host = new IrcScreen();
                host.setDirectOpenTarget("functions", "route");
                mc.setScreen(host);
            }
            float cx = host.width / 2.0f, cy = host.height / 2.0f;
            var panel = host.getActiveTertiaryPanel();
            float x = Float.isNaN(virtualX) ? (panel == null ? cx : panel.x() + panel.width() / 2.0f) : virtualX;
            float y = Float.isNaN(virtualY) ? (panel == null ? cy : panel.y() + panel.height() / 2.0f) : virtualY;
            host.openRouteOverlay(cx + (x - cx) * host.getAnimatedScale(), cy + (y - cy) * host.getAnimatedScale(), component);
        });
    }

}
