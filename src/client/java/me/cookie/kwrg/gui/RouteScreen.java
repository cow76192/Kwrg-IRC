package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.RouteManager;
import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RouteScreen extends Screen {

    private static final int ENTRY_H = 26;
    private static final int FILE_H = 22;

    private static final Color PANEL_BG = Color.ofArgb(0xF011161F);
    private static final Color PANEL_BORDER = Color.ofArgb(0x604FC3F7);
    private static final Color ROW_HOVER = Color.ofArgb(0x22FFFFFF);
    private static final Color ROW_SELECTED = Color.ofArgb(0x3D4FC3F7);
    private static final Color ROW_INVALID = Color.ofArgb(0x55FF5555);
    private static final Color ROW_INVALID_BORDER = Color.ofArgb(0xAAFF5555);
    private static final Color BADGE = Color.ofArgb(0xCC1B2A38);
    private static final Color BADGE_BORDER = Color.ofArgb(0x804FC3F7);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int fileListX;
    private int fileListY;
    private int fileListW;
    private int fileListH;
    private int routeListX;
    private int routeListY;
    private int routeListW;
    private int routeListH;
    private int editorY;
    private int editorNameX;
    private int editorNameW;
    private int editorValueX;
    private int editorValueW;
    private int editorSaveX;

    private List<Path> files = List.of();
    private int fileScroll = 0;
    private int routeScroll = 0;
    private int selectedIndex = -1;
    private int draggingIndex = -1;
    private int dragTarget = -1;
    private double dragStartY = 0.0;
    private double dragMouseX = 0.0;
    private double dragMouseY = 0.0;
    private double dragOffsetY = 0.0;
    private boolean dragMoved = false;
    private long lastFileRefresh = 0L;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    public RouteScreen() {
        super(Component.literal("路线合集管理器"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new RouteScreen()));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.files = RouteManager.listFiles();
        this.layout();

        this.nameBox = new EditBox(this.font, this.editorNameX, this.editorY, this.editorNameW, 20, Component.literal("名字"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("名字"));
        this.xBox = new EditBox(this.font, this.editorValueX, this.editorY, this.editorValueW, 20, Component.literal("X"));
        this.xBox.setHint(Component.literal("X"));
        this.yBox = new EditBox(this.font, this.editorValueX + this.editorValueW + 6, this.editorY, this.editorValueW, 20, Component.literal("Y"));
        this.yBox.setHint(Component.literal("Y"));
        this.zBox = new EditBox(this.font, this.editorValueX + (this.editorValueW + 6) * 2, this.editorY, this.editorValueW, 20, Component.literal("Z"));
        this.zBox.setHint(Component.literal("Z"));

        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(this.xBox);
        this.addRenderableWidget(this.yBox);
        this.addRenderableWidget(this.zBox);

        if (this.selectedIndex >= RouteManager.entries().size()) {
            this.selectedIndex = RouteManager.entries().isEmpty() ? -1 : RouteManager.entries().size() - 1;
        }
        if (this.selectedIndex >= 0) {
            this.loadEditor(this.selectedIndex);
        } else {
            this.clearEditor();
        }
    }

    private void layout() {
        this.panelW = Math.min(560, this.width - 40);
        this.panelH = Math.min(440, this.height - 40);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        this.fileListX = this.panelX + 12;
        this.fileListY = this.panelY + 74;
        this.fileListW = this.panelW - 24;
        this.fileListH = 92;

        this.routeListX = this.panelX + 12;
        this.routeListY = this.fileListY + this.fileListH + 30;
        this.routeListW = this.panelW - 24;
        this.editorY = this.panelY + this.panelH - 32;
        this.routeListH = Math.max(52, this.editorY - 26 - this.routeListY);

        int inner = this.panelW - 24;
        this.editorNameW = Math.max(110, inner - 3 * 58 - 3 * 6 - 64);
        this.editorNameX = this.panelX + 12;
        this.editorValueW = 58;
        this.editorValueX = this.editorNameX + this.editorNameW + 6;
        this.editorSaveX = this.editorValueX + (this.editorValueW + 6) * 3;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        OwoUIGraphics owo = OwoUIGraphics.of(graphics);

        long now = System.currentTimeMillis();
        if (now - this.lastFileRefresh > 1000L) {
            this.lastFileRefresh = now;
            this.files = RouteManager.listFiles();
        }

        RenderUtil.drawSubpixelRoundedPanel(owo, this.panelX, this.panelY, this.panelW, this.panelH,
                14.0f, PANEL_BG, PANEL_BORDER);

        this.drawText(owo, "路线合集管理器", this.panelX + 16, this.panelY + 12, 0xFFE1F5FE);
        this.drawText(owo, "文件夹: kwrg/routes", this.panelX + 16, this.panelY + 28, 0xFF78909C);

        this.drawToggle(owo, mouseX, mouseY);
        this.drawClose(owo, mouseX, mouseY);
        this.drawFiles(owo, graphics, mouseX, mouseY);
        this.drawEntries(owo, graphics, mouseX, mouseY);
        this.drawEditor(owo, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawToggle(OwoUIGraphics owo, int mouseX, int mouseY) {
        int x = this.panelX + this.panelW - 176;
        int y = this.panelY + 12;
        boolean hovered = this.inRect(mouseX, mouseY, x, y, 84, 18);
        boolean on = IrcConfig.get().routeShowLine;

        Color bg = Color.ofArgb(on ? 0x5533CC88 : 0x33FFFFFF);
        Color border = Color.ofArgb(hovered ? 0xCC4FC3F7 : 0x604FC3F7);
        RenderUtil.drawSubpixelRoundedPanel(owo, x, y, 84, 18, 6.0f, bg, border);
        this.drawText(owo, on ? "连线: 开" : "连线: 关", x + 8, y + 5, on ? 0xFF69F0AE : 0xFF90A4AE);

        boolean routeVisible = IrcConfig.get().routeVisible;
        int visibilityY = y + 22;
        boolean visibilityHovered = this.inRect(mouseX, mouseY, x, visibilityY, 84, 18);
        Color visibilityBg = Color.ofArgb(routeVisible ? 0x55FF5252 : 0x5533CC88);
        Color visibilityBorder = Color.ofArgb(visibilityHovered
                ? (routeVisible ? 0xCCFF5252 : 0xCC33CC88)
                : (routeVisible ? 0x66FF5252 : 0x6633CC88));
        RenderUtil.drawSubpixelRoundedPanel(owo, x, visibilityY, 84, 18, 6.0f, visibilityBg, visibilityBorder);
        this.drawText(owo, routeVisible ? "停止显示" : "恢复显示", x + 8, visibilityY + 5,
                routeVisible ? 0xFFFF8A80 : 0xFF69F0AE);
    }

    private void drawClose(OwoUIGraphics owo, int mouseX, int mouseY) {
        int x = this.panelX + this.panelW - 26;
        int y = this.panelY + 10;
        boolean hovered = this.inRect(mouseX, mouseY, x, y, 16, 16);
        this.drawText(owo, "×", x + 4, y + 2, hovered ? 0xFFFF5252 : 0xFF90A4AE);
    }

    private void drawFiles(OwoUIGraphics owo, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.drawText(owo, "可用路线 (" + this.files.size() + ")", this.fileListX, this.fileListY - 13, 0xFF4FC3F7);

        int openX = this.fileListX + this.fileListW - 68;
        int openY = this.fileListY - 19;
        boolean openHovered = this.inRect(mouseX, mouseY, openX, openY, 68, 16);
        RenderUtil.drawSubpixelRoundedPanel(owo, openX, openY, 68, 16, 5.0f,
                Color.ofArgb(openHovered ? 0x5533CC88 : 0x221B2A38),
                Color.ofArgb(openHovered ? 0xCC33CC88 : 0x6633CC88));
        this.drawText(owo, "打开目录", openX + 10, openY + 4, 0xFF69F0AE);

        RenderUtil.drawSubpixelRoundedPanel(owo, this.fileListX, this.fileListY, this.fileListW, this.fileListH,
                8.0f, Color.ofArgb(0x66080C12), Color.ofArgb(0x33394A5A));

        if (this.files.isEmpty()) {
            this.drawText(owo, "把 .txt / .json 路线放进 kwrg/routes 文件夹", this.fileListX + 10, this.fileListY + 12, 0xFF78909C);
            return;
        }

        graphics.enableScissor(this.fileListX, this.fileListY, this.fileListX + this.fileListW, this.fileListY + this.fileListH);
        int start = Math.max(0, this.fileScroll / FILE_H);
        for (int i = start; i < this.files.size(); i++) {
            int rowY = this.fileListY + i * FILE_H - this.fileScroll;
            if (rowY + FILE_H < this.fileListY) continue;
            if (rowY > this.fileListY + this.fileListH) break;

            Path path = this.files.get(i);
            boolean current = path.equals(RouteManager.currentFile());
            boolean hovered = this.inRect(mouseX, mouseY, this.fileListX, rowY, this.fileListW, FILE_H);
            Color bg = current ? Color.ofArgb(0x334FC3F7) : (hovered ? ROW_HOVER : Color.ofArgb(0x00000000));
            RenderUtil.drawSubpixelRoundedPanel(owo, this.fileListX + 4, rowY + 2, this.fileListW - 8, FILE_H - 4,
                    5.0f, bg, Color.ofArgb(0x00000000));
            int color = current ? 0xFF4FC3F7 : 0xFFECEFF1;
            this.drawText(owo, this.fit(path.getFileName().toString(), this.fileListW - 24), this.fileListX + 10, rowY + 6, color);
        }
        graphics.disableScissor();

        if (this.files.size() * FILE_H > this.fileListH) {
            this.drawScrollbar(owo, this.fileListX + this.fileListW - 4, this.fileListY, this.fileListH,
                    this.files.size() * FILE_H, this.fileScroll);
        }
        this.drawText(owo, "左键复制 / 右键导入 / Shift+左键删除", this.fileListX, this.fileListY + this.fileListH + 4, 0xFF607D8B);
    }

    private void drawEntries(OwoUIGraphics owo, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<RouteManager.Entry> entries = RouteManager.entries();
        String title = RouteManager.currentFile() == null
                ? "当前路线: 未导入"
                : "当前路线: " + RouteManager.currentFileName() + " (" + entries.size() + ")";
        if (RouteManager.currentFile() == null) {
            this.drawText(owo, title, this.routeListX, this.routeListY - 13, 0xFF4FC3F7);
            this.drawText(owo, "请在上方右键导入一个 .txt 路线", this.routeListX + 4, this.routeListY + 10, 0xFF78909C);
            return;
        }

        int importX = this.routeListX + this.routeListW - 138;
        int importY = this.routeListY - 19;
        boolean importHovered = this.inRect(mouseX, mouseY, importX, importY, 138, 16);
        this.drawText(owo, this.fit(title, importX - this.routeListX - 8), this.routeListX, this.routeListY - 13, 0xFF4FC3F7);
        RenderUtil.drawSubpixelRoundedPanel(owo, importX, importY, 138, 16, 5.0f,
                Color.ofArgb(importHovered ? 0x5533CC88 : 0x221B2A38),
                Color.ofArgb(importHovered ? 0xCC33CC88 : 0x6633CC88));
        this.drawText(owo, "导入路线到 Waypoints", importX + 8, importY + 4, 0xFF69F0AE);

        RenderUtil.drawSubpixelRoundedPanel(owo, this.routeListX, this.routeListY, this.routeListW, this.routeListH,
                8.0f, Color.ofArgb(0x66080C12), Color.ofArgb(0x33394A5A));

        graphics.enableScissor(this.routeListX, this.routeListY, this.routeListX + this.routeListW, this.routeListY + this.routeListH);
        int start = Math.max(0, this.routeScroll / ENTRY_H);
        for (int i = start; i < entries.size(); i++) {
            int rowY = this.routeListY + i * ENTRY_H - this.routeScroll;
            if (rowY + ENTRY_H < this.routeListY) continue;
            if (rowY > this.routeListY + this.routeListH) break;

            if (i == this.draggingIndex && this.dragMoved) {
                RenderUtil.drawSubpixelRoundedPanel(owo, this.routeListX + 4, rowY + 2, this.routeListW - 8, ENTRY_H - 4,
                        6.0f, Color.ofArgb(0x18000000), Color.ofArgb(0x664FC3F7));
                continue;
            }

            RouteManager.Entry entry = entries.get(i);
            boolean valid = RouteManager.valid(entry);
            boolean selected = i == this.selectedIndex;
            boolean hovered = this.inRect(mouseX, mouseY, this.routeListX, rowY, this.routeListW, ENTRY_H);
            boolean dropTarget = this.draggingIndex >= 0 && this.dragMoved && i == this.dragTarget;

            Color bg;
            Color border;
            if (!valid) {
                bg = ROW_INVALID;
                border = ROW_INVALID_BORDER;
            } else if (selected || dropTarget) {
                bg = ROW_SELECTED;
                border = Color.ofArgb(0xAA4FC3F7);
            } else if (hovered) {
                bg = ROW_HOVER;
                border = Color.ofArgb(0x33394A5A);
            } else {
                bg = Color.ofArgb(0x22000000);
                border = Color.ofArgb(0x22394A5A);
            }

            RenderUtil.drawSubpixelRoundedPanel(owo, this.routeListX + 4, rowY + 2, this.routeListW - 8, ENTRY_H - 4,
                    6.0f, bg, border);

            int textX = this.routeListX + 12;
            if (valid) {
                int badgeX = this.routeListX + 12;
                int badgeY = rowY + 4;
                RenderUtil.drawSubpixelRoundedPanel(owo, badgeX, badgeY, 18, 18, 5.0f, BADGE, BADGE_BORDER);
                RenderUtil.drawCenteredSmoothText(owo, Component.literal(String.valueOf(i + 1)),
                        badgeX + 9.0f, badgeY + 8.0f, 0.75f, 0xFF4FC3F7);
                textX = badgeX + 26;
            }

            String name = RouteManager.displayName(entry, i);
            String text;
            int color;
            if (valid) {
                text = name + "  " + entry.x + " " + entry.y + " " + entry.z;
                color = 0xFFECEFF1;
            } else {
                text = name + "  (坐标为空)";
                color = 0xFFFF8A80;
            }
            this.drawText(owo, this.fit(text, this.routeListW - (textX - this.routeListX) - 16), textX, rowY + 7, color);
        }

        int addY = this.routeListY + entries.size() * ENTRY_H - this.routeScroll;
        if (addY + ENTRY_H > this.routeListY && addY < this.routeListY + this.routeListH) {
            boolean hovered = this.inRect(mouseX, mouseY, this.routeListX, addY, this.routeListW, ENTRY_H);
            RenderUtil.drawSubpixelRoundedPanel(owo, this.routeListX + 4, addY + 2, this.routeListW - 8, ENTRY_H - 4,
                    6.0f, hovered ? ROW_HOVER : Color.ofArgb(0x22000000), Color.ofArgb(0x4433CC88));
            this.drawText(owo, "＋ 添加坐标", this.routeListX + 12, addY + 8, 0xFF69F0AE);
        }
        graphics.disableScissor();

        int contentHeight = entries.size() * ENTRY_H + ENTRY_H;
        if (contentHeight > this.routeListH) {
            this.drawScrollbar(owo, this.routeListX + this.routeListW - 4, this.routeListY, this.routeListH,
                    contentHeight, this.routeScroll);
        }
        this.drawText(owo, "Ctrl+左键插入 / Shift+左键删除 / 拖动排序", this.routeListX, this.routeListY + this.routeListH + 4, 0xFF607D8B);

        if (this.dragMoved && this.draggingIndex >= 0 && this.draggingIndex < entries.size()) {
            RouteManager.Entry dragged = entries.get(this.draggingIndex);
            float width = this.routeListW - 8.0f;
            float x = (float) (this.dragMouseX - 20.0);
            x = Mth.clamp(x, this.routeListX + 4.0f, this.routeListX + this.routeListW - width - 4.0f);
            float y = (float) (this.dragMouseY - this.dragOffsetY - 3.0);

            RenderUtil.drawSubpixelRoundedPanel(owo, x + 3.0f, y + 4.0f, width, ENTRY_H - 4.0f,
                    6.0f, Color.ofArgb(0x66000000), Color.ofArgb(0x44000000));
            RenderUtil.drawSubpixelRoundedPanel(owo, x, y, width, ENTRY_H - 4.0f,
                    6.0f, Color.ofArgb(0xF01B2A38), Color.ofArgb(0xCC4FC3F7));

            boolean valid = RouteManager.valid(dragged);
            float textX = x + 12.0f;
            if (valid) {
                float badgeX = x + 4.0f;
                float badgeY = y + 4.0f;
                RenderUtil.drawSubpixelRoundedPanel(owo, badgeX, badgeY, 18.0f, 18.0f, 5.0f, BADGE, BADGE_BORDER);
                RenderUtil.drawCenteredSmoothText(owo, Component.literal(String.valueOf(this.draggingIndex + 1)),
                        badgeX + 9.0f, badgeY + 8.0f, 0.75f, 0xFF4FC3F7);
                textX = badgeX + 26.0f;
            }

            String name = RouteManager.displayName(dragged, this.draggingIndex);
            String text = valid
                    ? name + "  " + dragged.x + " " + dragged.y + " " + dragged.z
                    : name + "  (坐标为空)";
            int color = valid ? 0xFFECEFF1 : 0xFFFF8A80;
            this.drawText(owo, this.fit(text, (int) (width - (textX - x) - 12.0f)), textX, y + 7.0f, color);
        }
    }

    private void drawEditor(OwoUIGraphics owo, int mouseX, int mouseY) {
        this.drawText(owo, "编辑选中坐标 / Enter 保存", this.editorNameX, this.editorY - 12, 0xFF90A4AE);
        boolean hovered = this.inRect(mouseX, mouseY, this.editorSaveX, this.editorY, 58, 20);
        RenderUtil.drawSubpixelRoundedPanel(owo, this.editorSaveX, this.editorY, 58, 20, 6.0f,
                Color.ofArgb(hovered ? 0x5533CC88 : 0x331B2A38), Color.ofArgb(0x6633CC88));
        this.drawText(owo, "保存", this.editorSaveX + 17, this.editorY + 6, 0xFF69F0AE);
    }

    private void drawScrollbar(OwoUIGraphics owo, int x, int y, int height, int contentHeight, int scroll) {
        if (contentHeight <= height) return;
        int thumbH = Math.max(16, height * height / contentHeight);
        int maxScroll = Math.max(1, contentHeight - height);
        int thumbY = y + (int) ((height - thumbH) * (scroll / (float) maxScroll));
        RenderUtil.drawSubpixelRoundedPanel(owo, x, y, 3.0f, height, 1.5f,
                Color.ofArgb(0x22000000), null);
        RenderUtil.drawSubpixelRoundedPanel(owo, x, thumbY, 3.0f, thumbH, 1.5f,
                Color.ofArgb(0xAA4FC3F7), null);
    }

    private void drawText(OwoUIGraphics owo, String text, float x, float y, int color) {
        RenderUtil.drawFluidSubpixelText(owo, Component.literal(text), x, y, color);
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, Math.max(1, width - 6)) + "…";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.panelX + this.panelW - 26, this.panelY + 10, 16, 16)) {
            this.onClose();
            return true;
        }
        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.panelX + this.panelW - 176, this.panelY + 12, 84, 18)) {
            this.toggleLine();
            return true;
        }
        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.panelX + this.panelW - 176, this.panelY + 34, 84, 18)) {
            IrcConfig config = IrcConfig.get();
            config.routeVisible = !config.routeVisible;
            IrcConfig.save();
            return true;
        }
        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.editorSaveX, this.editorY, 58, 20)) {
            this.applyEditor();
            return true;
        }
        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.fileListX + this.fileListW - 68, this.fileListY - 19, 68, 16)) {
            this.openDirectory();
            return true;
        }
        if (click.button() == 0 && this.inRect(click.x(), click.y(), this.routeListX + this.routeListW - 138, this.routeListY - 19, 138, 16)) {
            this.importToWaypoints();
            return true;
        }

        int fileIndex = this.fileIndexAt(click.x(), click.y());
        if (fileIndex >= 0) {
            Path path = this.files.get(fileIndex);
            if (click.button() == 1) {
                if (path.equals(RouteManager.currentFile())) {
                    RouteManager.clear();
                    this.files = RouteManager.listFiles();
                    this.selectedIndex = -1;
                    this.routeScroll = 0;
                    this.clearEditor();
                    ChatNotifier.send("§e[Route] 已取消显示 | " + path.getFileName(), NotificationType.INFO);
                } else if (RouteManager.importFile(path)) {
                    this.files = RouteManager.listFiles();
                    this.selectedIndex = RouteManager.entries().isEmpty() ? -1 : 0;
                    this.routeScroll = 0;
                    if (this.selectedIndex >= 0) this.loadEditor(this.selectedIndex);
                    else this.clearEditor();
                }
            } else if (click.button() == 0) {
                if (click.hasShiftDown()) {
                    RouteManager.deleteFile(path);
                    this.files = RouteManager.listFiles();
                    if (RouteManager.currentFile() == null) {
                        this.selectedIndex = -1;
                        this.clearEditor();
                    }
                } else {
                    RouteManager.copyFile(path);
                }
            }
            return true;
        }

        int entryIndex = this.entryIndexAt(click.x(), click.y());
        if (entryIndex >= 0 && click.button() == 0) {
            if (click.hasShiftDown()) {
                this.deleteEntry(entryIndex);
            } else if (click.hasControlDown()) {
                this.addEmptyAt(entryIndex + 2);
            } else {
                this.selectedIndex = entryIndex;
                this.draggingIndex = entryIndex;
                this.dragTarget = entryIndex;
                this.dragStartY = click.y();
                this.dragMouseX = click.x();
                this.dragMouseY = click.y();
                this.dragOffsetY = Mth.clamp(click.y() - (this.routeListY + entryIndex * ENTRY_H - this.routeScroll), 0.0, ENTRY_H);
                this.dragMoved = false;
            }
            return true;
        }

        if (click.button() == 0 && this.isAddRow(click.x(), click.y())) {
            this.addEmptyAt(RouteManager.entries().size() + 1);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.draggingIndex >= 0 && click.button() == 0) {
            this.dragMouseX = click.x();
            this.dragMouseY = click.y();
            if (Math.abs(click.y() - this.dragStartY) > 4.0) this.dragMoved = true;
            if (this.dragMoved) {
                int target = this.entryIndexAt(click.x(), click.y());
                if (target < 0) {
                    target = click.y() < this.routeListY ? 0 : RouteManager.entries().size() - 1;
                }
                this.dragTarget = Mth.clamp(target, 0, Math.max(0, RouteManager.entries().size() - 1));
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.draggingIndex >= 0) {
            int from = this.draggingIndex;
            if (this.dragMoved && this.dragTarget >= 0 && this.dragTarget != from) {
                RouteManager.move(from, this.dragTarget);
                this.selectedIndex = this.dragTarget;
            } else {
                this.selectedIndex = from;
            }
            this.loadEditor(this.selectedIndex);
            this.draggingIndex = -1;
            this.dragTarget = -1;
            this.dragMoved = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.inRect(mouseX, mouseY, this.fileListX, this.fileListY, this.fileListW, this.fileListH)) {
            int max = Math.max(0, this.files.size() * FILE_H - this.fileListH);
            this.fileScroll = Mth.clamp(this.fileScroll - (int) (verticalAmount * 24), 0, max);
            return true;
        }
        if (this.inRect(mouseX, mouseY, this.routeListX, this.routeListY, this.routeListW, this.routeListH)) {
            int content = RouteManager.entries().size() * ENTRY_H + ENTRY_H;
            int max = Math.max(0, content - this.routeListH);
            this.routeScroll = Mth.clamp(this.routeScroll - (int) (verticalAmount * 24), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.applyEditor();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        RouteManager.saveCurrent();
        super.removed();
    }

    private void toggleLine() {
        IrcConfig config = IrcConfig.get();
        config.routeShowLine = !config.routeShowLine;
        IrcConfig.save();
    }

    private void openDirectory() {
        Path dir = RouteManager.getDir();
        try {
            Files.createDirectories(dir);
            net.minecraft.util.Util.getPlatform().openPath(dir);
            ChatNotifier.send("§a[Route] 已打开目录 | " + dir.toAbsolutePath(), NotificationType.SUCCESS);
        } catch (Exception e) {
            ChatNotifier.send("§c[Route] 打开目录失败 | " + e.getMessage(), NotificationType.ERR);
        }
    }

    private void importToWaypoints() {
        if (RouteManager.currentFile() == null) {
            ChatNotifier.send("§c[Route] 尚未导入路线", NotificationType.ERR);
            return;
        }

        String source = RouteManager.currentFileName();
        int imported = WaypointManager.importFromRoute(source, RouteManager.entries());
        if (imported > 0) {
            IrcConfig config = IrcConfig.get();
            config.routeVisible = false;
            IrcConfig.save();
        }
        ChatNotifier.send("§a[Route] 已导入路线到 Waypoints | " + imported + " 条 | 来源 [" + source + "]"
                + (imported > 0 ? " | 路线显示已停止" : ""), NotificationType.SUCCESS);
    }

    private void applyEditor() {
        if (this.nameBox == null) return;
        String name = this.nameBox.getValue().trim();
        String x = this.xBox.getValue().trim();
        String y = this.yBox.getValue().trim();
        String z = this.zBox.getValue().trim();

        if (this.selectedIndex >= 0 && this.selectedIndex < RouteManager.entries().size()) {
            RouteManager.update(this.selectedIndex, name, x, y, z);
        } else {
            int index = RouteManager.add(name, x, y, z, RouteManager.entries().size() + 1);
            if (index < 0) return;
            this.selectedIndex = index;
        }
        this.loadEditor(this.selectedIndex);
        this.scrollTo(this.selectedIndex);
    }

    private void addEmptyAt(int position) {
        int index = RouteManager.addEmpty(position);
        if (index < 0) return;
        this.selectedIndex = index;
        this.nameBox.setValue("");
        this.xBox.setValue("");
        this.yBox.setValue("");
        this.zBox.setValue("");
        this.setFocused(this.nameBox);
        this.nameBox.setFocused(true);
        this.scrollTo(index);
    }

    private void deleteEntry(int index) {
        if (!RouteManager.deleteAt(index)) return;
        if (RouteManager.entries().isEmpty()) {
            this.selectedIndex = -1;
            this.clearEditor();
            return;
        }
        this.selectedIndex = Math.min(index, RouteManager.entries().size() - 1);
        this.loadEditor(this.selectedIndex);
    }

    private void loadEditor(int index) {
        this.selectedIndex = index;
        List<RouteManager.Entry> entries = RouteManager.entries();
        if (index < 0 || index >= entries.size()) {
            this.clearEditor();
            return;
        }
        RouteManager.Entry entry = entries.get(index);
        this.nameBox.setValue(entry.name);
        this.xBox.setValue(entry.x);
        this.yBox.setValue(entry.y);
        this.zBox.setValue(entry.z);
    }

    private void clearEditor() {
        this.selectedIndex = -1;
        if (this.nameBox == null) return;
        this.nameBox.setValue("");
        this.xBox.setValue("");
        this.yBox.setValue("");
        this.zBox.setValue("");
    }

    private void scrollTo(int index) {
        int rowTop = index * ENTRY_H;
        if (rowTop < this.routeScroll) {
            this.routeScroll = rowTop;
        } else if (rowTop + ENTRY_H > this.routeScroll + this.routeListH) {
            this.routeScroll = rowTop + ENTRY_H - this.routeListH;
        }
        int content = RouteManager.entries().size() * ENTRY_H + ENTRY_H;
        this.routeScroll = Mth.clamp(this.routeScroll, 0, Math.max(0, content - this.routeListH));
    }

    private int fileIndexAt(double mouseX, double mouseY) {
        if (!this.inRect(mouseX, mouseY, this.fileListX, this.fileListY, this.fileListW, this.fileListH)) return -1;
        int index = (int) ((mouseY - this.fileListY + this.fileScroll) / FILE_H);
        return index >= 0 && index < this.files.size() ? index : -1;
    }

    private int entryIndexAt(double mouseX, double mouseY) {
        if (!this.inRect(mouseX, mouseY, this.routeListX, this.routeListY, this.routeListW, this.routeListH)) return -1;
        int index = (int) ((mouseY - this.routeListY + this.routeScroll) / ENTRY_H);
        return index >= 0 && index < RouteManager.entries().size() ? index : -1;
    }

    private boolean isAddRow(double mouseX, double mouseY) {
        if (!this.inRect(mouseX, mouseY, this.routeListX, this.routeListY, this.routeListW, this.routeListH)) return false;
        int addY = this.routeListY + RouteManager.entries().size() * ENTRY_H - this.routeScroll;
        return mouseY >= addY && mouseY < addY + ENTRY_H;
    }

    private boolean inRect(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
