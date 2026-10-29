package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class SliderPaneComponent extends BaseGuiComponent {

    private static final int COMPONENT_HEIGHT = 36;
    private static final int PADDING_X = 10;
    private static final float CARD_RADIUS = 7.0f;

    // 动画速率与参数
    private static final float HOVER_ANIM_SPEED = 6.5f;
    private static final float ACTIVE_ANIM_SPEED = 8.5f;
    private static final long CLICK_GLIDE_DURATION_MS = 180L;
    private static final double DRAG_THRESHOLD_PX = 1.0;

    private String label = "";
    private float min = 0.0f;
    private float max = 1.0f;
    private float defaultValue = 0.0f;
    private float step = 0.0f;
    private Supplier<Float> getter = () -> 0.0f;
    private Consumer<Float> setter = val -> {};
    private Function<Float, String> formatter = val -> String.format("%.2f", val);

    private long saveDelayMs = 400L;
    private Runnable customDelayedSave = null;

    // 拖拽与点击状态
    private boolean dragging = false;
    private boolean isDraggingMotion = false;
    private double mouseDownX = 0.0;

    // 进度与过渡动画状态
    private float visualProgress = Float.NaN;
    private float clickAnimFrom = 0.0f;
    private float clickAnimTo = 0.0f;
    private long clickAnimStartMs = 0L;
    private boolean clickAnimating = false;

    private float hoverProgress = 0.0f;
    private float activeProgress = 0.0f;
    private long lastFrameTimeMs = -1L;
    private int lastMouseX = 0;

    public SliderPaneComponent() {
        this.sizing(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT));
    }

    public SliderPaneComponent(String label) {
        this();
        this.label = label != null ? label : "";
    }

    // ==================== 链式构造 API ====================

    public SliderPaneComponent label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public SliderPaneComponent range(float min, float max) {
        this.min = min;
        this.max = max;
        this.defaultValue = Mth.clamp(this.defaultValue, min, max);
        return this;
    }

    public SliderPaneComponent defaultValue(float defaultValue) {
        this.defaultValue = Mth.clamp(defaultValue, this.min, this.max);
        return this;
    }

    public SliderPaneComponent step(float step) {
        this.step = Math.max(0.0f, step);
        return this;
    }

    public SliderPaneComponent getter(Supplier<Float> getter) {
        if (getter != null) this.getter = getter;
        return this;
    }

    public SliderPaneComponent setter(Consumer<Float> setter) {
        if (setter != null) this.setter = setter;
        return this;
    }

    public SliderPaneComponent formatter(Function<Float, String> formatter) {
        if (formatter != null) this.formatter = formatter;
        return this;
    }

    public SliderPaneComponent saveDelayMs(long delayMs) {
        this.saveDelayMs = Math.max(50L, delayMs);
        return this;
    }

    public SliderPaneComponent onSaveDelayed(Runnable callback) {
        this.customDelayedSave = callback;
        return this;
    }

    public static SliderPaneComponent ofInt(
            String label,
            int min,
            int max,
            int defaultValue,
            Supplier<Integer> getter,
            Consumer<Integer> setter,
            IntFunction<String> formatter
    ) {
        return new SliderPaneComponent(label)
                .range(min, max)
                .defaultValue(defaultValue)
                .step(1.0f)
                .getter(() -> {
                    Integer v = getter.get();
                    return v != null ? v.floatValue() : (float) defaultValue;
                })
                .setter(val -> setter.accept(Math.round(val)))
                .formatter(val -> formatter.apply(Math.round(val)));
    }

    public static SliderPaneComponent ofFloat(
            String label,
            float min,
            float max,
            float defaultValue,
            float step,
            Supplier<Float> getter,
            Consumer<Float> setter,
            Function<Float, String> formatter
    ) {
        return new SliderPaneComponent(label)
                .range(min, max)
                .defaultValue(defaultValue)
                .step(step)
                .getter(getter)
                .setter(setter)
                .formatter(formatter);
    }

    public void resetToDefault() {
        float defVal = quantize(defaultValue);
        if (Float.isNaN(visualProgress)) {
            visualProgress = normalize(currentActualValue());
        }
        clickAnimFrom = visualProgress;
        clickAnimTo = normalize(defVal);
        clickAnimStartMs = System.currentTimeMillis();
        clickAnimating = true;

        setter.accept(defVal);
        triggerDelayedSave();
    }

    private void triggerDelayedSave() {
        if (customDelayedSave != null) {
            customDelayedSave.run();
        } else {
            IrcConfig.saveDelayed(saveDelayMs);
        }
    }

    private static boolean isGlfwLeftMouseDown() {
        var window = Minecraft.getInstance().getWindow();
        if (window == null) return false;
        return GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 200;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return COMPONENT_HEIGHT;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.lastMouseX = mouseX;

        long now = System.currentTimeMillis();
        float dt = lastFrameTimeMs < 0L ? 0.016f : Math.min(0.1f, (now - lastFrameTimeMs) / 1000.0f);
        lastFrameTimeMs = now;

        float trackX = this.x + PADDING_X;
        float trackW = Math.max(10.0f, this.width - PADDING_X * 2.0f);

        if (dragging) {
            if (isGlfwLeftMouseDown()) {
                if (!isDraggingMotion && Math.abs(mouseX - mouseDownX) >= DRAG_THRESHOLD_PX) {
                    isDraggingMotion = true;
                }
                if (isDraggingMotion) {
                    applyDragAt(mouseX, trackX, trackW);
                }
            } else {
                dragging = false;
                isDraggingMotion = false;
                triggerDelayedSave();
            }
        }

        float actualValue = currentActualValue();
        float targetProgress = normalize(actualValue);
        if (Float.isNaN(visualProgress)) {
            visualProgress = targetProgress;
        }

        if (clickAnimating && !isDraggingMotion) {
            // 直接点击目标位置
            float raw = Mth.clamp((now - clickAnimStartMs) / (float) CLICK_GLIDE_DURATION_MS, 0.0f, 1.0f);
            float eased = AnimationUtil.easeOutCubic(raw);
            visualProgress = AnimationUtil.lerp(eased, clickAnimFrom, clickAnimTo);
            if (raw >= 1.0f) {
                visualProgress = clickAnimTo;
                clickAnimating = false;
            }
        } else {
            // 长按拖动
            float followFactor = 1.0f - (float) Math.pow(0.00005f, dt);
            visualProgress = AnimationUtil.lerp(followFactor, visualProgress, targetProgress);
        }

        boolean hovered = isMouseInside(mouseX, mouseY);

        // 悬停与激活态进度更新
        float targetHover = (hovered || dragging) ? 1.0f : 0.0f;
        if (hoverProgress < targetHover) {
            hoverProgress = Math.min(1.0f, hoverProgress + dt * HOVER_ANIM_SPEED);
        } else if (hoverProgress > targetHover) {
            hoverProgress = Math.max(0.0f, hoverProgress - dt * HOVER_ANIM_SPEED);
        }

        float targetActive = dragging ? 1.0f : 0.0f;
        if (activeProgress < targetActive) {
            activeProgress = Math.min(1.0f, activeProgress + dt * ACTIVE_ANIM_SPEED);
        } else if (activeProgress > targetActive) {
            activeProgress = Math.max(0.0f, activeProgress - dt * ACTIVE_ANIM_SPEED);
        }

        float hoverEase = targetHover >= 1.0f
                ? AnimationUtil.easeOutCubic(hoverProgress)
                : (1.0f - AnimationUtil.easeOutCubic(1.0f - hoverProgress));
        float activeEase = targetActive >= 1.0f
                ? AnimationUtil.easeOutCubic(activeProgress)
                : (1.0f - AnimationUtil.easeOutCubic(1.0f - activeProgress));
        float highlightEase = Math.max(hoverEase * 0.55f, activeEase);

        float panelAlpha = GuiThemeService.opacity();

        // 绘制卡片背景与边框
        int borderRgb = AnimationUtil.lerpRgb(GuiThemeService.cardBorder(), GuiThemeService.accent(), highlightEase * 0.65f);
        RenderUtil.drawSubpixelRoundedPanel(
                graphics,
                this.x, this.y, this.width, this.height,
                CARD_RADIUS, 1.0f,
                AnimationUtil.withAlpha(GuiThemeService.cardBg(), Math.round(panelAlpha * 225.0f)),
                AnimationUtil.withAlpha(borderRgb, Math.round(panelAlpha * 165.0f))
        );

        // 绘制左侧标题与右侧数值
        int labelRgb = AnimationUtil.lerpRgb(GuiThemeService.textBody(), textStrong(), hoverEase);
        RenderUtil.drawFluidSubpixelText(
                graphics,
                Component.literal(label),
                trackX, this.y + 6.0f,
                AnimationUtil.argb(labelRgb, Math.round(panelAlpha * 245.0f))
        );

        String valueText = formatter.apply(actualValue);
        float valueX = trackX + trackW - Minecraft.getInstance().font.width(valueText);
        int valueRgb = AnimationUtil.lerpRgb(GuiThemeService.textLabel(), GuiThemeService.accent(), highlightEase);
        RenderUtil.drawFluidSubpixelText(
                graphics,
                Component.literal(valueText),
                valueX, this.y + 6.0f,
                AnimationUtil.argb(valueRgb, Math.round(panelAlpha * 255.0f))
        );

        // 绘制轨道底槽
        float trackY = this.y + 23.0f;
        float trackH = AnimationUtil.lerp(activeEase, 4.5f, 5.5f);
        int trackBorderRgb = AnimationUtil.lerpRgb(paneBorder(), GuiThemeService.accentSoft(), highlightEase * 0.75f);

        RenderUtil.drawSubpixelRoundedPanel(
                graphics,
                trackX, trackY, trackW, trackH, trackH * 0.5f,
                AnimationUtil.withAlpha(circleBg(), Math.round(panelAlpha * 215.0f)),
                AnimationUtil.withAlpha(trackBorderRgb, Math.round(panelAlpha * 95.0f))
        );

        // 绘制轨道高亮填充条
        float clampedVisual = Mth.clamp(visualProgress, 0.0f, 1.0f);
        float fillW = Math.max(trackH, trackW * clampedVisual);
        int hoverFillRgb = AnimationUtil.lerpRgb(rowBg(), GuiThemeService.accent(), hoverEase * 0.45f);
        int fillRgb = AnimationUtil.lerpRgb(hoverFillRgb, GuiThemeService.accent(), activeEase);

        if (clampedVisual > 0.002f) {
            RenderUtil.drawSubpixelRoundedPanel(
                    graphics,
                    trackX, trackY, fillW, trackH, trackH * 0.5f,
                    AnimationUtil.withAlpha(fillRgb, Math.round(panelAlpha * 240.0f)),
                    null
            );
        }

        // 绘制原点
        float baseKnobSize = AnimationUtil.lerp(hoverEase, 7.0f, 8.8f);
        float knobSize = AnimationUtil.lerp(activeEase, baseKnobSize, 10.8f);

        float centerX = Mth.clamp(trackX + trackW * clampedVisual, trackX + knobSize * 0.5f, trackX + trackW - knobSize * 0.5f);
        float centerY = trackY + trackH * 0.5f;

        if (hoverEase > 0.01f || activeEase > 0.01f) {
            float haloExpand = AnimationUtil.lerp(activeEase, 3.5f, 6.5f);
            float haloSize = knobSize + haloExpand;
            int haloAlpha = Math.round(panelAlpha * AnimationUtil.lerp(activeEase, hoverEase * 42.0f, 88.0f));
            if (haloAlpha > 2) {
                RenderUtil.drawSubpixelRoundedPanel(
                        graphics,
                        centerX - haloSize * 0.5f,
                        centerY - haloSize * 0.5f,
                        haloSize, haloSize, haloSize * 0.5f,
                        AnimationUtil.withAlpha(GuiThemeService.accent(), haloAlpha),
                        null
                );
            }
        }

        int knobIdleToHoverRgb = AnimationUtil.lerpRgb(toggleOn(), dotHover(), hoverEase);
        int knobFillRgb = AnimationUtil.lerpRgb(knobIdleToHoverRgb, textStrong(), activeEase);
        int knobBorderRgb = AnimationUtil.lerpRgb(rowBorder(), GuiThemeService.accent(), highlightEase);

        RenderUtil.drawSubpixelRoundedPanel(
                graphics,
                centerX - knobSize * 0.5f,
                centerY - knobSize * 0.5f,
                knobSize, knobSize, knobSize * 0.5f,
                AnimationUtil.lerp(activeEase, 1.0f, 1.5f),
                AnimationUtil.withAlpha(knobFillRgb, Math.round(panelAlpha * 255.0f)),
                AnimationUtil.withAlpha(knobBorderRgb, Math.round(panelAlpha * 235.0f))
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        double globalX = resolveGlobalMouseX(click.x());
        float trackX = this.x + PADDING_X;
        float trackW = Math.max(10.0f, this.width - PADDING_X * 2.0f);

        if (click.button() == 0) {
            this.dragging = true;
            this.isDraggingMotion = false;
            this.mouseDownX = globalX;
            applyClickAt(globalX, trackX, trackW);
            return true;
        } else if (click.button() == 1) {
            // 右键平滑重置为默认值
            resetToDefault();
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.dragging && click.button() == 0) {
            double globalX = resolveGlobalMouseX(click.x());
            if (!this.isDraggingMotion && Math.abs(globalX - this.mouseDownX) >= DRAG_THRESHOLD_PX) {
                this.isDraggingMotion = true;
            }
            float trackX = this.x + PADDING_X;
            float trackW = Math.max(10.0f, this.width - PADDING_X * 2.0f);
            applyDragAt(globalX, trackX, trackW);
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (this.dragging && click.button() == 0) {
            this.dragging = false;
            this.isDraggingMotion = false;
            triggerDelayedSave();
            return true;
        }
        return super.onMouseUp(click);
    }

    private void applyClickAt(double mouseX, float trackX, float trackW) {
        float ratio = Mth.clamp((float) ((mouseX - trackX) / trackW), 0.0f, 1.0f);
        float newValue = quantize(min + ratio * (max - min));
        float newProgress = normalize(newValue);

        if (Float.isNaN(visualProgress)) {
            visualProgress = normalize(currentActualValue());
        }

        clickAnimFrom = visualProgress;
        clickAnimTo = newProgress;
        clickAnimStartMs = System.currentTimeMillis();
        clickAnimating = true;

        setter.accept(newValue);
        triggerDelayedSave();
    }

    private void applyDragAt(double mouseX, float trackX, float trackW) {
        float ratio = Mth.clamp((float) ((mouseX - trackX) / trackW), 0.0f, 1.0f);
        float newValue = quantize(min + ratio * (max - min));
        clickAnimating = false;

        if (Math.abs(newValue - currentActualValue()) > 1e-5f) {
            setter.accept(newValue);
            triggerDelayedSave();
        }
    }

    private float normalize(float val) {
        if (max <= min) return 0.0f;
        return Mth.clamp((val - min) / (max - min), 0.0f, 1.0f);
    }

    private float quantize(float rawValue) {
        float clamped = Mth.clamp(rawValue, min, max);
        if (step > 0.0f) {
            clamped = min + Math.round((clamped - min) / step) * step;
            clamped = Mth.clamp(clamped, min, max);
        }
        return clamped;
    }

    private float currentActualValue() {
        Float v = getter.get();
        return v != null && Float.isFinite(v) ? Mth.clamp(v, min, max) : defaultValue;
    }

    private boolean isMouseInside(double mx, double my) {
        return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
    }

    private double resolveGlobalMouseX(double eventX) {
        if (this.lastMouseX > 0) return this.lastMouseX;
        return eventX <= this.width ? this.x + eventX : eventX;
    }
}