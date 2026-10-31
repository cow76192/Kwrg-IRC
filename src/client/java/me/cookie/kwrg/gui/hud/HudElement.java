package me.cookie.kwrg.gui.hud;

import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class HudElement {

    public static final class Anchor {

        public static final String LEFT = "LEFT";
        public static final String CENTER = "CENTER";
        public static final String RIGHT = "RIGHT";
        public static final String TOP = "TOP";
        public static final String MIDDLE = "MIDDLE";
        public static final String BOTTOM = "BOTTOM";

        public static final float DEFAULT_MARGIN = 7.0f;

        private Anchor() {
        }

        public static String normalizeX(String value) {
            return (LEFT.equals(value) || RIGHT.equals(value)) ? value : CENTER;
        }

        public static String normalizeY(String value) {
            return (MIDDLE.equals(value) || BOTTOM.equals(value)) ? value : TOP;
        }

        public static float resolveX(String anchorX, float margin, float offsetX, float width, float screenW) {
            if (screenW <= width) return (screenW - width) / 2.0f;

            float x = switch (normalizeX(anchorX)) {
                case LEFT -> margin;
                case RIGHT -> screenW - margin - width;
                default -> (screenW - width) / 2.0f;
            };
            return Mth.clamp(x + offsetX, 0.0f, screenW - width);
        }

        public static float resolveY(String anchorY, float margin, float offsetY, float height, float screenH) {
            if (screenH <= height) return (screenH - height) / 2.0f;

            float y = switch (normalizeY(anchorY)) {
                case MIDDLE -> (screenH - height) / 2.0f;
                case BOTTOM -> screenH - margin - height;
                default -> margin;
            };
            return Mth.clamp(y + offsetY, 0.0f, screenH - height);
        }

        public static float resolveCenterX(String anchorX, float margin, float offsetX, float width, float screenW) {
            return resolveX(anchorX, margin, offsetX, width, screenW) + width / 2.0f;
        }

        public static String labelX(String anchorX) {
            return switch (normalizeX(anchorX)) {
                case LEFT -> "靠左";
                case RIGHT -> "靠右";
                default -> "居中";
            };
        }

        public static String labelY(String anchorY) {
            return switch (normalizeY(anchorY)) {
                case MIDDLE -> "居中";
                case BOTTOM -> "底部";
                default -> "顶部";
            };
        }
    }

    public static class State {

        public static final float MIN_SCALE = 0.5f;
        public static final float MAX_SCALE = 2.0f;

        public String anchorX = Anchor.CENTER;
        public String anchorY = Anchor.TOP;
        public float marginX = Anchor.DEFAULT_MARGIN;
        public float marginY = Anchor.DEFAULT_MARGIN;
        public float offsetX = 0.0f;
        public float offsetY = 0.0f;
        public float scale = 1.0f;
        public boolean enabled = true;
        public float opacity = 1.0f;
        public boolean shadow = true;
        public int padding = 6;

        public static State topCenter() {
            State state = new State();
            state.anchorX = Anchor.CENTER;
            state.anchorY = Anchor.TOP;
            return state;
        }

        public static State bottomRight(float marginX, float marginY) {
            State state = new State();
            state.anchorX = Anchor.RIGHT;
            state.anchorY = Anchor.BOTTOM;
            state.marginX = marginX;
            state.marginY = marginY;
            return state;
        }

        public static State rightMiddle(float marginX) {
            State state = new State();
            state.anchorX = Anchor.RIGHT;
            state.anchorY = Anchor.MIDDLE;
            state.marginX = marginX;
            return state;
        }

        public void sanitize() {
            anchorX = Anchor.normalizeX(anchorX);
            anchorY = Anchor.normalizeY(anchorY);

            if (!Float.isFinite(marginX) || marginX < 0.0f) marginX = Anchor.DEFAULT_MARGIN;
            if (!Float.isFinite(marginY) || marginY < 0.0f) marginY = Anchor.DEFAULT_MARGIN;
            marginX = Mth.clamp(marginX, 0.0f, 500.0f);
            marginY = Mth.clamp(marginY, 0.0f, 500.0f);

            if (!Float.isFinite(offsetX)) offsetX = 0.0f;
            if (!Float.isFinite(offsetY)) offsetY = 0.0f;
            offsetX = Mth.clamp(offsetX, -4000.0f, 4000.0f);
            offsetY = Mth.clamp(offsetY, -4000.0f, 4000.0f);

            if (!Float.isFinite(scale) || scale <= 0.0f) scale = 1.0f;
            scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);

            if (!Float.isFinite(opacity)) opacity = 1.0f;
            opacity = Mth.clamp(opacity, 0.0f, 1.0f);
            padding = Mth.clamp(padding, 0, 64);
        }

        public State copy() {
            State copy = new State();
            copy.anchorX = this.anchorX;
            copy.anchorY = this.anchorY;
            copy.marginX = this.marginX;
            copy.marginY = this.marginY;
            copy.offsetX = this.offsetX;
            copy.offsetY = this.offsetY;
            copy.scale = this.scale;
            copy.enabled = this.enabled;
            copy.opacity = this.opacity;
            copy.shadow = this.shadow;
            copy.padding = this.padding;
            return copy;
        }
    }

    private final String id;
    private final String displayName;
    private final State defaultState;
    private final boolean supportsScale;

    private Supplier<Boolean> enabledGetter = () -> true;
    private Consumer<Boolean> enabledSetter = value -> { };
    private Supplier<float[]> boundsGetter = () -> new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private Consumer<Boolean> previewSetter = value -> { };
    private Supplier<Boolean> followGlobalGetter = () -> false;
    private Consumer<Boolean> followGlobalSetter = value -> { };

    public HudElement(String id, String displayName, State defaultState) {
        this(id, displayName, defaultState, true);
    }

    public HudElement(String id, String displayName, State defaultState, boolean supportsScale) {
        this.id = id;
        this.displayName = displayName;
        this.defaultState = defaultState;
        this.supportsScale = supportsScale;
    }

    public HudElement enabled(Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.enabledGetter = getter;
        this.enabledSetter = setter;
        return this;
    }

    public HudElement bounds(Supplier<float[]> getter) {
        this.boundsGetter = getter;
        return this;
    }

    public HudElement preview(Consumer<Boolean> setter) {
        this.previewSetter = setter;
        return this;
    }

    public HudElement followGlobal(Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.followGlobalGetter = getter;
        this.followGlobalSetter = setter;
        return this;
    }

    public boolean followsGlobal() {
        return followGlobalGetter.get();
    }

    public void setFollowGlobal(boolean enabled) {
        followGlobalSetter.accept(enabled);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public State defaultState() {
        return defaultState.copy();
    }

    public boolean supportsScale() {
        return supportsScale;
    }

    public boolean isEnabled() {
        return enabledGetter.get();
    }

    public void setEnabled(boolean enabled) {
        enabledSetter.accept(enabled);
    }

    public float[] bounds() {
        float[] bounds = boundsGetter.get();
        return bounds != null ? bounds : new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    public void setEditorPreview(boolean enabled) {
        previewSetter.accept(enabled);
    }
}
