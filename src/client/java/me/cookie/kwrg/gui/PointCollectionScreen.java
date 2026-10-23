package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.PointListDrag;
import me.cookie.kwrg.gui.component.ScrollbarDrag;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.component.route.RoutePointComponent;
import me.cookie.kwrg.gui.component.route.RouteNodeAnimation;
import me.cookie.kwrg.gui.component.waypoint.RegionSelectionPane;
import me.cookie.kwrg.gui.component.waypoint.RouteUpdatePane;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A modal owned by IrcScreen; its existing menu stays mounted during both transitions. */
public abstract class PointCollectionScreen {
    private static final long ANIMATION_MS = 250;
    private static final int FILE_HEIGHT = 44;

    private final PointCollectionStore store;
    private final float originX, originY;
    private final IrcScreen host;
    private final PanelItemComponent originComponent;

    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1;
    private float closingFrom;
    private float pageFrom, pageTarget;
    private long pageStartedAt, refreshedAt, editedAt;
    private boolean dirty;
    private boolean saveBlocked;
    private Runnable pendingNavigation;
    private long saveFailureAt;

    private final RouteButtonComponent retrySaveButton = new RouteButtonComponent();
    private final RouteButtonComponent discardSaveButton = new RouteButtonComponent();
    private final RouteButtonComponent continueEditingButton = new RouteButtonComponent();

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int fileScroll, pointScroll;
    private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();
    private boolean draggingPointScrollbar;

    private final Map<Path, FileRow> files = new LinkedHashMap<>();
    private final List<RoutePointComponent> points = new ArrayList<>();
    private final PointListDrag<RoutePointComponent> pointDrag = new PointListDrag<>();

    private final Map<RoutePointComponent, PointMotion> pointMotions = new IdentityHashMap<>();
    private final Map<Path, List<String>> displayOrders = new HashMap<>();

    private Path pointsFile;
    private List<? extends PointEntry> dragEntries = List.of();

    private long dragFrameAt;
    private float dragScroll;

    private final RouteButtonComponent closeButton = new RouteButtonComponent();
    private final RouteButtonComponent directoryButton = new RouteButtonComponent();
    private final RouteButtonComponent backButton = new RouteButtonComponent();
    private final RouteButtonComponent addButton = new RouteButtonComponent();
    private final RouteButtonComponent newRouteButton = new RouteButtonComponent();
    private final RouteButtonComponent saveRouteButton = new RouteButtonComponent();
    private final RouteButtonComponent cancelRouteButton = new RouteButtonComponent();

    private EditBox routeNameInput;
    private EditBox draggedInput;

    private RegionSelectionPane regionPane;
    private RouteUpdatePane routeUpdatePane;

    private Path renamingRoute;
    private String routeNameError = "";

    private RouteButtonComponent namingSource;
    private RouteButtonComponent.Bounds namingOrigin;

    private Color namingOriginColor;
    private long namingAnimStarted;
    private float namingClosingFrom;
    private boolean namingClosing;
    private Path routeToReveal;

    private final RouteButtonComponent confirmDeleteButton = new RouteButtonComponent();
    private final RouteButtonComponent cancelDeleteButton = new RouteButtonComponent();

    private PointEntry pendingDelete;
    private Path pendingDeleteFile;
    private RoutePointComponent deletingPoint;
    private FileRow deletingFile;

    private RoutePointComponent deleteSourcePoint;
    private RouteButtonComponent.Bounds deleteOrigin;

    private Color deleteOriginColor;
    private long deleteAnimStarted;
    private boolean deleteClosing, deleteConfirmed;
    private float deleteClosingFrom;

    protected PointCollectionScreen(IrcScreen host, PanelItemComponent originComponent, float originX, float originY, PointCollectionStore store) {
        this.store = store;
        this.host = host;
        this.originComponent = originComponent;
        this.originX = originX;
        this.originY = originY;
        refreshFiles();
    }

    public float progress() {
        long now = System.currentTimeMillis();
        return closingAt < 0 ? AnimationUtil.easeOutCubic((now - openedAt) / (float) ANIMATION_MS)
                : closingFrom * (1 - AnimationUtil.easeOutCubic((now - closingAt) / (float) ANIMATION_MS));
    }

    public boolean isFinished() { return closingAt >= 0 && System.currentTimeMillis() - closingAt >= ANIMATION_MS; }

    private float pageProgress() {
        return AnimationUtil.lerp(AnimationUtil.easeOutCubic((System.currentTimeMillis() - pageStartedAt) / (float) ANIMATION_MS), pageFrom, pageTarget);
    }

    private boolean interactive() {
        return deletingPoint == null && deletingFile == null && closingAt < 0 && System.currentTimeMillis() - openedAt >= ANIMATION_MS
                && System.currentTimeMillis() - pageStartedAt >= ANIMATION_MS;
    }

    public void back() {
        cancelPointDrag();

        scrollbarDrag.cancel();
        if (closingAt >= 0) {
            return;
        }

        if (!flush()) {
            offerSaveRecovery(this::back);
            return;
        }

        blurPoints();
        if (pageTarget > 0) {
            pageFrom = pageProgress();
            pageTarget = 0;
            pageStartedAt = System.currentTimeMillis();
            refreshFiles();
        } else close();
    }

    private void close() {
        cancelPointDrag();

        scrollbarDrag.cancel();
        if (!flush()) {
            offerSaveRecovery(this::close);
            return;
        }

        regionPane = null;
        routeUpdatePane = null;
        pendingDelete = null;
        routeNameInput = null;
        blurPoints();
        closingFrom = progress();
        closingAt = System.currentTimeMillis();
    }

    public boolean flush() {
        if (dirty && store.saveCurrent()) {
            dirty = false;
        }

        saveBlocked = dirty;
        return !dirty;
    }

    private void markDirty() {
        dirty = true;
        saveBlocked = false;
        editedAt = System.currentTimeMillis();
    }

    private void offerSaveRecovery(Runnable navigation) {
        if (pendingNavigation == null) {
            pendingNavigation = navigation;
            saveFailureAt = System.currentTimeMillis();
        }
    }

    private void resolveSaveFailure(boolean discard) {
        if (pendingNavigation == null) {
            return;
        }

        if (discard) {
            store.discardCurrentEdits();
            dirty = saveBlocked = false;
            rebuildPoints();
            refreshFiles();
        } else if (!flush()) {
            return;
        }

        Runnable navigation = pendingNavigation;
        pendingNavigation = null;
        navigation.run();
    }

    private void refreshFiles() {
        Map<Path, FileRow> next = new LinkedHashMap<>();

        for (Path path : store.listFiles()) {
            FileRow row = files.get(path);
            if (row == null) {
                row = new FileRow(path);
            }

            row.options = store.fileOptions(path);
            if (store.externalFile(path)) {
                row.externalAction = store.externalActionLabel(path);
            }
            next.put(path, row);
        }

        files.clear();
        files.putAll(next);
        refreshedAt = System.currentTimeMillis();
    }

    private void rebuildPoints() {
        cancelPointDrag();
        blurPoints();
        Map<PointEntry, RoutePointComponent> existing = new IdentityHashMap<>();
        for (RoutePointComponent point : points) {
            existing.put(point.entry(), point);
        }

        points.clear();

        if (!java.util.Objects.equals(pointsFile, store.currentFile())) {
            pointMotions.clear();
        }

        pointsFile = store.currentFile();
        var entries = orderedEntries();
        for (int i = 0; i < entries.size(); i++) {
            RoutePointComponent point = existing.get(entries.get(i));
            if (point == null) {
                point = new RoutePointComponent(i, entries.get(i), store, this::markDirty);
            }
            point.setIndex(i);
            points.add(point);
        }

        pointMotions.keySet().retainAll(points);
        rememberDisplayOrder();
    }

    private List<? extends PointEntry> orderedEntries() {
        var entries = new ArrayList<>(store.entries());
        if (!store.persistsPointOrder()) {
            List<String> order = displayOrders.get(store.currentFile());
            if (order != null) {
                Map<String, Integer> positions = new HashMap<>();
                for (int i = 0; i < order.size(); i++) positions.put(order.get(i), i);
                // Stable sorting appends new nodes while preserving the surviving nodes' positions.
                entries.sort(Comparator.comparingInt(entry -> positions.getOrDefault(entry.id, Integer.MAX_VALUE)));
            }
        }
        return entries;
    }

    private void rememberDisplayOrder() {
        if (!store.persistsPointOrder() && store.currentFile() != null) {
            displayOrders.put(store.currentFile(), points.stream().map(point -> point.entry().id).toList());
        }
    }

    private String label(String text) { return text.replace("路线", store.noun()); }

    private boolean samePoints() {
        if (!java.util.Objects.equals(pointsFile, store.currentFile())) return false;

        var entries = store.entries();
        if (entries.size() != points.size()) {
            return false;
        }

        if (!store.persistsPointOrder()) {
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

    private void blurPoints() {
        draggedInput = null;
        for (var point : points) point.blur();
    }

    private void layout(int width, int height) {
        panelW = Math.min(560, width - 40);
        panelH = Math.min(440, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        contentX = panelX + 12;
        contentY = panelY + 70;
        contentW = panelW - 24;
        contentH = Math.max(24, panelH - 110);

        fileScroll = Mth.clamp(fileScroll, 0, Math.max(0, files.size() * FILE_HEIGHT - contentH));
        pointScroll = Mth.clamp(pointScroll, 0, Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH));

        closeButton.bounds(panelX + panelW - 32, panelY + 14, 20, 22);
        directoryButton.bounds(panelX + panelW - 120, panelY + 14, 80, 22);
        newRouteButton.bounds(panelX + panelW - 205, panelY + 14, 78, 22);
        addButton.bounds(panelX + panelW - 120, panelY + 14, 80, 22);
        backButton.bounds(panelX + 12, panelY + 14, 48, 22);

        confirmDeleteButton.bounds(panelX + panelW / 2 - 96, panelY + panelH / 2 + 20, 88, 24);
        cancelDeleteButton.bounds(panelX + panelW / 2 + 8, panelY + panelH / 2 + 20, 88, 24);

        saveRouteButton.bounds(panelX + panelW / 2 - 96, panelY + panelH / 2 + 42, 88, 24);
        cancelRouteButton.bounds(panelX + panelW / 2 + 8, panelY + panelH / 2 + 42, 88, 24);

        if (routeNameInput != null) {
            int dialogW = Math.min(340, panelW - 32);
            routeNameInput.setX(panelX + (panelW - dialogW) / 2 + 16);
            routeNameInput.setY(panelY + panelH / 2 - 28);
            routeNameInput.setWidth(dialogW - 32);
        }

        long now = System.currentTimeMillis();
        float offset = 0;
        for (FileRow row : files.values()) {
            row.layout(contentX, contentY + Math.round(offset) - fileScroll, contentW);
            offset += FILE_HEIGHT * row.appearance.remaining(now);
        }

        offset = 0;
        for (RoutePointComponent point : points) {
            PointMotion motion = pointMotions.computeIfAbsent(point, ignored -> new PointMotion());
            point.layout(contentX + Math.round(motion.horizontalOffset(now)), contentY + Math.round(motion.position(offset, now)) - pointScroll, contentW);
            offset += RoutePointComponent.HEIGHT * point.remaining(now);
        }
    }

    /** Slot motion is independent of scrolling, so wheel/edge scrolling stays attached to the pointer. */
    private static final class PointMotion {
        private float from, target = Float.NaN;
        private float releasedX;
        private long startedAt, releasedAt;

        float position(float next, long now) {
            if (Float.isNaN(target)) from = target = next;
            float current = AnimationUtil.lerp(AnimationUtil.easeOutCubic((now - startedAt) / 180f), from, target);
            if (next != target) {
                from = current;
                target = next;
                startedAt = now;
            }
            return current;
        }

        float horizontalOffset(long now) {
            return releasedX * (1 - AnimationUtil.easeOutCubic((now - releasedAt) / 180f));
        }

        void snap(float x, float position, long now) {
            from = target = position;
            startedAt = now;
            releasedX = x;
            releasedAt = now;
        }
    }

    private boolean dragEntriesUnchanged() {
        var entries = store.entries();
        return java.util.Objects.equals(pointsFile, store.currentFile()) && entries.equals(dragEntries);
    }

    private void updatePointDrag(double mouseX, double mouseY, long now) {
        if (pointDrag.item() == null) return;
        if (!interactive() || pageTarget == 0 || pendingDelete != null || pendingNavigation != null
                || routeNameInput != null || regionPane != null || routeUpdatePane != null || !dragEntriesUnchanged()) {
            cancelPointDrag();
            return;
        }

        if (pointDrag.update(mouseX, mouseY, now)) {
            blurPoints();
            dragScroll = pointScroll;
            dragFrameAt = now;
        }

        if (!pointDrag.holding()) return;

        float elapsed = Math.min(50, now - dragFrameAt) / 1000f;
        dragFrameAt = now;
        // Scroll at either edge, including while holding the pointer still.
        if (pointDrag.moved() && mouseX >= contentX - 24 && mouseX <= contentX + contentW + 24) {
            float edge = Math.min(32, contentH / 3f);
            float velocity = mouseY < contentY + edge ? -Mth.clamp((float) (contentY + edge - mouseY) / edge, 0, 1)
                    : mouseY > contentY + contentH - edge ? Mth.clamp((float) (mouseY - contentY - contentH + edge) / edge, 0, 1) : 0;
            dragScroll = Mth.clamp(dragScroll + velocity * 240 * elapsed, 0, Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH));
            pointScroll = Math.round(dragScroll);
        }

        int from = points.indexOf(pointDrag.item());
        int to = pointDrag.insertionIndex(contentY, pointScroll, RoutePointComponent.HEIGHT, points.size());
        if (mouseX >= contentX - 24 && mouseX <= contentX + contentW + 24 && from != to) {
            points.add(to, points.remove(from));
            for (int i = 0; i < points.size(); i++) points.get(i).setIndex(i);
        }
    }

    private void cancelPointDrag() {
        if (pointDrag.holding()) {
            int index = points.indexOf(pointDrag.item());
            if (index >= 0) {
                pointMotions.computeIfAbsent(pointDrag.item(), ignored -> new PointMotion())
                        .snap(pointDrag.x() - contentX, pointDrag.y() - contentY + pointScroll, System.currentTimeMillis());
                points.add(Math.min(pointDrag.originalIndex(), points.size() - 1), points.remove(index));
                for (int i = 0; i < points.size(); i++) points.get(i).setIndex(i);
            }
        }

        pointDrag.clear();
        dragEntries = List.of();
    }

    private void dropPoint() {
        if (!pointDrag.holding()) {
            pointDrag.clear();
            return;
        }

        if (!dragEntriesUnchanged()) {
            cancelPointDrag();
            return;
        }

        RoutePointComponent held = pointDrag.item();
        int to = points.indexOf(held);
        pointMotions.computeIfAbsent(held, ignored -> new PointMotion())
                .snap(pointDrag.x() - contentX, pointDrag.y() - contentY + pointScroll, System.currentTimeMillis());
        int from = store.entries().indexOf(held.entry());
        pointDrag.clear();
        dragEntries = List.of();

        if (store.persistsPointOrder()) {
            if (store.moveEntry(from, to)) {
                markDirty();
                flush();
            }
        } else {
            rememberDisplayOrder();
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        long now = System.currentTimeMillis();
        finishRouteNaming(now);
        finishDeletion(now);
        if (regionPane != null && regionPane.finished()) {
            regionPane = null;
        }

        if (routeUpdatePane != null && routeUpdatePane.finished()) {
            routeUpdatePane = null;
        }

        if (dirty && !saveBlocked && pendingNavigation == null && now - editedAt > 350) {
            flush();
        }

        if (pageTarget == 0 && routeNameInput == null && regionPane == null && routeUpdatePane == null && deletingFile == null && now - refreshedAt > 1000) {
            refreshFiles();
        }

        if (pageTarget > 0 && pendingDelete == null && deletingPoint == null
                && (!pointDrag.holding() || !dragEntriesUnchanged()) && !samePoints() && !saveBlocked && flush()) {
            rebuildPoints();
        }

        layout(width, height);
        updatePointDrag(mouseX, mouseY, now);
        if (pointDrag.holding()) layout(width, height);

        float alpha = progress();
        float sourceX = originX, sourceY = originY, sourceW = 52, sourceH = 28;

        if (originComponent != null && originComponent.parent() != null) {
            float scale = host.getAnimatedScale();
            sourceX = width / 2.0f + (originComponent.x() + originComponent.width() / 2.0f - width / 2.0f) * scale;
            sourceY = height / 2.0f + (originComponent.y() + originComponent.height() / 2.0f - height / 2.0f) * scale;
            sourceW = originComponent.width() * scale;
            sourceH = originComponent.height() * scale;
        }

        float w = AnimationUtil.lerp(alpha, sourceW, panelW), h = AnimationUtil.lerp(alpha, sourceH, panelH);
        float cx = AnimationUtil.lerp(alpha, sourceX, width / 2.0f), cy = AnimationUtil.lerp(alpha, sourceY, height / 2.0f);

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx - w / 2, cy - h / 2);
        graphics.pose().scale(w / panelW, h / panelH);
        graphics.pose().translate(-panelX, -panelY);

        OwoUIGraphics owo = RenderUtil.withOpacity(OwoUIGraphics.of(graphics), alpha * GuiThemeService.opacity());
        RenderUtil.drawSubpixelRoundedPanel(owo, panelX, panelY, panelW, panelH, 14,
                Color.ofArgb(AnimationUtil.argb(GuiThemeService.panelBg(), 0xF5)), Color.ofArgb(AnimationUtil.argb(GuiThemeService.panelOutline(), 0x80)));

        if (alpha > 0.03f) {
            boolean editor = pageTarget > 0;
            closeButton.draw(owo, "×", false, mouseX, mouseY);
            if (editor) {
                backButton.draw(owo, "‹ 返回", false, mouseX, mouseY);
                addButton.draw(owo, "+ 点位", false, mouseX, mouseY);
            } else {
                if (routeNameInput == null || namingSource != newRouteButton) {
                    newRouteButton.draw(owo, label("+ 新建路线"), false, mouseX, mouseY);
                }

                directoryButton.draw(owo, "打开目录", false, mouseX, mouseY);
            }

            RenderUtil.drawSmoothText(owo, RenderUtil.fitText(editor ? store.currentFileName() : label("路线管理"), panelW - (editor ? 205 : 235), 0.85f), panelX + (editor ? 68 : 16), panelY + 17, AnimationUtil.opaqueArgb(GuiThemeService.textStrong()));
            RenderUtil.drawSmoothText(owo, editor ? "直接编辑名称 / X / Y / Z · 修改自动保存" : store.supportsLines() ? label("选择路线文件 · 独立设置显示与连线") : "多点位集同时启用 · 独立显示与区域 · 灰色路线仅可导入", panelX + 16, panelY + 45, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));
            RenderUtil.fillSubpixel(owo, panelX + 12, panelY + 61, panelX + panelW - 12, panelY + 62, AnimationUtil.argb(GuiThemeService.panelOutline(), 0x30));

            graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
            float page = pageProgress();
            graphics.pose().pushMatrix();
            graphics.pose().translate(-page * (contentW + 16), 0);

            if (page < 1) {
                renderFiles(owo, mouseX, mouseY);
            }

            graphics.pose().popMatrix();
            graphics.pose().pushMatrix();
            graphics.pose().translate((1 - page) * (contentW + 16), 0);

            if (page > 0) {
                renderPoints(graphics, owo, mouseX, mouseY, partialTick);
            }
            graphics.pose().popMatrix();
            graphics.disableScissor();

            RenderUtil.drawScrollThumb(owo, contentX + contentW - 2, contentY, contentH,
                    editor ? points.size() * RoutePointComponent.HEIGHT : files.size() * FILE_HEIGHT,
                    editor ? pointScroll : fileScroll, Color.ofArgb(AnimationUtil.argb(GuiThemeService.panelOutline(), 0xAA)));
            RenderUtil.drawSmoothText(owo, editor ? "长按序号 / 空白拖动排序 · 右键返回 · × 删除需确认" : store.supportsLines() ? "左键激活 · 右键编辑 · Shift + 左键删除 · 右键面板外关闭" : "左键切换激活 · 右键编辑 · Shift + 左键删除 · 可多选激活", panelX + 16, panelY + panelH - 25, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));

            if (pointDrag.holding()) renderHeldPoint(graphics, owo, mouseX, mouseY, partialTick, now);


            if (pendingDelete != null) {
                renderDeleteConfirmation(owo, mouseX, mouseY);
            }

            if (routeNameInput != null) {
                renderRouteNaming(graphics, owo, mouseX, mouseY, partialTick);
            }

            if (regionPane != null) {
                regionPane.render(owo, mouseX, mouseY, partialTick, panelX, panelY, panelW, panelH);
            }

            if (routeUpdatePane != null) {
                routeUpdatePane.render(owo, mouseX, mouseY, panelX, panelY, panelW, panelH);
            }

            if (pendingNavigation != null) {
                renderSaveRecovery(owo, mouseX, mouseY);
            }
        }

        graphics.pose().popMatrix();
    }

    private void renderSaveRecovery(OwoUIGraphics graphics, int mouseX, int mouseY) {
        int width = Math.min(430, panelW - 24), height = 142;
        int x = panelX + (panelW - width) / 2, y = panelY + (panelH - height) / 2;
        int buttonW = (width - 48) / 3;

        retrySaveButton.bounds(x + 12, y + 102, buttonW, 26);
        discardSaveButton.bounds(x + 24 + buttonW, y + 102, buttonW, 26);
        continueEditingButton.bounds(x + 36 + buttonW * 2, y + 102, buttonW, 26);

        float progress = AnimationUtil.easeOutCubic((System.currentTimeMillis() - saveFailureAt) / (float) ANIMATION_MS);
        var origin = (pageTarget > 0 ? backButton : closeButton).bounds();
        float currentX = AnimationUtil.lerp(progress, origin.x(), x), currentY = AnimationUtil.lerp(progress, origin.y(), y);
        float currentW = AnimationUtil.lerp(progress, origin.width(), width), currentH = AnimationUtil.lerp(progress, origin.height(), height);

        RenderUtil.drawSubpixelRoundedPanel(graphics, currentX, currentY, currentW, currentH, 8, Color.ofArgb(AnimationUtil.opaqueArgb(GuiThemeService.cardBg())), Color.ofArgb(AnimationUtil.argb(GuiThemeService.danger(), 0xAA)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / width, currentH / height);
        graphics.pose().translate(-x, -y);
        graphics = RenderUtil.withOpacity(graphics, progress);

        RenderUtil.drawSmoothText(graphics, "修改尚未保存", x + 16, y + 16, AnimationUtil.opaqueArgb(GuiThemeService.warning()));
        RenderUtil.drawSmoothText(graphics, "文件可能被外部修改，或当前无法写入。", x + 16, y + 42, AnimationUtil.opaqueArgb(GuiThemeService.textBody()));
        RenderUtil.drawSmoothText(graphics, "放弃本次编辑会重载文件，不会覆盖磁盘内容。", x + 16, y + 66, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));

        retrySaveButton.draw(graphics, "重试保存", false, mouseX, mouseY);
        discardSaveButton.draw(graphics, "放弃本次编辑", false, mouseX, mouseY);
        continueEditingButton.draw(graphics, "继续编辑", false, mouseX, mouseY);

        graphics.pose().popMatrix();
    }

    private void renderFiles(OwoUIGraphics owo, int mouseX, int mouseY) {
        if (files.isEmpty()) {
            RenderUtil.drawSmoothText(owo, label("暂无路线文件"), contentX + 12, contentY + 24, AnimationUtil.opaqueArgb(GuiThemeService.textBody()));
            RenderUtil.drawSmoothText(owo, label("点击 + 新建路线，或打开目录导入 .json / .txt 文件"), contentX + 12, contentY + 44, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));
        }

        for (FileRow row : files.values()) {
            if (row.y + FILE_HEIGHT > contentY && row.y < contentY + contentH) {
                row.draw(owo, mouseX, mouseY, routeUpdatePane != null ? routeUpdatePane.source() : regionPane != null ? regionPane.source() : routeNameInput == null ? null : namingSource);
            }
        }
    }

    private void renderPoints(GuiGraphicsExtractor graphics, OwoUIGraphics owo, int mouseX, int mouseY, float partialTick) {
        if (points.isEmpty()) {
            RenderUtil.drawSmoothText(owo, "暂无点位，点击右上角 + 点位开始添加", contentX + 12, contentY + 24, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));
        }

        for (RoutePointComponent point : points) {
            if (pointDrag.holding() && point == pointDrag.item()) continue;
            int y = point.y();
            if (y + RoutePointComponent.HEIGHT > contentY && y < contentY + contentH) {
                point.render(graphics, owo, mouseX, mouseY, partialTick);
            } else {
                point.releaseInputs();
            }
        }
    }

    private void renderHeldPoint(GuiGraphicsExtractor graphics, OwoUIGraphics owo, int mouseX, int mouseY, float partialTick, long now) {
        RoutePointComponent held = pointDrag.item();
        float lift = AnimationUtil.easeOutCubic(pointDrag.heldFor(now) / 130f);
        float x = pointDrag.x(), y = pointDrag.y();
        held.layout(Math.round(x), Math.round(y), contentW);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + contentW / 2f, y + RoutePointComponent.HEIGHT / 2f);
        graphics.pose().scale(1 + lift * 0.025f, 1 + lift * 0.025f);
        graphics.pose().translate(-x - contentW / 2f, -y - RoutePointComponent.HEIGHT / 2f);

        RenderUtil.drawSubpixelRoundedPanel(owo, x - 1, y + 3 + 3 * lift, contentW + 2, RoutePointComponent.HEIGHT - 2, 8,
                Color.ofArgb(0x70000000), Color.ofArgb(0x00000000));
        RenderUtil.drawSubpixelRoundedPanel(owo, x - 1, y + 1, contentW + 2, RoutePointComponent.HEIGHT - 2, 8,
                Color.ofArgb(AnimationUtil.opaqueArgb(GuiThemeService.cardBg())), Color.ofArgb(AnimationUtil.argb(GuiThemeService.accentSoft(), 0xCC)));
        held.render(graphics, owo, mouseX, mouseY, partialTick);

        graphics.pose().popMatrix();
    }

    private int pointAt(double x, double y) {
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    public void mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (pointDrag.holding()) {
            cancelPointDrag();
            return;
        }
        pointDrag.clear();
        if (click.button() == 0) {
            scrollbarDrag.cancel();
        }
        draggedInput = null;
        if (!interactive()) {
            return;
        }

        if (pendingNavigation != null) {
            if (click.button() == 1) {
                pendingNavigation = null;
                return;
            }

            if (click.button() != 0 || System.currentTimeMillis() - saveFailureAt < ANIMATION_MS) {
                return;
            }

            if (retrySaveButton.contains(click.x(), click.y())) {
                retrySaveButton.press();
                resolveSaveFailure(false);
            } else if (discardSaveButton.contains(click.x(), click.y())) {
                discardSaveButton.press();
                resolveSaveFailure(true);
            } else if (continueEditingButton.contains(click.x(), click.y())) {
                continueEditingButton.press();
                pendingNavigation = null;
            }

            return;
        }

        if (routeUpdatePane != null) {
            routeUpdatePane.mouseClicked(click);
            return;
        }

        if (regionPane != null) {
            regionPane.mouseClicked(click, doubled);
            return;
        }

        if (click.button() == 1 && pageTarget > 0) {
            if (deleteSourcePoint != null) {
                deleteSourcePoint.hideDeleteButton(false);
            }
            pendingDelete = null;
            back();
            return;
        }

        if (routeNameInput != null) {
            if (namingClosing) {
                return;
            }
            if (click.button() == 1) {
                closeRouteNaming();
                return;
            }

            if (namingProgress() < 0.999f) {
                return;
            }
            if (click.button() == 0 && cancelRouteButton.contains(click.x(), click.y())) {
                closeRouteNaming();
            } else if (click.button() == 0 && saveRouteButton.contains(click.x(), click.y())) {
                saveRouteName();
            } else if (click.button() == 0) {
                routeNameInput.setFocused(routeNameInput.isMouseOver(click.x(), click.y()));
                if (routeNameInput.mouseClicked(click, doubled)) {
                    draggedInput = routeNameInput;
                }
            }
            return;
        }

        if (pendingDelete != null) {
            if (deleteClosing || deleteProgress() < 0.999f) {
                return;
            }
            if (click.button() == 0 && confirmDeleteButton.contains(click.x(), click.y())) {
                closeDeleteConfirmation(true);
            } else if (click.button() == 0 && cancelDeleteButton.contains(click.x(), click.y())) {
                closeDeleteConfirmation(false);
            }
            return;
        }

        boolean editor = pageTarget > 0;
        int contentHeight = editor ? points.size() * RoutePointComponent.HEIGHT : files.size() * FILE_HEIGHT;
        if (scrollbarDrag.begin(click, contentX + contentW - 2, contentY, contentH,
                ScrollbarDrag.thumbHeight(contentH, contentHeight, 14), editor ? pointScroll : fileScroll, Math.max(0, contentHeight - contentH))) {
            draggingPointScrollbar = editor;
            blurPoints();
            applyScrollbarDrag();
            return;
        }

        if (!insidePanel(click.x(), click.y())) {
            if (click.button() == 1 || (pageTarget > 0 && click.button() == 0)) {
                close();
            }
            return;
        }

        if (click.button() == 0 && closeButton.contains(click.x(), click.y())) {
            closeButton.press();
            close();
            return;
        }

        if (pageTarget > 0) {
            if (click.button() == 0 && backButton.contains(click.x(), click.y())) {
                backButton.press();
                back();
                return;
            }
            if (click.button() == 0 && addButton.contains(click.x(), click.y())) {
                if (!flush()) {
                    return;
                }

                addButton.press();
                var previousEntries = java.util.Collections.newSetFromMap(new IdentityHashMap<PointEntry, Boolean>());
                previousEntries.addAll(store.entries());
                store.addEmpty(points.size() + 1);

                PointEntry added = store.entries().stream().filter(entry -> !entry.temporary && !previousEntries.contains(entry)).findFirst().orElse(null);
                if (added == null) {
                    return;
                }

                markDirty();
                rebuildPoints();

                int addedIndex = -1;
                for (int i = 0; i < points.size(); i++) {
                    if (points.get(i).entry() == added) { addedIndex = i; break; }
                }

                if (addedIndex < 0) return;
                pointScroll = Math.max(0, (addedIndex + 1) * RoutePointComponent.HEIGHT - contentH);
                layout(Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
                points.get(addedIndex).focus(0);
                return;
            }

            if (insideContent(click.x(), click.y())) {
                int index = pointAt(click.x(), click.y());
                for (int i = 0; i < points.size(); i++) {
                    if (i != index) {
                        points.get(i).blur();
                    }
                }

                if (index >= 0 && index < points.size() && points.get(index).mouseClicked(click, doubled)) {
                    if (!flush()) {
                        return;
                    }
                    blurPoints();

                    pendingDelete = points.get(index).entry();
                    pendingDeleteFile = store.currentFile();

                    deleteSourcePoint = points.get(index);
                    deleteOrigin = deleteSourcePoint.deleteButton().bounds();
                    deleteOriginColor = deleteSourcePoint.deleteButton().color();
                    deleteSourcePoint.hideDeleteButton(true);
                    deleteAnimStarted = System.currentTimeMillis();
                    deleteClosing = deleteConfirmed = false;
                } else if (click.button() == 0 && index >= 0 && index < points.size()) {
                    RoutePointComponent point = points.get(index);
                    draggedInput = point.clickedInput(click.x(), click.y());
                    if (point.canDrag(click.x(), click.y())) {
                        dragEntries = new ArrayList<>(store.entries());
                        pointDrag.press(point, index, click.x(), click.y(), point.x(), point.y(), System.currentTimeMillis());
                    }
                }
            } else {
                blurPoints();
            }
        } else {
            if (click.button() == 0 && newRouteButton.contains(click.x(), click.y())) {
                newRouteButton.press();
                startRouteNaming(null, newRouteButton);
                return;
            }

            if (click.button() == 0 && directoryButton.contains(click.x(), click.y())) {
                directoryButton.press();
                openDirectory();
                return;
            }

            FileRow row = fileAt(click.x(), click.y());
            if (row == null) {
                return;
            }

            if (row.appearance.progress(System.currentTimeMillis()) < 0.95f) {
                return;
            }

            if (!flush()) {
                offerSaveRecovery(() -> mouseClicked(click, doubled));
                return;
            }

            if (store.externalFile(row.path)) {
                if (click.button() == 0 && row.importButton.contains(click.x(), click.y())) {
                    row.importButton.press();
                    try {
                        if (row.externalAction.equals("更新")) {
                            var plan = store.prepareExternalUpdate(row.path);
                            if (plan.needsConfirmation()) {
                                routeUpdatePane = new RouteUpdatePane(row.importButton, plan, this::refreshFiles);
                            } else {
                                var summary = plan.apply(Map.of());
                                refreshFiles();
                                ChatNotifier.send("§a[Waypoint] 更新完成 | " + summary.describe(), NotificationType.SUCCESS);
                            }
                        } else {
                            store.importExternal(row.path);
                            refreshFiles();
                            ChatNotifier.send("§a[Waypoint] 已导入为独立点位集", NotificationType.SUCCESS);
                        }
                    } catch (Exception e) {
                        ChatNotifier.send("§c[Waypoint] " + row.externalAction + "失败 | " + e.getMessage(), NotificationType.ERR);
                    }
                }

                return;
            }

            if (click.button() == 1) {
                row.surface.press();
                if (store.importFile(row.path)) {
                    rebuildPoints();
                    pointScroll = 0;
                    pageFrom = pageProgress();
                    pageTarget = 1;
                    pageStartedAt = System.currentTimeMillis();
                }

            } else if (click.button() == 0) {
                if (!store.supportsLines() && row.region.contains(click.x(), click.y())) {
                    row.region.press();
                    regionPane = new RegionSelectionPane(row.region, store.regions(row.path), selected -> store.setRegions(row.path, selected));
                } else if (!store.protectedFile(row.path) && row.rename.contains(click.x(), click.y())) {
                    row.rename.press();
                    startRouteNaming(row.path, row.rename);
                } else if (row.visible.contains(click.x(), click.y()) || store.supportsLines() && row.line.contains(click.x(), click.y())) {
                    boolean toggleVisible = row.visible.contains(click.x(), click.y());
                    (toggleVisible ? row.visible : row.line).press();
                    var options = row.options;
                    if (store.setFileOptions(row.path, toggleVisible ? !options.visible() : options.visible(),
                            toggleVisible ? options.showLine() : !options.showLine())) {
                        refreshFiles();
                    } else if (row.path.equals(store.currentFile())) {
                        markDirty();
                    }

                } else if (click.hasShiftDown() && !store.protectedFile(row.path)) {
                    if (store.deleteFile(row.path)) {
                        displayOrders.remove(row.path);
                        deletingFile = row;
                        row.appearance.remove(System.currentTimeMillis());
                    }
                } else {
                    row.surface.press();
                    if (!store.supportsLines() && !store.setEnabled(row.path, !store.enabled(row.path))) {
                        ChatNotifier.send("§c[Waypoint] 启用状态保存失败，已恢复原状态 | " + row.path.getFileName(), NotificationType.ERR);
                        return;
                    }

                    if (store.importFile(row.path)) {
                        rebuildPoints();
                        refreshFiles();
                    }
                }
            }
        }
    }

    public void mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (click.button() == 0 && pointDrag.item() != null) {
            updatePointDrag(click.x(), click.y(), System.currentTimeMillis());
            if (pointDrag.item() != null) return;
        }

        if (scrollbarDrag.dragging()) {
            int contentHeight = draggingPointScrollbar ? points.size() * RoutePointComponent.HEIGHT : files.size() * FILE_HEIGHT;
            if (scrollbarDrag.drag(click, contentH, ScrollbarDrag.thumbHeight(contentH, contentHeight, 14), Math.max(0, contentHeight - contentH))) {
                applyScrollbarDrag();
            }
            return;
        }

        if (pendingNavigation != null) {
            return;
        }

        if (routeUpdatePane != null) {
            return;
        }

        if (regionPane != null) {
            regionPane.mouseDragged(click, deltaX, deltaY);
            return;
        }

        if (!interactive() || click.button() != 0 || draggedInput == null || pendingDelete != null || namingClosing && routeNameInput != null) {
            return;
        }
        // Keep forwarding to the pressed input even when the pointer leaves its bounds.
        draggedInput.mouseDragged(click, deltaX, deltaY);
    }

    public void mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && pointDrag.item() != null) {
            if (pointDrag.holding()) updatePointDrag(click.x(), click.y(), System.currentTimeMillis());
            dropPoint();
        }

        scrollbarDrag.release(click);
        if (routeUpdatePane != null) {
            return;
        }

        if (regionPane != null) {
            regionPane.mouseReleased(click);
            return;
        }

        if (click.button() != 0) {
            return;
        }

        if (draggedInput != null) {
            draggedInput.mouseReleased(click);
        }
        draggedInput = null;
    }

    private void applyScrollbarDrag() {
        if (draggingPointScrollbar) {
            pointScroll = Math.round(scrollbarDrag.scroll());
        } else {
            fileScroll = Math.round(scrollbarDrag.scroll());
        }
    }

    public void mouseScrolled(double x, double y, double amount) {
        if (pointDrag.holding()) {
            pointScroll = Mth.clamp(pointScroll - (int) (amount * 30), 0, Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH));
            dragScroll = pointScroll;
            updatePointDrag(x, y, System.currentTimeMillis());
            return;
        }

        pointDrag.clear();
        if (pendingNavigation != null) {
            return;
        }

        if (routeUpdatePane != null) {
            routeUpdatePane.mouseScrolled(x, y, amount);
            return;
        }

        if (regionPane != null) {
            regionPane.mouseScrolled(x, y, amount);
            return;
        }

        if (!interactive() || pendingDelete != null || routeNameInput != null || !insideContent(x, y)) {
            return;
        }

        draggedInput = null;
        if (pageTarget > 0) {
            blurPoints();
            flush();
            pointScroll = Mth.clamp(pointScroll - (int) (amount * 30), 0, Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH));
        } else {
            fileScroll = Mth.clamp(fileScroll - (int) (amount * 30), 0, Math.max(0, files.size() * FILE_HEIGHT - contentH));
        }
    }

    public void keyPressed(KeyEvent input) {
        if (pointDrag.holding()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) cancelPointDrag();
            return;
        }

        pointDrag.clear();
        if (pendingNavigation != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                pendingNavigation = null;
            } else if (input.key() == GLFW.GLFW_KEY_ENTER && System.currentTimeMillis() - saveFailureAt >= ANIMATION_MS) {
                resolveSaveFailure(false);
            }
            return;
        }

        if (routeUpdatePane != null) {
            routeUpdatePane.keyPressed(input);
            return;
        }

        if (regionPane != null) {
            regionPane.keyPressed(input);
            return;
        }

        if (deletingPoint != null || deletingFile != null) {
            return;
        }

        if (routeNameInput != null) {
            if (namingClosing) {
                return;
            }

            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeRouteNaming();
            } else if (namingProgress() < 0.999f) {
                return;
            } else if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                saveRouteName();
            } else {
                routeNameInput.keyPressed(input);
            }
            return;
        }
        if (pendingDelete != null) {
            if (deleteClosing) {
                return;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeDeleteConfirmation(false);
            } else if (deleteProgress() >= 0.999f && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
                closeDeleteConfirmation(true);
            }
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return;
        }

        if (!interactive() || pageTarget == 0) {
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            flush();
            blurPoints();
        } else if (input.key() == GLFW.GLFW_KEY_TAB && !points.isEmpty()) {
            int focused = -1;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).focusedField() >= 0) {
                    focused = i * 4 + points.get(i).focusedField();
                }
            }

            int total = points.size() * 4;
            int next = focused < 0 ? (input.hasShiftDown() ? total - 1 : 0)
                    : Math.floorMod(focused + (input.hasShiftDown() ? -1 : 1), total);
            blurPoints();
            points.get(next / 4).focus(next % 4);
            int top = next / 4 * RoutePointComponent.HEIGHT;
            pointScroll = Mth.clamp(pointScroll, Math.max(0, top + RoutePointComponent.HEIGHT - contentH), top);
        } else {
            for (var point : points) {
                if (point.keyPressed(input)) {
                    break;
                }
            }
        }
    }

    public void charTyped(CharacterEvent input) {
        if (pointDrag.holding()) return;
        pointDrag.clear();

        if (pendingNavigation != null) {
            return;
        }

        if (routeUpdatePane != null) {
            return;
        }

        if (regionPane != null) {
            regionPane.charTyped(input);
            return;
        }

        if (routeNameInput != null) {
            if (!namingClosing && namingProgress() >= 0.999f) {
                routeNameInput.charTyped(input);
            }
            return;
        }
        if (interactive() && pendingDelete == null && pageTarget > 0) {
            for (var point : points) {
                if (point.charTyped(input)) {
                    break;
                }
            }
        }
    }

    public boolean preeditUpdated(PreeditEvent input) {
        if (pointDrag.holding()) return true;

        if (pendingNavigation != null) {
            return true;
        }
        if (routeUpdatePane != null) {
            return true;
        }
        if (regionPane != null) {
            regionPane.preeditUpdated(input);
            return true;
        }

        if (routeNameInput != null) {
            if (!namingClosing && namingProgress() >= 0.999f) {
                routeNameInput.preeditUpdated(input);
            }
            return true;
        }
        if (interactive() && pendingDelete == null && pageTarget > 0) {
            for (var point : points) {
                if (point.preeditUpdated(input)) {
                    return true;
                }
            }
        }
        return true;
    }

    private void startRouteNaming(Path file, RouteButtonComponent source) {
        if (!flush()) {
            return;
        }
        namingSource = source;
        namingOrigin = source.bounds();
        namingOriginColor = source.color();

        namingAnimStarted = System.currentTimeMillis();
        namingClosing = false;

        routeToReveal = null;
        renamingRoute = file;

        routeNameError = "";
        routeNameInput = new EditBox(FontManagerHelper.getGuiInputFont(), 0, 0, 200, 18, Component.literal(label("路线名称")));
        routeNameInput.setBordered(false);
        routeNameInput.setTextShadow(false);
        routeNameInput.setMaxLength(69);
        routeNameInput.setHint(Component.literal(label("输入路线名称")));

        if (file != null) {
            routeNameInput.setValue(file.getFileName().toString().replaceFirst("(?i)\\.(json|txt)$", ""));
        }

        routeNameInput.setResponder(value -> routeNameError = "");
        routeNameInput.setFocused(true);
    }

    private void saveRouteName() {
        if (namingClosing) {
            return;
        }


        try {
            routeToReveal = renamingRoute == null ? store.createRoute(routeNameInput.getValue())
                    : store.renameRoute(renamingRoute, routeNameInput.getValue());
            if (renamingRoute != null && !renamingRoute.equals(routeToReveal)) {
                List<String> order = displayOrders.remove(renamingRoute);
                if (order != null) displayOrders.put(routeToReveal, order);
            }

            closeRouteNaming();
        } catch (IllegalArgumentException e) {
            routeNameError = e.getMessage();
        } catch (Exception e) {
            routeNameError = "保存失败，请检查文件权限或名称是否重复";
        }
    }

    private float namingProgress() {
        float elapsed = (System.currentTimeMillis() - namingAnimStarted) / (float) ANIMATION_MS;
        return namingClosing ? namingClosingFrom * (1 - AnimationUtil.easeOutCubic(elapsed)) : AnimationUtil.easeOutCubic(elapsed);
    }

    private void closeRouteNaming() {
        if (namingClosing) {
            return;
        }

        draggedInput = null;
        namingClosingFrom = namingProgress();
        namingClosing = true;
        namingAnimStarted = System.currentTimeMillis();
        routeNameInput.setFocused(false);
    }

    private void finishRouteNaming(long now) {
        if (routeNameInput == null || !namingClosing || now - namingAnimStarted < ANIMATION_MS) {
            return;
        }

        routeNameInput = null;
        namingSource = null;

        if (routeToReveal != null) {
            refreshFiles();
            int index = new ArrayList<>(files.keySet()).indexOf(routeToReveal);
            if (index >= 0) {
                fileScroll = Math.max(0, (index + 1) * FILE_HEIGHT - contentH);
            }
            routeToReveal = null;
        }
    }

    private void renderRouteNaming(GuiGraphicsExtractor graphics, OwoUIGraphics owo, int mouseX, int mouseY, float partialTick) {
        int dialogW = Math.min(340, panelW - 32);
        int x = panelX + (panelW - dialogW) / 2, y = panelY + panelH / 2 - 76;

        float progress = namingProgress();

        float currentX = AnimationUtil.lerp(progress, namingOrigin.x(), x);
        float currentY = AnimationUtil.lerp(progress, namingOrigin.y(), y);
        float currentW = AnimationUtil.lerp(progress, namingOrigin.width(), dialogW);
        float currentH = AnimationUtil.lerp(progress, namingOrigin.height(), 152);
        RenderUtil.drawSubpixelRoundedPanel(owo, currentX, currentY, currentW, currentH,
                AnimationUtil.lerp(progress, 5, 10),
                AnimationUtil.lerpColor(progress, namingOriginColor, Color.ofArgb(AnimationUtil.opaqueArgb(GuiThemeService.cardBg()))),
                Color.ofArgb(AnimationUtil.argb(GuiThemeService.regionFill(), 0x88)));

        float labelAlpha = 1 - Mth.clamp(progress * 4, 0, 1);

        if (labelAlpha > 0.005f) {
            RenderUtil.drawCenteredSmoothText(RenderUtil.withOpacity(owo, labelAlpha),
                    Component.literal(renamingRoute == null ? label("+ 新建路线") : "改名"),
                    currentX + currentW / 2, currentY + currentH / 2, 0.80f, AnimationUtil.opaqueArgb(GuiThemeService.textBody()));
        }

        float contentAlpha = Mth.clamp((progress - 0.2f) / 0.8f, 0, 1);
        if (contentAlpha <= 0.005f) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / dialogW, currentH / 152);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + dialogW, y + 152);

        OwoUIGraphics content = RenderUtil.withOpacity(owo, contentAlpha);

        RenderUtil.drawSmoothText(content, renamingRoute == null ? label("新建路线") : label("修改路线名称"), x + 16, y + 16, AnimationUtil.opaqueArgb(GuiThemeService.textStrong()));
        routeNameInput.extractRenderState(content, mouseX, mouseY, partialTick);
        RenderUtil.fillSubpixel(content, x + 16, y + 67, x + dialogW - 16, y + 68, AnimationUtil.opaqueArgb(GuiThemeService.accentSoft()));

        String hint = routeNameError.isEmpty() ? label("名称将用作路线文件名，同名文件不会被覆盖") : routeNameError;
        RenderUtil.drawSmoothText(content, RenderUtil.fitText(hint, dialogW - 32, 0.85f), x + 16, y + 82, routeNameError.isEmpty() ? AnimationUtil.opaqueArgb(GuiThemeService.textMuted()) : AnimationUtil.opaqueArgb(GuiThemeService.danger()));

        saveRouteButton.draw(content, renamingRoute == null ? "创建" : "保存名称", true, mouseX, mouseY);
        cancelRouteButton.draw(content, "取消", false, mouseX, mouseY);

        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    private void renderDeleteConfirmation(OwoUIGraphics graphics, int mouseX, int mouseY) {
        int dialogW = Math.min(320, panelW - 32);
        int x = panelX + (panelW - dialogW) / 2, y = panelY + panelH / 2 - 56;

        float progress = deleteProgress();

        float currentX = AnimationUtil.lerp(progress, deleteOrigin.x(), x);
        float currentY = AnimationUtil.lerp(progress, deleteOrigin.y(), y);
        float currentW = AnimationUtil.lerp(progress, deleteOrigin.width(), dialogW);
        float currentH = AnimationUtil.lerp(progress, deleteOrigin.height(), 112);

        RenderUtil.drawSubpixelRoundedPanel(graphics, currentX, currentY, currentW, currentH,
                AnimationUtil.lerp(progress, 5, 10),
                AnimationUtil.lerpColor(progress, deleteOriginColor, Color.ofArgb(AnimationUtil.opaqueArgb(GuiThemeService.cardBg()))), Color.ofArgb(AnimationUtil.argb(GuiThemeService.danger(), 0xAA)));
        float labelAlpha = 1 - Mth.clamp(progress * 4, 0, 1);

        if (labelAlpha > 0.005f) {
            RenderUtil.drawCenteredSmoothText(RenderUtil.withOpacity(graphics, labelAlpha),
                    Component.literal("×"), currentX + currentW / 2, currentY + currentH / 2, 0.80f, AnimationUtil.opaqueArgb(GuiThemeService.textBody()));
        }

        float contentAlpha = Mth.clamp((progress - 0.2f) / 0.8f, 0, 1);
        if (contentAlpha <= 0.005f) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / dialogW, currentH / 112);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + dialogW, y + 112);

        OwoUIGraphics content = RenderUtil.withOpacity(graphics, contentAlpha);

        RenderUtil.drawSmoothText(content, "确认删除这个点位？", x + 16, y + 16, AnimationUtil.opaqueArgb(GuiThemeService.danger()));
        String name = pendingDelete.name == null || pendingDelete.name.isBlank() ? "未命名点位" : pendingDelete.name;
        RenderUtil.drawSmoothText(content, RenderUtil.fitText(name, dialogW - 32, 0.85f), x + 16, y + 37, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));

        confirmDeleteButton.draw(content, "确认删除", true, mouseX, mouseY);
        cancelDeleteButton.draw(content, "取消", false, mouseX, mouseY);

        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    private float deleteProgress() {
        float elapsed = (System.currentTimeMillis() - deleteAnimStarted) / (float) ANIMATION_MS;
        return deleteClosing ? deleteClosingFrom * (1 - AnimationUtil.easeOutCubic(elapsed)) : AnimationUtil.easeOutCubic(elapsed);
    }

    private void closeDeleteConfirmation(boolean confirmed) {
        if (deleteClosing) {
            return;
        }

        deleteClosingFrom = deleteProgress();
        deleteClosing = true;
        deleteConfirmed = confirmed;
        deleteAnimStarted = System.currentTimeMillis();

        (confirmed ? confirmDeleteButton : cancelDeleteButton).press();
    }

    private void finishDeletion(long now) {
        if (pendingDelete != null && deleteClosing && now - deleteAnimStarted >= ANIMATION_MS) {
            deleteSourcePoint.hideDeleteButton(false);
            if (deleteConfirmed && pendingDeleteFile != null && pendingDeleteFile.equals(store.currentFile())) {
                int index = store.entries().indexOf(pendingDelete);
                if (index >= 0 && store.deleteAt(index)) {
                    deletingPoint = deleteSourcePoint;
                    deletingPoint.remove(now);
                }
            }
            pendingDelete = null;
            deleteSourcePoint = null;
        }

        if (deletingPoint != null && deletingPoint.removalFinished(now)) {
            deletingPoint = null;
            rebuildPoints();
        }

        if (deletingFile != null && deletingFile.appearance.finished(now)) {
            deletingFile = null;
            refreshFiles();
            rebuildPoints();
        }
    }

    private boolean insidePanel(double x, double y) { return x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH; }

    private boolean insideContent(double x, double y) { return x >= contentX && x < contentX + contentW && y >= contentY && y < contentY + contentH; }
    private FileRow fileAt(double x, double y) {
        if (!insideContent(x, y)) {
            return null;
        }

        for (FileRow row : files.values()) {
            if (y >= row.y && y < row.y + FILE_HEIGHT) {
                return row;
            }
        }
        return null;
    }

    private void openDirectory() {
        try {
            Files.createDirectories(store.getDir());
            net.minecraft.util.Util.getPlatform().openPath(store.getDir());
        } catch (Exception e) { ChatNotifier.send("§c[Route] 打开目录失败 | " + e.getMessage(), NotificationType.ERR); }
    }

    private final class FileRow {
        private final Path path;
        private PointCollectionStore.FileOptions options;

        private final RouteButtonComponent surface = new RouteButtonComponent();
        private final RouteButtonComponent visible = new RouteButtonComponent();
        private final RouteButtonComponent line = new RouteButtonComponent();
        private final RouteButtonComponent rename = new RouteButtonComponent();
        private final RouteButtonComponent region = new RouteButtonComponent();
        private final RouteButtonComponent importButton = new RouteButtonComponent();

        private String externalAction = "导入";
        private final RouteNodeAnimation appearance = new RouteNodeAnimation();
        private int x, y, width;

        private FileRow(Path path) {
            this.path = path;
            this.options = store.fileOptions(path);
        }

        private void layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;

            surface.bounds(x, y + 2, width - 4, FILE_HEIGHT - 4);
            visible.bounds(x + width - 132, y + 11, 58, 22);
            line.bounds(x + width - 68, y + 11, 58, 22);
            rename.bounds(x + width - 196, y + 11, 58, 22);

            if (!store.supportsLines()) {
                visible.bounds(x + width - 68, y + 11, 58, 22);
                region.bounds(x + width - 132, y + 11, 58, 22);
                rename.bounds(x + width - 196, y + 11, 58, 22);
            }

            importButton.bounds(x + width - 68, y + 11, 58, 22);
        }
        private void draw(OwoUIGraphics graphics, int mouseX, int mouseY, RouteButtonComponent hiddenButton) {
            float appear = appearance.progress(System.currentTimeMillis());
            if (appear <= 0.005f) {
                return;
            }

            graphics = RenderUtil.beginRowAppearance(graphics, x, y, width, FILE_HEIGHT, appear);

            boolean external = store.externalFile(path);
            boolean active = !external && store.active(path);

            surface.draw(graphics, "", active, mouseX, mouseY);
            String name = store.protectedFile(path) ? "默认点位 (default.json)" : path.getFileName().toString();
            RenderUtil.drawSmoothText(graphics, RenderUtil.fitText((active ? "● " : "") + name, width - (external ? 86 : 218), 0.85f), x + 10, y + 11, external ? AnimationUtil.opaqueArgb(GuiThemeService.textMuted()) : active ? AnimationUtil.opaqueArgb(GuiThemeService.accentSoft()) : AnimationUtil.opaqueArgb(GuiThemeService.textStrong()));

            String hint = external ? "来自 routes · 导入为独立点位集"
                    : active ? store.supportsLines() ? "当前路线 · 右键编辑" : "已激活 · 右键编辑"
                    : "左键激活 · 右键编辑";
            RenderUtil.drawSmoothText(graphics, hint, x + 10, y + 27, AnimationUtil.opaqueArgb(GuiThemeService.textMuted()));

            if (external) {
                if (hiddenButton != importButton) {
                    importButton.draw(graphics, externalAction, false, mouseX, mouseY);
                }
                graphics.pose().popMatrix();
                return;
            }

            visible.draw(graphics, options.visible() ? "显示 开" : "显示 关", options.visible(), mouseX, mouseY);
            if (store.supportsLines()) {
                line.draw(graphics, options.showLine() ? "连线 开" : "连线 关", options.showLine(), mouseX, mouseY);
            } else {
                if (hiddenButton != region) {
                    region.draw(graphics, "生效区域", false, mouseX, mouseY);
                }
            }

            if (hiddenButton != rename && !store.protectedFile(path)) {
                rename.draw(graphics, "改名", false, mouseX, mouseY);
            }

            graphics.pose().popMatrix();
        }
    }
}
