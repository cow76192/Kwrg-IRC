package me.cookie.kwrg.gui.component.pane;

import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.BasePaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class TextInfoPaneComponent extends BasePaneComponent {

    private static final int BASE_HEIGHT = 24;

    private final String label;
    private final Supplier<String> valueSupplier;
    private final Supplier<Component> componentSupplier;
    private final boolean copyable;
    private final boolean isHeader;

    private float clickFeedback = 0.0f;
    private BiConsumer<TextInfoPaneComponent, MouseButtonEvent> clickAction = null;

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier) {
        this(label, valueSupplier, null, true, false);
    }

    public TextInfoPaneComponent(String label, Supplier<Component> componentSupplier, boolean copyable) {
        this(label, () -> componentSupplier.get().getString(), componentSupplier, copyable, false);
    }

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier, boolean copyable, boolean isHeader) {
        this(label, valueSupplier, null, copyable, isHeader);
    }

    public TextInfoPaneComponent(String label, Supplier<Component> componentSupplier, Runnable clickAction) {
        this(label, () -> componentSupplier.get().getString(), componentSupplier, true, false);
        this.clickAction = (pane, click) -> {
            if (clickAction != null) clickAction.run();
        };
        this.cursorStyle(CursorStyle.HAND);
    }

    public TextInfoPaneComponent(String label, Supplier<Component> componentSupplier, BiConsumer<TextInfoPaneComponent, MouseButtonEvent> clickAction) {
        this(label, () -> componentSupplier.get().getString(), componentSupplier, true, false);
        this.clickAction = clickAction;
        this.cursorStyle(CursorStyle.HAND);
    }

    public static TextInfoPaneComponent header(String centeredTitle, BiConsumer<TextInfoPaneComponent, MouseButtonEvent> clickAction) {
        TextInfoPaneComponent pane = new TextInfoPaneComponent(centeredTitle, () -> "", false, true);
        pane.clickAction = clickAction;
        pane.cursorStyle(CursorStyle.HAND);
        return pane;
    }

    public static TextInfoPaneComponent header(String centeredTitle) {
        return new TextInfoPaneComponent(centeredTitle, () -> "", null, false, true);
    }

    public TextInfoPaneComponent(String label, Supplier<String> valueSupplier, Supplier<Component> componentSupplier, boolean copyable, boolean isHeader) {
        super(BASE_HEIGHT);
        this.label = label;
        this.valueSupplier = valueSupplier;
        this.componentSupplier = componentSupplier;
        this.copyable = copyable;
        this.isHeader = isHeader;

        this.cursorStyle(copyable ? CursorStyle.HAND : CursorStyle.NONE);
    }

    @Override
    protected int getBorderColorArgb() {
        int borderAlpha = (int) (40 + this.hoverAnimation * 160);
        return ARGB.color(borderAlpha, 144, 164, 174);
    }

    @Override
    protected void renderPaneContent(OwoUIGraphics graphics, Font font, float halfW, float halfH,
                                     float textStartY, float textScale, float delta) {
        if (this.clickFeedback > 0.01f) {
            this.clickFeedback = Math.max(0.0f, this.clickFeedback - delta * 0.25f);
        }

        int stableWidth = Math.max(154, this.width() - 16);

        Component valComp = this.componentSupplier != null ? this.componentSupplier.get() : null;
        String valStr = valComp != null ? valComp.getString() : (this.valueSupplier != null ? this.valueSupplier.get() : "");

        MutableComponent wrapSource = Component.empty();
        wrapSource.append(FontManagerHelper.apply(this.label));
        if (!this.isHeader && !valStr.isEmpty()) {
            wrapSource.append(FontManagerHelper.apply(": "));
            wrapSource.append(FontManagerHelper.apply(valComp != null ? valComp : Component.literal(valStr)));
        }

        List<FormattedCharSequence> wrappedLines = font.split(wrapSource, stableWidth);
        int lineCount = Math.max(1, wrappedLines.size());
        this.targetHeight = BASE_HEIGHT + (lineCount - 1) * font.lineHeight;

        float headerStartY = -halfH + 6.0f;

        if (this.isHeader) {
            for (int i = 0; i < wrappedLines.size(); i++) {
                String lineStr = sequenceToString(wrappedLines.get(i));
                Component styledHeaderLine = FontManagerHelper.apply(lineStr);
                float titleW = font.width(styledHeaderLine) * textScale;
                int curLineY = (int) (headerStartY + i * font.lineHeight);

                graphics.pose().pushMatrix();
                graphics.pose().translate(-titleW / 2.0f, curLineY);
                graphics.pose().scale(textScale, textScale);
                graphics.drawText(styledHeaderLine, 0, 0, 1.0f, 0xFFB0BEC5, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();
            }
        } else {
            if (wrappedLines.size() <= 1) {
                float textH = font.lineHeight * textScale;
                float singleLineY = (int) (-textH / 2.0f);

                Component styledLabel = FontManagerHelper.apply(this.label);
                graphics.pose().pushMatrix();
                graphics.pose().translate(-halfW + 8.0f, singleLineY);
                graphics.pose().scale(textScale, textScale);
                graphics.drawText(styledLabel, 0, 0, 1.0f, 0xFF78909C, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();

                if (valComp != null) {
                    Component styledVal = FontManagerHelper.apply(valComp);
                    float valW = font.width(styledVal) * textScale;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(halfW - valW - 8.0f, singleLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(styledVal, 0, 0, 1.0f, 0xFFFFFFFF, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                } else if (!valStr.isEmpty()) {
                    Component styledVal = FontManagerHelper.apply(valStr);
                    float valW = font.width(styledVal) * textScale;
                    int valTextColor = this.hovered ? 0xFFFFFFFF : 0xFFECEFF1;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(halfW - valW - 8.0f, singleLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(styledVal, 0, 0, 1.0f, valTextColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                }
            } else {
                for (int i = 0; i < wrappedLines.size(); i++) {
                    Component styledLine = FontManagerHelper.toComponent(wrappedLines.get(i));
                    int curLineY = (int) (headerStartY + i * font.lineHeight);

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(-halfW + 8.0f, curLineY);
                    graphics.pose().scale(textScale, textScale);
                    graphics.drawText(styledLine, 0, 0, 1.0f, 0xFFECEFF1, OwoUIGraphics.TextAnchor.TOP_LEFT);
                    graphics.pose().popMatrix();
                }
            }
        }
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
        if (super.onMouseDown(click, doubled)) return true;

        if (click.button() == 0 && this.clickAction != null) {
            this.clickFeedback = 1.0f;
            this.clickAction.accept(this, click);
            return true;
        }

        if (this.copyable && click.button() == 0) {
            this.clickFeedback = 1.0f;
            String val = this.componentSupplier != null ? this.componentSupplier.get().getString() : (this.valueSupplier != null ? this.valueSupplier.get() : "");
            if (!val.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(val);
                ChatNotifier.send("§a[IRC] 已复制 " + this.label + ": §f" + val, NotificationType.SUCCESS);
            }
            return true;
        }
        return false;
    }
}