package me.cookie.kwrg.gui.component.waypoint;

import me.cookie.kwrg.gui.component.route.RouteButtonComponent;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.util.SkyblockIsland;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

/** Searchable multi-select, morphing out of its source button without a backdrop. */
public final class RegionSelectionPane {
    private final RouteButtonComponent source;
    private final RouteButtonComponent.Bounds origin;
    private final Color originColor;
    private final EditBox search = new EditBox(FontManagerHelper.getGuiInputFont(), 0, 0, 200, 18, Component.literal("搜索岛屿"));
    private final Set<String> selected = new LinkedHashSet<>();
    private final Consumer<Set<String>> changed;
    private final Map<String, RouteButtonComponent> choices = new LinkedHashMap<>();
    private final RouteButtonComponent done = new RouteButtonComponent();
    private long startedAt = System.currentTimeMillis();
    private boolean closing, dragging;
    private float closingFrom;
    private int x, y, width, height, scroll;

    public RegionSelectionPane(RouteButtonComponent source, Set<String> selected, Consumer<Set<String>> changed) {
        this.source = source; this.origin = source.bounds(); this.originColor = source.color();
        this.selected.addAll(selected); this.changed = changed;
        search.setBordered(false); search.setTextShadow(false); search.setMaxLength(64);
        search.setHint(Component.literal("搜索岛屿名称 / ID")); search.setFocused(true);
        search.setResponder(value -> scroll = 0);
        for (var island : SkyblockIsland.ALL) choices.put(island.id(), new RouteButtonComponent());
    }
    public RouteButtonComponent source() { return source; }
    public boolean finished() { return closing && System.currentTimeMillis() - startedAt >= 250; }
    private float progress() {
        float eased = AnimationUtil.easeOutCubic((System.currentTimeMillis() - startedAt) / 250f);
        return closing ? closingFrom * (1 - eased) : eased;
    }
    public void close() {
        if (closing) return;
        closingFrom = progress(); closing = true; startedAt = System.currentTimeMillis();
        dragging = false; search.setFocused(false);
    }

    public void render(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTick, int panelX, int panelY, int panelW, int panelH) {
        width = Math.min(360, panelW - 32); height = Math.min(320, panelH - 24);
        x = panelX + (panelW - width) / 2; y = panelY + (panelH - height) / 2;
        search.setX(x + 16); search.setY(y + 42); search.setWidth(width - 32);
        done.bounds(x + width - 72, y + height - 32, 56, 22);
        List<SkyblockIsland> results = SkyblockIsland.search(search.getValue());
        int listH = Math.max(0, height - 112);
        scroll = Mth.clamp(scroll, 0, Math.max(0, results.size() * 26 - listH));
        for (int i = 0; i < results.size(); i++) choices.get(results.get(i).id()).bounds(x + 16, y + 72 + i * 26 - scroll, width - 32, 23);
        float p = progress();
        float currentX = AnimationUtil.lerp(p, origin.x(), x), currentY = AnimationUtil.lerp(p, origin.y(), y);
        float currentW = AnimationUtil.lerp(p, origin.width(), width), currentH = AnimationUtil.lerp(p, origin.height(), height);
        RenderUtil.drawSubpixelRoundedPanel(graphics, currentX, currentY, currentW, currentH, AnimationUtil.lerp(p, 5, 10),
                AnimationUtil.lerpColor(p, originColor, Color.ofArgb(0xFF1C2734)), Color.ofArgb(0x885AA5C4));
        float labelAlpha = 1 - Mth.clamp(p * 4, 0, 1);
        if (labelAlpha > 0.005f) RenderUtil.drawCenteredSmoothText(RenderUtil.withOpacity(graphics, labelAlpha), Component.literal("生效区域"), currentX + currentW / 2, currentY + currentH / 2, 0.8f, 0xFFD2DFE8);
        float alpha = Mth.clamp((p - 0.2f) / 0.8f, 0, 1);
        if (alpha <= 0.005f) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY); graphics.pose().scale(currentW / width, currentH / height); graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + width, y + height);
        OwoUIGraphics content = RenderUtil.withOpacity(graphics, alpha);
        RenderUtil.drawSmoothText(content, "生效区域 · 可多选", x + 16, y + 15, 0xFFE1F5FE);
        search.extractRenderState(content, mouseX, mouseY, partialTick);
        RenderUtil.fillSubpixel(content, x + 16, y + 64, x + width - 16, y + 65, 0xFF80D7FF);
        graphics.enableScissor(x + 12, y + 71, x + width - 12, y + 72 + listH);
        for (int i = 0; i < results.size(); i++) {
            int rowY = y + 72 + i * 26 - scroll;
            if (rowY + 23 < y + 72 || rowY > y + 72 + listH) continue;
            SkyblockIsland island = results.get(i);
            choices.get(island.id()).draw(content, (selected.contains(island.id()) ? "✓ " : "  ") + island.name(), selected.contains(island.id()), mouseX, mouseY);
        }
        graphics.disableScissor();
        RenderUtil.drawSmoothText(content, selected.contains("all") ? "所有区域均生效" : "已选 " + selected.size() + " 个区域", x + 16, y + height - 24, 0xFF8CA7BA);
        done.draw(content, "完成", false, mouseX, mouseY);
        graphics.disableScissor(); graphics.pose().popMatrix();
    }

    public void mouseClicked(MouseButtonEvent click, boolean doubled) {
        dragging = false;
        if (closing) return;
        if (click.button() == 1) { close(); return; }
        if (click.button() != 0 || progress() < 0.999f) return;
        if (done.contains(click.x(), click.y())) { done.press(); close(); return; }
        if (click.y() >= y + 72 && click.y() < y + height - 40 && click.x() >= x + 16 && click.x() < x + width - 16) {
            for (SkyblockIsland island : SkyblockIsland.search(search.getValue())) if (choices.get(island.id()).contains(click.x(), click.y())) {
                choices.get(island.id()).press();
                if (island.id().equals("all")) { selected.clear(); selected.add("all"); }
                else { selected.remove("all"); if (!selected.add(island.id())) selected.remove(island.id()); }
                changed.accept(Set.copyOf(selected));
                return;
            }
        }
        search.setFocused(search.isMouseOver(click.x(), click.y()));
        dragging = search.mouseClicked(click, doubled);
    }
    public void mouseDragged(MouseButtonEvent click, double dx, double dy) { if (dragging && !closing) search.mouseDragged(click, dx, dy); }
    public void mouseReleased(MouseButtonEvent click) { if (click.button() == 0) { search.mouseReleased(click); dragging = false; } }
    public void mouseScrolled(double mouseX, double mouseY, double amount) {
        if (closing || progress() < 0.999f || mouseX < x || mouseX > x + width || mouseY < y + 72 || mouseY > y + height - 40) return;
        dragging = false; scroll -= (int) (amount * 26);
    }
    public void keyPressed(KeyEvent input) { if (input.key() == GLFW.GLFW_KEY_ESCAPE) close(); else if (!closing && progress() >= 0.999f) search.keyPressed(input); }
    public void charTyped(CharacterEvent input) { if (!closing && progress() >= 0.999f) search.charTyped(input); }
    public void preeditUpdated(PreeditEvent input) { if (!closing && progress() >= 0.999f) search.preeditUpdated(input); }
}
