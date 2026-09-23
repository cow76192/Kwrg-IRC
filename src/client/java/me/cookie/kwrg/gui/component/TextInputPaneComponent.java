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
import java.util.List;
import java.util.function.Consumer;

public class TextInputPaneComponent extends BaseUIComponent {

    private static final int BG_COLOR_RGB = 0x161D26;
    private static final int BASE_HEIGHT = 26;

    private final String placeholder;
    private final String rightHint;
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
    private float rightHintWipe = 0.0f;

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit) {
        this(placeholder, onEnterSubmit, "");
    }

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit, String rightHint) {
        this.placeholder = placeholder != null ? placeholder : "";
        this.rightHint = rightHint != null ? rightHint : "";
        this.onEnterSubmit = onEnterSubmit;

        this.cursorStyle(CursorStyle.TEXT);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));
    }

    public TextInputPaneComponent asButton(String text, Runnable onReadOnlyClick) {
        this.readOnly = true;
        this.text = text != null ? text : "";
        this.cursorPos = this.text.length();
        this.onReadOnlyClick = onReadOnlyClick;
        this.cursorStyle(CursorStyle.HAND);
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
        if (this.onTextChanged != null) {
            this.onTextChanged.accept(this.text);
        }
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
        resetCursorBlink();
        return this;
    }

    public TextInputPaneComponent onTextChanged(Consumer<String> onTextChanged) {
        this.onTextChanged = onTextChanged;
        return this;
    }

    private void notifyTextChanged() {
        if (this.onTextChanged != null) {
            this.onTextChanged.accept(this.text);
        }
    }

    private void clampCursor() {
        this.cursorPos = Mth.clamp(this.cursorPos, 0, this.text.length());
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
            clampCursor();
            resetCursorBlink();
        }
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        this.focused = false;
        this.selectAll = false;
    }

    private record LineInfo(int start, int end, String text, Component styledText) {}

    private List<LineInfo> computeLines(Font font, int stableWidth) {
        List<LineInfo> result = new ArrayList<>();
        if (this.text.isEmpty()) {
            result.add(new LineInfo(0, 0, "", Component.literal("")));
            return result;
        }

        Component styledText = FontManagerHelper.apply(this.text);
        List<FormattedCharSequence> wrappedLines = font.split(styledText, stableWidth);
        if (wrappedLines.isEmpty()) {
            result.add(new LineInfo(0, 0, "", Component.literal("")));
            return result;
        }

        int searchIdx = 0;
        for (int i = 0; i < wrappedLines.size(); i++) {
            FormattedCharSequence seq = wrappedLines.get(i);
            String seqStr = sequenceToString(seq);
            int start = this.text.indexOf(seqStr, searchIdx);
            if (start == -1) {
                start = searchIdx;
            }

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
            result.add(new LineInfo(start, end, lineStr, styledLine));
            searchIdx = end;
        }
        return result;
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
        float targetHover = (this.hovered || this.focused) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        var font = Minecraft.getInstance().font;
        int stableWidth = Math.max(154, this.width() - 16);

        String displayText = this.text.isEmpty() ? this.placeholder : this.text;
        Component styledDisplayText = FontManagerHelper.apply(displayText);
        List<FormattedCharSequence> wrappedLines = font.split(styledDisplayText, stableWidth);
        int lineCount = Math.max(1, wrappedLines.size());
        int targetHeight = BASE_HEIGHT + (lineCount - 1) * font.lineHeight;

        this.currentAnimatedHeight = Mth.lerp(delta * 0.35f, this.currentAnimatedHeight, (float) targetHeight);
        int renderH = Math.max(BASE_HEIGHT, Math.round(this.currentAnimatedHeight));
        this.height = renderH;

        int x = this.x();
        int y = this.y();
        int width = this.width();

        Color paneBg = Color.ofRgb(BG_COLOR_RGB);
        int borderAlpha = (int) (40 + this.hoverAnimation * 180);
        int borderRgb = this.focused ? 0x90CAF9 : (this.readOnly ? 0x80DEEA : 0x90A4AE);
        Color borderColor = Color.ofArgb(ARGB.color(borderAlpha, (borderRgb >> 16) & 0xFF, (borderRgb >> 8) & 0xFF, borderRgb & 0xFF));

        float cx = x + width / 2.0f;
        float cy = y + renderH / 2.0f;
        float halfW = width / 2.0f;
        float halfH = renderH / 2.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);

        RenderUtil.drawRoundedPanel(graphics, (int) -halfW, (int) -halfH, width, renderH, 6.0f, paneBg, borderColor);

        float textStartY = -halfH + 7.0f;
        float textScale = 0.85f;

        //绘制光标开始
        boolean blink = (System.currentTimeMillis() - this.lastActionTime < 300) || (System.currentTimeMillis() % 1000 < 500);
        boolean showCursor = !this.readOnly && this.focused && blink && !this.text.isEmpty();

        int textColor = this.selectAll ? 0xFF80DEEA : (this.readOnly ? (this.hovered ? 0xFFFFFFFF : 0xFFB2EBF2) : (this.focused ? 0xFFFFFFFF : 0xFFECEFF1));

        List<LineInfo> lines = computeLines(font, stableWidth);
        int cursorLineIdx = getCursorLineIndex(lines);

        if (this.text.isEmpty() && !this.focused && !this.placeholder.isEmpty()) {
            Component styledPlaceholder = FontManagerHelper.apply(this.placeholder);
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(styledPlaceholder, 0, 0, 1.0f, 0xFF607D8B, OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        } else {
            for (int i = 0; i < lines.size(); i++) {
                LineInfo line = lines.get(i);
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
        //绘制光标结束

        //计算并调用righthint开始
        if (!this.rightHint.isEmpty()) {
            float inputW = 0.0f;
            boolean isWrapped = lines.size() > 1;
            if (!lines.isEmpty()) {
                inputW = font.width(lines.get(0).styledText) * textScale;
            }
            float usableW = width - 16.0f;
            float safeGap = 28.0f;
            boolean forceHide = (this.text.isEmpty() && !this.placeholder.isEmpty()) || isWrapped;
            this.rightHintWipe = renderRightHint(graphics, font, this.rightHint, halfW - 8.0f, textStartY, textScale, 0x607D8B, delta, this.rightHintWipe, inputW, usableW, safeGap, forceHide);
        } else {
            this.rightHintWipe = 0.0f;
        }

        graphics.pose().popMatrix();
    }
    //计算并调用righthint结束

    //静态渲染righthint完整方法开始
    public static float renderRightHint(OwoUIGraphics graphics, Font font, String hint,
                                        float rightEdgeX, float startY, float scale, int rgb,
                                        float delta, float currentWipe,
                                        float inputW, float usableW, float safeGap, boolean forceHide) {
        if (hint == null || hint.isEmpty()) return 0.0f;
        float hintW = font.width(FontManagerHelper.apply(hint)) * scale;

        float targetWipe;
        if (forceHide) {
            targetWipe = hintW + 100.0f;
        } else {
            float spaceForHint = usableW - inputW - safeGap;
            if (spaceForHint >= hintW) {
                targetWipe = 0.0f;
            } else if (spaceForHint <= 0) {
                targetWipe = hintW + 100.0f;
            } else {
                targetWipe = hintW - spaceForHint;
            }
        }

        float newWipe = currentWipe + (targetWipe - currentWipe) * Mth.clamp(delta * 0.40f, 0.0f, 1.0f);
        if (Math.abs(targetWipe - newWipe) < 0.5f) {
            newWipe = targetWipe;
        }

        int baseR = (rgb >> 16) & 0xFF;
        int baseG = (rgb >> 8) & 0xFF;
        int baseB = rgb & 0xFF;

        float startX = rightEdgeX - hintW;

        graphics.pose().pushMatrix();
        graphics.pose().translate(startX, startY);
        graphics.pose().scale(scale, scale);

        float charX = 0.0f;
        for (int i = 0; i < hint.length(); i++) {
            String ch = String.valueOf(hint.charAt(i));
            Component styledCh = FontManagerHelper.apply(ch);
            float chW = font.width(styledCh);

            float charAlpha;
            if (charX + chW <= newWipe) {
                charAlpha = 0.0f;
            } else if (charX >= newWipe) {
                charAlpha = 1.0f;
            } else {
                charAlpha = (charX + chW - newWipe) / chW;
            }

            if (charAlpha > 0.01f) {
                int a = (int) (255 * charAlpha);
                int finalColor = ARGB.color(a, baseR, baseG, baseB);
                graphics.drawText(styledCh, charX, 0, 1.0f, finalColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
            }
            charX += chW;
        }
        graphics.pose().popMatrix();

        graphics.pose().popMatrix();
    }
    //静态渲染righthint完整方法结束

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
            this.selectAll = false;
            resetCursorBlink();

            if (this.text.isEmpty()) {
                this.cursorPos = 0;
                return true;
            }

            var font = Minecraft.getInstance().font;
            float textScale = 0.85f;

            double localX = click.x() >= this.x() ? (click.x() - this.x()) : click.x();
            double localY = click.y() >= this.y() ? (click.y() - this.y()) : click.y();

            double textRelX = (localX - 8.0) / textScale;
            double textRelY = (localY - 7.0) / textScale;

            List<LineInfo> lines = computeLines(font, Math.max(154, this.width() - 16));
            if (lines.isEmpty()) {
                this.cursorPos = this.text.length();
                return true;
            }

            int lineIdx = (int) (textRelY / font.lineHeight);

            if (lineIdx >= lines.size()) {
                this.cursorPos = this.text.length();
                return true;
            }

            if (lineIdx < 0) {
                this.cursorPos = 0;
                return true;
            }

            LineInfo targetLine = lines.get(lineIdx);
            int lineWidth = font.width(targetLine.styledText);

            if (textRelX >= lineWidth) {
                if (lineIdx == lines.size() - 1) {
                    this.cursorPos = this.text.length();
                } else {
                    this.cursorPos = targetLine.end;
                }
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
            String val = this.text.trim();
            if (this.onEnterSubmit != null) {
                this.onEnterSubmit.accept(val);
            }
            this.selectAll = false;
            return true;
        }

        // 全选：Ctrl + A
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_A) {
            if (!this.text.isEmpty()) {
                this.selectAll = true;
            }
            return true;
        }

        // 光标左移
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = 0;
            } else if (input.hasControlDown()) {
                this.cursorPos = findPreviousWord(this.text, this.cursorPos);
            } else {
                this.cursorPos = Math.max(0, this.cursorPos - 1);
            }
            resetCursorBlink();
            return true;
        }

        // 光标右移
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = this.text.length();
            } else if (input.hasControlDown()) {
                this.cursorPos = findNextWord(this.text, this.cursorPos);
            } else {
                this.cursorPos = Math.min(this.text.length(), this.cursorPos + 1);
            }
            resetCursorBlink();
            return true;
        }

        // 光标上移
        if (input.key() == GLFW.GLFW_KEY_UP) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = 0;
                resetCursorBlink();
                return true;
            }
            var font = Minecraft.getInstance().font;
            List<LineInfo> lines = computeLines(font, Math.max(154, this.width() - 16));
            int curLineIdx = getCursorLineIndex(lines);
            if (curLineIdx > 0) {
                LineInfo curLine = lines.get(curLineIdx);
                LineInfo prevLine = lines.get(curLineIdx - 1);

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

        if (input.key() == GLFW.GLFW_KEY_DOWN) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = this.text.length();
                resetCursorBlink();
                return true;
            }
            var font = Minecraft.getInstance().font;
            List<LineInfo> lines = computeLines(font, Math.max(154, this.width() - 16));
            int curLineIdx = getCursorLineIndex(lines);
            if (curLineIdx < lines.size() - 1) {
                LineInfo curLine = lines.get(curLineIdx);
                LineInfo nextLine = lines.get(curLineIdx + 1);

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

        // Home 键行首 / Ctrl+Home 全文开头
        if (input.key() == GLFW.GLFW_KEY_HOME) {
            if (input.hasControlDown()) {
                this.cursorPos = 0;
            } else {
                var font = Minecraft.getInstance().font;
                List<LineInfo> lines = computeLines(font, Math.max(154, this.width() - 16));
                int curLineIdx = getCursorLineIndex(lines);
                this.cursorPos = lines.get(curLineIdx).start;
            }
            this.selectAll = false;
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // End 键行尾 / Ctrl+End 全文末尾
        if (input.key() == GLFW.GLFW_KEY_END) {
            if (input.hasControlDown()) {
                this.cursorPos = this.text.length();
            } else {
                var font = Minecraft.getInstance().font;
                List<LineInfo> lines = computeLines(font, Math.max(154, this.width() - 16));
                int curLineIdx = getCursorLineIndex(lines);
                this.cursorPos = lines.get(curLineIdx).end;
            }
            this.selectAll = false;
            clampCursor();
            resetCursorBlink();
            return true;
        }

        // 退格删除 (Backspace)
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
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
            return true;
        }

        return super.onKeyPress(input);
    }

    private static int findPreviousWord(String text, int cursor) {
        int pos = cursor - 1;
        while (pos > 0 && Character.isWhitespace(text.charAt(pos))) {
            pos--;
        }
        while (pos > 0 && !Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }
        return Math.max(0, pos);
    }

    private static int findNextWord(String text, int cursor) {
        int pos = cursor;
        int len = text.length();
        while (pos < len && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        while (pos < len && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return Math.min(len, pos);
    }
}