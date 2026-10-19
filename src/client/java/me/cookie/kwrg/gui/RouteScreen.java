package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.component.route.RoutePointComponent;
import me.cookie.kwrg.gui.component.route.RouteNodeAnimation;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.manager.RouteManager;
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
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A modal owned by IrcScreen; its existing menu stays mounted during both transitions. */
public final class RouteScreen {
    private static final long ANIMATION_MS = 250;
    private static final int FILE_HEIGHT = 44;
    private final float originX, originY;
    private final IrcScreen host;
    private final PanelItemComponent originComponent;
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1;
    private float closingFrom;
    private float pageFrom, pageTarget;
    private long pageStartedAt, refreshedAt, editedAt;
    private boolean dirty;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int fileScroll, pointScroll;
    private final Map<Path, FileRow> files = new LinkedHashMap<>();
    private final List<RoutePointComponent> points = new ArrayList<>();
    private final RouteButtonComponent closeButton = new RouteButtonComponent();
    private final RouteButtonComponent directoryButton = new RouteButtonComponent();
    private final RouteButtonComponent backButton = new RouteButtonComponent();
    private final RouteButtonComponent addButton = new RouteButtonComponent();
    private final RouteButtonComponent newRouteButton = new RouteButtonComponent();
    private final RouteButtonComponent saveRouteButton = new RouteButtonComponent();
    private final RouteButtonComponent cancelRouteButton = new RouteButtonComponent();
    private EditBox routeNameInput;
    private EditBox draggedInput;
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
    private RouteManager.Entry pendingDelete;
    private Path pendingDeleteFile;
    private RoutePointComponent deletingPoint;
    private FileRow deletingFile;
    private RoutePointComponent deleteSourcePoint;
    private RouteButtonComponent.Bounds deleteOrigin;
    private Color deleteOriginColor;
    private long deleteAnimStarted;
    private boolean deleteClosing, deleteConfirmed;
    private float deleteClosingFrom;

    RouteScreen(IrcScreen host, PanelItemComponent originComponent, float originX, float originY) {
        this.host = host;
        this.originComponent = originComponent;
        this.originX = originX;
        this.originY = originY;
        refreshFiles();
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
        if (closingAt >= 0) return;
        flush();
        blurPoints();
        if (pageTarget > 0) {
            pageFrom = pageProgress();
            pageTarget = 0;
            pageStartedAt = System.currentTimeMillis();
            refreshFiles();
        } else close();
    }

    private void close() {
        pendingDelete = null;
        routeNameInput = null;
        flush();
        blurPoints();
        closingFrom = progress();
        closingAt = System.currentTimeMillis();
    }

    public void flush() { if (dirty && RouteManager.saveCurrent()) dirty = false; }
    private void markDirty() { dirty = true; editedAt = System.currentTimeMillis(); }

    private void refreshFiles() {
        Map<Path, FileRow> next = new LinkedHashMap<>();
        for (Path path : RouteManager.listFiles()) {
            FileRow row = files.get(path);
            if (row == null) row = new FileRow(path);
            row.options = RouteManager.fileOptions(path);
            next.put(path, row);
        }
        files.clear();
        files.putAll(next);
        refreshedAt = System.currentTimeMillis();
    }

    private void rebuildPoints() {
        blurPoints();
        Map<RouteManager.Entry, RoutePointComponent> existing = new IdentityHashMap<>();
        for (RoutePointComponent point : points) existing.put(point.entry(), point);
        points.clear();
        var entries = RouteManager.entries();
        for (int i = 0; i < entries.size(); i++) {
            RoutePointComponent point = existing.get(entries.get(i));
            if (point == null) point = new RoutePointComponent(i, entries.get(i), this::markDirty);
            point.setIndex(i);
            points.add(point);
        }
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
            point.layout(contentX, contentY + Math.round(offset) - pointScroll, contentW);
            offset += RoutePointComponent.HEIGHT * point.remaining(now);
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        long now = System.currentTimeMillis();
        finishRouteNaming(now);
        finishDeletion(now);
        if (dirty && now - editedAt > 350) flush();
        if (pageTarget == 0 && routeNameInput == null && deletingFile == null && now - refreshedAt > 1000) refreshFiles();
        layout(width, height);
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
        OwoUIGraphics owo = RenderUtil.withOpacity(OwoUIGraphics.of(graphics), alpha);
        RenderUtil.drawSubpixelRoundedPanel(owo, panelX, panelY, panelW, panelH, 14,
                Color.ofArgb(0xF511161F), Color.ofArgb(0x804FC3F7));
        if (alpha > 0.03f) {
            boolean editor = pageTarget > 0;
            closeButton.draw(owo, "×", false, mouseX, mouseY);
            if (editor) {
                backButton.draw(owo, "‹ 返回", false, mouseX, mouseY);
                addButton.draw(owo, "+ 点位", false, mouseX, mouseY);
            } else {
                if (routeNameInput == null || namingSource != newRouteButton) newRouteButton.draw(owo, "+ 新建路线", false, mouseX, mouseY);
                directoryButton.draw(owo, "打开目录", false, mouseX, mouseY);
            }
            RenderUtil.drawSmoothText(owo, RenderUtil.fitText(editor ? RouteManager.currentFileName() : "路线管理", panelW - (editor ? 205 : 235), 0.85f), panelX + (editor ? 68 : 16), panelY + 17, 0xFFE1F5FE);
            RenderUtil.drawSmoothText(owo, editor ? "直接编辑名称 / X / Y / Z · 修改自动保存" : "选择路线文件 · 独立设置每份路线的显示与连线", panelX + 16, panelY + 45, 0xFF8CA7BA);
            RenderUtil.fillSubpixel(owo, panelX + 12, panelY + 61, panelX + panelW - 12, panelY + 62, 0x304FC3F7);
            graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
            float page = pageProgress();
            graphics.pose().pushMatrix();
            graphics.pose().translate(-page * (contentW + 16), 0);
            if (page < 1) renderFiles(owo, mouseX, mouseY);
            graphics.pose().popMatrix();
            graphics.pose().pushMatrix();
            graphics.pose().translate((1 - page) * (contentW + 16), 0);
            if (page > 0) renderPoints(graphics, owo, mouseX, mouseY, partialTick);
            graphics.pose().popMatrix();
            graphics.disableScissor();
            RenderUtil.drawScrollThumb(owo, contentX + contentW - 2, contentY, contentH,
                    editor ? points.size() * RoutePointComponent.HEIGHT : files.size() * FILE_HEIGHT,
                    editor ? pointScroll : fileScroll, Color.ofArgb(0xAA4FC3F7));
            RenderUtil.drawSmoothText(owo, editor ? "右键任意位置返回路线列表 · × 删除需确认" : "左键激活 · 右键编辑 · Shift + 左键删除 · 右键面板外关闭", panelX + 16, panelY + panelH - 25, 0xFF7893A6);
            if (pendingDelete != null) renderDeleteConfirmation(owo, mouseX, mouseY);
            if (routeNameInput != null) renderRouteNaming(graphics, owo, mouseX, mouseY, partialTick);
        }
        graphics.pose().popMatrix();
    }

    private void renderFiles(OwoUIGraphics owo, int mouseX, int mouseY) {
        if (files.isEmpty()) {
            RenderUtil.drawSmoothText(owo, "暂无路线文件", contentX + 12, contentY + 24, 0xFFCFDFEA);
            RenderUtil.drawSmoothText(owo, "点击 + 新建路线，或打开目录导入 .json / .txt 文件", contentX + 12, contentY + 44, 0xFF7893A6);
        }
        for (FileRow row : files.values()) if (row.y + FILE_HEIGHT > contentY && row.y < contentY + contentH) {
            row.draw(owo, mouseX, mouseY, routeNameInput == null ? null : namingSource);
        }
    }

    private void renderPoints(GuiGraphicsExtractor graphics, OwoUIGraphics owo, int mouseX, int mouseY, float partialTick) {
        if (points.isEmpty()) RenderUtil.drawSmoothText(owo, "暂无点位，点击右上角 + 点位开始添加", contentX + 12, contentY + 24, 0xFF8CA7BA);
        float offset = 0;
        long now = System.currentTimeMillis();
        for (RoutePointComponent point : points) {
            int y = contentY + Math.round(offset) - pointScroll;
            if (y + RoutePointComponent.HEIGHT > contentY && y < contentY + contentH) point.render(graphics, owo, mouseX, mouseY, partialTick);
            offset += RoutePointComponent.HEIGHT * point.remaining(now);
        }
    }

    public void mouseClicked(MouseButtonEvent click, boolean doubled) {
        draggedInput = null;
        if (!interactive()) return;
        if (click.button() == 1 && pageTarget > 0) {
            if (deleteSourcePoint != null) deleteSourcePoint.hideDeleteButton(false);
            pendingDelete = null;
            back();
            return;
        }
        if (routeNameInput != null) {
            if (namingClosing) return;
            if (click.button() == 1) { closeRouteNaming(); return; }
            if (namingProgress() < 0.999f) return;
            if (click.button() == 0 && cancelRouteButton.contains(click.x(), click.y())) closeRouteNaming();
            else if (click.button() == 0 && saveRouteButton.contains(click.x(), click.y())) saveRouteName();
            else if (click.button() == 0) {
                routeNameInput.setFocused(routeNameInput.isMouseOver(click.x(), click.y()));
                if (routeNameInput.mouseClicked(click, doubled)) draggedInput = routeNameInput;
            }
            return;
        }
        if (pendingDelete != null) {
            if (deleteClosing || deleteProgress() < 0.999f) return;
            if (click.button() == 0 && confirmDeleteButton.contains(click.x(), click.y())) closeDeleteConfirmation(true);
            else if (click.button() == 0 && cancelDeleteButton.contains(click.x(), click.y())) closeDeleteConfirmation(false);
            return;
        }
        if (!insidePanel(click.x(), click.y())) {
            if (click.button() == 1 || (pageTarget > 0 && click.button() == 0)) close();
            return;
        }
        if (click.button() == 0 && closeButton.contains(click.x(), click.y())) {
            closeButton.press(); close(); return;
        }
        if (pageTarget > 0) {
            if (click.button() == 0 && backButton.contains(click.x(), click.y())) {
                backButton.press(); back(); return;
            }
            if (click.button() == 0 && addButton.contains(click.x(), click.y())) {
                addButton.press(); flush();
                RouteManager.addEmpty(points.size() + 1);
                rebuildPoints();
                pointScroll = Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH);
                layout(Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
                if (!points.isEmpty()) points.getLast().focus(0);
                return;
            }
            if (insideContent(click.x(), click.y())) {
                int index = (int) ((click.y() - contentY + pointScroll) / RoutePointComponent.HEIGHT);
                for (int i = 0; i < points.size(); i++) if (i != index) points.get(i).blur();
                if (index >= 0 && index < points.size() && points.get(index).mouseClicked(click, doubled)) {
                    flush(); blurPoints();
                    pendingDelete = RouteManager.entries().get(index);
                    pendingDeleteFile = RouteManager.currentFile();
                    deleteSourcePoint = points.get(index);
                    deleteOrigin = deleteSourcePoint.deleteButton().bounds();
                    deleteOriginColor = deleteSourcePoint.deleteButton().color();
                    deleteSourcePoint.hideDeleteButton(true);
                    deleteAnimStarted = System.currentTimeMillis();
                    deleteClosing = deleteConfirmed = false;
                } else if (click.button() == 0 && index >= 0 && index < points.size()) {
                    draggedInput = points.get(index).clickedInput(click.x(), click.y());
                }
            } else blurPoints();
        } else {
            if (click.button() == 0 && newRouteButton.contains(click.x(), click.y())) {
                newRouteButton.press(); startRouteNaming(null, newRouteButton); return;
            }
            if (click.button() == 0 && directoryButton.contains(click.x(), click.y())) {
                directoryButton.press(); openDirectory(); return;
            }
            FileRow row = fileAt(click.x(), click.y());
            if (row == null) return;
            if (row.appearance.progress(System.currentTimeMillis()) < 0.95f) return;
            if (click.button() == 1) {
                row.surface.press(); flush();
                if (RouteManager.importFile(row.path)) {
                    rebuildPoints(); pointScroll = 0;
                    pageFrom = pageProgress(); pageTarget = 1; pageStartedAt = System.currentTimeMillis();
                }
            } else if (click.button() == 0) {
                if (row.rename.contains(click.x(), click.y())) {
                    row.rename.press(); startRouteNaming(row.path, row.rename);
                } else if (row.visible.contains(click.x(), click.y()) || row.line.contains(click.x(), click.y())) {
                    boolean toggleVisible = row.visible.contains(click.x(), click.y());
                    (toggleVisible ? row.visible : row.line).press();
                    var options = row.options;
                    if (RouteManager.setFileOptions(row.path, toggleVisible ? !options.visible() : options.visible(),
                            toggleVisible ? options.showLine() : !options.showLine())) refreshFiles();
                } else if (click.hasShiftDown()) {
                    flush();
                    if (RouteManager.deleteFile(row.path)) {
                        deletingFile = row;
                        row.appearance.remove(System.currentTimeMillis());
                    }
                } else {
                    row.surface.press(); flush();
                    if (RouteManager.importFile(row.path)) { rebuildPoints(); refreshFiles(); }
                }
            }
        }
    }

    public void mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!interactive() || click.button() != 0 || draggedInput == null || pendingDelete != null || namingClosing && routeNameInput != null) return;
        // Keep forwarding to the pressed input even when the pointer leaves its bounds.
        draggedInput.mouseDragged(click, deltaX, deltaY);
    }

    public void mouseReleased(MouseButtonEvent click) {
        if (click.button() != 0) return;
        if (draggedInput != null) draggedInput.mouseReleased(click);
        draggedInput = null;
    }

    public void mouseScrolled(double x, double y, double amount) {
        if (!interactive() || pendingDelete != null || routeNameInput != null || !insideContent(x, y)) return;
        draggedInput = null;
        if (pageTarget > 0) {
            blurPoints(); flush();
            pointScroll = Mth.clamp(pointScroll - (int) (amount * 30), 0, Math.max(0, points.size() * RoutePointComponent.HEIGHT - contentH));
        } else fileScroll = Mth.clamp(fileScroll - (int) (amount * 30), 0, Math.max(0, files.size() * FILE_HEIGHT - contentH));
    }

    public void keyPressed(KeyEvent input) {
        if (deletingPoint != null || deletingFile != null) return;
        if (routeNameInput != null) {
            if (namingClosing) return;
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) closeRouteNaming();
            else if (namingProgress() < 0.999f) return;
            else if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) saveRouteName();
            else routeNameInput.keyPressed(input);
            return;
        }
        if (pendingDelete != null) {
            if (deleteClosing) return;
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) closeDeleteConfirmation(false);
            else if (deleteProgress() >= 0.999f && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) closeDeleteConfirmation(true);
            return;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { back(); return; }
        if (!interactive() || pageTarget == 0) return;
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { flush(); blurPoints(); }
        else if (input.key() == GLFW.GLFW_KEY_TAB && !points.isEmpty()) {
            int focused = -1;
            for (int i = 0; i < points.size(); i++) if (points.get(i).focusedField() >= 0) focused = i * 4 + points.get(i).focusedField();
            int total = points.size() * 4;
            int next = focused < 0 ? (input.hasShiftDown() ? total - 1 : 0)
                    : Math.floorMod(focused + (input.hasShiftDown() ? -1 : 1), total);
            blurPoints(); points.get(next / 4).focus(next % 4);
            int top = next / 4 * RoutePointComponent.HEIGHT;
            pointScroll = Mth.clamp(pointScroll, Math.max(0, top + RoutePointComponent.HEIGHT - contentH), top);
        } else for (var point : points) if (point.keyPressed(input)) break;
    }

    public void charTyped(CharacterEvent input) {
        if (routeNameInput != null) {
            if (!namingClosing && namingProgress() >= 0.999f) routeNameInput.charTyped(input);
            return;
        }
        if (interactive() && pendingDelete == null && pageTarget > 0) for (var point : points) if (point.charTyped(input)) break;
    }

    public boolean preeditUpdated(PreeditEvent input) {
        if (routeNameInput != null) {
            if (!namingClosing && namingProgress() >= 0.999f) routeNameInput.preeditUpdated(input);
            return true;
        }
        if (interactive() && pendingDelete == null && pageTarget > 0) {
            for (var point : points) if (point.preeditUpdated(input)) return true;
        }
        return true;
    }

    private void startRouteNaming(Path file, RouteButtonComponent source) {
        flush();
        namingSource = source;
        namingOrigin = source.bounds();
        namingOriginColor = source.color();
        namingAnimStarted = System.currentTimeMillis();
        namingClosing = false;
        routeToReveal = null;
        renamingRoute = file;
        routeNameError = "";
        routeNameInput = new EditBox(FontManagerHelper.getGuiInputFont(), 0, 0, 200, 18, Component.literal("路线名称"));
        routeNameInput.setBordered(false);
        routeNameInput.setTextShadow(false);
        routeNameInput.setMaxLength(69);
        routeNameInput.setHint(Component.literal("输入路线名称"));
        if (file != null) routeNameInput.setValue(file.getFileName().toString().replaceFirst("(?i)\\.(json|txt)$", ""));
        routeNameInput.setResponder(value -> routeNameError = "");
        routeNameInput.setFocused(true);
    }

    private void saveRouteName() {
        if (namingClosing) return;
        try {
            routeToReveal = renamingRoute == null ? RouteManager.createRoute(routeNameInput.getValue())
                    : RouteManager.renameRoute(renamingRoute, routeNameInput.getValue());
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
        if (namingClosing) return;
        draggedInput = null;
        namingClosingFrom = namingProgress();
        namingClosing = true;
        namingAnimStarted = System.currentTimeMillis();
        routeNameInput.setFocused(false);
    }

    private void finishRouteNaming(long now) {
        if (routeNameInput == null || !namingClosing || now - namingAnimStarted < ANIMATION_MS) return;
        routeNameInput = null;
        namingSource = null;
        if (routeToReveal != null) {
            refreshFiles();
            int index = new ArrayList<>(files.keySet()).indexOf(routeToReveal);
            if (index >= 0) fileScroll = Math.max(0, (index + 1) * FILE_HEIGHT - contentH);
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
                AnimationUtil.lerpColor(progress, namingOriginColor, Color.ofArgb(0xFF1C2734)),
                Color.ofArgb(0x885AA5C4));
        float labelAlpha = 1 - Mth.clamp(progress * 4, 0, 1);
        if (labelAlpha > 0.005f) {
            RenderUtil.drawCenteredSmoothText(RenderUtil.withOpacity(owo, labelAlpha),
                    Component.literal(renamingRoute == null ? "+ 新建路线" : "改名"),
                    currentX + currentW / 2, currentY + currentH / 2, 0.80f, 0xFFD2DFE8);
        }
        float contentAlpha = Mth.clamp((progress - 0.2f) / 0.8f, 0, 1);
        if (contentAlpha <= 0.005f) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / dialogW, currentH / 152);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + dialogW, y + 152);
        OwoUIGraphics content = RenderUtil.withOpacity(owo, contentAlpha);
        RenderUtil.drawSmoothText(content, renamingRoute == null ? "新建路线" : "修改路线名称", x + 16, y + 16, 0xFFE1F5FE);
        routeNameInput.extractRenderState(content, mouseX, mouseY, partialTick);
        RenderUtil.fillSubpixel(content, x + 16, y + 67, x + dialogW - 16, y + 68, 0xFF80D7FF);
        String hint = routeNameError.isEmpty() ? "名称将用作路线文件名，同名文件不会被覆盖" : routeNameError;
        RenderUtil.drawSmoothText(content, RenderUtil.fitText(hint, dialogW - 32, 0.85f), x + 16, y + 82,
                routeNameError.isEmpty() ? 0xFF8CA7BA : 0xFFFFA8B4);
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
                AnimationUtil.lerpColor(progress, deleteOriginColor, Color.ofArgb(0xFF1C2734)), Color.ofArgb(0xAAAF6976));
        float labelAlpha = 1 - Mth.clamp(progress * 4, 0, 1);
        if (labelAlpha > 0.005f) RenderUtil.drawCenteredSmoothText(RenderUtil.withOpacity(graphics, labelAlpha),
                Component.literal("×"), currentX + currentW / 2, currentY + currentH / 2, 0.80f, 0xFFD2DFE8);
        float contentAlpha = Mth.clamp((progress - 0.2f) / 0.8f, 0, 1);
        if (contentAlpha <= 0.005f) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / dialogW, currentH / 112);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + dialogW, y + 112);
        OwoUIGraphics content = RenderUtil.withOpacity(graphics, contentAlpha);
        RenderUtil.drawSmoothText(content, "确认删除这个点位？", x + 16, y + 16, 0xFFFFCBD1);
        String name = pendingDelete.name == null || pendingDelete.name.isBlank() ? "未命名点位" : pendingDelete.name;
        RenderUtil.drawSmoothText(content, RenderUtil.fitText(name, dialogW - 32, 0.85f), x + 16, y + 37, 0xFFBBCDDD);
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
        if (deleteClosing) return;
        deleteClosingFrom = deleteProgress();
        deleteClosing = true;
        deleteConfirmed = confirmed;
        deleteAnimStarted = System.currentTimeMillis();
        (confirmed ? confirmDeleteButton : cancelDeleteButton).press();
    }

    private void finishDeletion(long now) {
        if (pendingDelete != null && deleteClosing && now - deleteAnimStarted >= ANIMATION_MS) {
            deleteSourcePoint.hideDeleteButton(false);
            if (deleteConfirmed && pendingDeleteFile != null && pendingDeleteFile.equals(RouteManager.currentFile())) {
                int index = RouteManager.entries().indexOf(pendingDelete);
                if (index >= 0 && RouteManager.deleteAt(index)) {
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
        if (!insideContent(x, y)) return null;
        for (FileRow row : files.values()) if (y >= row.y && y < row.y + FILE_HEIGHT) return row;
        return null;
    }

    private static void openDirectory() {
        try {
            Files.createDirectories(RouteManager.getDir());
            net.minecraft.util.Util.getPlatform().openPath(RouteManager.getDir());
        } catch (Exception e) { ChatNotifier.send("§c[Route] 打开目录失败 | " + e.getMessage(), NotificationType.ERR); }
    }

    private static final class FileRow {
        private final Path path;
        private RouteManager.FileOptions options;
        private final RouteButtonComponent surface = new RouteButtonComponent();
        private final RouteButtonComponent visible = new RouteButtonComponent();
        private final RouteButtonComponent line = new RouteButtonComponent();
        private final RouteButtonComponent rename = new RouteButtonComponent();
        private final RouteNodeAnimation appearance = new RouteNodeAnimation();
        private int x, y, width;
        private FileRow(Path path) { this.path = path; this.options = RouteManager.fileOptions(path); }
        private void layout(int x, int y, int width) {
            this.x = x; this.y = y; this.width = width;
            surface.bounds(x, y + 2, width - 4, FILE_HEIGHT - 4);
            visible.bounds(x + width - 132, y + 11, 58, 22);
            line.bounds(x + width - 68, y + 11, 58, 22);
            rename.bounds(x + width - 196, y + 11, 58, 22);
        }
        private void draw(OwoUIGraphics graphics, int mouseX, int mouseY, RouteButtonComponent hiddenButton) {
            float appear = appearance.progress(System.currentTimeMillis());
            if (appear <= 0.005f) return;
            graphics = RenderUtil.beginRowAppearance(graphics, x, y, width, FILE_HEIGHT, appear);
            boolean current = path.equals(RouteManager.currentFile());
            surface.draw(graphics, "", current, mouseX, mouseY);
            RenderUtil.drawSmoothText(graphics, RenderUtil.fitText((current ? "● " : "") + path.getFileName(), width - 218, 0.85f), x + 10, y + 11, current ? 0xFF92DFFF : 0xFFE0EAF1);
            RenderUtil.drawSmoothText(graphics, current ? "当前路线 · 右键编辑" : "右键进入点位编辑", x + 10, y + 27, 0xFF7893A6);
            visible.draw(graphics, options.visible() ? "显示 开" : "显示 关", options.visible(), mouseX, mouseY);
            line.draw(graphics, options.showLine() ? "连线 开" : "连线 关", options.showLine(), mouseX, mouseY);
            if (hiddenButton != rename) rename.draw(graphics, "改名", false, mouseX, mouseY);
            graphics.pose().popMatrix();
        }
    }
}
