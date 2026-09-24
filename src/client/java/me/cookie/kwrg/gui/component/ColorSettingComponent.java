package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.util.ColorUtil;

import java.util.function.Consumer;

public class ColorSettingComponent extends FlowLayout {

    private final ColorPickerComponent picker;
    private final Consumer<String> onColorChanged;
    private TextInputPaneComponent hexInput;
    private TextInputPaneComponent rgbInput;

    private int currentRgbInt = 0x00F2FE;
    private boolean applyingProgrammatic = false;

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged) {
        this(initialColorStr, onColorChanged, true);
    }

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged, boolean showInfoPanes) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.margins(Insets.of(2, 0, 0, 0));
        this.surface(Surface.BLANK);
        this.onColorChanged = onColorChanged;

        this.picker = new ColorPickerComponent();
        this.picker.sizing(Sizing.fill(100), Sizing.fixed(120));
        this.picker.selectorWidth(12);
        this.picker.selectorPadding(6);
        this.picker.showAlpha(false);
        this.picker.margins(Insets.bottom(showInfoPanes ? 6 : 2));

        java.awt.Color currentColor = ColorUtil.parseSingleColor(initialColorStr);
        if (currentColor != null) {
            this.currentRgbInt = currentColor.getRGB() & 0xFFFFFF;
            this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));
        } else {
            this.picker.selectedColor(Color.ofRgb(0x00F2FE));
        }

        this.child(this.picker);

        if (showInfoPanes) {
            this.hexInput = new TextInputPaneComponent("", input -> {
                java.awt.Color c = ColorUtil.parseHexLoose(input);
                if (c != null) {
                    commitColor(c.getRGB() & 0xFFFFFF);
                } else {
                    NotificationManager.err("无法解析 HEX", "请输入3位或6位#HEX颜色");
                    this.hexInput.setText(getHex());
                }
            }, "#RRGGBB");

            this.rgbInput = new TextInputPaneComponent("", input -> {
                java.awt.Color c = ColorUtil.parseRgbLoose(input);
                if (c != null) {
                    commitColor(c.getRGB() & 0xFFFFFF);
                } else {
                    NotificationManager.err("无法解析 RGB", "支持格式rgb(r,g,b)或r_g_b");
                    this.rgbInput.setText(getRgb());
                }
            }, "r g b");

            this.hexInput.setText(getHex());
            this.rgbInput.setText(getRgb());

            this.child(this.hexInput);
            this.child(this.rgbInput);
        }

        this.picker.onChanged().subscribe(newColor -> {
            if (this.applyingProgrammatic) return;
            this.currentRgbInt = newColor.rgb() & 0xFFFFFF;
            syncInputTexts();
            if (this.onColorChanged != null) {
                this.onColorChanged.accept(getHex());
            }
        });
    }

    private void commitColor(int rgb) {
        this.currentRgbInt = rgb;

        this.applyingProgrammatic = true;
        this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));
        this.applyingProgrammatic = false;

        syncInputTexts();
        if (this.onColorChanged != null) {
            this.onColorChanged.accept(getHex());
        }
    }

    private void syncInputTexts() {
        if (this.hexInput != null) this.hexInput.setText(getHex());
        if (this.rgbInput != null) this.rgbInput.setText(getRgb());
    }

    public String getHex() {
        return String.format("#%06X", this.currentRgbInt);
    }

    public String getRgb() {
        int r = (this.currentRgbInt >> 16) & 0xFF;
        int g = (this.currentRgbInt >> 8) & 0xFF;
        int b = this.currentRgbInt & 0xFF;
        return String.format("rgb(%d, %d, %d)", r, g, b);
    }

    public int getRgbInt() {
        return this.currentRgbInt;
    }

    public ColorPickerComponent getPicker() {
        return this.picker;
    }
}