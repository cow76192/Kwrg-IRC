package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

public abstract class BasePaneComponent extends BaseUIComponent {

    public static final float DEFAULT_TEXT_SCALE = 0.85f;

    protected final int baseHeight;
    protected float hoverAnimation = 0.0f;
    protected float currentAnimatedHeight;
    protected int targetHeight;

    protected boolean focused = false;
    protected Runnable onRightClick = null;

    public BasePaneComponent(int baseHeight) {
        this.baseHeight = baseHeight;
        this.currentAnimatedHeight = baseHeight;
        this.targetHeight = baseHeight;

        this.sizing(Sizing.fill(100), Sizing.fixed(baseHeight));
        this.margins(Insets.vertical(2));
    }

    public BasePaneComponent onRightClick(Runnable onRightClick) {
        this.onRightClick = onRightClick;
        return this;
    }

    /**
     * 获取边框颜色 (ARGB)，子类可覆写以定制高亮色
     */
    protected int getBorderColorArgb() {
        return AnimationUtil.argb(this.borderBaseRgb(), (int) (40 + this.hoverAnimation * borderHoverSpan()));
    }

    protected int borderBaseRgb() {
        return this.focused ? BaseGuiComponent.paneBorderFocused() : BaseGuiComponent.cardBorder();
    }

    protected float borderHoverSpan() {
        return 180f;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;

        // 悬停动画计算
        float targetHover = (this.hovered || this.focused) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        // 高度动画计算
        this.currentAnimatedHeight = Mth.lerp(delta * 0.35f, this.currentAnimatedHeight, (float) this.targetHeight);
        int renderH = Math.max(this.baseHeight, Math.round(this.currentAnimatedHeight));
        this.height = renderH;

        int x = this.x();
        int y = this.y();
        int width = this.width();

        Color paneBg = Color.ofRgb(BaseGuiComponent.cardBg());
        Color borderColor = Color.ofArgb(getBorderColorArgb());

        float halfW = width / 2.0f;
        float halfH = renderH / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + halfW, y + halfH);

        // 绘制通用圆角背景与边框
        RenderUtil.drawRoundedPanel(graphics, (int) -halfW, (int) -halfH, width, renderH, 6.0f, paneBg, borderColor);

        // 留出内边距基准位置
        float textStartY = -halfH + 7.0f;
        renderPaneContent(graphics, font, halfW, halfH, textStartY, DEFAULT_TEXT_SCALE, delta);

        graphics.pose().popMatrix();
    }

    /**
     * 子类绘制具体内容，坐标原点已位移至面板中心 (0, 0)
     */
    protected abstract void renderPaneContent(OwoUIGraphics graphics, Font font, float halfW, float halfH,
                                              float textStartY, float textScale, float delta);

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 1 && this.onRightClick != null) {
            this.onRightClick.run();
            return true;
        }
        return super.onMouseDown(click, doubled);
    }
}