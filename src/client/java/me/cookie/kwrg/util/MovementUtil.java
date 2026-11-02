package me.cookie.kwrg.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class MovementUtil {

    private static final Minecraft MC = Minecraft.getInstance();

    private MovementUtil() {
    }

    public static boolean canSimulateInput() {
        return MC.player != null && MC.level != null && MC.screen == null && MC.options != null;
    }

    public static void setForward(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyUp : null, pressed);
    }

    public static void setBackward(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyDown : null, pressed);
    }

    public static void setLeft(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyLeft : null, pressed);
    }

    public static void setRight(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyRight : null, pressed);
    }

    public static void setJump(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyJump : null, pressed);
    }

    public static void setSneak(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyShift : null, pressed);
    }

    public static void setAttack(boolean pressed) {
        setKeySafe(MC.options != null ? MC.options.keyAttack : null, pressed);
    }

    public static void clickAttack() {
        if (!canSimulateInput()) return;
        KeyMapping.click(InputConstants.getKey(MC.options.keyAttack.saveString()));
    }

    public static void setMove(boolean forward, boolean backward, boolean left, boolean right) {
        setForward(forward);
        setBackward(backward);
        setLeft(left);
        setRight(right);
    }

    public static void releaseAll() {
        if (MC.options == null) return;
        Options options = MC.options;
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
        options.keyAttack.setDown(false);

        if (MC.screen == null) {
            KeyMapping.setAll();
        }
    }

    public static void releaseMove() {
        if (MC.options == null) return;
        Options options = MC.options;
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);

        if (MC.screen == null) {
            KeyMapping.setAll();
        }
    }

    public static boolean isPhysicallyDown(KeyMapping key) {
        if (key == null || MC.getWindow() == null) return false;
        InputConstants.Key boundKey = InputConstants.getKey(key.saveString());
        if (boundKey.getType() == InputConstants.Type.KEYSYM && boundKey.getValue() != InputConstants.UNKNOWN.getValue()) {
            return InputConstants.isKeyDown(MC.getWindow(), boundKey.getValue());
        }
        return false;
    }

    private static void setKeySafe(KeyMapping key, boolean pressed) {
        if (key == null) return;
        if (pressed && !canSimulateInput()) {
            key.setDown(false);
            return;
        }
        key.setDown(pressed);
    }
}