package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.manager.GuiColorManager;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PanelItemComponent extends BaseGuiComponent {

    public enum ItemType {
        TRIGGER,
        TOGGLE
    }

    public static final int BASE_HEIGHT = 28;
    public static final int ITEM_SPACING = 4;
    public static final int FULL_FOOTPRINT = BASE_HEIGHT + ITEM_SPACING; // 32px
    public static final int PREVIEW_TEX_SIZE = 64;

    private final String title;
    private final ItemType type;
    private final BiConsumer<PanelItemComponent, MouseButtonEvent> leftClickAction;
    private final Consumer<PanelItemComponent> rightClickAction;
    private final BooleanSupplier activeSupplier;
    private boolean localToggled = false;

    private float hoverAnimation = 0.0f;
    private float activeAnimation = 0.0f;
    private float clickFeedback = 0.0f;

    private boolean playSpawnAnimation = false;
    private float spawnProgress = 1.0f;

    private boolean isDismissing = false;
    private float dismissProgress = 0.0f;
    private Runnable onDismissComplete = null;

    private Identifier previewTexture = null;

    private java.util.function.Supplier<Component> styledTitleSupplier = null;

    private java.util.function.Supplier<Identifier> previewSupplier = null;
    private int previewTexW = PREVIEW_TEX_SIZE;
    private int previewTexH = PREVIEW_TEX_SIZE;

    public PanelItemComponent(String title, Runnable leftClickAction, Consumer<PanelItemComponent> rightClickAction) {
        this(title, ItemType.TRIGGER, (item, click) -> {
            if (leftClickAction != null) leftClickAction.run();
        }, rightClickAction, () -> false);
    }

    public PanelItemComponent(String title, ItemType type, Runnable leftClickAction, Consumer<PanelItemComponent> rightClickAction, BooleanSupplier activeSupplier) {
        this(title, type, (item, click) -> {
            if (leftClickAction != null) leftClickAction.run();
        }, rightClickAction, activeSupplier);
    }

    public PanelItemComponent(String title, ItemType type, BiConsumer<PanelItemComponent, MouseButtonEvent> leftClickAction, Consumer<PanelItemComponent> rightClickAction) {
        this(title, type, leftClickAction, rightClickAction, () -> false);
    }

    public PanelItemComponent(String title, Runnable leftClickAction) {
        this(title, ItemType.TRIGGER, (item, click) -> {
            if (leftClickAction != null) leftClickAction.run();
        }, null, () -> false);
    }

    public PanelItemComponent(String title) {
        this(title, ItemType.TRIGGER, (item, click) -> {}, null, () -> false);
    }

    public PanelItemComponent(String title, ItemType type, Runnable leftClickAction, Consumer<PanelItemComponent> rightClickAction) {
        this(title, type, (item, click) -> {
            if (leftClickAction != null) leftClickAction.run();
        }, rightClickAction, () -> false);
    }

    public PanelItemComponent(String title, ItemType type, BiConsumer<PanelItemComponent, MouseButtonEvent> leftClickAction, Consumer<PanelItemComponent> rightClickAction, BooleanSupplier activeSupplier) {
        this.title = title;
        this.type = type;
        this.leftClickAction = leftClickAction;
        this.rightClickAction = rightClickAction;
        this.activeSupplier = activeSupplier;

        this.cursorStyle(CursorStyle.HAND);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));

        if (this.isItemActive()) {
            this.activeAnimation = 1.0f;
        }
    }

    public PanelItemComponent withSpawnAnimation() {
        this.playSpawnAnimation = true;
        this.spawnProgress = 0.0f;
        return this;
    }

    /** Static texture (square, old behaviour). */
    public PanelItemComponent withPreview(Identifier previewTexture) {
        return withPreview(() -> previewTexture, PREVIEW_TEX_SIZE, PREVIEW_TEX_SIZE);
    }

    /** Lazy texture: the supplier may return null while loading. texW/texH decide the aspect ratio. */
    public PanelItemComponent withPreview(java.util.function.Supplier<Identifier> supplier, int texW, int texH) {
        this.previewSupplier = supplier;
        this.previewTexW = texW;
        this.previewTexH = texH;
        return this;
    }

    public PanelItemComponent withStyledTitle(java.util.function.Supplier<Component> supplier) {
        this.styledTitleSupplier = supplier;
        return this;
    }

    public void dismiss(Runnable onComplete) {
        if (this.isDismissing) return;
        this.isDismissing = true;
        this.dismissProgress = 0.0f;
        this.onDismissComplete = onComplete;
    }

    public int getLayoutFootprint() {
        if (this.isDismissing) {
            float dismissEase = AnimationUtil.easeOutCubic(this.dismissProgress);
            return Math.round((1.0f - dismissEase) * FULL_FOOTPRINT);
        }
        if (this.playSpawnAnimation && this.spawnProgress < 1.0f) {
            float spawnEase = AnimationUtil.easeOutCubic(this.spawnProgress);
            return Math.round(spawnEase * FULL_FOOTPRINT);
        }
        return FULL_FOOTPRINT;
    }

    public boolean isToggled() {
        return this.isItemActive();
    }

    public void setToggled(boolean toggled) {
        this.localToggled = toggled;
    }

    public boolean isItemActive() {
        return this.activeSupplier != null ? this.activeSupplier.getAsBoolean() : this.localToggled;
    }

    public ItemType getType() {
        return this.type;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.playSpawnAnimation && this.spawnProgress < 1.0f) {
            this.spawnProgress = Math.min(1.0f, this.spawnProgress + delta * 0.30f);
        }
        float spawnEase = this.playSpawnAnimation ? AnimationUtil.easeOutCubic(this.spawnProgress) : 1.0f;

        if (this.isDismissing) {
            this.dismissProgress = Math.min(1.0f, this.dismissProgress + delta * 0.32f);
            if (this.dismissProgress >= 1.0f && this.onDismissComplete != null) {
                Runnable callback = this.onDismissComplete;
                this.onDismissComplete = null;
                callback.run();
                return;
            }
        }
        float dismissEase = this.isDismissing ? AnimationUtil.easeOutCubic(this.dismissProgress) : 0.0f;

        float totalAlpha = (1.0f - dismissEase) * spawnEase;
        int footprint = this.getLayoutFootprint();
        if (totalAlpha <= 0.005f || footprint <= 1) return;

        float targetHover = this.hovered ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        float targetActive = (this.type == ItemType.TOGGLE && this.isItemActive()) ? 1.0f : 0.0f;
        this.activeAnimation = Mth.clamp(this.activeAnimation + (targetActive - this.activeAnimation) * delta * 0.30f, 0.0f, 1.0f);

        if (this.clickFeedback > 0.01f) {
            this.clickFeedback = Math.max(0.0f, this.clickFeedback - delta * 0.25f);
        }

        int x = this.x();
        int y = this.y();
        int width = this.width();
        int height = BASE_HEIGHT;

        float bgAlphaFactor = Mth.lerp(this.activeAnimation, this.hoverAnimation * 0.22f, 0.85f + this.hoverAnimation * 0.15f);
        int currentBgAlpha = (int) (255 * bgAlphaFactor * totalAlpha);

        float borderAlphaFactor = Mth.lerp(this.activeAnimation, this.hoverAnimation * 0.45f, 1.0f);
        int currentBorderAlpha = (int) (255 * borderAlphaFactor * totalAlpha);

        Color itemBg = Color.ofArgb(GuiColorManager.withAlpha(this.rowBg(), currentBgAlpha));

        int borderFrom = this.rowBg();
        int borderTo = this.rowBorder();
        int borderR = (int) Mth.lerp(this.activeAnimation, (borderFrom >> 16) & 0xFF, (borderTo >> 16) & 0xFF);
        int borderG = (int) Mth.lerp(this.activeAnimation, (borderFrom >> 8) & 0xFF, (borderTo >> 8) & 0xFF);
        int borderB = (int) Mth.lerp(this.activeAnimation, borderFrom & 0xFF, borderTo & 0xFF);
        Color itemBorder = Color.ofArgb(ARGB.color(currentBorderAlpha, borderR, borderG, borderB));

        float curScale = (0.80f + 0.20f * spawnEase) * (1.0f - dismissEase * 0.20f);
        float cx = x + width / 2.0f;
        float cy = y + Math.min(BASE_HEIGHT, footprint) / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(curScale, curScale);

        int halfW = width / 2;
        int halfH = BASE_HEIGHT / 2;
        if (currentBgAlpha > 1 || currentBorderAlpha > 1) {
            RenderUtil.drawRoundedPanel(graphics, -halfW, -halfH, width, BASE_HEIGHT, 6.0f, itemBg, itemBorder);
        }

        var font = Minecraft.getInstance().font;
        float textScale = 0.88f;
        float textH = font.lineHeight * textScale;

        float emphasis = Math.max(this.activeAnimation, this.hoverAnimation);
        int textFrom = this.rowText();
        int textTo = this.textStrong();
        int textR = (int) Mth.lerp(emphasis, (textFrom >> 16) & 0xFF, (textTo >> 16) & 0xFF);
        int textG = (int) Mth.lerp(emphasis, (textFrom >> 8) & 0xFF, (textTo >> 8) & 0xFF);
        int textB = (int) Mth.lerp(emphasis, textFrom & 0xFF, textTo & 0xFF);
        int textColor = ARGB.color((int) (255 * totalAlpha), textR, textG, textB);

        float textOffsetX = 10.0f;
        if (this.previewSupplier != null) {
            float ph = BASE_HEIGHT - 8;
            float pw = ph * this.previewTexW / (float) this.previewTexH; // keeps 5:8
            float px = -halfW + 4;
            float py = -ph / 2.0f;

            Identifier preview = this.previewSupplier.get();
            if (preview != null) {
                RenderUtil.drawTexture(graphics, preview, px, py, pw, ph, this.previewTexW, this.previewTexH);
            }
            textOffsetX = 4 + pw + 6;
        }
        Component displayTitle = this.styledTitleSupplier != null
                ? this.styledTitleSupplier.get()
                : Component.literal(this.title).setStyle(FontManagerHelper.getGuiStyle());
        graphics.drawText(displayTitle, -halfW + textOffsetX, -textH / 2.0f, textScale, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);

        int dotX = halfW - 12;
        int dotY = 0;

        if (this.type == ItemType.TOGGLE) {
            int mutedRgb = this.toggleOff();
            int activeRgb = this.toggleOn();
            int dotR = (int) Mth.lerp(this.activeAnimation, (mutedRgb >> 16) & 0xFF, (activeRgb >> 16) & 0xFF);
            int dotG = (int) Mth.lerp(this.activeAnimation, (mutedRgb >> 8) & 0xFF, (activeRgb >> 8) & 0xFF);
            int dotB = (int) Mth.lerp(this.activeAnimation, mutedRgb & 0xFF, activeRgb & 0xFF);
            Color dotColor = Color.ofArgb(ARGB.color((int) (255 * totalAlpha), dotR, dotG, dotB));
            Color dotFade = AnimationUtil.withAlpha(ARGB.color(255, dotR, dotG, dotB), 0);
            RenderUtil.drawAACircle(graphics, dotX, dotY, 2.5, dotColor, dotFade);
        } else if (this.hoverAnimation > 0.05f) {
            Color dotColor = Color.ofArgb(GuiColorManager.withAlpha(this.dotHover(), (int) (160 * this.hoverAnimation * totalAlpha)));
            Color dotFade = Color.ofArgb(GuiColorManager.withAlpha(this.dotHover(), 0));
            RenderUtil.drawAACircle(graphics, dotX, dotY, 2.0, dotColor, dotFade);
        }

        graphics.pose().popMatrix();
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.spawnProgress < 0.7f || this.isDismissing) return false;

        if (click.button() == 0) {
            this.clickFeedback = 1.0f;
            if (this.type == ItemType.TOGGLE) {
                this.localToggled = !this.localToggled;
            }

//            System.out.println("[KWRG Action] >>> 左键点击: " + this.title);
            if (this.leftClickAction != null) {
                try {
                    this.leftClickAction.accept(this, click);
                } catch (Throwable t) {
//                    System.err.println("[KWRG Action] 执行出错: " + this.title);
                    t.printStackTrace();
                }
            }
            return true;
        } else if (click.button() == 1) {
//            System.out.println("[KWRG UI] >>> 右键打开三级配置菜单: " + this.title);
            if (this.rightClickAction != null) {
                try {
                    this.rightClickAction.accept(this);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    public String getTitle() {
        return this.title;
    }
}
