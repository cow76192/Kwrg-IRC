package me.cookie.kwrg.gui.hud;

import me.cookie.kwrg.irc.config.IrcConfig;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

final class HudDragController {

    private static final float SNAP_DISTANCE = 6.0f;
    private static final float SCALE_STEP = 0.05f;

    private boolean dragging = false;
    private double dragStartMouseX = 0.0;
    private double dragStartMouseY = 0.0;
    private float dragStartOffsetX = 0.0f;
    private float dragStartOffsetY = 0.0f;

    void begin(double mouseX, double mouseY, HudElement target) {
        dragging = true;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;

        HudElementRegistry.cancelResetAnimation(target.id());
        HudElement.State state = HudElementRegistry.state(target.id());
        dragStartOffsetX = state.offsetX;
        dragStartOffsetY = state.offsetY;
    }

    boolean drag(MouseButtonEvent click, String selectedId, int screenWidth, int screenHeight) {
        if (!dragging || click.button() != 0 || selectedId == null) {
            return false;
        }

        HudElement.State state = HudElementRegistry.state(selectedId);
        state.offsetX = (float) (dragStartOffsetX + (click.x() - dragStartMouseX));
        state.offsetY = (float) (dragStartOffsetY + (click.y() - dragStartMouseY));
        state.sanitize();

        if (!click.hasControlDown()) {
            applySnapping(state, selectedId, screenWidth, screenHeight);
        }

        IrcConfig.saveDelayed(400);
        return true;
    }

    boolean release(MouseButtonEvent click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            IrcConfig.save();
            return true;
        }
        return false;
    }

    void applyScaleStep(HudElement.State state, double scrollY) {
        float delta = (scrollY > 0 ? 1.0f : -1.0f) * SCALE_STEP;
        float newScale = Math.round((state.scale + delta) * 100.0f) / 100.0f;
        state.scale = Mth.clamp(newScale, HudElement.State.MIN_SCALE, HudElement.State.MAX_SCALE);
        state.sanitize();
    }

    private void applySnapping(HudElement.State state, String selectedId, int screenWidth, int screenHeight) {
        HudElement element = HudElementRegistry.get(selectedId);
        if (element == null) return;

        float[] bounds = element.bounds();
        if (bounds == null || bounds[2] <= 0.0f || bounds[3] <= 0.0f) return;

        float left = HudElement.Anchor.resolveX(state.anchorX, state.marginX, state.offsetX, bounds[2], screenWidth);
        float top = HudElement.Anchor.resolveY(state.anchorY, state.marginY, state.offsetY, bounds[3], screenHeight);
        float right = left + bounds[2];
        float bottom = top + bounds[3];

        float[] bestX = {Float.NaN};
        float[] bestY = {Float.NaN};

        considerSnap(left + bounds[2] / 2.0f, screenWidth / 2.0f, bestX);
        considerSnap(left, 0.0f, bestX);
        considerSnap(right, screenWidth, bestX);

        considerSnap(top + bounds[3] / 2.0f, screenHeight / 2.0f, bestY);
        considerSnap(top, 0.0f, bestY);
        considerSnap(bottom, screenHeight, bestY);

        if (!Float.isNaN(bestX[0])) state.offsetX += bestX[0];
        if (!Float.isNaN(bestY[0])) state.offsetY += bestY[0];
        state.sanitize();
    }

    private static void considerSnap(float value, float target, float[] best) {
        float delta = target - value;
        if (Math.abs(delta) > SNAP_DISTANCE) return;
        if (Float.isNaN(best[0]) || Math.abs(delta) < Math.abs(best[0])) {
            best[0] = delta;
        }
    }
}
