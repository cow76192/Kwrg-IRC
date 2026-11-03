package me.cookie.kwrg.gui.pointcollection;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.component.route.RouteNodeAnimation;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class FileRow {
    private final PointCollectionStore store;
    final Path path;
    PointCollectionStore.FileOptions options;
    private long optionsModified = Long.MIN_VALUE;
    private long optionsSize = -1L;

    final RouteButtonComponent surface = new RouteButtonComponent();
    final RouteButtonComponent visible = new RouteButtonComponent();
    final RouteButtonComponent line = new RouteButtonComponent();
    final RouteButtonComponent rename = new RouteButtonComponent();
    final RouteButtonComponent region = new RouteButtonComponent();
    final RouteButtonComponent importButton = new RouteButtonComponent();

    String externalAction = "导入";
    final RouteNodeAnimation appearance = new RouteNodeAnimation();
    private int x;
    int y;
    private int width;

    FileRow(Path path, PointCollectionStore store) {
        this.path = path;
        this.store = store;
        this.options = store.fileOptions(path);
    }

    PointCollectionStore.FileOptions refreshOptions() {
        long modified;
        long size;

        try {
            modified = Files.getLastModifiedTime(path).toMillis();
            size = Files.size(path);
        } catch (IOException e) {
            optionsModified = Long.MIN_VALUE;
            optionsSize = -1L;
            return store.fileOptions(path);
        }

        if (options != null && optionsModified == modified && optionsSize == size) {
            return options;
        }

        optionsModified = modified;
        optionsSize = size;
        return store.fileOptions(path);
    }

    void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;

        surface.bounds(x, y + 2, width - 4, PointCollectionScreen.FILE_HEIGHT - 4);
        visible.bounds(x + width - 132, y + 11, 58, 22);
        line.bounds(x + width - 68, y + 11, 58, 22);
        rename.bounds(x + width - 196, y + 11, 58, 22);

        if (!store.supportsLines()) {
            visible.bounds(x + width - 68, y + 11, 58, 22);
            region.bounds(x + width - 132, y + 11, 58, 22);
            rename.bounds(x + width - 196, y + 11, 58, 22);
            if (store.supportsTracers()) {
                visible.bounds(x + width - 132, y + 11, 58, 22);
                region.bounds(x + width - 196, y + 11, 58, 22);
                rename.bounds(x + width - 260, y + 11, 58, 22);
            }
        }

        importButton.bounds(x + width - 68, y + 11, 58, 22);
    }
    void draw(OwoUIGraphics graphics, int mouseX, int mouseY, RouteButtonComponent hiddenButton) {
        float appear = appearance.progress(System.currentTimeMillis());
        if (appear <= 0.005f) {
            return;
        }

        graphics = RenderUtil.beginRowAppearance(graphics, x, y, width, PointCollectionScreen.FILE_HEIGHT, appear);

        boolean external = store.externalFile(path);
        boolean active = !external && store.active(path);

        surface.draw(graphics, "", active, mouseX, mouseY);
        String name = store.protectedFile(path) ? "默认点位 (default.json)" : path.getFileName().toString();
        RenderUtil.drawSmoothText(graphics, RenderUtil.fitText((active ? "● " : "") + name, width - (external ? 86 : store.supportsTracers() ? 282 : 218), 0.85f), x + 10, y + 11, external ? AnimationUtil.opaqueArgb(GuiThemeService.routeInactive()) : active ? AnimationUtil.opaqueArgb(GuiThemeService.routeAccentText()) : AnimationUtil.opaqueArgb(GuiThemeService.routeTitleAlt()));

        String hint = external ? "来自 routes · 导入为独立点位集"
                : active ? store.supportsLines() ? "当前路线 · 右键编辑" : "已激活 · 右键编辑"
                : "左键激活 · 右键编辑";
        RenderUtil.drawSmoothText(graphics, hint, x + 10, y + 27, AnimationUtil.opaqueArgb(GuiThemeService.routeDimmer()));

        if (external) {
            if (hiddenButton != importButton) {
                importButton.draw(graphics, externalAction, false, mouseX, mouseY);
            }
            graphics.pose().popMatrix();
            return;
        }

        visible.draw(graphics, options.visible() ? "显示 开" : "显示 关", options.visible(), mouseX, mouseY);
        if (store.supportsLines() || store.supportsTracers()) {
            line.draw(graphics, options.showLine() ? "连线 开" : "连线 关", options.showLine(), mouseX, mouseY);
        }
        if (!store.supportsLines()) {
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
