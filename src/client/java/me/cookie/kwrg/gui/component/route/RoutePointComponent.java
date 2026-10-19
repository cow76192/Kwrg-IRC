package me.cookie.kwrg.gui.component.route;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.gui.PointCollectionStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** One route point, with an editable name, three decimal inputs and a visibility switch. */
public final class RoutePointComponent {
    public static final int HEIGHT = 40;
    private int index;
    private final PointCollectionStore store;
    private final PointEntry entry;
    private final EditBox[] inputs = new EditBox[3];
    private final EditBox nameInput;
    private final float[] focusProgress = new float[4];
    private final RouteButtonComponent visibleButton = new RouteButtonComponent();
    private final RouteButtonComponent deleteButton = new RouteButtonComponent();
    private int x, y, width;
    private long lastFrame = System.currentTimeMillis();
    private final RouteNodeAnimation appearance = new RouteNodeAnimation();

    public PointEntry entry() { return entry; }
    public void setIndex(int index) { this.index = index; }
    public RouteButtonComponent deleteButton() { return deleteButton; }
    public void remove(long now) { blur(); appearance.remove(now); }
    public boolean removalFinished(long now) { return appearance.finished(now); }
    public float remaining(long now) { return appearance.remaining(now); }
    private boolean hideDeleteButton;
    public void hideDeleteButton(boolean hide) { hideDeleteButton = hide; }

    public RoutePointComponent(int index, PointEntry entry, PointCollectionStore store, Runnable changed) {
        this.index = index;
        this.store = store;
        this.entry = entry;
        nameInput = new EditBox(FontManagerHelper.getGuiInputFont(), 0, 0, 100, 16, Component.literal("点位名称"));
        nameInput.setBordered(false);
        nameInput.setTextShadow(false);
        nameInput.setMaxLength(64);
        nameInput.setValue(entry.name);
        nameInput.setHint(Component.literal("点位名称"));
        nameInput.setResponder(value -> {
            entry.name = value;
            changed.run();
        });
        String[] values = {entry.x, entry.y, entry.z};
        for (int axis = 0; axis < 3; axis++) {
            int currentAxis = axis;
            DecimalInput input = new DecimalInput(Component.literal("XYZ".substring(axis, axis + 1)));
            input.setBordered(false);
            input.setTextShadow(false);
            input.setMaxLength(24);
            input.setValue(values[axis]);
            input.onChanged = value -> {
                switch (currentAxis) { case 0 -> entry.x = value; case 1 -> entry.y = value; case 2 -> entry.z = value; }
                changed.run();
            };
            inputs[axis] = input;
        }
    }

    public void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        int inputW = Math.max(28, Math.min(64, (width - 150) / 3));
        int startX = x + width - 92 - (inputW + 12) * 3;
        nameInput.setX(x + 28);
        nameInput.setY(y + 16);
        nameInput.setWidth(Math.max(12, startX - x - 40));
        for (int axis = 0; axis < 3; axis++) {
            inputs[axis].setX(startX + (inputW + 12) * axis);
            inputs[axis].setY(y + 16);
            inputs[axis].setWidth(inputW);
        }
        visibleButton.bounds(x + width - 80, y + 10, 52, 22);
        deleteButton.bounds(x + width - 24, y + 10, 20, 22);
    }

    public void render(GuiGraphicsExtractor graphics, OwoUIGraphics owo, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float appear = appearance.progress(now);
        if (appear <= 0.005f) return;
        owo = RenderUtil.beginRowAppearance(owo, x, y, width, HEIGHT, appear);
        float step = (float) (1 - Math.exp(-(now - lastFrame) / 90.0));
        lastFrame = now;
        boolean valid = entry.valid();
        RenderUtil.drawSubpixelRoundedPanel(owo, x, y + 2, width, HEIGHT - 4, 7,
                Color.ofArgb(valid ? 0xA0192431 : 0xB038242A), Color.ofArgb(valid ? 0x35394A5A : 0xAAAF5D65));
        RenderUtil.drawSmoothText(owo, Component.literal((index + 1) + "."), x + 6, y + 17, 0.72f, 0xFF81929F);
        for (int field = 0; field < 4; field++) {
            EditBox input = field(field);
            focusProgress[field] = AnimationUtil.lerp(step, focusProgress[field], input.isFocused() ? 1 : 0);
            Color line = AnimationUtil.lerpColor(focusProgress[field], Color.ofArgb(0xFF546979), Color.ofArgb(0xFF80D7FF));
            RenderUtil.drawSmoothText(owo, Component.literal(field == 0 ? entry.temporary ? "名称 · 临时" : "名称" : "XYZ".substring(field - 1, field)), input.getX(), y + 5, 0.62f, 0xFF819BAF);
            RenderUtil.fillSubpixel(owo, input.getX(), y + 32, input.getX() + input.getWidth(), y + 32.8f, line.argb());
            input.setTextColor(field == 0 ? (entry.visible ? 0xFFE0ECF4 : 0xFF81929F) : valid ? 0xFFECEFF1 : 0xFFFFB4BC);
            // EditBox does not apply its widget alpha to text; use the row's fading graphics.
            input.extractRenderState(owo, mouseX, mouseY, partialTick);
        }
        visibleButton.draw(owo, entry.visible ? "显示" : "隐藏", entry.visible, mouseX, mouseY);
        if (!hideDeleteButton) deleteButton.draw(owo, "×", false, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    /** @return true when the caller should ask for deletion confirmation. */
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0 || appearance.progress(System.currentTimeMillis()) < 0.95f) return false;
        if (visibleButton.contains(click.x(), click.y())) {
            visibleButton.press();
            entry.visible = !entry.visible;
            store.saveCurrent();
        } else if (deleteButton.contains(click.x(), click.y())) {
            deleteButton.press();
            return true;
        } else {
            for (int field = 0; field < 4; field++) {
                EditBox input = field(field);
                boolean selected = input.isMouseOver(click.x(), click.y());
                input.setFocused(selected);
                if (selected) input.mouseClicked(click, doubled);
            }
        }
        return false;
    }

    private EditBox field(int index) { return index == 0 ? nameInput : inputs[index - 1]; }
    public EditBox clickedInput(double x, double y) {
        for (int i = 0; i < 4; i++) {
            EditBox input = field(i);
            if (input.isFocused() && input.isMouseOver(x, y)) return input;
        }
        return null;
    }
    public void blur() { for (int i = 0; i < 4; i++) field(i).setFocused(false); }
    public int focusedField() {
        for (int i = 0; i < 4; i++) if (field(i).isFocused()) return i;
        return -1;
    }
    public void focus(int index) {
        blur();
        EditBox input = field(index);
        input.setFocused(true);
        input.setHighlightPos(0);
        input.setCursorPosition(input.getValue().length());
    }
    public boolean keyPressed(KeyEvent input) {
        for (int i = 0; i < 4; i++) if (field(i).isFocused()) return field(i).keyPressed(input);
        return false;
    }
    public boolean charTyped(CharacterEvent input) {
        for (int i = 0; i < 4; i++) if (field(i).isFocused()) return field(i).charTyped(input);
        return false;
    }

    public boolean preeditUpdated(PreeditEvent input) {
        return nameInput.isFocused() && nameInput.preeditUpdated(input);
    }

    private static final class DecimalInput extends EditBox {
        private Consumer<String> onChanged = value -> {};
        private boolean inserting;

        private DecimalInput(Component label) {
            super(FontManagerHelper.getGuiInputFont(), 0, 0, 60, 16, label);
            setResponder(value -> { if (!inserting) onChanged.accept(value); });
        }

        @Override
        public void insertText(String text) {
            String previous = getValue();
            int cursor = getCursorPosition();
            inserting = true;
            try {
                super.insertText(text);
                if (!getValue().matches("-?\\d*(?:\\.\\d*)?")) {
                    setValue(previous);
                    setCursorPosition(cursor);
                }
            } finally {
                inserting = false;
            }
            if (!previous.equals(getValue())) onChanged.accept(getValue());
        }
    }
}
