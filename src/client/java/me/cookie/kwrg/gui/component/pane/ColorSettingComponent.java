package me.cookie.kwrg.gui.component.pane;

import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import me.cookie.kwrg.util.ColorUtil;

import java.util.function.Consumer;

public class ColorSettingComponent extends FlowLayout {

    private final ColorPickerComponent picker;
    private TextInfoPaneComponent hexPane;
    private TextInfoPaneComponent rgbPane;

    private int currentRgbInt = 0x00F2FE;
    private boolean updatingFromCode = false;

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged) {
        this(initialColorStr, onColorChanged, true);
    }

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged, boolean showInfoPanes) {
        this(initialColorStr, onColorChanged, showInfoPanes, null);
    }

    /**
     * 传入自定义 Pane 时，showInfoPanes 自动设为 false
     *
     * @param initialColorStr 初始颜色
     * @param onColorChanged  颜色保存回调
     * @param onPickerUpdated 调色盘改动时回调
     * @param customPanes     自定义 Pane 列表
     */
    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged,
                                 Consumer<String> onPickerUpdated, UIComponent... customPanes) {
        this(initialColorStr, onColorChanged, false, onPickerUpdated, customPanes);
    }

    private ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged,
                                  boolean showInfoPanes, Consumer<String> onPickerUpdated, UIComponent... customPanes) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.margins(Insets.of(2, 0, 0, 0));
        this.surface(Surface.BLANK);

        boolean hasCustom = customPanes != null && customPanes.length > 0;
        boolean effectiveShowInfo = !hasCustom && showInfoPanes;

        this.picker = new ColorPickerComponent();
        this.picker.sizing(Sizing.fill(100), Sizing.fixed(120));
        this.picker.selectorWidth(12);
        this.picker.selectorPadding(6);
        this.picker.showAlpha(false);
        this.picker.margins(Insets.bottom(effectiveShowInfo || hasCustom ? 6 : 2));

        java.awt.Color currentColor = ColorUtil.parseSingleColor(initialColorStr);
        if (currentColor != null) {
            this.currentRgbInt = currentColor.getRGB() & 0xFFFFFF;
            this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));
        } else {
            this.picker.selectedColor(Color.ofRgb(0x00F2FE));
        }

        this.child(this.picker);

        // 挂载自定义 Pane
        if (hasCustom) {
            for (UIComponent pane : customPanes) {
                if (pane != null) {
                    this.child(pane);
                }
            }
        } else if (effectiveShowInfo) {
            this.hexPane = new TextInfoPaneComponent("HEX", this::getHex);
            this.rgbPane = new TextInfoPaneComponent("RGB", this::getRgb);
            this.child(this.hexPane);
            this.child(this.rgbPane);
        }

        // 调色盘滑动监听
        this.picker.onChanged().subscribe(newColor -> {
            if (this.updatingFromCode) return;

            this.currentRgbInt = newColor.rgb() & 0xFFFFFF;
            String hex = getHex();

            if (onPickerUpdated != null) {
                onPickerUpdated.accept(hex);
            }

            if (onColorChanged != null) {
                onColorChanged.accept(hex);
            }
        });
    }

    public void setColorFromExternal(String colorStr, Consumer<String> onColorChanged) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        if (c == null) return;

        int newRgb = c.getRGB() & 0xFFFFFF;
        if (this.currentRgbInt == newRgb) return;

        this.currentRgbInt = newRgb;
        this.updatingFromCode = true;
        this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));
        this.updatingFromCode = false;

        String hex = getHex();
        if (onColorChanged != null) {
            onColorChanged.accept(hex);
        }
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