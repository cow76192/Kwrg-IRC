package me.cookie.kwrg.gui.title;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import me.cookie.kwrg.mixin.accessor.TitleBrowserLanServerAccessor;
import me.cookie.kwrg.render.utils.LoadingOverlayRenderer;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class TitleBrowser {
    public interface Host {
        TitleBrowser kwrg$browser();
    }

    public interface Embedded {
        TitleBrowser kwrg$owner();

        void kwrg$owner(TitleBrowser browser);
    }

    public interface CompactList {
        boolean kwrg$isCompact();

        void kwrg$compact();
    }

    public interface Pingable {
        boolean kwrg$queuePing();
    }

    private static final long TRAVEL_MS = 600L;
    private static final long DIVE_MS = 500L;
    private static final long LAUNCH_MS = 400L;
    private static final int SIGNAL_SLOTS = 256;
    private static final int SOLO = 0x79EDAB;
    private static final int NETWORK = 0xA48FFF;

    private static final Component BOARD = Component.translatable("kwrg.title.board");
    private static final Component MATRIX = Component.translatable("kwrg.title.matrix");
    private static final Component EMPTY = Component.translatable("kwrg.title.empty");

    private static TitleBrowser drawing;

    private final TitleScreen title;
    private final int[] signals = new int[SIGNAL_SLOTS / 8];
    private Screen panel;
    private AbstractSelectionList<?> list;
    private boolean network;
    private boolean open;
    private volatile boolean suspended;
    private long travelStart;
    private float travelFrom;
    private long selectionStart;
    private String worldId;
    private int worldModel;
    private int beaconCount;
    private int selectedBeacon = -1;
    private int beaconPage;
    private int pingCursor;
    private String restoreWorld;
    private Runnable enterWorld;
    private long diveStart;
    private boolean diveShown;
    private ServerLaunch launch;
    private float panelAlpha = 1.0f;

    public TitleBrowser(TitleScreen title) {
        this.title = title;
    }

    public static TitleBrowser owner(Screen panel) {
        return ((Embedded) panel).kwrg$owner();
    }

    public static boolean ownsServerList(Screen screen) {
        return screen instanceof JoinMultiplayerScreen && owner(screen) instanceof TitleBrowser browser
                && browser.network && browser.panel == screen;
    }

    public static void returnFromPortal(Minecraft game, Screen previousList) {
        TitleBrowser previous = previousList == null ? null : owner(previousList);
        if (previous != null && previous.panel == previousList) {
            ServerPortalRenderer.returnTo(previous.title);
            previous.resume(game);
            return;
        }

        TitleScreen title = new TitleScreen();
        ServerPortalRenderer.returnTo(title);
        game.setScreen(title);

        TitleBrowser browser = ((Host) title).kwrg$browser();
        browser.open(new JoinMultiplayerScreen(title), true);
        browser.travelFrom = 1.0f;
    }

    // A vanilla dialog or a failed connection can return to its original list.
    public static boolean route(Minecraft game, Screen next) {
        if (next != null && owner(next) instanceof TitleBrowser browser && browser.panel == next) {
            browser.resume(game);
            return true;
        }

        if (game.screen instanceof Host host) {
            TitleBrowser browser = host.kwrg$browser();
            if (browser.open && next == browser.title) {
                browser.close();
                return true;
            }

            if (browser.open && browser.network && next != null && next.getClass() == JoinMultiplayerScreen.class) {
                browser.install(next);
                return true;
            }
        }
        return false;
    }

    public void open(Screen next, boolean multiplayer) {
        long now = Util.getMillis();
        travelFrom = travel(now);
        travelStart = now;
        open = true;
        network = multiplayer;
        if (multiplayer) ServerPortalRenderer.prepare();
        install(next);
    }

    private void install(Screen next) {
        release();
        panel = next;
        ((Embedded) next).kwrg$owner(this);
        next.added();
        next.init(title.width, title.height);
        list = next.children().stream().filter(AbstractSelectionList.class::isInstance)
                .map(widget -> (AbstractSelectionList<?>) widget).findFirst().orElse(null);
        suspended = false;
        resize(title.width, title.height);
        worldId = null;
        worldModel = 0;
        restoreWorld = null;
        beaconCount = 0;
        selectedBeacon = -1;
        beaconPage = 0;
        pingCursor = 0;
        enterWorld = null;
    }

    private void resume(Minecraft game) {
        if (game.screen == title && !suspended) return;
        String selectedWorld = list instanceof WorldSelectionList worlds
                && worlds.getSelected() instanceof WorldSelectionList.WorldListEntry row ? row.getLevelSummary().getLevelId() : null;
        String selectedServer = list instanceof ServerSelectionList servers
                && servers.getSelected() instanceof ServerSelectionList.OnlineServerEntry row ? row.getServerData().ip : null;
        String search = panel.children().stream().filter(EditBox.class::isInstance)
                .map(widget -> ((EditBox) widget).getValue()).findFirst().orElse("");
        Screen next = network ? new JoinMultiplayerScreen(title) : new SelectWorldScreen(title);

        game.setScreen(title);
        open = true;
        travelFrom = 1.0f;
        travelStart = Util.getMillis();
        install(next);

        if (list instanceof ServerSelectionList servers && selectedServer != null) {
            for (var row : servers.children()) {
                if (row instanceof ServerSelectionList.OnlineServerEntry entry && selectedServer.equals(entry.getServerData().ip)) {
                    servers.setSelected(row);
                    resize(title.width, title.height);
                    break;
                }
            }
        } else if (list instanceof WorldSelectionList) {
            restoreWorld = selectedWorld;
            for (var widget : next.children()) {
                if (widget instanceof EditBox box) box.setValue(search);
            }
        }
    }

    public void close() {
        long now = Util.getMillis();
        travelFrom = travel(now);
        travelStart = now;
        open = false;
        enterWorld = null;
        cancelLaunch();
        release();
        panel = null;
        list = null;
    }

    public void suspend() {
        release();
        enterWorld = null;
        cancelLaunch();
        suspended = true;
    }

    private void cancelLaunch() {
        if (launch == null) return;
        launch = null;
        ServerPortalRenderer.cancelLaunch(title);
    }

    public void returnedToTitle() {
        if (open && suspended) {
            close();
            travelFrom = 0.0f;
        }
    }

    private void release() {
        if (panel == null || suspended) return;
        suspended = true;
        panel.removed();

        if (list instanceof ServerSelectionList servers) {
            for (var row : servers.children()) {
                if (row instanceof ServerSelectionList.OnlineServerEntry server) server.close();
            }
        }
    }

    public boolean handlesInput() {
        return open || travel(Util.getMillis()) > 0.01f;
    }

    public Screen inputPanel() {
        return open && !suspended && enterWorld == null && launch == null && !ServerPortalRenderer.returning(title)
                && travel(Util.getMillis()) > 0.53f ? panel : null;
    }

    public boolean isActive(Screen screen) {
        return open && !suspended && panel == screen && Minecraft.getInstance().screen == title;
    }

    public void tick() {
        if (open && !suspended) {
            panel.tick();
            panel.handleDelayedNarration();
            if (launch == null && list instanceof ServerSelectionList servers) queuePings(servers);
        }

        if (launch != null && Util.getMillis() - launch.start() >= LAUNCH_MS) {
            Runnable connect = launch.connect();
            launch = null;
            ServerPortalRenderer.handoff(panel);
            connect.run();
        }

        if (enterWorld != null && diveShown) {
            Runnable action = enterWorld;
            try {
                action.run();
            } finally {
                enterWorld = null;
            }
        }
    }

    private void queuePings(ServerSelectionList servers) {
        var rows = servers.children();
        int end = Math.min(rows.size(), beaconPage + SIGNAL_SLOTS);
        int started = 0;

        while (pingCursor < end && started < 2) {
            var row = rows.get(pingCursor++);
            if (row instanceof Pingable ping && ping.kwrg$queuePing()) started++;
        }
    }

    public boolean dive(WorldSelectionList.WorldListEntry entry, Runnable action) {
        if (!isActive(panel) || !(panel instanceof SelectWorldScreen) || !entry.getLevelSummary().primaryActionActive()) return false;

        if (enterWorld == null) {
            selectWorld(entry.getLevelSummary(), Util.getMillis());
            diveStart = Util.getMillis();
            diveShown = false;
            enterWorld = action;
        }
        return true;
    }

    public boolean launch(Screen source, ServerData server, Runnable connect) {
        if (!isActive(source) || !(list instanceof ServerSelectionList servers)) return false;

        if (launch != null) return true;

        var rows = servers.children();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);

            boolean target = row instanceof ServerSelectionList.OnlineServerEntry online && online.getServerData() == server
                    || row instanceof ServerSelectionList.NetworkServerEntry lan && server.type() == ServerData.Type.LAN
                    && ((TitleBrowserLanServerAccessor) lan).kwrg$lanServer().getAddress().equals(server.ip);

            if (!target) continue;
            if (!ServerPortalRenderer.prepare()) return false;
            servers.setSelected(row);
            updateSignals(servers, i);

            launch = new ServerLaunch(Util.getMillis(), selectedBeacon, connect);
            ServerPortalRenderer.launch(title, source);
            return true;
        }
        return false;
    }

    public float menuVisibility() {
        float camera = travel(Util.getMillis());
        return open ? 1.0f - ease(Math.min(1.0f, camera / 0.22f)) : 1.0f - camera;
    }

    private float travel(long now) {
        float step = Math.clamp((now - travelStart) / (float) TRAVEL_MS, 0.0f, 1.0f);
        return travelFrom + ((open ? 1.0f : 0.0f) - travelFrom) * ease(step);
    }

    public LoadingOverlayRenderer.BrowserFrame scene() {
        long now = Util.getMillis();
        float camera = travel(now);
        if (camera <= 0.0f) return LoadingOverlayRenderer.BrowserFrame.NONE;

        float dive = enterWorld == null ? 0.0f : Math.min(1.0f, (now - diveStart) / (float) DIVE_MS);
        if (dive >= 1.0f) diveShown = true;

        if (open && enterWorld == null && list instanceof WorldSelectionList worlds) {
            if (restoreWorld != null && worlds.getSelected() == null) {
                boolean hasRows = false;
                for (var row : worlds.children()) {
                    if (row instanceof WorldSelectionList.WorldListEntry entry) {
                        hasRows = true;
                        if (restoreWorld.equals(entry.getLevelSummary().getLevelId())) {
                            worlds.setSelected(row);
                            resize(title.width, title.height);
                            break;
                        }
                    }
                }
                if (hasRows) restoreWorld = null;
            }

            var row = worlds.getSelected();
            if (row != null) restoreWorld = null;
            selectWorld(row instanceof WorldSelectionList.WorldListEntry entry ? entry.getLevelSummary() : null, now);
        }

        if (open && launch == null && list instanceof ServerSelectionList servers) {
            updateSignals(servers, servers.children().indexOf(servers.getSelected()));
        }

        float launchProgress = launch == null ? 0.0f : launchProgress(now);
        if (launch != null) ServerPortalRenderer.launchFrame(title, launchProgress);
        float stage = Math.max(launchProgress, 1.0f - ServerPortalRenderer.returnReveal(title));
        float growth = Math.clamp((now - selectionStart) / 480.0f, 0.0f, 1.0f);
        return new LoadingOverlayRenderer.BrowserFrame(camera, network, worldModel, ease(growth), dive, beaconCount,
                selectedBeacon, signals, stage, launch == null ? -1 : launch.slot());
    }

    private void updateSignals(ServerSelectionList servers, int selectedIndex) {
        Arrays.fill(signals, 0);
        var rows = servers.children();
        int pageStart = Math.max(0, selectedIndex / SIGNAL_SLOTS * SIGNAL_SLOTS);
        if (pageStart != beaconPage) {
            beaconPage = pageStart;
            pingCursor = pageStart;
        }

        beaconCount = 0;
        selectedBeacon = -1;
        for (int i = pageStart; i < rows.size() && beaconCount < SIGNAL_SLOTS; i++) {
            var row = rows.get(i);
            int signal = row instanceof ServerSelectionList.OnlineServerEntry server ? signal(server.getServerData())
                    : row instanceof ServerSelectionList.NetworkServerEntry ? 5 : 0;
            if (signal == 0) continue;
            signals[beaconCount / 8] |= signal << (beaconCount % 8 * 4);
            if (i == selectedIndex) selectedBeacon = beaconCount;
            beaconCount++;
        }
    }

    private float launchProgress(long now) {
        return Math.clamp((now - launch.start()) / (float) LAUNCH_MS, 0.0f, 1.0f);
    }

    private void selectWorld(LevelSummary summary, long now) {
        String id = summary == null ? null : summary.getLevelId();
        if (Objects.equals(id, worldId)) return;
        worldId = id;
        worldModel = summary == null ? 0 : summary.isHardcore() ? 3 : summary.getGameMode() == GameType.CREATIVE ? 2 : 1;
        selectionStart = now;
    }

    public void resize(int width, int height) {
        if (panel == null || suspended) return;
        panel.resize(width, height);
        if (list == null) return;

        int left = left(width);
        int size = panelWidth(width);
        int header = header(height);
        int top = header + (network ? 37 : 58);
        int bottom = height - 15;

        List<AbstractButton> buttons = new ArrayList<>();
        for (var child : panel.children()) {
            if (child instanceof AbstractButton button) {
                buttons.add(button);
            } else if (child instanceof EditBox search) {
                search.setRectangle(size - 12, 17, left, header + 34);
            } else if (child instanceof StringWidget label) {
                label.visible = false;
            }
        }

        int primary = Math.min(buttons.size(), network && size >= 175 ? 3 : 2);
        int columns = size >= 215 ? 5 : size >= 150 ? 4 : size >= 103 ? 3 : 2;
        int rows = primary == 0 ? 0 : 1 + (buttons.size() - primary + columns - 1) / columns;
        int footer = Math.max(top + 32, bottom - rows * 19);

        ((CompactList) list).kwrg$compact();
        list.updateSizeAndPosition(size, Math.max(28, footer - top - 5), left, top);

        for (int i = 0; i < buttons.size(); i++) {
            int count = i < primary ? primary : columns;
            int col = i < primary ? i : (i - primary) % columns;
            int row = i < primary ? 0 : 1 + (i - primary) / columns;
            int x = left + size * col / count;
            int w = left + size * (col + 1) / count - x - 4;
            buttons.get(i).setRectangle(w, 16, x, footer + row * 19);
        }
    }

    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        long now = Util.getMillis();
        float camera = travel(now);

        if (enterWorld != null && now - diveStart > 240L) {
            float flash = Math.clamp((now - diveStart - 270L) / 220.0f, 0.0f, 1.0f);
            graphics.fill(0, 0, title.width, title.height, color(0xDBFAFF, Math.round(255 * ease(flash))));
            return;
        }

        if (!open || suspended || camera < 0.20f) return;
        float slide = launch == null ? 1.0f - ServerPortalRenderer.returnReveal(title)
                : ease(Math.min(1.0f, launchProgress(now) / 0.61f));
        if (slide >= 1.0f) return;
        panelAlpha = 1.0f - slide;

        int left = left(title.width);
        int top = header(title.height);
        int size = panelWidth(title.width);
        int accent = network ? NETWORK : SOLO;

        Font font = title.getFont();
        var pose = graphics.pose();
        pose.pushMatrix().translate(-(left + size + 6) * slide, 0.0f);

        if (list != null) {
            float reveal = Math.clamp((camera - 0.20f) / 0.40f, 0.0f, 1.0f);
            graphics.enableScissor(left - 1, top - 1, left + Math.round((size + 2) * reveal), title.height);
            graphics.fill(left, top, left + 24, top + 1, panelColor(color(accent, 210)));
            graphics.text(font, network ? MATRIX : BOARD, left, top + 5, panelColor(color(accent, 190)), false);

            label(graphics, font, panel.getTitle(), left, top + 16, size - 6, 1.55f, panelColor(0xFFEBF9FF));

            if (list instanceof WorldSelectionList && list.children().isEmpty()) {
                label(graphics, font, EMPTY, left + 5, list.getY() + 7, size - 16, 1.0f, 0xFF8DB4C8);
            }
        }

        TitleBrowser previous = drawing;
        drawing = this;

        try {
            panel.extractRenderState(graphics, inputPanel() == null ? -1 : mouseX, inputPanel() == null ? -1 : mouseY, tick);
        } finally {
            drawing = previous;
            if (list != null) graphics.disableScissor();
            pose.popMatrix();
            panelAlpha = 1.0f;
        }
    }

    public static boolean drawButton(AbstractButton button, GuiGraphicsExtractor graphics) {
        if (drawing == null) return false;

        int accent = drawing.network ? NETWORK : SOLO;
        boolean hot = button.isActive() && button.isHoveredOrFocused();

        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

        graphics.fillGradient(x, y, x + w, y + h, drawing.panelColor(hot ? color(accent, 86) : 0x59192B40),
                drawing.panelColor(0x49101B2D));
        graphics.fill(x, y + 3, x + 2, y + h - 3, drawing.panelColor(color(accent, hot ? 255 : button.isActive() ? 140 : 65)));

        label(graphics, drawing.title.getFont(), button.getMessage(), x + 5, y + (h - 8) / 2, w - 8, 1.0f,
                drawing.panelColor(button.isActive() ? 0xFFE3F6FF : 0xFF8392A6));

        if (hot && button.isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
        return true;
    }

    public static void drawRow(AbstractSelectionList<?> list, LayoutElement row,
                               GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (drawing == null || drawing.list != list) return;

        boolean selected = list.getSelected() == row;
        boolean hovered = mouseX >= row.getX() && mouseX < row.getX() + row.getWidth()
                && mouseY >= row.getY() && mouseY < row.getY() + row.getHeight();

        int accent = drawing.network ? NETWORK : SOLO;

        if (row instanceof WorldSelectionList.WorldListEntry world) {
            var summary = world.getLevelSummary();
            accent = summary.isHardcore() ? 0xF96A7C : summary.getGameMode() == GameType.CREATIVE ? 0xBD93FF : SOLO;
        } else if (row instanceof ServerSelectionList.OnlineServerEntry server) {
            accent = switch (signal(server.getServerData())) {
                case 1 -> SOLO;
                case 2 -> 0xF9C777;
                case 3 -> 0xFB785F;
                case 4 -> 0x768299;
                default -> 0x76C6EC;
            };
        }

        int x = row.getX();
        int y = row.getY();
        int w = row.getWidth();
        int h = row.getHeight();

        graphics.fillGradient(x - 4, y, x + w + 3, y + h - 2,
                drawing.panelColor(selected ? color(accent, 98) : hovered ? color(accent, 60) : 0x7120394C),
                drawing.panelColor(0x6610192A));
        graphics.fill(x - 4, y + 3, x - 2, y + h - 5, drawing.panelColor(color(accent, selected ? 255 : 170)));
    }

    private int panelColor(int argb) {
        return color(argb & 0xFFFFFF, Math.round((argb >>> 24) * panelAlpha));
    }

    private static int signal(ServerData server) {
        return switch (server.state()) {
            case SUCCESSFUL -> server.ping < 0 ? 4 : server.ping < 150 ? 1 : server.ping < 300 ? 2 : 3;
            case UNREACHABLE, INCOMPATIBLE -> 4;
            default -> 5;
        };
    }

    private static void label(GuiGraphicsExtractor graphics, Font font, Component value,
                              int x, int y, int width, float maxScale, int color) {
        float scale = Math.min(maxScale, (float) width / Math.max(1, font.width(value)));

        graphics.pose().pushMatrix().translate(x, y).scale(scale);
        graphics.text(font, value, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private static int color(int rgb, int alpha) {
        return rgb | alpha << 24;
    }

    static int left(int width) {
        return Math.max(8, Math.round(width * 0.10f));
    }

    private static int panelWidth(int width) {
        return Math.max(84, Math.round(width * 0.43f));
    }

    private static int header(int height) {
        return Math.max(6, Math.min(40, (int) (height * 0.11f)));
    }

    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private record ServerLaunch(long start, int slot, Runnable connect) {
    }
}
