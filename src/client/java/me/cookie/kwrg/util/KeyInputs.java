package me.cookie.kwrg.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class KeyInputs {

    private static final Set<String> CURRENT = new HashSet<>();
    private static final Set<String> PREVIOUS = new HashSet<>();

    private KeyInputs() {
    }

    public static void beginTick() {
        PREVIOUS.clear();
        PREVIOUS.addAll(CURRENT);
        CURRENT.clear();
    }

    public static void reset() {
        CURRENT.clear();
        PREVIOUS.clear();
    }

    public static boolean justPressed(String name) {
        if (name == null || name.isBlank()) return false;
        if (!isDown(name)) return false;
        if (!CURRENT.add(name)) return false;
        return !PREVIOUS.contains(name);
    }

    public static InputConstants.Key from(KeyEvent event) {
        return InputConstants.getKey(event);
    }

    public static InputConstants.Key from(MouseButtonEvent click) {
        return InputConstants.Type.MOUSE.getOrCreate(click.button());
    }

    public static InputConstants.Key parse(String name) {
        if (name == null || name.isBlank()) return null;
        return InputConstants.getKey(name);
    }

    public static String nameOf(InputConstants.Key key) {
        return key == null ? "" : key.getName();
    }

    public static String displayName(String name) {
        if (name == null || name.isBlank()) return "未绑定";
        if (name.startsWith("key.keyboard.")) return name.substring("key.keyboard.".length()).toUpperCase(Locale.ROOT);
        if (name.startsWith("key.mouse.")) return "MOUSE " + name.substring("key.mouse.".length()).toUpperCase(Locale.ROOT);
        if (name.startsWith("key.scan.")) return name.substring("key.scan.".length()).toUpperCase(Locale.ROOT);
        return name.toUpperCase(Locale.ROOT);
    }

    public static boolean isDown(String name) {
        InputConstants.Key key = parse(name);
        if (key == null) return false;

        var window = Minecraft.getInstance().getWindow();
        if (window == null) return false;

        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        return false;
    }
}
