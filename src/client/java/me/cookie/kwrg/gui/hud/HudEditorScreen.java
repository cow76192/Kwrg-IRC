package me.cookie.kwrg.gui.hud;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import me.cookie.kwrg.render.InventoryHudRenderer;
import me.cookie.kwrg.render.PlayerDollHudRenderer;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HudEditorScreen extends Screen {

    private static final float GRAB_PADDING = 10.0f;
    private static final float SNAP_DISTANCE = 6.0f;
    private static final float SCALE_STEP = 0.05f;
    private static final int HINT_BAR_HEIGHT = 44;
    private static final int LIST_WIDTH = 168;
    private static final int LIST_ROW_HEIGHT = 22;
    private static final int LIST_TOP = 34;
    private static final int LIST_HEADER_HEIGHT = 26;
    private static final float LIST_CORNER_RADIUS = 12.0f;
    private static final float ROW_CORNER_RADIUS = 6.0f;

    private final Map<String, Float> selectionProgressMap = new HashMap<>();
    private final Map<String, Float> hoverProgressMap = new HashMap<>();
    private long lastFrameTime = -1L;

    private static final Color COLOR_SELECTED = Color.ofArgb(0x3D4FC3F7); // 选中态天蓝高亮
    private static final Color COLOR_HOVERED = Color.ofArgb(0x22FFFFFF);  // 悬停态半透明白
    private static final Color COLOR_TRANSPARENT_BASE = Color.ofArgb(0x004FC3F7); // 透明基色（同色相防发脏）

    // 过渡速率
    private static final float ANIM_SPEED = 1.75f;

    private String selectedId;
    private boolean dragging = false;
    private double dragStartMouseX = 0.0;
    private double dragStartMouseY = 0.0;
    private float dragStartOffsetX = 0.0f;
    private float dragStartOffsetY = 0.0f;

    public HudEditorScreen(String initialSelectionId) {
        super(Component.literal("HUD 布局编辑器"));
        this.selectedId = initialSelectionId;
    }

    public static void open(String elementId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new HudEditorScreen(elementId)));
    }

    @Override
    protected void init() {
        HudElementRegistry.setEditorPreview(true);
        if (selectedId == null || HudElementRegistry.get(selectedId) == null) {
            List<HudElement> elements = HudElementRegistry.all();
            if (!elements.isEmpty()) selectedId = elements.get(0).id();
        }

        if (selectedId != null && !selectionProgressMap.containsKey(selectedId)) {
            selectionProgressMap.put(selectedId, 1.0f);
        }
        lastFrameTime = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        HudElementRegistry.setEditorPreview(false);
        IrcConfig.save();
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
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        DynamicIslandRenderer.updateAnimationState();
        // All HUD providers have finished; use sample content only if none drew a scoreboard.
        ScoreboardIslandRenderer.renderPreviewIfEmpty(graphics);
        InventoryHudRenderer.renderPreviewIfEmpty(graphics);
        PlayerDollHudRenderer.renderPreviewIfEmpty(graphics);
        OwoUIGraphics owo = OwoUIGraphics.of(graphics);

        List<HudElement> elements = HudElementRegistry.all();
        for (HudElement element : elements) {
            drawElementOutline(owo, element, mouseX, mouseY);
        }

        drawElementList(owo, elements, mouseX, mouseY);
        drawHints(owo);
    }

    private void drawElementOutline(OwoUIGraphics owo, HudElement element, int mouseX, int mouseY) {
        float[] bounds = element.bounds();
        if (bounds == null || bounds[2] <= 0.0f || bounds[3] <= 0.0f) return;

        boolean selected = element.id().equals(selectedId);
        boolean hovered = isOverRect(bounds, mouseX, mouseY);
        boolean hidden = !element.isEnabled();

        int alpha = selected ? 0xE6 : (hovered ? 0xB0 : 0x66);
        int rgb = hidden ? 0x9E9E9E : 0x4FC3F7;
        Color outline = Color.ofArgb(ARGB.color(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
        Color fill = Color.ofArgb(selected ? 0x1A4FC3F7 : 0x00000000);

        RenderUtil.drawSubpixelRoundedPanel(owo, bounds[0] - 3.0f, bounds[1] - 3.0f, bounds[2] + 6.0f, bounds[3] + 6.0f,
                13.0f, selected ? 2.0f : 1.4f, fill, outline);

        HudElement.State state = HudElementRegistry.state(element.id());
        String scaleText = Math.abs(state.scale - 1.0f) > 0.005f
                ? String.format(Locale.ROOT, " (%.2fx)", state.scale)
                : "";
        String label = element.displayName() + scaleText + (hidden ? " (已隐藏)" : "");
        RenderUtil.drawFluidSubpixelText(owo, Component.literal(label), bounds[0], Math.max(2.0f, bounds[1] - 13.0f),
                ARGB.color(selected ? 0xFF : 0xB0, 0xE1, 0xF5, 0xFE));
    }

    private void drawElementList(OwoUIGraphics owo, List<HudElement> elements, int mouseX, int mouseY) {

        long now = System.currentTimeMillis();
        float dt = lastFrameTime < 0 ? 0.016f : (float) (now - lastFrameTime) / 1000.0f;
        lastFrameTime = now;
        dt = Math.min(dt, 0.1f);

        int left = this.width - LIST_WIDTH;
        int listBottom = Math.min(this.height - HINT_BAR_HEIGHT, LIST_TOP + LIST_HEADER_HEIGHT + elements.size() * LIST_ROW_HEIGHT + 6);

        float panelX = left;
        float panelY = LIST_TOP - 8.0f;
        float panelW = this.width - 6 - panelX;
        float panelH = listBottom - panelY;

        RenderUtil.drawSubpixelRoundedPanel(owo, panelX, panelY, panelW, panelH, LIST_CORNER_RADIUS,
                Color.ofArgb(0xB4101620), Color.ofArgb(0x3D4FC3F7));
        RenderUtil.drawFluidSubpixelText(owo, Component.literal("HUD 元素"), left + 10, LIST_TOP, 0xFF4FC3F7);

        float rowX = left + 5.0f;
        float rowW = this.width - 6 - rowX - 5.0f;

        int rowY = LIST_TOP + LIST_HEADER_HEIGHT;
        for (HudElement element : elements) {
            String id = element.id();
            boolean selected = id.equals(selectedId);
            boolean hovered = isInside(mouseX, mouseY, left, rowY, LIST_WIDTH - 12, LIST_ROW_HEIGHT);

            float targetSelect = selected ? 1.0f : 0.0f;
            float currentSelect = selectionProgressMap.getOrDefault(id, 0.0f);
            if (currentSelect < targetSelect) {
                currentSelect = Math.min(targetSelect, currentSelect + dt * ANIM_SPEED);
            } else if (currentSelect > targetSelect) {
                currentSelect = Math.max(targetSelect, currentSelect - dt * ANIM_SPEED);
            }
            selectionProgressMap.put(id, currentSelect);

            float targetHover = hovered ? 1.0f : 0.0f;
            float currentHover = hoverProgressMap.getOrDefault(id, 0.0f);
            if (currentHover < targetHover) {
                currentHover = Math.min(targetHover, currentHover + dt * (ANIM_SPEED * 1.5f));
            } else if (currentHover > targetHover) {
                currentHover = Math.max(targetHover, currentHover - dt * (ANIM_SPEED * 1.5f));
            }
            hoverProgressMap.put(id, currentHover);

            float selectEased = targetSelect >= 1.0f
                    ? AnimationUtil.easeOutCubic(currentSelect)
                    : (1.0f - AnimationUtil.easeOutCubic(1.0f - currentSelect));

            float hoverEased = targetHover >= 1.0f
                    ? AnimationUtil.easeOutCubic(currentHover)
                    : (1.0f - AnimationUtil.easeOutCubic(1.0f - currentHover));

            Color baseColor = AnimationUtil.lerpColor(hoverEased, COLOR_TRANSPARENT_BASE, COLOR_HOVERED);
            Color rowBgColor = AnimationUtil.lerpColor(selectEased, baseColor, COLOR_SELECTED);

            if (rowBgColor.alpha() > 0.005f) {
                RenderUtil.drawSubpixelRoundedPanel(owo, rowX, rowY, rowW, LIST_ROW_HEIGHT - 2, ROW_CORNER_RADIUS,
                        rowBgColor, null);
            }

            int titleColor = ARGB.color(
                    0xFF,
                    Math.round(Mth.lerp(selectEased, 0xCF, 0xFF)),
                    Math.round(Mth.lerp(selectEased, 0xD8, 0xFF)),
                    Math.round(Mth.lerp(selectEased, 0xDC, 0xFF))
            );

            HudElement.State state = HudElementRegistry.state(element.id());
            String line = element.displayName();
            String detail = HudElement.Anchor.labelX(state.anchorX) + "·" + HudElement.Anchor.labelY(state.anchorY);
            if (!element.isEnabled()) detail += " · 隐藏";

            RenderUtil.drawFluidSubpixelText(owo, Component.literal(line), left + 10, rowY + 6, titleColor);
            RenderUtil.drawFluidSubpixelText(owo, Component.literal(detail), left + 82, rowY + 6, 0xFF90A4AE);

            rowY += LIST_ROW_HEIGHT;
        }

        RenderUtil.drawFluidSubpixelText(owo, Component.literal("点击选中 · 拖动移动 · 滚轮缩放"), left + 10, listBottom + 3, 0xFF78909C);
    }

    private float applyEase(float current, float target) {
        if (target >= 1.0f) {
            return (float) AnimationUtil.easeOutCubic(current);
        } else {
            return 1.0f - (float) AnimationUtil.easeOutCubic(1.0f - current);
        }
    }

    private void drawHints(OwoUIGraphics owo) {
        int barTop = this.height - HINT_BAR_HEIGHT;
        owo.fill(0, barTop, this.width, this.height, 0xB40A0E14);

        HudElement selected = HudElementRegistry.get(selectedId);
        String controls = "拖动元素调整位置  ·  悬停滚轮缩放大小  ·  按住 Ctrl 关闭磁吸  ·  方向键微调(Shift x10)  ·  R 重置当前  ·  A 重置全部  ·  ESC 保存退出";
        String state = selected == null ? "未选中元素" : describeState(selected);

        RenderUtil.drawFluidSubpixelText(owo, Component.literal(controls), 12.0f, barTop + 8.0f, 0xFFECEFF1);
        RenderUtil.drawFluidSubpixelText(owo, Component.literal(state), 12.0f, barTop + 24.0f, 0xFF4FC3F7);
    }

    private String describeState(HudElement element) {
        HudElement.State state = HudElementRegistry.state(element.id());
        return element.displayName()
                + "   锚点 X: " + HudElement.Anchor.labelX(state.anchorX) + "  Y: " + HudElement.Anchor.labelY(state.anchorY)
                + "   偏移 X: " + Math.round(state.offsetX) + "  Y: " + Math.round(state.offsetY)
                + "   缩放: " + String.format(Locale.ROOT, "%.2fx", state.scale);
    }

    private static boolean isInside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static boolean isOverRect(float[] bounds, double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, bounds[0] - GRAB_PADDING, bounds[1] - GRAB_PADDING,
                bounds[2] + GRAB_PADDING * 2.0f, bounds[3] + GRAB_PADDING * 2.0f);
    }

    private HudElement hitTest(double mouseX, double mouseY) {
        List<HudElement> elements = HudElementRegistry.all();
        for (int i = elements.size() - 1; i >= 0; i--) {
            float[] bounds = elements.get(i).bounds();
            if (bounds != null && bounds[2] > 0.0f && bounds[3] > 0.0f && isOverRect(bounds, mouseX, mouseY)) {
                return elements.get(i);
            }
        }
        return null;
    }

    private HudElement hitTestList(double mouseX, double mouseY) {
        int left = this.width - LIST_WIDTH;
        if (mouseX < left) return null;

        List<HudElement> elements = HudElementRegistry.all();
        int rowY = LIST_TOP + LIST_HEADER_HEIGHT;
        for (HudElement element : elements) {
            if (isInside(mouseX, mouseY, left, rowY, LIST_WIDTH - 12, LIST_ROW_HEIGHT)) return element;
            rowY += LIST_ROW_HEIGHT;
        }
        return null;
    }

    private static String configModuleFor(String elementId) {
        return switch (elementId) {
            case HudElementRegistry.ISLAND -> "dynamicIsland";
            case HudElementRegistry.SCOREBOARD -> "dynamicScoreboard";
            case HudElementRegistry.INVENTORY -> "inventoryHud";
            case HudElementRegistry.PLAYER_DOLL -> "playerDollHud";
            default -> null;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Math.abs(scrollY) > 1e-4) {
            HudElement target = hitTest(mouseX, mouseY);
            if (target == null) {
                target = hitTestList(mouseX, mouseY);
            }

            if (target != null) {
                selectedId = target.id();
                HudElement.State state = HudElementRegistry.state(target.id());

                float delta = (scrollY > 0 ? 1.0f : -1.0f) * SCALE_STEP;
                float newScale = Math.round((state.scale + delta) * 100.0f) / 100.0f;
                state.scale = Mth.clamp(newScale, HudElement.State.MIN_SCALE, HudElement.State.MAX_SCALE);
                state.sanitize();

                IrcConfig.saveDelayed(400);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 1) {
            HudElement hit = hitTest(click.x(), click.y());
            if (hit != null) {
                String module = configModuleFor(hit.id());
                if (module != null) {
                    IrcScreen.openDirectToModule(Category.STYLE.getName(), module);
                    return true;
                }
            }
        }

        if (click.button() == 0) {
            HudElement listHit = hitTestList(click.x(), click.y());
            if (listHit != null) {
                selectedId = listHit.id();
                return true;
            }

            HudElement hit = hitTest(click.x(), click.y());
            if (hit != null) {
                selectedId = hit.id();
                dragging = true;
                dragStartMouseX = click.x();
                dragStartMouseY = click.y();

                HudElementRegistry.cancelResetAnimation(hit.id());
                HudElement.State state = HudElementRegistry.state(hit.id());
                dragStartOffsetX = state.offsetX;
                dragStartOffsetY = state.offsetY;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0 && selectedId != null) {
            HudElement.State state = HudElementRegistry.state(selectedId);
            state.offsetX = (float) (dragStartOffsetX + (click.x() - dragStartMouseX));
            state.offsetY = (float) (dragStartOffsetY + (click.y() - dragStartMouseY));
            state.sanitize();

            if (!click.hasControlDown()) {
                applySnapping(state);
            }

            IrcConfig.saveDelayed(400);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private void applySnapping(HudElement.State state) {
        HudElement element = HudElementRegistry.get(selectedId);
        if (element == null) return;

        float[] bounds = element.bounds();
        if (bounds == null || bounds[2] <= 0.0f || bounds[3] <= 0.0f) return;

        float left = HudElement.Anchor.resolveX(state.anchorX, state.marginX, state.offsetX, bounds[2], this.width);
        float top = HudElement.Anchor.resolveY(state.anchorY, state.marginY, state.offsetY, bounds[3], this.height);
        float right = left + bounds[2];
        float bottom = top + bounds[3];

        float[] bestX = {Float.NaN};
        float[] bestY = {Float.NaN};

        considerSnap(left + bounds[2] / 2.0f, this.width / 2.0f, bestX);
        considerSnap(left, 0.0f, bestX);
        considerSnap(right, this.width, bestX);

        considerSnap(top + bounds[3] / 2.0f, this.height / 2.0f, bestY);
        considerSnap(top, 0.0f, bestY);
        considerSnap(bottom, this.height, bestY);

        if (!Float.isNaN(bestX[0])) state.offsetX += bestX[0];
        if (!Float.isNaN(bestY[0])) state.offsetY += bestY[0];
        state.sanitize();
    }

    private static void considerSnap(float value, float target, float[] best) {
        float delta = target - value;
        if (Math.abs(delta) > SNAP_DISTANCE) return;
        if (Float.isNaN(best[0]) || Math.abs(delta) < Math.abs(best[0])) {
            best[0] = delta;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            IrcConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        float step = input.hasShiftDown() ? 10.0f : 1.0f;

        switch (input.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (nudgeSelected(input.key(), step)) return true;
            }
            case GLFW.GLFW_KEY_R -> {
                if (selectedId != null) {
                    HudElementRegistry.reset(selectedId);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_A -> {
                HudElementRegistry.resetAll();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT_BRACKET -> {
                cycleSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                cycleSelection(1);
                return true;
            }
            default -> {
            }
        }

        return super.keyPressed(input);
    }

    private boolean nudgeSelected(int key, float step) {
        if (selectedId == null) return false;

        HudElementRegistry.cancelResetAnimation(selectedId);
        HudElement.State state = HudElementRegistry.state(selectedId);
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> state.offsetX -= step;
            case GLFW.GLFW_KEY_RIGHT -> state.offsetX += step;
            case GLFW.GLFW_KEY_UP -> state.offsetY -= step;
            case GLFW.GLFW_KEY_DOWN -> state.offsetY += step;
            default -> {
                return false;
            }
        }
        state.sanitize();
        IrcConfig.saveDelayed(400);
        return true;
    }

    private void cycleSelection(int direction) {
        List<HudElement> elements = HudElementRegistry.all();
        if (elements.isEmpty()) return;

        int current = 0;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).id().equals(selectedId)) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + direction, elements.size());
        selectedId = elements.get(next).id();
    }
}