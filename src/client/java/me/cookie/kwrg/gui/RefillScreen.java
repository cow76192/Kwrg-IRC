package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.component.waypoint.RegionSelectionPane;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.RefillManager;
import me.cookie.kwrg.module.modules.functions.RefillModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.KeyInputs;
import me.cookie.kwrg.util.skyblock.SkyblockIsland;
import me.cookie.kwrg.util.skyblock.SkyblockItemHelper;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RefillScreen implements ModalOverlay {

    private static final long ANIMATION_MS = 250;
    private static final int ROW_HEIGHT = 30;
    private static final int SUGGESTION_HEIGHT = 22;
    private static final int SUGGESTION_LIMIT = 6;

    private enum Field {
        ITEM, TARGET, AUTO
    }

    private final IrcScreen host;
    private final PanelItemComponent originComponent;
    private final float originX, originY;
    private final long openedAt = System.currentTimeMillis();

    private long closingAt = -1;
    private float closingFrom;

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int scroll;

    private final RouteButtonComponent closeButton = new RouteButtonComponent();
    private final RouteButtonComponent addButton = new RouteButtonComponent();
    private final List<Row> rows = new ArrayList<>();
    private final List<RouteButtonComponent> suggestionButtons = new ArrayList<>();

    private EditBox editor;
    private int editingIndex = -1;
    private Field editingField = Field.ITEM;

    private RegionSelectionPane regionPane;
    private int regionIndex = -1;
    private int recordingIndex = -1;

    private String suggestionQuery = null;
    private List<String> suggestionCache = List.of();

    RefillScreen(IrcScreen host, PanelItemComponent originComponent, float originX, float originY) {
        this.host = host;
        this.originComponent = originComponent;
        this.originX = originX;
        this.originY = originY;
        for (int index = 0; index < SUGGESTION_LIMIT; index++) {
            suggestionButtons.add(new RouteButtonComponent());
        }
    }

    public static void open() {
        openAt(Float.NaN, Float.NaN, null);
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
                host.setDirectOpenTarget("functions", "refill");
                mc.setScreen(host);
            }

            float cx = host.width / 2.0f, cy = host.height / 2.0f;
            float x = Float.isNaN(virtualX) ? cx : virtualX;
            float y = Float.isNaN(virtualY) ? cy : virtualY;
            host.openRefillOverlay(cx + (x - cx) * host.getAnimatedScale(), cy + (y - cy) * host.getAnimatedScale(), component);
        });
    }

    @Override
    public float progress() {
        long now = System.currentTimeMillis();
        return closingAt < 0 ? AnimationUtil.easeOutCubic((now - openedAt) / (float) ANIMATION_MS)
                : closingFrom * (1 - AnimationUtil.easeOutCubic((now - closingAt) / (float) ANIMATION_MS));
    }

    @Override
    public boolean isFinished() {
        return closingAt >= 0 && System.currentTimeMillis() - closingAt >= ANIMATION_MS;
    }

    private boolean interactive() {
        return closingAt < 0 && System.currentTimeMillis() - openedAt >= ANIMATION_MS;
    }

    private void close() {
        commitEditor();
        regionPane = null;
        regionIndex = -1;
        recordingIndex = -1;
        closingFrom = progress();
        closingAt = System.currentTimeMillis();
    }

    @Override
    public boolean flush() {
        if (IrcConfig.save()) return true;
        ChatNotifier.send("§c[IRC] 自动补充配置保存失败", NotificationType.ERR);
        return false;
    }

    private RefillModule.Settings settings() {
        RefillModule.Settings settings = RefillModule.CONFIG.get();
        if (settings.refillEntries == null) settings.refillEntries = new ArrayList<>();
        return settings;
    }

    private List<RefillModule.Entry> entries() {
        return settings().refillEntries;
    }

    private void syncRows() {
        int size = entries().size();
        while (rows.size() > size) {
            rows.remove(rows.size() - 1);
        }
        while (rows.size() < size) {
            rows.add(new Row());
        }
        if (regionIndex >= size) {
            regionPane = null;
            regionIndex = -1;
        }
        if (editingIndex >= size) {
            editor = null;
            editingIndex = -1;
        }
    }

    private void save() {
        IrcConfig.saveDelayed(400);
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

        scroll = Mth.clamp(scroll, 0, Math.max(0, entries().size() * ROW_HEIGHT - contentH));

        closeButton.bounds(panelX + panelW - 32, panelY + 14, 20, 22);
        addButton.bounds(panelX + panelW - 150, panelY + 14, 108, 22);

        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = contentY + index * ROW_HEIGHT - scroll;
            row.bounds(contentX, y, contentW);
            if (editingIndex == index && editor != null) {
                RouteButtonComponent field = row.field(editingField);
                editor.setX(field.bounds().x());
                editor.setY(field.bounds().y() + 2);
                editor.setWidth(editingField == Field.ITEM ? 220 : field.bounds().width());
            }
        }

        if (editor != null && editingIndex >= 0 && editingField == Field.ITEM) {
            List<String> matches = matches(editor.getValue());
            int baseY = editor.getY() + 22;
            if (baseY + SUGGESTION_LIMIT * SUGGESTION_HEIGHT > panelY + panelH - 12) {
                baseY = editor.getY() - SUGGESTION_LIMIT * SUGGESTION_HEIGHT - 2;
            }
            for (int index = 0; index < suggestionButtons.size(); index++) {
                RouteButtonComponent button = suggestionButtons.get(index);
                if (index >= matches.size()) {
                    button.bounds(0, 0, 0, 0);
                    continue;
                }
                button.bounds(editor.getX(), baseY + index * SUGGESTION_HEIGHT, 220, 20);
            }
        } else {
            for (RouteButtonComponent button : suggestionButtons) {
                button.bounds(0, 0, 0, 0);
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        syncRows();
        layout(width, height);

        if (regionPane != null && regionPane.finished()) {
            regionPane = null;
            regionIndex = -1;
        }

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
                AnimationUtil.withAlpha(GuiThemeService.panelBg(), 0xF5), AnimationUtil.withAlpha(GuiThemeService.accent(), 0x80));

        if (alpha > 0.03f) {
            long now = System.currentTimeMillis();
            closeButton.draw(owo, "×", false, mouseX, mouseY);
            addButton.draw(owo, "+ 添加物品", false, mouseX, mouseY);

            RenderUtil.drawSmoothText(owo, RenderUtil.fitText("自动补充", panelW - 260, 0.85f), panelX + 16, panelY + 17,
                    AnimationUtil.opaqueArgb(GuiThemeService.routeTitle()));
            RenderUtil.drawSmoothText(owo, "GFS 补货 · 每条目独立目标 / 自动间隔 / 快捷键 / 生效岛屿", panelX + 16, panelY + 45,
                    AnimationUtil.opaqueArgb(GuiThemeService.routeMuted()));
            RenderUtil.fillSubpixel(owo, panelX + 12, panelY + 61, panelX + panelW - 12, panelY + 62,
                    AnimationUtil.argb(GuiThemeService.accent(), 0x30));

            graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
            List<RefillModule.Entry> entries = entries();
            for (int index = 0; index < entries.size(); index++) {
                Row row = rows.get(index);
                if (row.item.bounds().y() + 24 < contentY || row.item.bounds().y() > contentY + contentH) {
                    continue;
                }
                renderRow(owo, row, entries.get(index), index, mouseX, mouseY, now);
            }
            graphics.disableScissor();

            RenderUtil.drawScrollThumb(owo, contentX + contentW - 2, contentY, contentH, entries.size() * ROW_HEIGHT, scroll,
                    AnimationUtil.withAlpha(GuiThemeService.accent(), 0xAA));

            if (entries.isEmpty()) {
                RenderUtil.drawSmoothText(owo, "暂无条目 · 点击右上角 + 添加物品", contentX + 12, contentY + 22,
                        AnimationUtil.opaqueArgb(GuiThemeService.routeBodyAlt()));
            }

            if (editor != null) {
                editor.setTextColor(AnimationUtil.opaqueArgb(GuiThemeService.routeTitle()));
                editor.extractRenderState(owo, mouseX, mouseY, partialTick);
                if (editingField == Field.ITEM) {
                    renderSuggestions(owo, mouseX, mouseY);
                }
            }

            if (regionPane != null) {
                regionPane.render(owo, mouseX, mouseY, partialTick, panelX, panelY, panelW, panelH);
            }
        }

        graphics.pose().popMatrix();
    }

    private void renderRow(OwoUIGraphics owo, Row row, RefillModule.Entry entry, int index, int mouseX, int mouseY, long now) {
        float appear = row.appear(now);
        OwoUIGraphics faded = RenderUtil.withOpacity(owo, appear);

        boolean editingItem = editingIndex == index && editingField == Field.ITEM;
        boolean editingTarget = editingIndex == index && editingField == Field.TARGET;
        boolean editingAuto = editingIndex == index && editingField == Field.AUTO;

        if (!editingItem) {
            row.item.draw(faded, itemLabel(entry), false, mouseX, mouseY);
        }
        if (!editingTarget) {
            row.target.draw(faded, "目标 " + entry.target, false, mouseX, mouseY);
        }

        row.mode.draw(faded, entry.auto ? "自动" : "手动", entry.auto, mouseX, mouseY);
        row.delete.draw(faded, "×", false, mouseX, mouseY);

        if (entry.auto) {
            if (!editingAuto) {
                row.auto.draw(faded, entry.autoSeconds <= 0 ? "间隔 关" : "间隔 " + entry.autoSeconds + "s", entry.autoSeconds > 0, mouseX, mouseY);
            }
            row.islands.draw(faded, "岛屿 " + islandLabel(entry), false, mouseX, mouseY);
        } else {
            boolean bound = entry.key != null && !entry.key.isBlank();
            row.hotkey.draw(faded, "", bound, mouseX, mouseY);
            renderHotkey(faded, row, entry, index);
        }
    }

    private void renderHotkey(OwoUIGraphics owo, Row row, RefillModule.Entry entry, int index) {
        String name = entry.key == null ? "" : entry.key;

        if (recordingIndex == index) {
            drawHotkeyLabel(owo, row, "按键…", AnimationUtil.opaqueArgb(GuiThemeService.accent()));
            return;
        }

        if (name.isBlank()) {
            drawHotkeyLabel(owo, row, "快捷键 未绑定", AnimationUtil.opaqueArgb(GuiThemeService.chipIdleText()));
            return;
        }

        String label = KeyInputs.displayName(name);
        if (keyConflict(name)) {
            drawHotkeyLabel(owo, row, "⚠ " + label, AnimationUtil.opaqueArgb(GuiThemeService.danger()));
            return;
        }

        drawHotkeyLabel(owo, row, label, AnimationUtil.opaqueArgb(GuiThemeService.chipActiveText()));
    }

    private static void drawHotkeyLabel(OwoUIGraphics owo, Row row, String label, int color) {
        var bounds = row.hotkey.bounds();
        RenderUtil.drawCenteredSmoothText(owo, Component.literal(label), bounds.x() + bounds.width() / 2.0f,
                bounds.y() + bounds.height() / 2.0f, 0.80f, color);
    }

    private boolean keyConflict(String name) {
        int count = 0;
        for (RefillModule.Entry entry : entries()) {
            if (entry == null || entry.key == null || !entry.key.equals(name)) continue;
            if (++count > 1) return true;
        }
        return false;
    }

    private void renderSuggestions(OwoUIGraphics owo, int mouseX, int mouseY) {
        List<String> matches = matches(editor.getValue());
        for (int index = 0; index < matches.size() && index < suggestionButtons.size(); index++) {
            suggestionButtons.get(index).draw(owo, matches.get(index), false, mouseX, mouseY);
        }
    }

    private static String itemLabel(RefillModule.Entry entry) {
        return entry.item == null || entry.item.isBlank() ? "选择物品" : entry.item;
    }

    private static String islandLabel(RefillModule.Entry entry) {
        List<String> islands = entry.islands;
        if (islands == null || islands.isEmpty() || islands.contains("all")) return "全部";
        if (islands.size() == 1) return shortName(islands.get(0));

        StringBuilder label = new StringBuilder(shortName(islands.get(0)));
        if (islands.size() > 1) label.append(" +").append(islands.size() - 1);
        return label.toString();
    }

    private static String shortName(String id) {
        for (SkyblockIsland island : SkyblockIsland.ALL) {
            if (island.id().equals(id)) {
                String name = island.name();
                int slash = name.indexOf(" / ");
                return slash > 0 ? name.substring(0, slash) : name;
            }
        }
        return id;
    }

    private List<String> matches(String keyword) {
        String key = keyword == null ? "" : keyword.trim();
        if (key.equals(suggestionQuery)) return suggestionCache;

        String query = key.toUpperCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();

        for (String raw : SkyblockItemHelper.getSuggestedIds()) {
            if (raw == null || raw.isEmpty()) continue;
            String id = RefillManager.normalize(raw);
            if (id.isEmpty() || ids.contains(id)) continue;
            if (!query.isEmpty() && !id.contains(query)) continue;
            ids.add(id);
            if (ids.size() >= SUGGESTION_LIMIT) break;
        }

        suggestionQuery = key;
        suggestionCache = ids;
        return ids;
    }

    private void openEditor(int index, Field field) {
        RefillModule.Entry entry = entryAt(index);
        if (entry == null) return;

        String value = switch (field) {
            case ITEM -> entry.item == null ? "" : entry.item;
            case TARGET -> String.valueOf(entry.target);
            case AUTO -> String.valueOf(entry.autoSeconds);
        };

        editingIndex = index;
        editingField = field;
        editor = new EditBox(FontManagerHelper.getGuiInputFont(), 0, 0, 220, 18, Component.literal(""));
        editor.setBordered(false);
        editor.setTextShadow(false);
        editor.setMaxLength(field == Field.ITEM ? 64 : 4);
        editor.setValue(value);
        editor.setFocused(true);
    }

    private void commitEditor() {
        if (editor == null || editingIndex < 0) return;
        RefillModule.Entry entry = entryAt(editingIndex);
        if (entry != null) {
            String value = editor.getValue() == null ? "" : editor.getValue().trim();
            switch (editingField) {
                case ITEM -> {
                    if (!value.isEmpty()) entry.item = value.toUpperCase(Locale.ROOT);
                }
                case TARGET -> entry.target = Mth.clamp(parseInt(value, entry.target), 1, 2240);
                case AUTO -> entry.autoSeconds = Mth.clamp(parseInt(value, entry.autoSeconds), 0, 600);
            }
            save();
        }
        editor = null;
        editingIndex = -1;
    }

    private void cancelEditor() {
        editor = null;
        editingIndex = -1;
    }

    private RefillModule.Entry entryAt(int index) {
        List<RefillModule.Entry> entries = entries();
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < contentX || mouseX > contentX + contentW) return -1;
        if (mouseY < contentY || mouseY > contentY + contentH) return -1;
        int index = (int) ((mouseY - contentY + scroll) / ROW_HEIGHT);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public void mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (regionPane != null) {
            regionPane.mouseClicked(click, doubled);
            return;
        }

        if (!interactive()) return;

        if (recordingIndex >= 0) {
            if (click.button() == 1) {
                recordingIndex = -1;
                return;
            }

            RefillModule.Entry entry = entryAt(recordingIndex);
            if (entry != null) {
                entry.key = KeyInputs.nameOf(KeyInputs.from(click));
                save();
            }
            recordingIndex = -1;
            return;
        }

        if (editor != null && editingField == Field.ITEM) {
            List<String> matches = matches(editor.getValue());
            for (int index = 0; index < matches.size() && index < suggestionButtons.size(); index++) {
                if (suggestionButtons.get(index).contains(click.x(), click.y())) {
                    editor.setValue(matches.get(index));
                    commitEditor();
                    return;
                }
            }
        }

        if (editor != null && insideEditor(click.x(), click.y())) {
            editor.mouseClicked(click, doubled);
            return;
        }

        if (closeButton.contains(click.x(), click.y())) {
            closeButton.press();
            close();
            return;
        }

        if (addButton.contains(click.x(), click.y())) {
            addButton.press();
            commitEditor();
            RefillModule.Entry entry = new RefillModule.Entry();
            entries().add(entry);
            syncRows();
            save();
            return;
        }

        commitEditor();

        int index = rowAt(click.x(), click.y());
        if (index >= 0 && index < entries().size()) {
            Row row = rows.get(index);
            RefillModule.Entry entry = entries().get(index);

            if (row.item.contains(click.x(), click.y())) {
                openEditor(index, Field.ITEM);
                return;
            }
            if (row.target.contains(click.x(), click.y())) {
                openEditor(index, Field.TARGET);
                return;
            }
            if (row.mode.contains(click.x(), click.y())) {
                row.mode.press();
                commitEditor();
                recordingIndex = -1;
                entry.auto = !entry.auto;
                save();
                return;
            }

            if (entry.auto) {
                if (row.auto.contains(click.x(), click.y())) {
                    openEditor(index, Field.AUTO);
                    return;
                }
                if (row.islands.contains(click.x(), click.y())) {
                    row.islands.press();
                    Set<String> selected = new LinkedHashSet<>(entry.islands == null ? List.of("all") : entry.islands);
                    regionIndex = index;
                    regionPane = new RegionSelectionPane(row.islands, selected, chosen -> {
                        entry.islands = new ArrayList<>(chosen);
                        return IrcConfig.save();
                    });
                    return;
                }
            } else if (row.hotkey.contains(click.x(), click.y())) {
                row.hotkey.press();
                commitEditor();
                recordingIndex = index;
                return;
            }

            if (row.delete.contains(click.x(), click.y())) {
                row.delete.press();
                recordingIndex = -1;
                entries().remove(index);
                syncRows();
                save();
            }
        }
    }

    private boolean insideEditor(double mouseX, double mouseY) {
        if (editor == null) return false;
        return mouseX >= editor.getX() && mouseX <= editor.getX() + editor.getWidth()
                && mouseY >= editor.getY() && mouseY <= editor.getY() + 18;
    }

    @Override
    public void mouseReleased(MouseButtonEvent click) {
        if (regionPane != null) {
            regionPane.mouseReleased(click);
            return;
        }
        if (editor != null) editor.mouseReleased(click);
    }

    @Override
    public void mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (regionPane != null) {
            regionPane.mouseDragged(click, deltaX, deltaY);
            return;
        }
        if (editor != null && insideEditor(click.x(), click.y())) {
            editor.mouseDragged(click, deltaX, deltaY);
        }
    }

    @Override
    public void mouseScrolled(double x, double y, double amount) {
        if (regionPane != null) {
            regionPane.mouseScrolled(x, y, amount);
            return;
        }
        if (x < contentX || x > contentX + contentW || y < contentY || y > contentY + contentH) return;
        scroll = Mth.clamp(scroll - (int) (amount * ROW_HEIGHT), 0, Math.max(0, entries().size() * ROW_HEIGHT - contentH));
    }

    @Override
    public void keyPressed(KeyEvent input) {
        if (regionPane != null) {
            regionPane.keyPressed(input);
            return;
        }

        if (recordingIndex >= 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                RefillModule.Entry entry = entryAt(recordingIndex);
                if (entry != null) {
                    entry.key = "";
                    save();
                }
                recordingIndex = -1;
                return;
            }

            RefillModule.Entry entry = entryAt(recordingIndex);
            if (entry != null) {
                entry.key = KeyInputs.nameOf(KeyInputs.from(input));
                save();
            }
            recordingIndex = -1;
            return;
        }

        if (editor != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelEditor();
                return;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitEditor();
                return;
            }
            editor.keyPressed(input);
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }

    @Override
    public void charTyped(CharacterEvent input) {
        if (regionPane != null) {
            regionPane.charTyped(input);
            return;
        }
        if (editor != null) editor.charTyped(input);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent input) {
        if (regionPane != null) {
            regionPane.preeditUpdated(input);
            return true;
        }
        if (editor != null) {
            editor.preeditUpdated(input);
            return true;
        }
        return false;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class Row {
        private final RouteButtonComponent item = new RouteButtonComponent();
        private final RouteButtonComponent target = new RouteButtonComponent();
        private final RouteButtonComponent mode = new RouteButtonComponent();
        private final RouteButtonComponent auto = new RouteButtonComponent();
        private final RouteButtonComponent hotkey = new RouteButtonComponent();
        private final RouteButtonComponent islands = new RouteButtonComponent();
        private final RouteButtonComponent delete = new RouteButtonComponent();
        private final long bornAt = System.currentTimeMillis();

        private float appear(long now) {
            return AnimationUtil.easeOutCubic(Mth.clamp((now - bornAt) / 180f, 0f, 1f));
        }

        private void bounds(int x, int y, int width) {
            item.bounds(x, y, 150, 24);
            target.bounds(x + 154, y, 62, 24);
            mode.bounds(x + 220, y, 70, 24);
            delete.bounds(x + width - 26, y, 22, 24);

            int trailing = Math.max(60, width - 294 - 30);
            auto.bounds(x + 294, y, 92, 24);
            hotkey.bounds(x + 294, y, trailing, 24);
            islands.bounds(x + 294 + 96, y, Math.max(60, trailing - 96), 24);
        }

        private RouteButtonComponent field(Field field) {
            return switch (field) {
                case ITEM -> item;
                case TARGET -> target;
                case AUTO -> auto;
            };
        }
    }
}
