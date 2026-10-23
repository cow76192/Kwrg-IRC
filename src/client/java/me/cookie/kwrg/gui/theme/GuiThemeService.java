package me.cookie.kwrg.gui.theme;

import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.util.Mth;

public final class GuiThemeService {

    private GuiThemeService() {}

    public static final int DEFAULT_PANEL_BG = 0x11161F;
    public static final float MIN_OPACITY = 0.1f;
    private static final float DEFAULT_OPACITY = 1.0f;
    private static final long TONE_ANIM_MS = 180L;

    private static final int PANE_BG = 0x161D26;
    private static final int PANE_BORDER = 0x90A4AE;
    private static final int PANE_BORDER_FOCUSED = 0x90CAF9;
    private static final int PANE_BORDER_READ_ONLY = 0x80DEEA;
    private static final int LINE_BASE = 0x26384A;
    private static final int ROW_BG = 0x5498B2;
    private static final int ROW_BORDER = 0x7BBBD3;
    private static final int TEXT_STRONG_DARK = 0xFFFFFF;
    private static final int TEXT_BODY_DARK = 0xECEFF1;
    private static final int TEXT_MUTED_DARK = 0x78909C;
    private static final int TEXT_FAINT_DARK = 0x607D8B;
    private static final int TEXT_DIM_DARK = 0x546E7A;
    private static final int TEXT_HEADER_DARK = 0xB0BEC5;
    private static final int TEXT_STRONG_LIGHT = 0x2E3742;
    private static final int TEXT_BODY_LIGHT = 0x3E4854;
    private static final int TEXT_MUTED_LIGHT = 0x616D7A;
    private static final int TEXT_FAINT_LIGHT = 0x7C8794;
    private static final int TEXT_DIM_LIGHT = 0x909BA7;
    private static final int TEXT_HEADER_LIGHT = 0x55606C;
    private static final int TOGGLE_OFF = 0x455A64;
    private static final int TOGGLE_ON = 0xB0BEC5;
    private static final int BADGE_BG = 0x1D2633;
    private static final int BADGE_TEXT = 0xF2F6FA;
    private static final int ACCENT = 0x4FC3F7;
    private static final int TEXT_READ_ONLY_DARK = 0xB2EBF2;
    private static final int TEXT_READ_ONLY_LIGHT = 0x2C5D6B;
    private static final int SELECTION_TINT = 0x26C6DA;
    private static final int PICKER_DEFAULT = 0x00F2FE;
    private static final int DOT_HOVER = 0xB0BEC5;
    private static final int DANGER_DARK = 0xFFA8B4;
    private static final int WARNING_DARK = 0xFFDB94;
    private static final int REGION_FILL = 0x5AA5C4;
    private static final int ACCENT_SOFT_DARK = 0x80D7FF;
    private static final int DANGER_LIGHT = 0x9B2C3A;
    private static final int WARNING_LIGHT = 0x8A5A00;
    private static final int ACCENT_SOFT_LIGHT = 0x1F6E86;
    private static final int ERROR_SURFACE_DARK = 0x38242A;
    private static final int ERROR_SURFACE_LIGHT = 0xF2DCE0;
    private static final int ERROR_BORDER_DARK = 0xAF5D65;
    private static final int ERROR_BORDER_LIGHT = 0xC0858D;

    private static final int TONE_PANEL_BG = 0;
    private static final int TONE_PANEL_OUTLINE = 1;
    private static final int TONE_CARD_BG = 2;
    private static final int TONE_CARD_BORDER = 3;
    private static final int TONE_TEXT_STRONG = 4;
    private static final int TONE_TEXT_BODY = 5;
    private static final int TONE_TEXT_MUTED = 6;
    private static final int TONE_TEXT_FAINT = 7;
    private static final int TONE_TEXT_DIM = 8;
    private static final int TONE_TEXT_HEADER = 9;
    private static final int TONE_TEXT_READ_ONLY = 10;
    private static final int TONE_DANGER = 11;
    private static final int TONE_WARNING = 12;
    private static final int TONE_ACCENT_SOFT = 13;
    private static final int TONE_ERROR_SURFACE = 14;
    private static final int TONE_ERROR_BORDER = 15;
    private static final int TONE_COUNT = 16;

    private static volatile int[] toneFrom = new int[TONE_COUNT];
    private static volatile int[] toneTo = new int[TONE_COUNT];
    private static volatile long toneStartMillis = 0L;

    private static volatile float cachedOpacity = DEFAULT_OPACITY;

    static {
        int[] initial = toneValuesFor(DEFAULT_PANEL_BG);
        System.arraycopy(initial, 0, toneFrom, 0, TONE_COUNT);
        System.arraycopy(initial, 0, toneTo, 0, TONE_COUNT);
    }

    public static float opacity() { return cachedOpacity; }
    public static int panelBg() { return tone(TONE_PANEL_BG); }
    public static int panelOutline() { return tone(TONE_PANEL_OUTLINE); }
    public static int cardBg() { return tone(TONE_CARD_BG); }
    public static int cardBorder() { return tone(TONE_CARD_BORDER); }
    public static int textStrong() { return tone(TONE_TEXT_STRONG); }
    public static int textBody() { return tone(TONE_TEXT_BODY); }
    public static int textMuted() { return tone(TONE_TEXT_MUTED); }
    public static int textFaint() { return tone(TONE_TEXT_FAINT); }
    public static int textDim() { return tone(TONE_TEXT_DIM); }
    public static int textHeader() { return tone(TONE_TEXT_HEADER); }
    public static int circleBg() { return PANE_BG; }
    public static int paneBorder() { return PANE_BORDER; }
    public static int paneBorderFocused() { return PANE_BORDER_FOCUSED; }
    public static int paneBorderReadOnly() { return PANE_BORDER_READ_ONLY; }
    public static int lineBase() { return LINE_BASE; }
    public static int rowBg() { return ROW_BG; }
    public static int rowBorder() { return ROW_BORDER; }
    public static int toggleOff() { return TOGGLE_OFF; }
    public static int toggleOn() { return TOGGLE_ON; }
    public static int badgeBg() { return BADGE_BG; }
    public static int badgeText() { return BADGE_TEXT; }
    public static int accent() { return ACCENT; }
    public static int textReadOnly() { return tone(TONE_TEXT_READ_ONLY); }
    public static int selectionTint() { return SELECTION_TINT; }
    public static int pickerDefault() { return PICKER_DEFAULT; }
    public static int dotHover() { return DOT_HOVER; }
    public static int danger() { return tone(TONE_DANGER); }
    public static int warning() { return tone(TONE_WARNING); }
    public static int regionFill() { return REGION_FILL; }
    public static int accentSoft() { return tone(TONE_ACCENT_SOFT); }
    public static int errorSurface() { return tone(TONE_ERROR_SURFACE); }
    public static int errorBorder() { return tone(TONE_ERROR_BORDER); }

    public static void setOpacity(float opacity) {
        if (!Float.isFinite(opacity)) return;
        cachedOpacity = Mth.clamp(opacity, MIN_OPACITY, 1.0f);
    }

    public static void setPanelBg(int panelBg) {
        int[] from = new int[TONE_COUNT];
        for (int i = 0; i < TONE_COUNT; i++) from[i] = tone(i);
        toneFrom = from;
        toneTo = toneValuesFor(panelBg & 0xFFFFFF);
        toneStartMillis = System.currentTimeMillis();
    }

    private static int tone(int index) {
        float progress = toneProgress();
        return progress >= 0.999f ? toneTo[index] : lerpRgb(toneFrom[index], toneTo[index], progress);
    }

    private static float toneProgress() {
        if (toneStartMillis == 0L) return 1.0f;
        float raw = Math.min(1.0f, (System.currentTimeMillis() - toneStartMillis) / (float) TONE_ANIM_MS);
        return AnimationUtil.easeOutCubic(raw);
    }

    private static int[] toneValuesFor(int panelBg) {
        boolean darkForeground = ColorUtil.relativeLuminance(panelBg) >= 0.179;
        int cardBg = ColorUtil.neighborTone(panelBg, 0.055f);
        return new int[]{
                panelBg,
                ColorUtil.deriveOutline(panelBg),
                cardBg,
                ColorUtil.deriveOutline(cardBg),
                darkForeground ? TEXT_STRONG_LIGHT : TEXT_STRONG_DARK,
                darkForeground ? TEXT_BODY_LIGHT : TEXT_BODY_DARK,
                darkForeground ? TEXT_MUTED_LIGHT : TEXT_MUTED_DARK,
                darkForeground ? TEXT_FAINT_LIGHT : TEXT_FAINT_DARK,
                darkForeground ? TEXT_DIM_LIGHT : TEXT_DIM_DARK,
                darkForeground ? TEXT_HEADER_LIGHT : TEXT_HEADER_DARK,
                darkForeground ? TEXT_READ_ONLY_LIGHT : TEXT_READ_ONLY_DARK,
                darkForeground ? DANGER_LIGHT : DANGER_DARK,
                darkForeground ? WARNING_LIGHT : WARNING_DARK,
                darkForeground ? ACCENT_SOFT_LIGHT : ACCENT_SOFT_DARK,
                darkForeground ? ERROR_SURFACE_LIGHT : ERROR_SURFACE_DARK,
                darkForeground ? ERROR_BORDER_LIGHT : ERROR_BORDER_DARK
        };
    }

    private static int lerpRgb(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
