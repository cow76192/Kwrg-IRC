package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Supplier;

public class TextInfoPaneComponent extends BaseUIComponent {

    private static final int BG_COLOR_RGB = 0x161D26;
    private static final int BASE_HEIGHT = 24;

    private final String label;
    private final Supplier<String> valueSupplier;
    private final Supplier<Component> componentSupplier;
    private final boolean copyable;
    private final boolean isHeader;

    private float hoverAnimation = 0.0f;
    private float clickFeedback = 0.0f;
    private float currentAnimatedHeight = BASE_HEIGHT;

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier) {
        this(label, valueSupplier, null, true, false);
    }

    public TextInfoPaneComponent(String label, Supplier<Component> componentSupplier, boolean copyable) {
        this(label, () -> componentSupplier.get().getString(), componentSupplier, copyable, false);
    }

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier, boolean copyable, boolean isHeader) {
        this(label, valueSupplier, null, copyable, isHeader);
    }

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier, Supplier<Component> componentSupplier, boolean copyable, boolean isHeader) {
        this.label = label;
        this.valueSupplier = valueSupplier;
        this.componentSupplier = componentSupplier;
        this.copyable = copyable;
        this.isHeader = isHeader;

        this.cursorStyle(copyable ? CursorStyle.HAND : CursorStyle.NONE);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));
    }

    public static TextInfoPaneComponent header(String centeredTitle) {
        return new TextInfoPaneComponent(centeredTitle, () -> "", null, false, true);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        float targetHover = (this.hovered && this.copyable) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        if (this.clickFeedback > 0.01f) {
            this.clickFeedback = Math.max(0.0f, this.clickFeedback - delta * 0.25f);
        }

        var font = Minecraft.getInstance().font;
        float textScale = 0.85f;
        int stableWidth = Math.max(154, this.width() - 16);

        Component valComp = this.componentSupplier != null ? this.componentSupplier.get() : null;
        String valStr = valComp != null ? valComp.getString() : (this.valueSupplier != null ? this.valueSupplier.get() : "");

        String fullDisplayText = this.isHeader ? this.label : (this.label + (valStr.isEmpty() ? "" : ": " + valStr));
        List<FormattedCharSequence> wrappedLines = font.split(Component.literal(fullDisplayText), stableWidth);
        int lineCount = Math.max(1, wrappedLines.size());
        int targetHeight = BASE_HEIGHT + (lineCount - 1) * font.lineHeight;

        // 平滑高度自适应
        this.currentAnimatedHeight = Mth.lerp(delta * 0.35f, this.currentAnimatedHeight, (float) targetHeight);
        int renderH = Math.max(BASE_HEIGHT, (int) Math.round(this.currentAnimatedHeight));
        this.height = renderH;

        int x = this.x();
        int y = this.y();
        int width = this.width();

        Color paneBg = Color.ofRgb(BG_COLOR_RGB);
        int borderAlpha = (int) (40 + this.hoverAnimation * 160);
        Color grayBorderColor = Color.ofArgb(ARGB.color(borderAlpha, 144, 164, 174));

        float cx = x + width / 2.0f;
        float cy = y + renderH / 2.0f;
        float halfW = width / 2.0f;
        float halfH = renderH / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);

        // ① 绘制底盘
        RenderUtil.drawRoundedPanel(graphics, (int) -halfW, (int) -halfH, width, renderH, 6.0f, paneBg, grayBorderColor);

        // ② 纯净单层文字渲染 (彻底消除闪烁感)
        float textStartY = -halfH + 6.0f;

        if (this.isHeader) {
            for (int i = 0; i < wrappedLines.size(); i++) {
                String lineStr = sequenceToString(wrappedLines.get(i));
                float titleW = font.width(lineStr) * textScale;
                int curLineY = (int) (textStartY + i * font.lineHeight);

                graphics.pose().pushMatrix();
                graphics.pose().translate(-titleW / 2.0f, curLineY);
                graphics.pose().scale(textScale, textScale);
                graphics.drawText(Component.literal(lineStr), 0, 0, 1.0f, 0xFFB0BEC5, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();
            }
        } else {
            if (wrappedLines.size() <= 1) {
                float textH = font.lineHeight * textScale;
                float singleLineY = (int) (-textH / 2.0f);

                graphics.pose().pushMatrix();
                graphics.pose().translate(-halfW + 8.0f, singleLineY);
                graphics.pose().scale(textScale, textScale);
                graphics.drawText(Component.literal(this.label), 0, 0, 1.0f, 0xFF78909C, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();

                if (valComp != null) {
                    float valW = font.width(valComp) * textScale;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(halfW - valW - 8.0f, singleLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(valComp, 0, 0, 1.0f, 0xFFFFFFFF, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                } else if (!valStr.isEmpty()) {
                    float valW = font.width(valStr) * textScale;
                    int valTextColor = this.hovered ? 0xFFFFFFFF : 0xFFECEFF1;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(halfW - valW - 8.0f, singleLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(Component.literal(valStr), 0, 0, 1.0f, valTextColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                }
            } else {
                for (int i = 0; i < wrappedLines.size(); i++) {
                    String lineStr = sequenceToString(wrappedLines.get(i));
                    int curLineY = (int) (textStartY + i * font.lineHeight);

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(-halfW + 8.0f, curLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(Component.literal(lineStr), 0, 0, 1.0f, 0xFFECEFF1, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                }
            }
        }

        graphics.pose().popMatrix();
    }

    private static String sequenceToString(FormattedCharSequence sequence) {
        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.copyable && click.button() == 0) {
            this.clickFeedback = 1.0f;
            String val = this.componentSupplier != null ? this.componentSupplier.get().getString() : (this.valueSupplier != null ? this.valueSupplier.get() : "");
            if (!val.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(val);
                ChatNotifier.send("§a[IRC] 已复制 " + this.label + ": §f" + val);
            }
            return true;
        }
        return super.onMouseDown(click, doubled);
    }
}