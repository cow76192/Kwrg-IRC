package me.cookie.kwrg.gui.theme;

import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public final class GuiThemeService {

    private GuiThemeService() {}

    public static final int DEFAULT_PANEL_BG = 0x11161F;
    private static final int DIALOG_BG = 0x1C2734;
    private static final int LIST_ROW_BG = 0x24394B;

    // 路线 / 点位集屏幕专用配色：原为固定青色系，深档逐字节等于 navigator 的原字面量
    private static final int ROUTE_TITLE_DARK = 0xE1F5FE;
    private static final int ROUTE_TITLE_LIGHT = 0x1B3A47;
    private static final int ROUTE_BODY_DARK = 0xD2DFE8;
    private static final int ROUTE_BODY_LIGHT = 0x2C4453;
    private static final int POINT_ROW_BG = 0x192431;
    private static final int POINT_ROW_BORDER = 0x394A5A;
    private static final int ROUTE_FAINT_DARK = 0x81929F;
    private static final int ROUTE_FAINT_LIGHT = 0x51697A;
    private static final int ROUTE_LABEL_DARK = 0x819BAF;
    private static final int ROUTE_LABEL_LIGHT = 0x4C6A80;
    private static final int ROUTE_DIM_DARK = 0x546979;
    private static final int ROUTE_DIM_LIGHT = 0x3A4C58;
    private static final int ROUTE_INPUT_DARK = 0xE0ECF4;
    private static final int ROUTE_INPUT_LIGHT = 0x22333F;
    private static final int ROUTE_SECONDARY_DARK = 0xA9BDCD;
    private static final int ROUTE_SECONDARY_LIGHT = 0x44606F;
    private static final int ROUTE_ACCENT_TEXT_DARK = 0x92DFFF;
    private static final int ROUTE_ACCENT_TEXT_LIGHT = 0x0E5A78;
    private static final int DANGER_TEXT_DARK = 0xFFB4BC;
    private static final int DANGER_TEXT_LIGHT = 0x9B2C3A;
    private static final int ROUTE_MUTED_DARK = 0x8CA7BA;
    private static final int ROUTE_MUTED_LIGHT = 0x4A6273;
    private static final int ROUTE_BODY_ALT_DARK = 0xCFDFEA;
    private static final int ROUTE_BODY_ALT_LIGHT = 0x2A4150;
    private static final int ROUTE_DIMMER_DARK = 0x7893A6;
    private static final int ROUTE_DIMMER_LIGHT = 0x4E6678;
    private static final int ROUTE_NAME_ALT_DARK = 0xBBCDDD;
    private static final int ROUTE_NAME_ALT_LIGHT = 0x33495A;
    private static final int ROUTE_INACTIVE_DARK = 0x808995;
    private static final int ROUTE_INACTIVE_LIGHT = 0x4C5661;
    private static final int ROUTE_TITLE_ALT_DARK = 0xE0EAF1;
    private static final int ROUTE_TITLE_ALT_LIGHT = 0x1E3542;
    private static final int DANGER_TITLE_DARK = 0xFFCBD1;
    private static final int DANGER_TITLE_LIGHT = 0x8E2230;
    private static final int DIALOG_ERROR_BORDER = 0xAF6976;
    private static final int CHIP_PRESSED_BG = 0x609CC2;
    private static final int CHIP_ACTIVE_BG = 0x357888;
    private static final int CHIP_HOVER_BG = 0x445B73;
    private static final int CHIP_IDLE_BG = 0x2C3B4B;
    private static final int CHIP_ACTIVE_BORDER_DARK = 0x69D1D8;
    private static final int CHIP_ACTIVE_BORDER_LIGHT = 0x0A424C;
    private static final int CHIP_IDLE_BORDER_DARK = 0x4FC3F7;
    private static final int CHIP_IDLE_BORDER_LIGHT = 0x0A2434;
    private static final int CHIP_ACTIVE_TEXT_DARK = 0xB2F5E7;
    private static final int CHIP_ACTIVE_TEXT_LIGHT = 0x1B222B;
    private static final int CHIP_IDLE_TEXT_DARK = 0xD2DFE8;
    private static final int CHIP_IDLE_TEXT_LIGHT = 0x2E3742;
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
    private static final int TEXT_LABEL_DARK = 0x90A4AE;
    private static final int TEXT_STRONG_LIGHT = 0x2E3742;
    private static final int TEXT_BODY_LIGHT = 0x3E4854;
    private static final int TEXT_MUTED_LIGHT = 0x616D7A;
    private static final int TEXT_FAINT_LIGHT = 0x7C8794;
    private static final int TEXT_DIM_LIGHT = 0x909BA7;
    private static final int TEXT_HEADER_LIGHT = 0x55606C;
    private static final int TEXT_LABEL_LIGHT = 0x4E5A66;
    private static final int TOGGLE_OFF = 0x455A64;
    private static final int TOGGLE_ON = 0xB0BEC5;
    private static final int BADGE_BG = 0x1D2633;
    private static final int BADGE_RING_DARK = 0xFFFFFF;
    private static final int BADGE_RING_LIGHT = 0x2E3742;
    private static final int BADGE_TEXT_DARK = 0xF2F6FA;
    private static final int BADGE_TEXT_LIGHT = 0x2E3742;
    private static final int ACCENT = 0x4FC3F7;
    private static final int TEXT_READ_ONLY_DARK = 0xB2EBF2;
    private static final int TEXT_READ_ONLY_LIGHT = 0x2C5D6B;
    private static final int SELECTION_TINT = 0x26C6DA;
    private static final int PICKER_DEFAULT = 0x00F2FE;
    private static final int DOT_HOVER = 0xB0BEC5;
    private static final int DANGER_DARK = 0xFFA8B4;
    private static final int WARNING_DARK = 0xFFDB94;
    private static final int REGION_BORDER = 0x5AA5C4;
    private static final int ACCENT_SOFT_DARK = 0x80D7FF;
    private static final int DANGER_LIGHT = 0x9B2C3A;
    private static final int WARNING_LIGHT = 0x8A5A00;
    private static final int ACCENT_SOFT_LIGHT = 0x1F6E86;
    private static final int ERROR_SURFACE_DARK = 0x38242A;
    private static final int ERROR_SURFACE_LIGHT = 0xF2DCE0;
    private static final int ERROR_BORDER_DARK = 0xAF5D65;
    private static final int ERROR_BORDER_LIGHT = 0xC0858D;

    private enum Tone {
        PANEL_BG,
        PANEL_OUTLINE,
        CARD_BG,
        CARD_BORDER,
        TEXT_STRONG,
        TEXT_BODY,
        TEXT_MUTED,
        TEXT_FAINT,
        TEXT_DIM,
        TEXT_HEADER,
        TEXT_READ_ONLY,
        DANGER,
        WARNING,
        ACCENT_SOFT,
        ERROR_SURFACE,
        ERROR_BORDER,
        TEXT_LABEL,
        BADGE_BG,
        BADGE_RING,
        BADGE_TEXT
    }

    private static volatile Map<Tone, Integer> toneFrom = toneValuesFor(DEFAULT_PANEL_BG);
    private static volatile Map<Tone, Integer> toneTo = toneFrom;
    private static volatile long toneStartMillis = 0L;

    private static volatile float cachedOpacity = DEFAULT_OPACITY;

    public static float opacity() { return cachedOpacity; }
    public static int panelBg() { return tone(Tone.PANEL_BG); }
    public static int panelOutline() { return tone(Tone.PANEL_OUTLINE); }
    public static int cardBg() { return tone(Tone.CARD_BG); }
    public static int dialogBg() { return raisedFromBase(DIALOG_BG); }
    public static int routeTitle() { return lightSurface() ? ROUTE_TITLE_LIGHT : ROUTE_TITLE_DARK; }
    public static int routeBody() { return lightSurface() ? ROUTE_BODY_LIGHT : ROUTE_BODY_DARK; }
    public static int routeMuted() { return lightSurface() ? ROUTE_MUTED_LIGHT : ROUTE_MUTED_DARK; }
    public static int routeBodyAlt() { return lightSurface() ? ROUTE_BODY_ALT_LIGHT : ROUTE_BODY_ALT_DARK; }
    public static int routeDimmer() { return lightSurface() ? ROUTE_DIMMER_LIGHT : ROUTE_DIMMER_DARK; }
    public static int routeNameAlt() { return lightSurface() ? ROUTE_NAME_ALT_LIGHT : ROUTE_NAME_ALT_DARK; }
    public static int routeInactive() { return lightSurface() ? ROUTE_INACTIVE_LIGHT : ROUTE_INACTIVE_DARK; }
    public static int routeTitleAlt() { return lightSurface() ? ROUTE_TITLE_ALT_LIGHT : ROUTE_TITLE_ALT_DARK; }
    public static int dangerTitle() { return lightSurface() ? DANGER_TITLE_LIGHT : DANGER_TITLE_DARK; }
    public static int dialogErrorBorder() { return DIALOG_ERROR_BORDER; }
    public static int routeFaint() { return lightSurface() ? ROUTE_FAINT_LIGHT : ROUTE_FAINT_DARK; }
    public static int routeLabel() { return lightSurface() ? ROUTE_LABEL_LIGHT : ROUTE_LABEL_DARK; }
    public static int routeDim() { return lightSurface() ? ROUTE_DIM_LIGHT : ROUTE_DIM_DARK; }
    public static int routeInputText() { return lightSurface() ? ROUTE_INPUT_LIGHT : ROUTE_INPUT_DARK; }
    public static int routeSecondary() { return lightSurface() ? ROUTE_SECONDARY_LIGHT : ROUTE_SECONDARY_DARK; }
    public static int routeAccentText() { return lightSurface() ? ROUTE_ACCENT_TEXT_LIGHT : ROUTE_ACCENT_TEXT_DARK; }
    public static int dangerText() { return lightSurface() ? DANGER_TEXT_LIGHT : DANGER_TEXT_DARK; }
    public static int pointRowBg() { return raisedFromBase(POINT_ROW_BG); }
    public static int pointRowBorder() { return raisedFromBase(POINT_ROW_BORDER); }
    public static int chipPressedBg() { return CHIP_PRESSED_BG; }
    public static int chipActiveBg() { return CHIP_ACTIVE_BG; }
    public static int chipHoverBg() { return CHIP_HOVER_BG; }
    public static int chipIdleBg() { return CHIP_IDLE_BG; }
    public static int chipActiveBorder() { return lightSurface() ? CHIP_ACTIVE_BORDER_LIGHT : CHIP_ACTIVE_BORDER_DARK; }
    public static int chipIdleBorder() { return lightSurface() ? CHIP_IDLE_BORDER_LIGHT : CHIP_IDLE_BORDER_DARK; }
    public static int chipActiveText() { return lightSurface() ? CHIP_ACTIVE_TEXT_LIGHT : CHIP_ACTIVE_TEXT_DARK; }
    public static int chipIdleText() { return lightSurface() ? CHIP_IDLE_TEXT_LIGHT : CHIP_IDLE_TEXT_DARK; }

    /** 主底色偏亮时，路线屏幕的文字档改用压暗版；判据与 toneValuesFor 同一处。 */
    private static boolean lightSurface() {
        return prefersDeepPalette(tone(Tone.PANEL_BG));
    }

    /** 在面板底与派生卡片底上比较两套字色的实测对比度，取更易读的一套。 */
    private static boolean prefersDeepPalette(int panelBg) {
        return prefersDeepText(panelBg, ColorUtil.neighborTone(panelBg, 0.055f));
    }
    public static int listRowBg() { return raisedFromBase(LIST_ROW_BG); }

    /** 以主 GUI 底色为基准反算出的抬升表面：默认主题下精确等于标定值，浅色主题下反向偏移。 */
    private static int raisedFrom(int base, int calibrated) {
        int sign = prefersDeepPalette(base) ? -1 : 1;
        int r = Math.clamp(((base >> 16) & 0xFF) + sign * (((calibrated >> 16) & 0xFF) - ((DEFAULT_PANEL_BG >> 16) & 0xFF)), 0, 255);
        int g = Math.clamp(((base >> 8) & 0xFF) + sign * (((calibrated >> 8) & 0xFF) - ((DEFAULT_PANEL_BG >> 8) & 0xFF)), 0, 255);
        int b = Math.clamp((base & 0xFF) + sign * ((calibrated & 0xFF) - (DEFAULT_PANEL_BG & 0xFF)), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int raisedFromBase(int calibrated) {
        return raisedFrom(tone(Tone.PANEL_BG), calibrated);
    }
    public static int cardBorder() { return tone(Tone.CARD_BORDER); }
    public static int textStrong() { return tone(Tone.TEXT_STRONG); }
    public static int textBody() { return tone(Tone.TEXT_BODY); }
    public static int textMuted() { return tone(Tone.TEXT_MUTED); }
    public static int textFaint() { return tone(Tone.TEXT_FAINT); }
    public static int textDim() { return tone(Tone.TEXT_DIM); }
    public static int textHeader() { return tone(Tone.TEXT_HEADER); }
    public static int textLabel() { return tone(Tone.TEXT_LABEL); }
    public static int circleBg() { return PANE_BG; }
    public static int paneBorder() { return PANE_BORDER; }
    public static int paneBorderFocused() { return PANE_BORDER_FOCUSED; }
    public static int paneBorderReadOnly() { return PANE_BORDER_READ_ONLY; }
    public static int lineBase() { return LINE_BASE; }
    public static int rowBg() { return ROW_BG; }
    public static int rowBorder() { return ROW_BORDER; }
    public static int toggleOff() { return TOGGLE_OFF; }
    public static int toggleOn() { return TOGGLE_ON; }
    public static int badgeBg() { return tone(Tone.BADGE_BG); }
    public static int badgeRing() { return tone(Tone.BADGE_RING); }
    public static int badgeText() { return tone(Tone.BADGE_TEXT); }
    public static int accent() { return ACCENT; }
    public static int textReadOnly() { return tone(Tone.TEXT_READ_ONLY); }
    public static int selectionTint() { return SELECTION_TINT; }
    public static int pickerDefault() { return PICKER_DEFAULT; }
    public static int dotHover() { return DOT_HOVER; }
    public static int danger() { return tone(Tone.DANGER); }
    public static int warning() { return tone(Tone.WARNING); }
    public static int regionBorder() { return REGION_BORDER; }
    public static int accentSoft() { return tone(Tone.ACCENT_SOFT); }
    public static int errorSurface() { return tone(Tone.ERROR_SURFACE); }
    public static int errorBorder() { return tone(Tone.ERROR_BORDER); }

    public static void setOpacity(float opacity) {
        if (!Float.isFinite(opacity)) return;
        cachedOpacity = Mth.clamp(opacity, MIN_OPACITY, 1.0f);
    }

    public static void setPanelBg(int panelBg) {
        EnumMap<Tone, Integer> from = new EnumMap<>(Tone.class);
        for (Tone tone : Tone.values()) from.put(tone, tone(tone));
        toneFrom = Map.copyOf(from);
        toneTo = toneValuesFor(panelBg & 0xFFFFFF);
        toneStartMillis = System.currentTimeMillis();
    }

    private static int tone(Tone tone) {
        float progress = toneProgress();
        return progress >= 0.999f ? toneTo.get(tone) : AnimationUtil.lerpRgb(toneFrom.get(tone), toneTo.get(tone), progress);
    }

    private static float toneProgress() {
        if (toneStartMillis == 0L) return 1.0f;
        float raw = Math.min(1.0f, (System.currentTimeMillis() - toneStartMillis) / (float) TONE_ANIM_MS);
        return AnimationUtil.easeOutCubic(raw);
    }

    /** 正文实际落在面板底与派生卡片底两种表面上，取两者中较差的那张比较，选对比度更高的一套字档。 */
    private static boolean prefersDeepText(int panelBg, int cardBg) {
        double lightText = Math.min(ColorUtil.contrastRatio(TEXT_BODY_DARK, panelBg), ColorUtil.contrastRatio(TEXT_BODY_DARK, cardBg));
        double deepText = Math.min(ColorUtil.contrastRatio(TEXT_BODY_LIGHT, panelBg), ColorUtil.contrastRatio(TEXT_BODY_LIGHT, cardBg));
        return deepText >= lightText;
    }

    private static Map<Tone, Integer> toneValuesFor(int panelBg) {
        int cardBg = ColorUtil.neighborTone(panelBg, 0.055f);
        boolean lightBackground = prefersDeepText(panelBg, cardBg);   // 与 lightSurface() 同一判据
        EnumMap<Tone, Integer> tones = new EnumMap<>(Tone.class);
        tones.put(Tone.PANEL_BG, panelBg);
        tones.put(Tone.PANEL_OUTLINE, ColorUtil.deriveOutline(panelBg));
        tones.put(Tone.CARD_BG, cardBg);
        tones.put(Tone.CARD_BORDER, ColorUtil.deriveOutline(cardBg));
        tones.put(Tone.TEXT_STRONG, lightBackground ? TEXT_STRONG_LIGHT : TEXT_STRONG_DARK);
        tones.put(Tone.TEXT_BODY, lightBackground ? TEXT_BODY_LIGHT : TEXT_BODY_DARK);
        tones.put(Tone.TEXT_MUTED, lightBackground ? TEXT_MUTED_LIGHT : TEXT_MUTED_DARK);
        tones.put(Tone.TEXT_FAINT, lightBackground ? TEXT_FAINT_LIGHT : TEXT_FAINT_DARK);
        tones.put(Tone.TEXT_DIM, lightBackground ? TEXT_DIM_LIGHT : TEXT_DIM_DARK);
        tones.put(Tone.TEXT_HEADER, lightBackground ? TEXT_HEADER_LIGHT : TEXT_HEADER_DARK);
        tones.put(Tone.TEXT_READ_ONLY, lightBackground ? TEXT_READ_ONLY_LIGHT : TEXT_READ_ONLY_DARK);
        tones.put(Tone.DANGER, lightBackground ? DANGER_LIGHT : DANGER_DARK);
        tones.put(Tone.WARNING, lightBackground ? WARNING_LIGHT : WARNING_DARK);
        tones.put(Tone.ACCENT_SOFT, lightBackground ? ACCENT_SOFT_LIGHT : ACCENT_SOFT_DARK);
        tones.put(Tone.ERROR_SURFACE, lightBackground ? ERROR_SURFACE_LIGHT : ERROR_SURFACE_DARK);
        tones.put(Tone.ERROR_BORDER, lightBackground ? ERROR_BORDER_LIGHT : ERROR_BORDER_DARK);
        tones.put(Tone.TEXT_LABEL, lightBackground ? TEXT_LABEL_LIGHT : TEXT_LABEL_DARK);
        tones.put(Tone.BADGE_BG, raisedFrom(panelBg, BADGE_BG));
        tones.put(Tone.BADGE_RING, lightBackground ? BADGE_RING_LIGHT : BADGE_RING_DARK);
        tones.put(Tone.BADGE_TEXT, lightBackground ? BADGE_TEXT_LIGHT : BADGE_TEXT_DARK);
        return Map.copyOf(tones);
    }
}
