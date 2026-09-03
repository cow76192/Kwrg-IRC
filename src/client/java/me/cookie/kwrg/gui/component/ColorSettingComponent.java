package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.util.ColorUtil;

import java.util.function.Consumer;

public class ColorSettingComponent extends FlowLayout {

    private final ColorPickerComponent picker;
    private TextInfoPaneComponent hexPane;
    private TextInfoPaneComponent rgbPane;

    private int currentRgbInt = 0x00F2FE;

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged) {
        this(initialColorStr, onColorChanged, true);
    }

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged, boolean showInfoPanes) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.margins(Insets.of(2, 0, 0, 0));
        this.surface(Surface.BLANK);

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
            this.hexPane = new TextInfoPaneComponent("HEX", this::getHex);
            this.rgbPane = new TextInfoPaneComponent("RGB", this::getRgb);
            this.child(this.hexPane);
            this.child(this.rgbPane);
        }

        this.picker.onChanged().subscribe(newColor -> {
            this.currentRgbInt = newColor.rgb() & 0xFFFFFF;
            String hex = getHex();
            if (onColorChanged != null) {
                onColorChanged.accept(hex);
            }
        });
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