package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class TextInputPaneComponent extends BaseUIComponent {

    private static final int BG_COLOR_RGB = 0x161D26;
    private static final int BASE_HEIGHT = 26;

    public enum Mode {
        NORMAL,
        TRIPLE_SLOT
    }

    private final Mode mode;

    private final String placeholder;
    private final Component styledPlaceholder;
    private final String rightHint;
    private final Component styledRightHint;
    private float cachedRightHintWidth = 0.0f;

    private final Consumer<String> onEnterSubmit;
    private Consumer<String> onTextChanged = null;
    private Runnable onRightClick = null;
    private String text = "";

    private int cursorPos = 0;
    private boolean selectAll = false;
    private long lastActionTime = 0;

    private boolean readOnly = false;
    private Runnable onReadOnlyClick = null;

    private boolean focused = false;
    private float hoverAnimation = 0.0f;
    private float currentAnimatedHeight = BASE_HEIGHT;
    private float rightHintAlpha = 0.0f;

    private boolean dirty = true;
    private int lastLayoutWidth = -1;
    private final List<LineInfo> cachedLines = new ArrayList<>();
    private int cachedTargetHeight = BASE_HEIGHT;

    private final String leftLabel;
    private final Component styledLeftLabel;

    private final int slotCount;
    private final String[] slotValues;
    private final String[] slotSuffixes;
    private final Component[] styledSlotSuffixes;
    private final float[] slotFocusProgress;

    private int activeSlot = -1;
    private Consumer<String[]> onTripleChanged = null;
    private Consumer<String[]> onTripleSubmit = null;
    private int[][] slotBounds = null;
    private boolean isDraggingSlot = false;
    private double dragAccumulator = 0.0;
    private static final double DRAG_SENSITIVITY = 1.2;

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit) {
        this(placeholder, onEnterSubmit, "");
    }

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit, String rightHint) {
        this.mode = Mode.NORMAL;
        this.placeholder = placeholder != null ? placeholder : "";
        this.styledPlaceholder = FontManagerHelper.apply(this.placeholder);

        this.rightHint = rightHint != null ? rightHint : "";
        this.styledRightHint = FontManagerHelper.apply(this.rightHint);

        this.leftLabel = "";
        this.styledLeftLabel = Component.empty();

        this.onEnterSubmit = onEnterSubmit;

        this.slotCount = 0;
        this.slotValues = new String[0];
        this.slotSuffixes = new String[0];
        this.styledSlotSuffixes = new Component[0];
        this.slotFocusProgress = new float[0];

        this.cursorStyle(CursorStyle.TEXT);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));
    }

    public TextInputPaneComponent(String leftLabel, String[] initialValues, String[] suffixes,
                                  Consumer<String[]> onTripleSubmit, Consumer<String[]> onTripleChanged) {
        this.mode = Mode.TRIPLE_SLOT;
        this.placeholder = "";
        this.styledPlaceholder = Component.empty();
        this.rightHint = "";
        this.styledRightHint = Component.empty();
        this.onEnterSubmit = null;

        this.leftLabel = leftLabel != null ? leftLabel : "";
        this.styledLeftLabel = FontManagerHelper.apply(this.leftLabel);

        // 动态计算槽位数量
        int count = 1;
        if (suffixes != null && suffixes.length > 0) {
            count = suffixes.length;
        } else if (initialValues != null && initialValues.length > 0) {
            count = initialValues.length;
        }
        this.slotCount = count;

        this.slotValues = new String[count];
        this.slotSuffixes = new String[count];
        this.styledSlotSuffixes = new Component[count];
        this.slotFocusProgress = new float[count];

        for (int i = 0; i < count; i++) {
            this.slotValues[i] = (initialValues != null && i < initialValues.length && initialValues[i] != null) ? initialValues[i] : "";
            this.slotSuffixes[i] = (suffixes != null && i < suffixes.length && suffixes[i] != null) ? suffixes[i] : "";
            this.styledSlotSuffixes[i] = FontManagerHelper.apply(this.slotSuffixes[i]);
            this.slotFocusProgress[i] = 0.0f;
        }

        this.onTripleSubmit = onTripleSubmit;
        this.onTripleChanged = onTripleChanged;

        this.cursorStyle(CursorStyle.TEXT);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));
    }

    public TextInputPaneComponent(String leftLabel, String[] suffixes, Consumer<String[]> onTripleSubmit) {
        this(leftLabel, null, suffixes, onTripleSubmit, null);
    }

    public TextInputPaneComponent asButton(String text, Runnable onReadOnlyClick) {
        this.readOnly = true;
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
        this.onReadOnlyClick = onReadOnlyClick;
        this.cursorStyle(CursorStyle.HAND);
        markDirty();
        return this;
    }

    public TextInputPaneComponent onRightClick(Runnable onRightClick) {
        this.onRightClick = onRightClick;
        return this;
    }

    public TextInputPaneComponent setText(String text) {
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
        this.selectAll = false;
        markDirty();
        notifyTextChanged();
        return this;
    }

    public TextInputPaneComponent bounds(int[][] bounds) {
        this.slotBounds = bounds;
        return this;
    }

    private int clampSlotValue(int slotIndex, int value) {
        if (this.slotBounds != null && slotIndex >= 0 && slotIndex < this.slotBounds.length && this.slotBounds[slotIndex] != null) {
            int min = this.slotBounds[slotIndex][0];
            int max = this.slotBounds[slotIndex][1];
            return Mth.clamp(value, min, max);
        }
        return value;
    }

    public String getText() {
        return this.text;
    }

    public String[] getSlotValues() {
        return this.slotValues.clone();
    }

    public TextInputPaneComponent setSlotValues(String[] values) {
        if (values != null && this.mode == Mode.TRIPLE_SLOT) {
            for (int i = 0; i < Math.min(this.slotCount, values.length); i++) {
                this.slotValues[i] = values[i] != null ? values[i] : "";
            }
            if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                this.cursorPos = this.slotValues[this.activeSlot].length();
            }
        }
        return this;
    }

    public int getCursorPos() {
        return this.cursorPos;
    }

    public TextInputPaneComponent setCursorPos(int cursorPos) {
        this.cursorPos = cursorPos;
        clampCursor();
        resetCursorBlink();
        return this;
    }

    public TextInputPaneComponent onTextChanged(Consumer<String> onTextChanged) {
        this.onTextChanged = onTextChanged;
        return this;
    }

    public TextInputPaneComponent onTripleChanged(Consumer<String[]> onTripleChanged) {
        this.onTripleChanged = onTripleChanged;
        return this;
    }

    public TextInputPaneComponent onTripleSubmit(Consumer<String[]> onTripleSubmit) {
        this.onTripleSubmit = onTripleSubmit;
        return this;
    }

    private void markDirty() {
        this.dirty = true;
    }

    private void notifyTextChanged() {
        markDirty();
        if (this.onTextChanged != null) {
            this.onTextChanged.accept(this.text);
        }
    }

    private void notifyTripleChanged() {
        if (this.onTripleChanged != null) {
            this.onTripleChanged.accept(this.slotValues);
        }
    }

    private void clampCursor() {
        if (this.mode == Mode.TRIPLE_SLOT) {
            if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                this.cursorPos = Mth.clamp(this.cursorPos, 0, this.slotValues[this.activeSlot].length());
            } else {
                this.cursorPos = 0;
            }
        } else {
            this.cursorPos = Mth.clamp(this.cursorPos, 0, this.text.length());
        }
    }

    private void resetCursorBlink() {
        this.lastActionTime = System.currentTimeMillis();
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return !this.readOnly;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        if (!this.readOnly) {
            this.focused = true;
            if (this.mode == Mode.TRIPLE_SLOT && this.activeSlot == -1) {
                this.activeSlot = 0;
                this.cursorPos = this.slotValues[0].length();
                this.selectAll = !this.slotValues[0].isEmpty();
            }
            clampCursor();
            resetCursorBlink();
        }
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        this.focused = false;
        this.selectAll = false;

        if (this.mode == Mode.TRIPLE_SLOT) {
            boolean actualChanged = false;
            if (this.slotBounds != null) {
                for (int i = 0; i < this.slotCount; i++) {
                    String cur = this.slotValues[i].trim();
                    if (cur.isEmpty()) {
                        this.slotValues[i] = String.valueOf(this.slotBounds[i][0]);
                        actualChanged = true;
                    } else {
                        try {
                            int val = Integer.parseInt(cur);
                            int clamped = clampSlotValue(i, val);
                            if (clamped != val) {
                                this.slotValues[i] = String.valueOf(clamped);
                                actualChanged = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (actualChanged) {
                notifyTripleChanged();
            }

            this.activeSlot = -1;
        }
    }

    private record LineInfo(int start, int end, String text, Component styledText) {}

    private void updateLayoutCache(Font font, int stableWidth) {
        if (this.mode == Mode.TRIPLE_SLOT) {
            this.cachedTargetHeight = BASE_HEIGHT;
            return;
        }

        if (!this.dirty && this.lastLayoutWidth == stableWidth) {
            return;
        }

        this.cachedLines.clear();
        this.lastLayoutWidth = stableWidth;
        this.dirty = false;

        if (!this.rightHint.isEmpty() && this.cachedRightHintWidth == 0.0f) {
            this.cachedRightHintWidth = font.width(this.styledRightHint);
        }

        if (this.text.isEmpty()) {
            this.cachedLines.add(new LineInfo(0, 0, "", Component.literal("")));
            this.cachedTargetHeight = BASE_HEIGHT;
            return;
        }

        Component styledText = FontManagerHelper.apply(this.text);
        List<FormattedCharSequence> wrappedLines = font.split(styledText, stableWidth);
        if (wrappedLines.isEmpty()) {
            this.cachedLines.add(new LineInfo(0, 0, "", Component.literal("")));
            this.cachedTargetHeight = BASE_HEIGHT;
            return;
        }

        int searchIdx = 0;
        for (int i = 0; i < wrappedLines.size(); i++) {
            FormattedCharSequence seq = wrappedLines.get(i);
            String seqStr = sequenceToString(seq);
            int start = this.text.indexOf(seqStr, searchIdx);
            if (start == -1) start = searchIdx;

            int end;
            String lineStr;
            if (i == wrappedLines.size() - 1) {
                end = this.text.length();
                lineStr = this.text.substring(start);
            } else {
                end = Mth.clamp(start + seqStr.length(), start, this.text.length());
                lineStr = this.text.substring(start, end);
            }

            Component styledLine = FontManagerHelper.apply(lineStr);
            this.cachedLines.add(new LineInfo(start, end, lineStr, styledLine));
            searchIdx = end;
        }

        int lineCount = Math.max(1, this.cachedLines.size());
        this.cachedTargetHeight = BASE_HEIGHT + (lineCount - 1) * font.lineHeight;
    }

    private int getCursorLineIndex(List<LineInfo> lines) {
        clampCursor();
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);
            if (i == lines.size() - 1 || this.cursorPos < line.end) {
                return i;
            }
            LineInfo next = lines.get(i + 1);
            if (this.cursorPos < next.start) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int getCharIndexAtX(Font font, String lineText, int targetX) {
        if (lineText.isEmpty() || targetX <= 0) return 0;
        int prevW = 0;
        for (int i = 1; i <= lineText.length(); i++) {
            int curW = font.width(FontManagerHelper.apply(lineText.substring(0, i)));
            int midPoint = (prevW + curW) / 2;
            if (targetX < midPoint) {
                return i - 1;
            }
            prevW = curW;
        }
        return lineText.length();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int stableWidth = Math.max(154, this.width() - 16);

        updateLayoutCache(font, stableWidth);

        float targetHover = (this.hovered || this.focused) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        this.currentAnimatedHeight = Mth.lerp(delta * 0.35f, this.currentAnimatedHeight, (float) this.cachedTargetHeight);
        int renderH = Math.max(BASE_HEIGHT, Math.round(this.currentAnimatedHeight));
        this.height = renderH;

        int x = this.x();
        int y = this.y();
        int width = this.width();

        Color paneBg = Color.ofRgb(BG_COLOR_RGB);
        int borderAlpha = (int) (40 + this.hoverAnimation * 180);
        int borderRgb = this.focused ? 0x90CAF9 : (this.readOnly ? 0x80DEEA : 0x90A4AE);
        Color borderColor = Color.ofArgb(ARGB.color(borderAlpha, (borderRgb >> 16) & 0xFF, (borderRgb >> 8) & 0xFF, borderRgb & 0xFF));

        float halfW = width / 2.0f;
        float halfH = renderH / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + halfW, y + halfH);

        RenderUtil.drawRoundedPanel(graphics, (int) -halfW, (int) -halfH, width, renderH, 6.0f, paneBg, borderColor);

        float textStartY = -halfH + 7.0f;
        float textScale = 0.85f;

        if (this.mode == Mode.TRIPLE_SLOT) {
            drawTripleSlotMode(graphics, font, halfW, textStartY, textScale, delta);
        } else {
            drawNormalMode(graphics, font, halfW, textStartY, textScale, width, delta);
        }

        graphics.pose().popMatrix();
    }

    private void drawNormalMode(OwoUIGraphics graphics, Font font, float halfW, float textStartY, float textScale, int width, float delta) {
        boolean blink = (System.currentTimeMillis() - this.lastActionTime < 300) || (System.currentTimeMillis() % 1000 < 500);
        boolean showCursor = !this.readOnly && this.focused && blink && !this.text.isEmpty();
        int textColor = this.selectAll ? 0xFF80DEEA : (this.readOnly ? (this.hovered ? 0xFFFFFFFF : 0xFFB2EBF2) : (this.focused ? 0xFFFFFFFF : 0xFFECEFF1));

        int cursorLineIdx = getCursorLineIndex(this.cachedLines);

        if (this.text.isEmpty() && !this.focused && !this.placeholder.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledPlaceholder, 0, 0, 1.0f, 0xFF607D8B, OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        } else {
            for (int i = 0; i < this.cachedLines.size(); i++) {
                LineInfo line = this.cachedLines.get(i);
                float curLineY = (int) (textStartY + i * font.lineHeight);

                graphics.pose().pushMatrix();
                graphics.pose().translate(-halfW + 8.0f, curLineY);
                graphics.pose().scale(textScale, textScale);

                if (this.selectAll) {
                    int lineW = font.width(line.styledText);
                    graphics.fill(-1, -1, lineW + 1, font.lineHeight, ARGB.color(100, 38, 198, 218));
                }

                graphics.drawText(line.styledText, 0, 0, 1.0f, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);

                if (showCursor && !this.selectAll && i == cursorLineIdx) {
                    int offsetInLine = Mth.clamp(this.cursorPos - line.start, 0, line.text.length());
                    String subStr = line.text.substring(0, offsetInLine);
                    int cursorX = subStr.isEmpty() ? 0 : font.width(FontManagerHelper.apply(subStr));
                    graphics.fill(cursorX, -1, cursorX + 1, font.lineHeight, 0xFFFFFFFF);
                }

                graphics.pose().popMatrix();
            }
        }

        drawRightHint(graphics, font, halfW, textStartY, textScale, width, delta);
    }

    private void drawTripleSlotMode(OwoUIGraphics graphics, Font font, float halfW, float textStartY, float textScale, float delta) {
        if (!this.leftLabel.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledLeftLabel, 0, 0, 1.0f, 0xFF90A4AE, OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        }

        float slotLineWidth = this.slotCount == 1 ? 32.0f : 26.0f;
        float slotGap = 8.0f;
        float rightEdge = halfW - 8.0f;

        float[] slotOccupiedW = new float[this.slotCount];
        float totalSlotsW = 0.0f;
        for (int i = 0; i < this.slotCount; i++) {
            float suffixW = (this.styledSlotSuffixes[i] != null && !this.slotSuffixes[i].isEmpty())
                    ? (font.width(this.styledSlotSuffixes[i]) * textScale + 2.0f) : 0.0f;
            slotOccupiedW[i] = slotLineWidth + suffixW;
            totalSlotsW += slotOccupiedW[i];
        }
        if (this.slotCount > 1) {
            totalSlotsW += slotGap * (this.slotCount - 1);
        }

        float startX = rightEdge - totalSlotsW;
        boolean blink = (System.currentTimeMillis() - this.lastActionTime < 300) || (System.currentTimeMillis() % 1000 < 500);

        float curX = startX;
        for (int i = 0; i < this.slotCount; i++) {
            boolean isCurFocused = (this.focused && this.activeSlot == i);

            float targetProgress = isCurFocused ? 1.0f : 0.0f;
            this.slotFocusProgress[i] = Mth.clamp(this.slotFocusProgress[i] + (targetProgress - this.slotFocusProgress[i]) * delta * 0.45f, 0.0f, 1.0f);
            float ease = RenderUtil.easeOutCubic(this.slotFocusProgress[i]);

            float lineX1 = curX;
            float lineY = textStartY + font.lineHeight * textScale + 1.5f;
            float lineH = 1.5f;
            float lineR = 0.75f;

            Color baseLineColor = Color.ofArgb(0xFF26384A);
            RenderUtil.drawSubpixelRoundedPanel(graphics, lineX1, lineY, slotLineWidth, lineH, lineR, baseLineColor, null);

            if (ease > 0.005f) {
                float centerLineX = lineX1 + slotLineWidth / 2.0f;
                float halfSpread = (slotLineWidth / 2.0f) * ease;
                float activeWidth = halfSpread * 2.0f;
                float activeX = centerLineX - halfSpread;
                float activeH = 2.0f;
                float activeR = 1.0f;
                Color activeLineColor = Color.ofArgb(ARGB.color((int) (255 * ease), 0x4F, 0xC3, 0xF7));
                RenderUtil.drawSubpixelRoundedPanel(graphics, activeX, lineY - 0.25f, activeWidth, activeH, activeR, activeLineColor, null);
            }

            String val = this.slotValues[i];

            graphics.pose().pushMatrix();
            graphics.pose().scale(textScale, textScale);

            float localSlotCenterX = (lineX1 + slotLineWidth / 2.0f) / textScale;
            float localTextY = textStartY / textScale;

            if (val.isEmpty()) {
                Component dash = Component.literal("_");
                float dashW = font.width(dash);
                graphics.drawText(dash, localSlotCenterX - dashW / 2.0f, localTextY, 1.0f, 0xFF546E7A, OwoUIGraphics.TextAnchor.TOP_LEFT);
            } else {
                Component styledVal = FontManagerHelper.apply(val);
                float valW = font.width(styledVal);
                float textDrawX = localSlotCenterX - valW / 2.0f;

                if (isCurFocused && this.selectAll) {
                    int selectBgColor = ARGB.color(120, 79, 195, 247);
                    graphics.fill((int) (textDrawX - 1), (int) (localTextY - 1), (int) (textDrawX + valW + 1), (int) (localTextY + font.lineHeight), selectBgColor);
                }

                int valTextColor = isCurFocused ? 0xFFFFFFFF : 0xFFECEFF1;
                graphics.drawText(styledVal, textDrawX, localTextY, 1.0f, valTextColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
            }

            if (isCurFocused && blink && !this.selectAll) {
                float prefixW = val.isEmpty() ? 0.0f : font.width(FontManagerHelper.apply(val.substring(0, Math.min(this.cursorPos, val.length()))));
                float totalW = val.isEmpty() ? 0.0f : font.width(FontManagerHelper.apply(val));
                float cursorDrawX = (lineX1 + slotLineWidth / 2.0f) / textScale - totalW / 2.0f + prefixW;

                graphics.fill((int) cursorDrawX, (int) localTextY - 1, (int) cursorDrawX + 1, (int) (localTextY + font.lineHeight), 0xFFFFFFFF);
            }

            graphics.pose().popMatrix();

            if (this.styledSlotSuffixes[i] != null && !this.slotSuffixes[i].isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(lineX1 + slotLineWidth + 2.0f, textStartY);
                graphics.pose().scale(textScale, textScale);
                graphics.drawText(this.styledSlotSuffixes[i], 0, 0, 1.0f, 0xFF78909C, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();
            }

            curX += slotOccupiedW[i] + slotGap;
        }
    }

    private void drawRightHint(OwoUIGraphics graphics, Font font, float halfW, float textStartY,
                               float textScale, int width, float delta) {
        if (this.rightHint.isEmpty()) return;

        float scaledHintW = this.cachedRightHintWidth * textScale;
        float firstLineW = this.cachedLines.isEmpty() ? 0.0f : (font.width(this.cachedLines.get(0).styledText) * textScale);

        boolean hasSpace = (this.cachedLines.size() <= 1) && (!this.text.isEmpty() || this.placeholder.isEmpty())
                && ((width - 16.0f - firstLineW - 24.0f) >= scaledHintW);

        float targetAlpha = hasSpace ? 1.0f : 0.0f;
        this.rightHintAlpha = Mth.clamp(this.rightHintAlpha + (targetAlpha - this.rightHintAlpha) * delta * 0.40f, 0.0f, 1.0f);

        if (this.rightHintAlpha > 0.02f) {
            int alpha = (int) (255 * this.rightHintAlpha);
            int color = ARGB.color(alpha, 0x60, 0x7D, 0x8B);

            graphics.pose().pushMatrix();
            graphics.pose().translate(halfW - 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledRightHint, 0, 0, 1.0f, color, OwoUIGraphics.TextAnchor.TOP_RIGHT);
            graphics.pose().popMatrix();
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
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.mode == Mode.TRIPLE_SLOT && click.button() == 0 && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            this.dragAccumulator += deltaX;

            if (!this.isDraggingSlot && Math.abs(this.dragAccumulator) >= 2.0) {
                this.isDraggingSlot = true;
                this.selectAll = false;
            }

            if (this.isDraggingSlot) {
                int deltaStep = (int) (this.dragAccumulator / DRAG_SENSITIVITY);
                if (deltaStep != 0) {
                    this.dragAccumulator -= deltaStep * DRAG_SENSITIVITY;

                    String currentStr = this.slotValues[this.activeSlot].trim();
                    int currentVal = 0;
                    if (!currentStr.isEmpty()) {
                        try {
                            currentVal = Integer.parseInt(currentStr);
                        } catch (NumberFormatException ignored) {}
                    }

                    int newVal = currentVal + deltaStep;
                    newVal = clampSlotValue(this.activeSlot, newVal);

                    this.slotValues[this.activeSlot] = String.valueOf(newVal);
                    this.cursorPos = this.slotValues[this.activeSlot].length();

                    notifyTripleChanged();
                    resetCursorBlink();
                }
                return true;
            }
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (this.mode == Mode.TRIPLE_SLOT && click.button() == 0) {
            if (this.isDraggingSlot) {
                this.isDraggingSlot = false;
                this.dragAccumulator = 0.0;
                this.selectAll = false;
                return true;
            }
        }
        return super.onMouseUp(click);
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 1 && this.onRightClick != null) {
            this.onRightClick.run();
            return true;
        }

        if (this.readOnly) {
            if (click.button() == 0 && this.onReadOnlyClick != null) {
                this.onReadOnlyClick.run();
                return true;
            }
            return false;
        }

        if (click.button() == 0) {
            this.focused = true;
            resetCursorBlink();

            double localX = click.x() >= this.x() ? (click.x() - this.x()) : click.x();
            double localY = click.y() >= this.y() ? (click.y() - this.y()) : click.y();

            if (this.mode == Mode.TRIPLE_SLOT) {
                var font = Minecraft.getInstance().font;
                float textScale = 0.85f;
                float slotLineWidth = this.slotCount == 1 ? 32.0f : 26.0f;
                float slotGap = 8.0f;
                float rightEdge = this.width() - 8.0f;

                float[] slotOccupiedW = new float[this.slotCount];
                float totalSlotsW = 0.0f;
                for (int i = 0; i < this.slotCount; i++) {
                    float suffixW = (this.styledSlotSuffixes[i] != null && !this.slotSuffixes[i].isEmpty())
                            ? (font.width(this.styledSlotSuffixes[i]) * textScale + 2.0f) : 0.0f;
                    slotOccupiedW[i] = slotLineWidth + suffixW;
                    totalSlotsW += slotOccupiedW[i];
                }
                if (this.slotCount > 1) {
                    totalSlotsW += slotGap * (this.slotCount - 1);
                }
                float startX = rightEdge - totalSlotsW;

                float curX = startX;
                int clickedSlot = -1;
                for (int i = 0; i < this.slotCount; i++) {
                    float slotEnd = curX + slotOccupiedW[i];
                    if (localX >= curX - 3.0f && localX <= slotEnd + 3.0f) {
                        clickedSlot = i;
                        break;
                    }
                    curX += slotOccupiedW[i] + slotGap;
                }

                if (clickedSlot != -1) {
                    this.activeSlot = clickedSlot;
                } else if (this.activeSlot == -1) {
                    this.activeSlot = 0;
                }

                this.isDraggingSlot = false;
                this.dragAccumulator = 0.0;

                this.cursorPos = this.slotValues[this.activeSlot].length();
                this.selectAll = !this.slotValues[this.activeSlot].isEmpty();
                clampCursor();
                return true;
            }

            this.selectAll = false;
            if (this.text.isEmpty()) {
                this.cursorPos = 0;
                return true;
            }

            var font = Minecraft.getInstance().font;
            float textScale = 0.85f;

            double textRelX = (localX - 8.0) / textScale;
            double textRelY = (localY - 7.0) / textScale;

            updateLayoutCache(font, Math.max(154, this.width() - 16));

            if (this.cachedLines.isEmpty()) {
                this.cursorPos = this.text.length();
                return true;
            }

            int lineIdx = (int) (textRelY / font.lineHeight);

            if (lineIdx >= this.cachedLines.size()) {
                this.cursorPos = this.text.length();
                return true;
            }

            if (lineIdx < 0) {
                this.cursorPos = 0;
                return true;
            }

            LineInfo targetLine = this.cachedLines.get(lineIdx);
            int lineWidth = font.width(targetLine.styledText);

            if (textRelX >= lineWidth) {
                this.cursorPos = (lineIdx == this.cachedLines.size() - 1) ? this.text.length() : targetLine.end;
            } else if (textRelX <= 0) {
                this.cursorPos = targetLine.start;
            } else {
                int charIdx = getCharIndexAtX(font, targetLine.text, (int) textRelX);
                this.cursorPos = targetLine.start + charIdx;
            }

            clampCursor();
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (this.readOnly || !this.focused) return false;
        char c = (char) input.codepoint();
        if (c >= 32 && c != 127) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot < 0 || this.activeSlot >= this.slotCount) return false;

                if (this.slotBounds != null && (c < '0' || c > '9')) {
                    return false;
                }

                String curVal = this.selectAll ? "" : this.slotValues[this.activeSlot];
                if (this.selectAll) {
                    this.cursorPos = 0;
                    this.selectAll = false;
                }

                clampCursor();
                String candidate = curVal.substring(0, this.cursorPos) + c + curVal.substring(this.cursorPos);

                if (this.slotBounds != null) {
                    try {
                        int num = Integer.parseInt(candidate);
                        int max = this.slotBounds[this.activeSlot][1];
                        if (num > max) {
                            candidate = String.valueOf(max);
                            this.cursorPos = candidate.length();
                        } else {
                            this.cursorPos++;
                        }
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                } else {
                    this.cursorPos++;
                }

                this.slotValues[this.activeSlot] = candidate;
                notifyTripleChanged();
                resetCursorBlink();
                return true;
            }

            if (this.selectAll) {
                this.text = "";
                this.cursorPos = 0;
                this.selectAll = false;
            }
            clampCursor();
            this.text = this.text.substring(0, this.cursorPos) + c + this.text.substring(this.cursorPos);
            this.cursorPos++;
            notifyTextChanged();
            resetCursorBlink();
            return true;
        }
        return super.onCharTyped(input);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        if (!this.focused || this.readOnly) return false;

        // 回车提交
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.onTripleSubmit != null) {
                    this.onTripleSubmit.accept(this.slotValues);
                }
            } else {
                String val = this.text.trim();
                if (this.onEnterSubmit != null) {
                    this.onEnterSubmit.accept(val);
                }
            }
            this.selectAll = false;
            return true;
        }

        // Tab / Shift+Tab 跨槽位切换
        if (this.mode == Mode.TRIPLE_SLOT && input.key() == GLFW.GLFW_KEY_TAB && this.slotCount > 1) {
            if (input.hasShiftDown()) {
                this.activeSlot = (this.activeSlot - 1 + this.slotCount) % this.slotCount;
            } else {
                this.activeSlot = (this.activeSlot + 1) % this.slotCount;
            }
            this.cursorPos = this.slotValues[this.activeSlot].length();
            this.selectAll = !this.slotValues[this.activeSlot].isEmpty();
            resetCursorBlink();
            return true;
        }

        // 全选：Ctrl + A
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_A) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot >= 0 && this.activeSlot < this.slotCount && !this.slotValues[this.activeSlot].isEmpty()) {
                    this.selectAll = true;
                }
            } else if (!this.text.isEmpty()) {
                this.selectAll = true;
            }
            return true;
        }

        // 光标左移
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.selectAll) {
                    this.selectAll = false;
                    this.cursorPos = 0;
                } else if (this.cursorPos > 0) {
                    this.cursorPos--;
                } else if (this.activeSlot > 0) {
                    this.activeSlot--;
                    this.cursorPos = this.slotValues[this.activeSlot].length();
                }
            } else {
                if (this.selectAll) {
                    this.selectAll = false;
                    this.cursorPos = 0;
                } else if (input.hasControlDown()) {
                    this.cursorPos = findPreviousWord(this.text, this.cursorPos);
                } else {
                    this.cursorPos = Math.max(0, this.cursorPos - 1);
                }
            }
            resetCursorBlink();
            return true;
        }

        // 光标右移
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.selectAll) {
                    this.selectAll = false;
                    this.cursorPos = this.slotValues[this.activeSlot].length();
                } else if (this.cursorPos < this.slotValues[this.activeSlot].length()) {
                    this.cursorPos++;
                } else if (this.activeSlot < this.slotCount - 1) {
                    this.activeSlot++;
                    this.cursorPos = 0;
                }
            } else {
                if (this.selectAll) {
                    this.selectAll = false;
                    this.cursorPos = this.text.length();
                } else if (input.hasControlDown()) {
                    this.cursorPos = findNextWord(this.text, this.cursorPos);
                } else {
                    this.cursorPos = Math.min(this.text.length(), this.cursorPos + 1);
                }
            }
            resetCursorBlink();
            return true;
        }

        // 退格删除 (Backspace)
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                    if (this.selectAll) {
                        this.slotValues[this.activeSlot] = "";
                        this.cursorPos = 0;
                        this.selectAll = false;
                        notifyTripleChanged();
                        resetCursorBlink();
                        return true;
                    }

                    String cur = this.slotValues[this.activeSlot];
                    clampCursor();
                    if (this.cursorPos > 0) {
                        this.slotValues[this.activeSlot] = cur.substring(0, this.cursorPos - 1) + cur.substring(this.cursorPos);
                        this.cursorPos--;
                        notifyTripleChanged();
                        resetCursorBlink();
                    } else if (this.activeSlot > 0) {
                        this.activeSlot--;
                        this.cursorPos = this.slotValues[this.activeSlot].length();
                        resetCursorBlink();
                    }
                }
                return true;
            }

            if (this.selectAll) {
                this.text = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyTextChanged();
                resetCursorBlink();
                return true;
            }
            clampCursor();
            if (this.cursorPos > 0) {
                this.text = this.text.substring(0, this.cursorPos - 1) + this.text.substring(this.cursorPos);
                this.cursorPos--;
                notifyTextChanged();
                resetCursorBlink();
                return true;
            }
            return true;
        }

        // 向后删除 (Delete)
        if (input.key() == GLFW.GLFW_KEY_DELETE) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                    if (this.selectAll) {
                        this.slotValues[this.activeSlot] = "";
                        this.cursorPos = 0;
                        this.selectAll = false;
                        notifyTripleChanged();
                        resetCursorBlink();
                        return true;
                    }

                    String cur = this.slotValues[this.activeSlot];
                    clampCursor();
                    if (this.cursorPos < cur.length()) {
                        this.slotValues[this.activeSlot] = cur.substring(0, this.cursorPos) + cur.substring(this.cursorPos + 1);
                        notifyTripleChanged();
                        resetCursorBlink();
                    }
                }
                return true;
            }

            if (this.selectAll) {
                this.text = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyTextChanged();
                resetCursorBlink();
                return true;
            }
            clampCursor();
            if (this.cursorPos < this.text.length()) {
                this.text = this.text.substring(0, this.cursorPos) + this.text.substring(this.cursorPos + 1);
                notifyTextChanged();
                resetCursorBlink();
                return true;
            }
            return true;
        }

        // Ctrl + V 粘贴
        boolean isPasteKey = input.hasControlDown() && input.key() == GLFW.GLFW_KEY_V;
        if (isPasteKey) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String cleanClip = clip.replaceAll("[\r\n]", "");
                if (this.mode == Mode.TRIPLE_SLOT) {
                    if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                        if (this.selectAll) {
                            this.slotValues[this.activeSlot] = cleanClip;
                            this.cursorPos = cleanClip.length();
                            this.selectAll = false;
                        } else {
                            String cur = this.slotValues[this.activeSlot];
                            clampCursor();
                            this.slotValues[this.activeSlot] = cur.substring(0, this.cursorPos) + cleanClip + cur.substring(this.cursorPos);
                            this.cursorPos += cleanClip.length();
                        }
                        notifyTripleChanged();
                        resetCursorBlink();
                    }
                } else {
                    if (this.selectAll) {
                        this.text = cleanClip;
                        this.cursorPos = cleanClip.length();
                        this.selectAll = false;
                    } else {
                        clampCursor();
                        this.text = this.text.substring(0, this.cursorPos) + cleanClip + this.text.substring(this.cursorPos);
                        this.cursorPos += cleanClip.length();
                    }
                    notifyTextChanged();
                    resetCursorBlink();
                }
            }
            return true;
        }

        // Ctrl + C 复制
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_C) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.slotValues[this.activeSlot]);
                }
            } else if (this.selectAll && !this.text.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.text);
            }
            return true;
        }

        // Ctrl + X 剪切
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_X) {
            if (this.mode == Mode.TRIPLE_SLOT) {
                if (this.activeSlot >= 0 && this.activeSlot < this.slotCount && !this.slotValues[this.activeSlot].isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.slotValues[this.activeSlot]);
                    this.slotValues[this.activeSlot] = "";
                    this.cursorPos = 0;
                    this.selectAll = false;
                    notifyTripleChanged();
                    resetCursorBlink();
                }
            } else if (!this.readOnly && this.selectAll && !this.text.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.text);
                this.text = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyTextChanged();
                resetCursorBlink();
            }
            return true;
        }

        if (this.mode == Mode.TRIPLE_SLOT) {
            return super.onKeyPress(input);
        }

        var font = Minecraft.getInstance().font;
        updateLayoutCache(font, Math.max(154, this.width() - 16));

        // 光标上移 (单行/多行模式专属)
        if (input.key() == GLFW.GLFW_KEY_UP) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = 0;
                resetCursorBlink();
                return true;
            }
            int curLineIdx = getCursorLineIndex(this.cachedLines);
            if (curLineIdx > 0) {
                LineInfo curLine = this.cachedLines.get(curLineIdx);
                LineInfo prevLine = this.cachedLines.get(curLineIdx - 1);

                int curOffset = Mth.clamp(this.cursorPos - curLine.start, 0, curLine.text.length());
                int targetX = font.width(FontManagerHelper.apply(curLine.text.substring(0, curOffset)));

                int prevOffset = getCharIndexAtX(font, prevLine.text, targetX);
                this.cursorPos = prevLine.start + prevOffset;
            } else {
                this.cursorPos = 0;
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // 光标下移 (单行/多行模式专属)
        if (input.key() == GLFW.GLFW_KEY_DOWN) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = this.text.length();
                resetCursorBlink();
                return true;
            }
            int curLineIdx = getCursorLineIndex(this.cachedLines);
            if (curLineIdx < this.cachedLines.size() - 1) {
                LineInfo curLine = this.cachedLines.get(curLineIdx);
                LineInfo nextLine = this.cachedLines.get(curLineIdx + 1);

                int curOffset = Mth.clamp(this.cursorPos - curLine.start, 0, curLine.text.length());
                int targetX = font.width(FontManagerHelper.apply(curLine.text.substring(0, curOffset)));

                int nextOffset = getCharIndexAtX(font, nextLine.text, targetX);
                this.cursorPos = nextLine.start + nextOffset;
            } else {
                this.cursorPos = this.text.length();
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // Home 键
        if (input.key() == GLFW.GLFW_KEY_HOME) {
            if (input.hasControlDown()) {
                this.cursorPos = 0;
            } else {
                int curLineIdx = getCursorLineIndex(this.cachedLines);
                this.cursorPos = this.cachedLines.get(curLineIdx).start;
            }
            this.selectAll = false;
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // End 键
        if (input.key() == GLFW.GLFW_KEY_END) {
            if (input.hasControlDown()) {
                this.cursorPos = this.text.length();
            } else {
                int curLineIdx = getCursorLineIndex(this.cachedLines);
                this.cursorPos = this.cachedLines.get(curLineIdx).end;
            }
            this.selectAll = false;
            clampCursor();
            resetCursorBlink();
            return true;
        }

        return super.onKeyPress(input);
    }

    private static int findPreviousWord(String text, int cursor) {
        int pos = cursor - 1;
        while (pos > 0 && Character.isWhitespace(text.charAt(pos))) pos--;
        while (pos > 0 && !Character.isWhitespace(text.charAt(pos - 1))) pos--;
        return Math.max(0, pos);
    }

    private static int findNextWord(String text, int cursor) {
        int pos = cursor;
        int len = text.length();
        while (pos < len && !Character.isWhitespace(text.charAt(pos))) pos++;
        while (pos < len && Character.isWhitespace(text.charAt(pos))) pos++;
        return Math.min(len, pos);
    }
}