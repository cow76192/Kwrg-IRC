package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class ColorSettingComponent extends FlowLayout {

    private final ColorPickerComponent picker;
    private final Consumer<String> onColorChanged;
    private TextInputPaneComponent hexInput;
    private TextInputPaneComponent rgbInput;
    private TextInputPaneComponent hueInput;
    private TextInputPaneComponent saturationInput;
    private TextInputPaneComponent lightnessInput;
    private TextInfoPaneComponent hslHeader;
    private TextInfoPaneComponent hexPane;
    private TextInfoPaneComponent rgbPane;

    private int currentRgbInt = 0x00F2FE;

    // 高精度 HSB 浮点状态，彻底解决低数值反算精度丢失问题
    private float currentH = 0.0f;
    private float currentS = 1.0f;
    private float currentB = 1.0f;

    private boolean applyingProgrammatic = false;

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged) {
        this(initialColorStr, onColorChanged, false, true);
    }

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged, boolean showInfoPanes) {
        this(initialColorStr, onColorChanged, showInfoPanes, true);
    }

    public ColorSettingComponent(String initialColorStr, Consumer<String> onColorChanged, boolean showInfoPanes, boolean showHslControls) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.margins(Insets.of(2, 0, 0, 0));
        this.surface(Surface.BLANK);
        this.onColorChanged = onColorChanged;

        this.picker = new ColorPickerComponent();
        this.picker.sizing(Sizing.fill(100), Sizing.fixed(120));
        this.picker.selectorWidth(12);
        this.picker.selectorPadding(6);
        this.picker.showAlpha(false);
        this.picker.margins(Insets.bottom((showInfoPanes || showHslControls) ? 6 : 2));

        java.awt.Color currentColor = ColorUtil.parseSingleColor(initialColorStr);
        if (currentColor != null) {
            this.currentRgbInt = currentColor.getRGB() & 0xFFFFFF;
            this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));
        } else {
            this.picker.selectedColor(Color.ofRgb(0x00F2FE));
        }

        float[] initHsb = java.awt.Color.RGBtoHSB((this.currentRgbInt >> 16) & 0xFF, (this.currentRgbInt >> 8) & 0xFF, this.currentRgbInt & 0xFF, null);
        this.currentH = initHsb[0];
        this.currentS = initHsb[1];
        this.currentB = initHsb[2];

        this.child(this.picker);

        this.hexInput = new TextInputPaneComponent("", input -> {
            java.awt.Color c = ColorUtil.parseHexLoose(input);
            if (c != null) {
                updateColorFromRgb(c.getRGB() & 0xFFFFFF);
                NotificationManager.success("HEX 颜色已应用", getHex());
            } else {
                NotificationManager.err("无法解析 HEX", "请输入 3位 或 6位 HEX 颜色");
                this.hexInput.setText(getHex());
            }
        }, "#RRGGBB");

        this.rgbInput = new TextInputPaneComponent("", input -> {
            java.awt.Color c = ColorUtil.parseRgbLoose(input);
            if (c != null) {
                updateColorFromRgb(c.getRGB() & 0xFFFFFF);
                NotificationManager.success("RGB 颜色已应用", getRgb());
            } else {
                NotificationManager.err("无法解析 RGB", "支持 rgb(r,g,b) 或 r g b");
                this.rgbInput.setText(getRgb());
            }
        }, "r g b");

        this.child(this.hexInput);
        this.child(this.rgbInput);

        if (showHslControls) {
            this.hslHeader = TextInfoPaneComponent.header("HSL参数");
            this.child(this.hslHeader);

            this.hueInput = new TextInputPaneComponent("", input -> {
                Integer degrees = parseHueDegrees(input);
                if (degrees != null) {
                    updateColorFromHsb(degrees / 360.0f, this.currentS, this.currentB);
                    NotificationManager.success("色相已应用", Math.round(this.currentH * 360.0f) + "°");
                } else {
                    NotificationManager.err("无法解析色相", "请输入 0-360 的角度值");
                    this.hueInput.setText(Math.round(this.currentH * 360.0f) + "°");
                }
            }, "色相0-360°");

            this.saturationInput = new TextInputPaneComponent("", input -> {
                Integer percent = parsePercent(input);
                if (percent != null) {
                    updateColorFromHsb(this.currentH, percent / 100.0f, this.currentB);
                    NotificationManager.success("饱和度已应用", Math.round(this.currentS * 100.0f) + "%");
                } else {
                    NotificationManager.err("无法解析饱和度", "请输入 0-100 的百分比");
                    this.saturationInput.setText(Math.round(this.currentS * 100.0f) + "%");
                }
            }, "饱和度0-100%");

            this.lightnessInput = new TextInputPaneComponent("", input -> {
                Integer percent = parsePercent(input);
                if (percent != null) {
                    updateColorFromHsb(this.currentH, this.currentS, percent / 100.0f);
                    NotificationManager.success("明度已应用", Math.round(this.currentB * 100.0f) + "%");
                } else {
                    NotificationManager.err("无法解析明度", "请输入 0-100 的百分比");
                    this.lightnessInput.setText(Math.round(this.currentB * 100.0f) + "%");
                }
            }, "明度0-100%");

            this.child(this.hueInput);
            this.child(this.saturationInput);
            this.child(this.lightnessInput);
        }

        if (showInfoPanes) {
            this.hexPane = new TextInfoPaneComponent("HEX", this::getHex);
            this.rgbPane = new TextInfoPaneComponent("RGB", this::getRgb);
            this.child(this.hexPane);
            this.child(this.rgbPane);
        }

        syncAllUI();

        this.picker.onChanged().subscribe(newColor -> {
            if (this.applyingProgrammatic) return;
            updateColorFromRgb(newColor.rgb() & 0xFFFFFF);
        });
    }

    private void updateColorFromRgb(int rgb) {
        this.currentRgbInt = rgb;
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        this.currentH = hsb[0];
        this.currentS = hsb[1];
        this.currentB = hsb[2];

        syncAllUI();
        if (this.onColorChanged != null) {
            this.onColorChanged.accept(getHex());
        }
    }

    private void updateColorFromHsb(float h, float s, float b) {
        this.currentH = h;
        this.currentS = s;
        this.currentB = b;
        this.currentRgbInt = java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;

        syncAllUI();
        if (this.onColorChanged != null) {
            this.onColorChanged.accept(getHex());
        }
    }

    private void syncAllUI() {
        this.applyingProgrammatic = true;

        if (this.hexInput != null) this.hexInput.setText(getHex());
        if (this.rgbInput != null) this.rgbInput.setText(getRgb());

        if (this.hueInput != null) this.hueInput.setText(Math.round(this.currentH * 360.0f) + "°");
        if (this.saturationInput != null) this.saturationInput.setText(Math.round(this.currentS * 100.0f) + "%");
        if (this.lightnessInput != null) this.lightnessInput.setText(Math.round(this.currentB * 100.0f) + "%");

        this.picker.selectedColor(Color.ofRgb(this.currentRgbInt));

        this.applyingProgrammatic = false;
    }

    private static Integer parseHueDegrees(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim().replace("°", "");
        try {
            float val = Float.parseFloat(clean);
            return Math.round(Mth.clamp(val, 0.0f, 360.0f));
        } catch (NumberFormatException e) { return null; }
    }

    private static Integer parsePercent(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim().replace("%", "");
        try {
            float val = Float.parseFloat(clean);
            if (clean.contains(".") && val <= 1.0f) val *= 100.0f;
            return Math.round(Mth.clamp(val, 0.0f, 100.0f));
        } catch (NumberFormatException e) { return null; }
    }

    public String getHex() { return String.format("#%06X", this.currentRgbInt); }
    public String getRgb() {
        int r = (this.currentRgbInt >> 16) & 0xFF;
        int g = (this.currentRgbInt >> 8) & 0xFF;
        int b = this.currentRgbInt & 0xFF;
        return String.format("rgb(%d, %d, %d)", r, g, b);
    }
    public int getRgbInt() { return this.currentRgbInt; }
    public ColorPickerComponent getPicker() { return this.picker; }
    public TextInfoPaneComponent getHslHeader() { return this.hslHeader; }
    public TextInfoPaneComponent getHexPane() { return this.hexPane; }
    public TextInfoPaneComponent getRgbPane() { return this.rgbPane; }
}