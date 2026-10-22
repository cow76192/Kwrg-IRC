package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import me.cookie.kwrg.util.ColorUtil;

public abstract class BaseGuiComponent extends BaseUIComponent {

    public static final int DEFAULT_PANEL_BG = 0x11161F;

    private static final int PANE_BG = 0x161D26;
    private static final int PANE_BORDER = 0x90A4AE;
    private static final int PANE_BORDER_FOCUSED = 0x90CAF9;
    private static final int PANE_BORDER_READ_ONLY = 0x80DEEA;
    private static final int LINE_BASE = 0x26384A;
    private static final int ROW_BG = 0x5498B2;
    private static final int ROW_BORDER = 0x7BBBD3;
    private static final int ROW_TEXT = 0xCFD8DC;
    private static final int TEXT_STRONG = 0xFFFFFF;
    private static final int TEXT_BODY = 0xECEFF1;
    private static final int TEXT_MUTED = 0x78909C;
    private static final int TEXT_FAINT = 0x607D8B;
    private static final int TEXT_DIM = 0x546E7A;
    private static final int TEXT_HEADER = 0xB0BEC5;
    private static final int TOGGLE_OFF = 0x455A64;
    private static final int TOGGLE_ON = 0xB0BEC5;
    private static final int BADGE_BG = 0x1D2633;
    private static final int ACCENT = 0x4FC3F7;
    private static final int TEXT_READ_ONLY = 0xB2EBF2;
    private static final int SELECTION_TINT = 0x26C6DA;
    private static final int PICKER_DEFAULT = 0x00F2FE;
    private static final int DOT_HOVER = 0xB0BEC5;

    private static int cachedPanelBg = DEFAULT_PANEL_BG;
    private static int cachedPanelOutline = ColorUtil.deriveOutline(DEFAULT_PANEL_BG);

    public static int panelBg() { return cachedPanelBg; }
    public static int panelOutline() { return cachedPanelOutline; }
    public static int paneBg() { return PANE_BG; }
    public static int paneBorder() { return PANE_BORDER; }
    public static int paneBorderFocused() { return PANE_BORDER_FOCUSED; }
    public static int paneBorderReadOnly() { return PANE_BORDER_READ_ONLY; }
    public static int lineBase() { return LINE_BASE; }
    public static int rowBg() { return ROW_BG; }
    public static int rowBorder() { return ROW_BORDER; }
    public static int rowText() { return ROW_TEXT; }
    public static int textStrong() { return TEXT_STRONG; }
    public static int textBody() { return TEXT_BODY; }
    public static int textMuted() { return TEXT_MUTED; }
    public static int textFaint() { return TEXT_FAINT; }
    public static int textDim() { return TEXT_DIM; }
    public static int textHeader() { return TEXT_HEADER; }
    public static int toggleOff() { return TOGGLE_OFF; }
    public static int toggleOn() { return TOGGLE_ON; }
    public static int badgeBg() { return BADGE_BG; }
    public static int accent() { return ACCENT; }
    public static int textReadOnly() { return TEXT_READ_ONLY; }
    public static int selectionTint() { return SELECTION_TINT; }
    public static int pickerDefault() { return PICKER_DEFAULT; }
    public static int dotHover() { return DOT_HOVER; }

    public static void setPanelBg(int panelBg) {
        cachedPanelBg = panelBg & 0xFFFFFF;
        cachedPanelOutline = ColorUtil.deriveOutline(cachedPanelBg);
    }
}
