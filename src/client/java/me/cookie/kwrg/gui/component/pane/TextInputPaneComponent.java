package me.cookie.kwrg.gui.component.pane;

import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.BasePaneComponent;
import me.cookie.kwrg.gui.manager.GuiColorManager;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TextInputPaneComponent extends BasePaneComponent implements GuiEventListener {

    private static final int BASE_HEIGHT = 26;

    private final String placeholder;
    private final Component styledPlaceholder;
    private final String rightHint;
    private final Component styledRightHint;
    private float cachedRightHintWidth = 0.0f;

    private final Consumer<String> onEnterSubmit;
    private Consumer<String> onTextChanged = null;
    private String text = "";

    // 光标与选区管理
    private int cursorPos = 0;
    private int selectionAnchor = 0;
    private boolean isDraggingSelection = false;
    private long lastActionTime = 0;

    private boolean readOnly = false;
    private Runnable onReadOnlyClick = null;
    private float rightHintAlpha = 0.0f;

    private boolean dirty = true;
    private int lastLayoutWidth = -1;
    private final List<LineInfo> cachedLines = new ArrayList<>();

    private IMEPreeditOverlay preeditOverlay = null;

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit) {
        this(placeholder, onEnterSubmit, "");
    }

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit, String rightHint) {
        super(BASE_HEIGHT);
        this.placeholder = placeholder != null ? placeholder : "";
        this.styledPlaceholder = FontManagerHelper.apply(this.placeholder);

        this.rightHint = rightHint != null ? rightHint : "";
        this.styledRightHint = FontManagerHelper.apply(this.rightHint);

        this.onEnterSubmit = onEnterSubmit;
        this.cursorStyle(CursorStyle.TEXT);
    }

    public TextInputPaneComponent asButton(String text, Runnable onReadOnlyClick) {
        this.readOnly = true;
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
        this.selectionAnchor = this.cursorPos;
        this.onReadOnlyClick = onReadOnlyClick;
        this.cursorStyle(CursorStyle.HAND);
        markDirty();
        return this;
    }

    public TextInputPaneComponent setText(String text) {
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
        this.selectionAnchor = this.cursorPos;
        markDirty();
        notifyTextChanged();
        return this;
    }

    public String getText() {
        return this.text;
    }

    public int getCursorPos() {
        return this.cursorPos;
    }

    public TextInputPaneComponent setCursorPos(int cursorPos) {
        this.cursorPos = cursorPos;
        clampCursor();
        this.selectionAnchor = this.cursorPos;
        resetCursorBlink();
        return this;
    }

    public TextInputPaneComponent onTextChanged(Consumer<String> onTextChanged) {
        this.onTextChanged = onTextChanged;
        return this;
    }

    // ==================== 选区工具方法 ====================

    public int getSelectionStart() {
        return Math.min(this.cursorPos, this.selectionAnchor);
    }

    public int getSelectionEnd() {
        return Math.max(this.cursorPos, this.selectionAnchor);
    }

    public boolean hasSelection() {
        return this.cursorPos != this.selectionAnchor;
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        return this.text.substring(getSelectionStart(), getSelectionEnd());
    }

    public void selectAll() {
        this.selectionAnchor = 0;
        this.cursorPos = this.text.length();
        resetCursorBlink();
    }

    public void clearSelection() {
        this.selectionAnchor = this.cursorPos;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        this.text = this.text.substring(0, start) + this.text.substring(end);
        this.cursorPos = start;
        this.selectionAnchor = this.cursorPos;
        clampCursor();
        notifyTextChanged();
        resetCursorBlink();
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

    private void clampCursor() {
        this.cursorPos = Mth.clamp(this.cursorPos, 0, this.text.length());
        this.selectionAnchor = Mth.clamp(this.selectionAnchor, 0, this.text.length());
    }

    private void resetCursorBlink() {
        this.lastActionTime = System.currentTimeMillis();
    }

    private void updateImeFocus(boolean isFocused) {
        if (this.readOnly) return;
        try {
            var client = Minecraft.getInstance();
            client.onTextInputFocusChange(this, isFocused);

            if (isFocused && client.textInputManager() != null) {
                int x0 = this.x() + 8;
                int y0 = this.y() + 7;
                int x1 = x0 + 2;
                int y1 = y0 + BASE_HEIGHT;
                client.textInputManager().setTextInputArea(x0, y0, x1, y1);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        if (!this.focused || this.readOnly) return false;
        var font = Minecraft.getInstance().font;
        this.preeditOverlay = event != null
                ? new IMEPreeditOverlay(event, font, font.lineHeight + 1)
                : null;
        return true;
    }

    @Override
    public ScreenRectangle getRectangle() {
        int x = this.x();
        int y = this.y();
        int w = this.width() > 0 ? this.width() : 170;
        int h = this.height() > 0 ? this.height() : BASE_HEIGHT;
        return new ScreenRectangle(x, y, w, h);
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused) {
            this.isDraggingSelection = false;
            clearSelection();
            updateImeFocus(false);
        } else {
            updateImeFocus(true);
        }
    }

    @Override
    public boolean isFocused() {
        return this.focused;
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
            clampCursor();
            resetCursorBlink();
            updateImeFocus(true);
        }
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        this.focused = false;
        this.isDraggingSelection = false;
        this.preeditOverlay = null;
        clearSelection();
        updateImeFocus(false);
    }

    @Override
    protected int borderBaseRgb() {
        return this.focused
                ? GuiColorManager.paneBorderFocused()
                : (this.readOnly ? GuiColorManager.paneBorderReadOnly() : GuiColorManager.paneBorder());
    }

    // ==================== 物理 Shift 键判定 ====================

    private static boolean isShiftPressed() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    // ==================== 排版与换行缓存 ====================

    private record LineInfo(int start, int end, String text, Component styledText) {}

    private void updateLayoutCache(Font font, int stableWidth) {
        if (!this.dirty && this.lastLayoutWidth == stableWidth) return;

        this.cachedLines.clear();
        this.lastLayoutWidth = stableWidth;
        this.dirty = false;

        if (!this.rightHint.isEmpty() && this.cachedRightHintWidth == 0.0f) {
            this.cachedRightHintWidth = font.width(this.styledRightHint);
        }

        if (this.text.isEmpty()) {
            this.cachedLines.add(new LineInfo(0, 0, "", Component.literal("")));
            this.targetHeight = BASE_HEIGHT;
            return;
        }

        Component styledText = FontManagerHelper.apply(this.text);
        List<FormattedCharSequence> wrappedLines = font.split(styledText, stableWidth);
        if (wrappedLines.isEmpty()) {
            this.cachedLines.add(new LineInfo(0, 0, "", Component.literal("")));
            this.targetHeight = BASE_HEIGHT;
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
        this.targetHeight = BASE_HEIGHT + (lineCount - 1) * font.lineHeight;
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
            if (targetX < midPoint) return i - 1;
            prevW = curW;
        }
        return lineText.length();
    }

    private int getCharIndexAt(Font font, double localX, double localY) {
        if (this.text.isEmpty()) return 0;

        float textScale = DEFAULT_TEXT_SCALE;
        double textRelX = (localX - 8.0) / textScale;
        double textRelY = (localY - 7.0) / textScale;

        updateLayoutCache(font, Math.max(154, this.width() - 16));
        if (this.cachedLines.isEmpty()) return this.text.length();

        int lineIdx = (int) (textRelY / font.lineHeight);
        if (lineIdx < 0) return 0;
        if (lineIdx >= this.cachedLines.size()) return this.text.length();

        LineInfo targetLine = this.cachedLines.get(lineIdx);
        int lineWidth = font.width(targetLine.styledText);

        if (textRelX <= 0) {
            return targetLine.start;
        } else if (textRelX >= lineWidth) {
            return (lineIdx == this.cachedLines.size() - 1) ? this.text.length() : targetLine.end;
        } else {
            int charIdx = getCharIndexAtX(font, targetLine.text, (int) textRelX);
            return targetLine.start + charIdx;
        }
    }

    @Override
    protected void renderPaneContent(OwoUIGraphics graphics, Font font, float halfW, float halfH,
                                     float textStartY, float textScale, float delta) {
        int stableWidth = Math.max(154, this.width() - 16);
        updateLayoutCache(font, stableWidth);

        boolean blink = (System.currentTimeMillis() - this.lastActionTime < 300) || (System.currentTimeMillis() % 1000 < 500);
        boolean showCursor = !this.readOnly && this.focused && blink;
        int textColor = GuiColorManager.opaque(this.readOnly
                ? (this.hovered ? GuiColorManager.textStrong() : GuiColorManager.textReadOnly())
                : (this.focused ? GuiColorManager.textStrong() : GuiColorManager.textBody()));

        int cursorLineIdx = getCursorLineIndex(this.cachedLines);
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        boolean hasSel = hasSelection();

        if (this.text.isEmpty() && !this.focused && !this.placeholder.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledPlaceholder, 0, 0, 1.0f, GuiColorManager.opaque(GuiColorManager.textFaint()), OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        } else {
            for (int i = 0; i < this.cachedLines.size(); i++) {
                LineInfo line = this.cachedLines.get(i);
                float curLineY = (int) (textStartY + i * font.lineHeight);

                graphics.pose().pushMatrix();
                graphics.pose().translate(-halfW + 8.0f, curLineY);
                graphics.pose().scale(textScale, textScale);

                if (hasSel) {
                    int overlapStart = Math.max(selStart, line.start);
                    int overlapEnd = Math.min(selEnd, line.end);

                    if (overlapStart < overlapEnd) {
                        int lineOffsetStart = overlapStart - line.start;
                        int lineOffsetEnd = overlapEnd - line.start;

                        String preSub = line.text.substring(0, lineOffsetStart);
                        String selSub = line.text.substring(lineOffsetStart, lineOffsetEnd);

                        int hX1 = preSub.isEmpty() ? 0 : font.width(FontManagerHelper.apply(preSub));
                        int hX2 = hX1 + font.width(FontManagerHelper.apply(selSub));

                        graphics.fill(hX1, -1, hX2, font.lineHeight, GuiColorManager.withAlpha(GuiColorManager.selectionTint(), 110));
                    }
                }

                graphics.drawText(line.styledText, 0, 0, 1.0f, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);

                if (showCursor && i == cursorLineIdx) {
                    int offsetInLine = Mth.clamp(this.cursorPos - line.start, 0, line.text.length());
                    String subStr = line.text.substring(0, offsetInLine);
                    int cursorX = subStr.isEmpty() ? 0 : font.width(FontManagerHelper.apply(subStr));
                    graphics.fill(cursorX, -1, cursorX + 1, font.lineHeight, GuiColorManager.opaque(GuiColorManager.textStrong()));
                }

                graphics.pose().popMatrix();
            }
        }

        if (this.focused && this.preeditOverlay != null) {
            graphics.setPreeditOverlay(this.preeditOverlay);
        }

        if (this.focused && !this.readOnly) {
            int curLineIdx = getCursorLineIndex(this.cachedLines);
            int cursorOffsetInLineX = 0;
            if (curLineIdx >= 0 && curLineIdx < this.cachedLines.size()) {
                LineInfo curLine = this.cachedLines.get(curLineIdx);
                int offsetInLine = Mth.clamp(this.cursorPos - curLine.start, 0, curLine.text.length());
                String subStr = curLine.text.substring(0, offsetInLine);
                cursorOffsetInLineX = subStr.isEmpty() ? 0 : font.width(FontManagerHelper.apply(subStr));
            }

            int cursorAbsX = this.x() + 8 + (int) (cursorOffsetInLineX * DEFAULT_TEXT_SCALE);
            int cursorAbsY = this.y() + 7 + (int) (curLineIdx * font.lineHeight);

            if (this.preeditOverlay != null) {
                this.preeditOverlay.updateInputPosition(cursorAbsX, cursorAbsY);
                graphics.setPreeditOverlay(this.preeditOverlay);
            }

            var client = Minecraft.getInstance();
            if (client.textInputManager() != null) {
                int x0 = cursorAbsX;
                int y0 = cursorAbsY;
                int x1 = cursorAbsX + 2;
                int y1 = cursorAbsY + font.lineHeight;
                client.textInputManager().setTextInputArea(x0, y0, x1, y1);
            }
        }

        drawRightHint(graphics, font, halfW, textStartY, textScale, this.width(), delta);
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
            int color = GuiColorManager.withAlpha(GuiColorManager.textFaint(), alpha);

            graphics.pose().pushMatrix();
            graphics.pose().translate(halfW - 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledRightHint, 0, 0, 1.0f, color, OwoUIGraphics.TextAnchor.TOP_RIGHT);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (super.onMouseDown(click, doubled)) return true;

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
            updateImeFocus(true);

            double localX = click.x() >= this.x() ? (click.x() - this.x()) : click.x();
            double localY = click.y() >= this.y() ? (click.y() - this.y()) : click.y();

            var font = Minecraft.getInstance().font;
            int clickedIdx = getCharIndexAt(font, localX, localY);

            this.cursorPos = clickedIdx;
            if (!isShiftPressed()) {
                this.selectionAnchor = clickedIdx;
            }

            this.isDraggingSelection = true;
            clampCursor();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.isDraggingSelection && !this.readOnly && this.focused) {
            var font = Minecraft.getInstance().font;
            double localX = click.x() >= this.x() ? (click.x() - this.x()) : click.x();
            double localY = click.y() >= this.y() ? (click.y() - this.y()) : click.y();

            this.cursorPos = getCharIndexAt(font, localX, localY);
            clampCursor();
            resetCursorBlink();
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (this.isDraggingSelection) {
            this.isDraggingSelection = false;
            return true;
        }
        return super.onMouseUp(click);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (this.readOnly || !this.focused) return false;
        this.preeditOverlay = null;
        int codepoint = input.codepoint();

        if (codepoint >= 32 && codepoint != 127) {
            if (hasSelection()) {
                deleteSelection();
            }
            clampCursor();

            String inserted = new String(Character.toChars(codepoint));
            this.text = this.text.substring(0, this.cursorPos) + inserted + this.text.substring(this.cursorPos);
            this.cursorPos += inserted.length();
            this.selectionAnchor = this.cursorPos;

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
            String val = this.text.trim();
            if (this.onEnterSubmit != null) {
                this.onEnterSubmit.accept(val);
            }
            clearSelection();
            return true;
        }

        // 全选 Ctrl+A
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }

        boolean shift = input.hasShiftDown();
        boolean ctrl = input.hasControlDown();

        // 光标左移
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            if (shift) {
                this.cursorPos = ctrl ? findPreviousWord(this.text, this.cursorPos) : Math.max(0, this.cursorPos - 1);
            } else {
                if (hasSelection()) {
                    this.cursorPos = getSelectionStart();
                    this.selectionAnchor = this.cursorPos;
                } else {
                    this.cursorPos = ctrl ? findPreviousWord(this.text, this.cursorPos) : Math.max(0, this.cursorPos - 1);
                    this.selectionAnchor = this.cursorPos;
                }
            }
            resetCursorBlink();
            return true;
        }

        // 光标右移
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (shift) {
                this.cursorPos = ctrl ? findNextWord(this.text, this.cursorPos) : Math.min(this.text.length(), this.cursorPos + 1);
            } else {
                if (hasSelection()) {
                    this.cursorPos = getSelectionEnd();
                    this.selectionAnchor = this.cursorPos;
                } else {
                    this.cursorPos = ctrl ? findNextWord(this.text, this.cursorPos) : Math.min(this.text.length(), this.cursorPos + 1);
                    this.selectionAnchor = this.cursorPos;
                }
            }
            resetCursorBlink();
            return true;
        }

        // 光标上移
        var font = Minecraft.getInstance().font;
        updateLayoutCache(font, Math.max(154, this.width() - 16));

        if (input.key() == GLFW.GLFW_KEY_UP) {
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
            if (!shift) {
                this.selectionAnchor = this.cursorPos;
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // 光标下移
        if (input.key() == GLFW.GLFW_KEY_DOWN) {
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
            if (!shift) {
                this.selectionAnchor = this.cursorPos;
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // Home
        if (input.key() == GLFW.GLFW_KEY_HOME) {
            if (ctrl) {
                this.cursorPos = 0;
            } else {
                int curLineIdx = getCursorLineIndex(this.cachedLines);
                this.cursorPos = this.cachedLines.get(curLineIdx).start;
            }
            if (!shift) {
                this.selectionAnchor = this.cursorPos;
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // End
        if (input.key() == GLFW.GLFW_KEY_END) {
            if (ctrl) {
                this.cursorPos = this.text.length();
            } else {
                int curLineIdx = getCursorLineIndex(this.cachedLines);
                this.cursorPos = this.cachedLines.get(curLineIdx).end;
            }
            if (!shift) {
                this.selectionAnchor = this.cursorPos;
            }
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // Backspace
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
                return true;
            }
            clampCursor();
            if (this.cursorPos > 0) {
                this.text = this.text.substring(0, this.cursorPos - 1) + this.text.substring(this.cursorPos);
                this.cursorPos--;
                this.selectionAnchor = this.cursorPos;
                notifyTextChanged();
                resetCursorBlink();
            }
            return true;
        }

        // Delete
        if (input.key() == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
                return true;
            }
            clampCursor();
            if (this.cursorPos < this.text.length()) {
                this.text = this.text.substring(0, this.cursorPos) + this.text.substring(this.cursorPos + 1);
                notifyTextChanged();
                resetCursorBlink();
            }
            return true;
        }

        // 剪贴板粘贴 (Ctrl + V)
        if (ctrl && input.key() == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String cleanClip = clip.replaceAll("[\r\n]", "");
                if (hasSelection()) {
                    deleteSelection();
                }
                clampCursor();
                this.text = this.text.substring(0, this.cursorPos) + cleanClip + this.text.substring(this.cursorPos);
                this.cursorPos += cleanClip.length();
                this.selectionAnchor = this.cursorPos;
                notifyTextChanged();
                resetCursorBlink();
            }
            return true;
        }

        // 复制 (Ctrl + C)
        if (ctrl && input.key() == GLFW.GLFW_KEY_C) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
            }
            return true;
        }

        // 剪切 (Ctrl + X)
        if (ctrl && input.key() == GLFW.GLFW_KEY_X) {
            if (!this.readOnly && hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                deleteSelection();
            }
            return true;
        }

        return super.onKeyPress(input);
    }

    private static String sequenceToString(FormattedCharSequence sequence) {
        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
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