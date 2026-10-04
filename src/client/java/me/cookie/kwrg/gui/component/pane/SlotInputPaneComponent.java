package me.cookie.kwrg.gui.component.pane;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.BasePaneComponent;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class SlotInputPaneComponent extends BasePaneComponent {

    private static final int BASE_HEIGHT = 26;

    private final String leftLabel;
    private final Component styledLeftLabel;

    private final int slotCount;
    private final String[] slotValues;
    private final String[] slotSuffixes;
    private final Component[] styledSlotSuffixes;
    private final float[] slotFocusProgress;

    private int activeSlot = -1;
    private int cursorPos = 0;
    private boolean selectAll = false;
    private long lastActionTime = 0;

    private Consumer<String[]> onValuesChanged = null;
    private Consumer<String[]> onValuesSubmit = null;

    private double[][] slotBounds = null;
    private double dragStep = 1.0;
    private int decimalPrecision = 0;
    private boolean isDraggingSlot = false;
    private double dragAccumulator = 0.0;
    private double dragSensitivity = 1.2;
    private double presetDragSensitivity = 16.0;

    private boolean isMultiplicative = false;
    private double dragMultiplier = 1.1;
    private final List<String>[] slotPresets;

    @SuppressWarnings("unchecked")
    public SlotInputPaneComponent(String leftLabel, String[] initialValues, String[] suffixes,
                                  Consumer<String[]> onValuesSubmit, Consumer<String[]> onValuesChanged) {
        super(BASE_HEIGHT);
        this.leftLabel = leftLabel != null ? leftLabel : "";
        this.styledLeftLabel = FontManagerHelper.apply(this.leftLabel);

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
        this.slotPresets = (List<String>[]) new List[count];

        for (int i = 0; i < count; i++) {
            this.slotValues[i] = (initialValues != null && i < initialValues.length && initialValues[i] != null) ? initialValues[i] : "";
            this.slotSuffixes[i] = (suffixes != null && i < suffixes.length && suffixes[i] != null) ? suffixes[i] : "";
            this.styledSlotSuffixes[i] = FontManagerHelper.apply(this.slotSuffixes[i]);
            this.slotFocusProgress[i] = 0.0f;
            this.slotPresets[i] = null;
        }

        this.onValuesSubmit = onValuesSubmit;
        this.onValuesChanged = onValuesChanged;

        this.cursorStyle(CursorStyle.TEXT);
    }

    public SlotInputPaneComponent(String leftLabel, String[] suffixes, Consumer<String[]> onValuesSubmit) {
        this(leftLabel, null, suffixes, onValuesSubmit, null);
    }

    public SlotInputPaneComponent bounds(double[][] bounds) {
        this.slotBounds = bounds;
        return this;
    }

    public SlotInputPaneComponent bounds(int[][] bounds) {
        if (bounds == null) {
            this.slotBounds = null;
            return this;
        }
        this.slotBounds = new double[bounds.length][2];
        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i] != null) {
                this.slotBounds[i][0] = bounds[i][0];
                this.slotBounds[i][1] = bounds[i][1];
            }
        }
        return this;
    }

    public SlotInputPaneComponent dragStep(double step) {
        this.dragStep = step;
        this.isMultiplicative = false;
        return this;
    }

    public SlotInputPaneComponent dragMultiplier(double multiplier) {
        this.dragMultiplier = Math.max(1.0001, multiplier);
        this.isMultiplicative = true;
        return this;
    }

    public SlotInputPaneComponent dragSensitivity(double sensitivity) {
        this.dragSensitivity = Math.max(0.1, sensitivity);
        return this;
    }

    public SlotInputPaneComponent presetDragSensitivity(double sensitivity) {
        this.presetDragSensitivity = Math.max(1.0, sensitivity);
        return this;
    }

    public SlotInputPaneComponent precision(int precision) {
        this.decimalPrecision = Math.max(0, precision);
        return this;
    }

    public SlotInputPaneComponent presets(List<?> presets) {
        for (int i = 0; i < this.slotCount; i++) {
            presetSlot(i, presets);
        }
        return this;
    }

    public SlotInputPaneComponent presets(Object... presets) {
        List<String> list = new ArrayList<>();
        if (presets != null) {
            for (Object obj : presets) {
                if (obj != null) list.add(String.valueOf(obj));
            }
        }
        return presets(list);
    }

    public SlotInputPaneComponent presetSlot(int slotIndex, List<?> presets) {
        if (slotIndex >= 0 && slotIndex < this.slotCount) {
            if (presets == null || presets.isEmpty()) {
                this.slotPresets[slotIndex] = null;
            } else {
                List<String> strList = new ArrayList<>();
                for (Object obj : presets) {
                    if (obj != null) strList.add(String.valueOf(obj));
                }
                this.slotPresets[slotIndex] = strList;
            }
        }
        return this;
    }

    public SlotInputPaneComponent presetSlot(int slotIndex, Object... presets) {
        List<String> list = new ArrayList<>();
        if (presets != null) {
            for (Object obj : presets) {
                if (obj != null) list.add(String.valueOf(obj));
            }
        }
        return presetSlot(slotIndex, list);
    }

    public String[] getSlotValues() {
        return this.slotValues.clone();
    }

    public SlotInputPaneComponent setSlotValues(String[] values) {
        if (values != null) {
            for (int i = 0; i < Math.min(this.slotCount, values.length); i++) {
                this.slotValues[i] = values[i] != null ? values[i] : "";
            }
            if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
                this.cursorPos = this.slotValues[this.activeSlot].length();
            }
        }
        return this;
    }

    public SlotInputPaneComponent onValuesChanged(Consumer<String[]> onValuesChanged) {
        this.onValuesChanged = onValuesChanged;
        return this;
    }

    public SlotInputPaneComponent onValuesSubmit(Consumer<String[]> onValuesSubmit) {
        this.onValuesSubmit = onValuesSubmit;
        return this;
    }

    private boolean hasSlotPresets(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slotCount && this.slotPresets[slotIndex] != null && !this.slotPresets[slotIndex].isEmpty();
    }

    private double clampSlotValue(int slotIndex, double value) {
        if (this.slotBounds != null && slotIndex >= 0 && slotIndex < this.slotBounds.length && this.slotBounds[slotIndex] != null) {
            return Mth.clamp(value, this.slotBounds[slotIndex][0], this.slotBounds[slotIndex][1]);
        }
        return value;
    }

    private String formatSlotValue(double value) {
        if (this.decimalPrecision <= 0) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%." + this.decimalPrecision + "f", value);
    }

    private void notifyValuesChanged() {
        if (this.onValuesChanged != null) {
            this.onValuesChanged.accept(this.slotValues);
        }
    }

    private void clampCursor() {
        if (this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            this.cursorPos = Mth.clamp(this.cursorPos, 0, this.slotValues[this.activeSlot].length());
        } else {
            this.cursorPos = 0;
        }
    }

    private void resetCursorBlink() {
        this.lastActionTime = System.currentTimeMillis();
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        this.focused = true;
        if (this.activeSlot == -1) {
            this.activeSlot = 0;
            this.cursorPos = this.slotValues[0].length();
            this.selectAll = !this.slotValues[0].isEmpty();
        }
        clampCursor();
        resetCursorBlink();
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        this.focused = false;
        this.selectAll = false;

        boolean actualChanged = false;
        if (this.slotBounds != null) {
            for (int i = 0; i < this.slotCount; i++) {
                if (hasSlotPresets(i)) continue;

                String cur = this.slotValues[i].trim();
                if (cur.isEmpty()) {
                    this.slotValues[i] = formatSlotValue(this.slotBounds[i][0]);
                    actualChanged = true;
                } else {
                    try {
                        double val = Double.parseDouble(cur);
                        double clamped = clampSlotValue(i, val);
                        String formatted = formatSlotValue(clamped);
                        if (!formatted.equals(cur)) {
                            this.slotValues[i] = formatted;
                            actualChanged = true;
                        }
                    } catch (NumberFormatException ignored) {
                        this.slotValues[i] = formatSlotValue(this.slotBounds[i][0]);
                        actualChanged = true;
                    }
                }
            }
        }

        if (actualChanged) {
            notifyValuesChanged();
        }
        this.activeSlot = -1;
    }

    @Override
    protected void renderPaneContent(OwoUIGraphics graphics, Font font, float halfW, float halfH,
                                     float textStartY, float textScale, float delta) {
        if (!this.leftLabel.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-halfW + 8.0f, textStartY);
            graphics.pose().scale(textScale, textScale);
            graphics.drawText(this.styledLeftLabel, 0, 0, 1.0f, 0xFF90A4AE, OwoUIGraphics.TextAnchor.TOP_LEFT);
            graphics.pose().popMatrix();
        }

        float slotLineWidth = this.slotCount == 1 ? (this.decimalPrecision > 0 ? 38.0f : 32.0f) : (this.decimalPrecision > 0 ? 30.0f : 26.0f);
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

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (click.button() == 0 && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            this.dragAccumulator += deltaX;

            if (!this.isDraggingSlot && Math.abs(this.dragAccumulator) >= 2.0) {
                this.isDraggingSlot = true;
                this.selectAll = false;
            }

            if (this.isDraggingSlot) {
                boolean hasPreset = hasSlotPresets(this.activeSlot);
                double sensitivity = hasPreset ? this.presetDragSensitivity : this.dragSensitivity;

                int deltaStep = (int) (this.dragAccumulator / sensitivity);
                if (deltaStep != 0) {
                    this.dragAccumulator -= deltaStep * sensitivity;

                    if (hasPreset) {
                        List<String> presets = this.slotPresets[this.activeSlot];
                        String currentStr = this.slotValues[this.activeSlot].trim();
                        int currentIndex = presets.indexOf(currentStr);

                        if (currentIndex == -1) {
                            for (int idx = 0; idx < presets.size(); idx++) {
                                if (presets.get(idx).equalsIgnoreCase(currentStr)) {
                                    currentIndex = idx;
                                    break;
                                }
                            }
                        }

                        if (currentIndex == -1) {
                            currentIndex = deltaStep > 0 ? -1 : presets.size();
                        }

                        int newIndex = Mth.clamp(currentIndex + deltaStep, 0, presets.size() - 1);
                        this.slotValues[this.activeSlot] = presets.get(newIndex);
                    } else if (this.isMultiplicative) {
                        String currentStr = this.slotValues[this.activeSlot].trim();
                        double currentVal = 0.0;
                        if (!currentStr.isEmpty()) {
                            try {
                                currentVal = Double.parseDouble(currentStr);
                            } catch (NumberFormatException ignored) {}
                        }

                        if (Math.abs(currentVal) < 1e-9) {
                            currentVal = (deltaStep > 0 ? 1 : -1) * (this.dragStep > 0 ? this.dragStep : 1.0);
                            deltaStep = deltaStep > 0 ? deltaStep - 1 : deltaStep + 1;
                        }

                        double factor = Math.pow(this.dragMultiplier, Math.abs(deltaStep));
                        double newVal = deltaStep > 0 ? (currentVal * factor) : (currentVal / factor);
                        newVal = clampSlotValue(this.activeSlot, newVal);
                        this.slotValues[this.activeSlot] = formatSlotValue(newVal);
                    } else {
                        String currentStr = this.slotValues[this.activeSlot].trim();
                        double currentVal = 0.0;
                        if (!currentStr.isEmpty()) {
                            try {
                                currentVal = Double.parseDouble(currentStr);
                            } catch (NumberFormatException ignored) {}
                        }

                        double newVal = currentVal + deltaStep * this.dragStep;
                        newVal = clampSlotValue(this.activeSlot, newVal);
                        this.slotValues[this.activeSlot] = formatSlotValue(newVal);
                    }

                    this.cursorPos = this.slotValues[this.activeSlot].length();
                    notifyValuesChanged();
                    resetCursorBlink();
                }
                return true;
            }
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (click.button() == 0 && this.isDraggingSlot) {
            this.isDraggingSlot = false;
            this.dragAccumulator = 0.0;
            this.selectAll = false;
            return true;
        }
        return super.onMouseUp(click);
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (super.onMouseDown(click, doubled)) return true;

        if (click.button() == 0) {
            this.focused = true;
            resetCursorBlink();

            double localX = click.x() >= this.x() ? (click.x() - this.x()) : click.x();

            var font = Minecraft.getInstance().font;
            float textScale = DEFAULT_TEXT_SCALE;
            float slotLineWidth = this.slotCount == 1 ? (this.decimalPrecision > 0 ? 38.0f : 32.0f) : (this.decimalPrecision > 0 ? 30.0f : 26.0f);
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
        return false;
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (!this.focused || this.activeSlot < 0 || this.activeSlot >= this.slotCount) return false;
        char c = (char) input.codepoint();
        if (c >= 32 && c != 127) {
            String curVal = this.selectAll ? "" : this.slotValues[this.activeSlot];
            if (this.selectAll) {
                this.cursorPos = 0;
                this.selectAll = false;
            }
            clampCursor();

            if (this.slotBounds != null) {
                boolean isDigit = c >= '0' && c <= '9';
                boolean isDot = (c == '.' && this.decimalPrecision > 0 && !curVal.contains("."));
                boolean isMinus = (c == '-' && this.cursorPos == 0 && !curVal.contains("-") && this.slotBounds[this.activeSlot][0] < 0);

                if (!isDigit && !isDot && !isMinus) return false;
            }

            String candidate = curVal.substring(0, this.cursorPos) + c + curVal.substring(this.cursorPos);

            if (this.slotBounds != null) {
                if (!candidate.equals("-") && !candidate.equals(".") && !candidate.equals("-.")) {
                    try {
                        double num = Double.parseDouble(candidate);
                        double max = this.slotBounds[this.activeSlot][1];
                        if (num > max) {
                            candidate = formatSlotValue(max);
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
            } else {
                this.cursorPos++;
            }

            this.slotValues[this.activeSlot] = candidate;
            notifyValuesChanged();
            resetCursorBlink();
            return true;
        }
        return super.onCharTyped(input);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        if (!this.focused) return false;

        // 回车提交
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.onValuesSubmit != null) {
                this.onValuesSubmit.accept(this.slotValues);
            }
            this.selectAll = false;
            return true;
        }

        // Tab / Shift+Tab 跨槽切换
        if (input.key() == GLFW.GLFW_KEY_TAB && this.slotCount > 1) {
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

        // 全选 Ctrl+A
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_A) {
            if (this.activeSlot >= 0 && this.activeSlot < this.slotCount && !this.slotValues[this.activeSlot].isEmpty()) {
                this.selectAll = true;
            }
            return true;
        }

        // 左右移动
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = 0;
            } else if (this.cursorPos > 0) {
                this.cursorPos--;
            } else if (this.activeSlot > 0) {
                this.activeSlot--;
                this.cursorPos = this.slotValues[this.activeSlot].length();
            }
            resetCursorBlink();
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            if (this.selectAll) {
                this.selectAll = false;
                this.cursorPos = this.slotValues[this.activeSlot].length();
            } else if (this.cursorPos < this.slotValues[this.activeSlot].length()) {
                this.cursorPos++;
            } else if (this.activeSlot < this.slotCount - 1) {
                this.activeSlot++;
                this.cursorPos = 0;
            }
            resetCursorBlink();
            return true;
        }

        // Backspace
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            if (this.selectAll) {
                this.slotValues[this.activeSlot] = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyValuesChanged();
                resetCursorBlink();
                return true;
            }

            String cur = this.slotValues[this.activeSlot];
            clampCursor();
            if (this.cursorPos > 0) {
                this.slotValues[this.activeSlot] = cur.substring(0, this.cursorPos - 1) + cur.substring(this.cursorPos);
                this.cursorPos--;
                notifyValuesChanged();
                resetCursorBlink();
            } else if (this.activeSlot > 0) {
                this.activeSlot--;
                this.cursorPos = this.slotValues[this.activeSlot].length();
                resetCursorBlink();
            }
            return true;
        }

        // Delete
        if (input.key() == GLFW.GLFW_KEY_DELETE && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            if (this.selectAll) {
                this.slotValues[this.activeSlot] = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyValuesChanged();
                resetCursorBlink();
                return true;
            }

            String cur = this.slotValues[this.activeSlot];
            clampCursor();
            if (this.cursorPos < cur.length()) {
                this.slotValues[this.activeSlot] = cur.substring(0, this.cursorPos) + cur.substring(this.cursorPos + 1);
                notifyValuesChanged();
                resetCursorBlink();
            }
            return true;
        }

        // 剪贴板支持
        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_V && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String cleanClip = clip.replaceAll("[\r\n]", "");
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
                notifyValuesChanged();
                resetCursorBlink();
            }
            return true;
        }

        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_C && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.slotValues[this.activeSlot]);
            return true;
        }

        if (input.hasControlDown() && input.key() == GLFW.GLFW_KEY_X && this.activeSlot >= 0 && this.activeSlot < this.slotCount) {
            if (!this.slotValues[this.activeSlot].isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.slotValues[this.activeSlot]);
                this.slotValues[this.activeSlot] = "";
                this.cursorPos = 0;
                this.selectAll = false;
                notifyValuesChanged();
                resetCursorBlink();
            }
            return true;
        }

        return super.onKeyPress(input);
    }
}