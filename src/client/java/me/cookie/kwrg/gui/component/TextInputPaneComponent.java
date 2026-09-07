package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public class TextInputPaneComponent extends BaseUIComponent {

    private static final int BG_COLOR_RGB = 0x161D26;
    private static final int BASE_HEIGHT = 26;

    private final String placeholder;
    private final Consumer<String> onEnterSubmit;
    private Consumer<String> onTextChanged = null;
    private Runnable onRightClick = null;
    private String text = "";

    // 全选状态标记
    private boolean selectAll = false;

    private boolean readOnly = false;
    private Runnable onReadOnlyClick = null;

    private boolean focused = false;
    private float hoverAnimation = 0.0f;
    private float currentAnimatedHeight = BASE_HEIGHT;

    public TextInputPaneComponent(String placeholder, Consumer<String> onEnterSubmit) {
        this.placeholder = placeholder;
        this.onEnterSubmit = onEnterSubmit;

        this.cursorStyle(CursorStyle.TEXT);
        this.sizing(Sizing.fill(100), Sizing.fixed(BASE_HEIGHT));
        this.margins(Insets.vertical(2));
    }

    public TextInputPaneComponent asButton(String text, Runnable onReadOnlyClick) {
        this.readOnly = true;
        this.text = text != null ? text : "";
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
        this.selectAll = false;
        if (this.onTextChanged != null) {
            this.onTextChanged.accept(this.text);
        }
        return this;
    }

    public String getText() {
        return this.text;
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

    @Override
    public boolean canFocus(FocusSource source) {
        return !this.readOnly;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        if (!this.readOnly) this.focused = true;
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        this.focused = false;
        this.selectAll = false;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        float targetHover = (this.hovered || this.focused) ? 1.0f : 0.0f;
        this.hoverAnimation = Mth.clamp(this.hoverAnimation + (targetHover - this.hoverAnimation) * delta * 0.35f, 0.0f, 1.0f);

        var font = Minecraft.getInstance().font;
        int stableWidth = Math.max(154, this.width() - 16);

        String displayText = this.text.isEmpty() ? this.placeholder : this.text;
        List<FormattedCharSequence> wrappedLines = font.split(Component.literal(displayText), stableWidth);
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

        // ① 绘制底盘
        RenderUtil.drawRoundedPanel(graphics, (int) -halfW, (int) -halfH, width, renderH, 6.0f, paneBg, borderColor);

        // ② 纯净单层文字渲染
        float textStartY = -halfH + 7.0f;
        float textScale = 0.85f;

        if (this.text.isEmpty() && !this.focused) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(Component.literal(this.placeholder), 0, 0, 1.0f, 0xFF607D8B, OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        } else {
            boolean showCursor = !this.readOnly && this.focused && (System.currentTimeMillis() % 1000 < 500);
            String textToDraw = this.text + (showCursor ? "_" : "");

            // 全选时高亮偏浅青蓝，正常聚焦为纯白
            int textColor = this.selectAll ? 0xFF80DEEA : (this.readOnly ? (this.hovered ? 0xFFFFFFFF : 0xFFB2EBF2) : (this.focused ? 0xFFFFFFFF : 0xFFECEFF1));

            List<FormattedCharSequence> linesToDraw = font.split(Component.literal(textToDraw), stableWidth);
            for (int i = 0; i < linesToDraw.size(); i++) {
                float curLineY = (int) (textStartY + i * font.lineHeight);
                String lineStr = sequenceToString(linesToDraw.get(i));

                graphics.pose().pushMatrix();
                graphics.pose().translate(-halfW + 8.0f, curLineY);
                graphics.pose().scale(textScale, textScale);

                // 全选时绘制浅色半透明选中高亮底色
                if (this.selectAll) {
                    int lineW = font.width(lineStr);
                    graphics.fill(-1, -1, lineW + 1, font.lineHeight, ARGB.color(100, 38, 198, 218));
                }

                graphics.drawText(Component.literal(lineStr), 0, 0, 1.0f, textColor, OwoUIGraphics.TextAnchor.TOP_LEFT);
                graphics.pose().popMatrix();
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
            this.selectAll = false; // 点击取消全选
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (this.readOnly || !this.focused) return false;
        char c = (char) input.codepoint();
        if (c >= 32 && c != 127) {
            // 【全选打字替换】：若处于全选状态，直接替换为新输入的字符
            if (this.selectAll) {
                this.text = "";
                this.selectAll = false;
            }
            this.text += c;
            notifyTextChanged();
            return true;
        }
        return super.onCharTyped(input);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        if (!this.focused) return false;

        // 回车提交
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            String val = this.text.trim();
            if (this.onEnterSubmit != null) {
                this.onEnterSubmit.accept(val);
            }
            this.selectAll = false;
            return true;
        }

        // 【新增】：Ctrl + A 全选 (Windows/Linux Ctrl 或 Mac Command)
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_A) {
            if (!this.text.isEmpty()) {
                this.selectAll = true;
            }
            return true;
        }

        // 退格删除
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.selectAll) {
                // 全选状态下按退格直接清空
                this.text = "";
                this.selectAll = false;
                notifyTextChanged();
                return true;
            } else if (!this.text.isEmpty()) {
                this.text = this.text.substring(0, this.text.length() - 1);
                notifyTextChanged();
                return true;
            }
        }

        // Ctrl + V 粘贴
        boolean isPasteKey = input.hasControlDown() && input.key() == GLFW.GLFW_KEY_V;
        if (isPasteKey) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String cleanClip = clip.replaceAll("[\r\n]", "");
                if (this.selectAll) {
                    this.text = cleanClip;
                    this.selectAll = false;
                } else {
                    this.text += cleanClip;
                }
                notifyTextChanged();
            }
            return true;
        }

        return super.onKeyPress(input);
    }
}