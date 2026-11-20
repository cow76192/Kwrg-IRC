package me.cookie.kwrg.window.input;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import me.cookie.kwrg.util.WindowThreadUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class WindowKeyboardInput {
    private static final int WM_KILLFOCUS = 0x0008;
    private static final int WM_NCDESTROY = 0x0082;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_KEYUP = 0x0101;
    private static final int WM_CHAR = 0x0102;
    private static final int WM_SYSKEYDOWN = 0x0104;
    private static final int WM_SYSKEYUP = 0x0105;
    private static final int WM_SYSCHAR = 0x0106;
    private static final int WM_UNICHAR = 0x0109;
    private static final int WM_IME_COMPOSITION = 0x010F;
    private static final int VK_PROCESSKEY = 0xE5;

    public interface Listener {
        void key(int key, int action, int modifiers);
        void character(int codepoint);
    }

    public interface KeyInterceptor {
        boolean key(int key, int action, int modifiers);
        default void reset() { }
    }

    private interface Procedure extends StdCallLibrary.StdCallCallback {
        Pointer invoke(Pointer window, int message, Pointer w, Pointer l, Pointer id, Pointer data);
    }

    private interface Windows extends StdCallLibrary {
        Windows API = Native.load("user32", Windows.class, W32APIOptions.DEFAULT_OPTIONS);
        short GetKeyState(int key);
        short VkKeyScanW(char character);
        Pointer GetForegroundWindow();
        Pointer SendMessageW(Pointer window, int message, Pointer w, Pointer l);
    }

    private interface Subclass extends StdCallLibrary {
        Subclass API = Native.load("comctl32", Subclass.class);
        boolean SetWindowSubclass(Pointer window, Procedure procedure, Pointer id, Pointer data);
        boolean RemoveWindowSubclass(Pointer window, Procedure procedure, Pointer id);
        Pointer DefSubclassProc(Pointer window, int message, Pointer w, Pointer l);
    }

    private static final Pointer ID = new Pointer(0x4B574B42);
    private static final Procedure PROCEDURE = WindowKeyboardInput::message;
    private static final GLFWCharCallback IME_CHARACTER = GLFWCharCallback.create((window, codepoint) -> {
        Listener input = WindowKeyboardInput.listener;
        if (input != null) input.character(codepoint);
    });
    private static final int[] SCAN_KEYS = new int[512];
    private static final int[] VIRTUAL_KEYS = new int[256];
    private static final Set<Integer> PRESSED = new HashSet<>();
    private static final Set<Integer> PASSIVE_PRESSED = new HashSet<>();
    private static Pointer game;
    private static long glfwWindow;
    private static volatile Listener listener;
    private static volatile Listener shortcutListener;
    private static volatile KeyInterceptor passiveInterceptor;
    private static char highSurrogate;

    private WindowKeyboardInput() { }

    public static void start(long window, Listener input) {
        WindowThreadUtil.run(() -> {
            install(window, true);
            listener = input;
        });
    }

    public static void watchShortcut(long window, Listener input) {
        if (shortcutListener == input && (input == null || glfwWindow == window)) return;
        WindowThreadUtil.run(() -> {
            if (input != null && (game == null || glfwWindow != window)) install(window, false);
            shortcutListener = input;
            removeIfIdle();
        });
    }

    public static void setPassiveInterceptor(long window, KeyInterceptor interceptor) {
        if (passiveInterceptor == interceptor && (interceptor == null || glfwWindow == window)) return;
        WindowThreadUtil.run(() -> {
            KeyInterceptor previous = passiveInterceptor;
            if (interceptor != null && (game == null || glfwWindow != window)) install(window, false);
            passiveInterceptor = interceptor;
            if (previous != null && previous != interceptor) {
                PASSIVE_PRESSED.clear();
                previous.reset();
            }
            removeIfIdle();
        });
    }

    private static void install(long window, boolean clearPressedKeys) {
        Listener shortcut = shortcutListener;
        KeyInterceptor passive = passiveInterceptor;
        remove();
        game = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
        glfwWindow = window;
        prepareKeys(window, clearPressedKeys);
        if (!Subclass.API.SetWindowSubclass(game, PROCEDURE, ID, null)) {
            game = null;
            throw new IllegalStateException("键盘消息拦截初始化失败");
        }
        shortcutListener = shortcut;
        passiveInterceptor = passive;
    }

    private static void prepareKeys(long window, boolean clearPressedKeys) {
        Arrays.fill(SCAN_KEYS, GLFW.GLFW_KEY_UNKNOWN);
        Arrays.fill(VIRTUAL_KEYS, GLFW.GLFW_KEY_UNKNOWN);

        GLFWKeyCallback discard = GLFWKeyCallback.create((handle, key, scan, action, modifiers) -> { });
        GLFWKeyCallback previous = GLFW.glfwSetKeyCallback(window, discard);
        try {
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
                int virtual = WindowInputKeys.virtualKey(key);
                if (virtual == 0) continue;
                int scan = GLFW.glfwGetKeyScancode(key);
                if (scan >= 0 && scan < SCAN_KEYS.length) SCAN_KEYS[scan] = key;
                VIRTUAL_KEYS[virtual] = key;

                if (!clearPressedKeys || GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) continue;
                PRESSED.add(key);
                long release = 0xC0000001L | (long) (scan & 0x1ff) << 16;
                Windows.API.SendMessageW(game, WM_KEYUP, new Pointer(virtual), new Pointer(release));
            }
        } finally {
            GLFW.glfwSetKeyCallback(window, previous);
            discard.free();
        }
    }

    private static Pointer message(Pointer window, int message, Pointer w, Pointer l, Pointer id, Pointer data) {
        if (message == WM_NCDESTROY) {
            remove();
            return Subclass.API.DefSubclassProc(window, message, w, l);
        }
        if (message == WM_KILLFOCUS) {
            PRESSED.clear();
            resetPassiveInterceptor();
            highSurrogate = 0;
            removeIfIdle();
            return Subclass.API.DefSubclassProc(window, message, w, l);
        }

        if (listener != null && message == WM_IME_COMPOSITION) {
            // GLFW emits committed IME text inside WM_IME_COMPOSITION, without a WM_CHAR message.
            GLFWCharCallback previous = GLFW.glfwSetCharCallback(glfwWindow, IME_CHARACTER);
            try {
                return Subclass.API.DefSubclassProc(window, message, w, l);
            } finally {
                GLFW.glfwSetCharCallback(glfwWindow, previous);
            }
        }

        if (message == WM_KEYDOWN || message == WM_KEYUP || message == WM_SYSKEYDOWN || message == WM_SYSKEYUP) {
            int virtual = (int) Pointer.nativeValue(w);
            if (virtual == VK_PROCESSKEY) return Subclass.API.DefSubclassProc(window, message, w, l);

            long lParam = Pointer.nativeValue(l);
            int key = resolveKey(virtual, lParam);
            boolean released = message == WM_KEYUP || message == WM_SYSKEYUP;

            if (listener == null) {
                if (PRESSED.contains(key)) {
                    if (released) {
                        PRESSED.remove(key);
                        boolean passive = PASSIVE_PRESSED.remove(key);
                        if (passive) {
                            KeyInterceptor interceptor = passiveInterceptor;
                            if (interceptor != null) interceptor.key(key, GLFW.GLFW_RELEASE, modifiers());
                        } else if (key == GLFW.GLFW_KEY_G && shortcutListener != null) {
                            shortcutListener.key(key, GLFW.GLFW_RELEASE, modifiers());
                        }
                        removeIfIdle();
                    } else if (PASSIVE_PRESSED.contains(key)) {
                        KeyInterceptor interceptor = passiveInterceptor;
                        if (interceptor != null) interceptor.key(key, GLFW.GLFW_REPEAT, modifiers());
                    }
                    return null;
                }

                boolean initialPress = !released && (lParam & 0x40000000L) == 0;
                if (initialPress) {
                    int modifiers = modifiers();
                    if (shortcutListener != null && key == GLFW.GLFW_KEY_G && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
                        PRESSED.add(key);
                        shortcutListener.key(key, GLFW.GLFW_PRESS, modifiers);
                        return null;
                    }

                    KeyInterceptor interceptor = passiveInterceptor;
                    if (interceptor != null && interceptor.key(key, GLFW.GLFW_PRESS, modifiers)) {
                        PRESSED.add(key);
                        PASSIVE_PRESSED.add(key);
                        return null;
                    }
                }
                return Subclass.API.DefSubclassProc(window, message, w, l);
            }

            int action;
            if (released) {
                PRESSED.remove(key);
                action = GLFW.GLFW_RELEASE;
            } else {
                action = PRESSED.add(key) ? GLFW.GLFW_PRESS : GLFW.GLFW_REPEAT;
            }

            listener.key(key, action, modifiers());
            return null;
        }

        boolean characterMessage = message == WM_CHAR || message == WM_SYSCHAR || message == WM_UNICHAR;
        if (listener == null && characterMessage && !PRESSED.isEmpty()) {
            int virtual = Windows.API.VkKeyScanW((char) Pointer.nativeValue(w)) & 255;
            if (PRESSED.contains(VIRTUAL_KEYS[virtual])) return null;
        }

        if (listener != null && characterMessage) {
            if (message == WM_UNICHAR && Pointer.nativeValue(w) == 0xffff) return new Pointer(1);
            if (message == WM_UNICHAR) listener.character((int) Pointer.nativeValue(w));
            else character((char) Pointer.nativeValue(w));
            return null;
        }
        return Subclass.API.DefSubclassProc(window, message, w, l);
    }

    private static int modifiers() {
        int modifiers = 0;
        if (Windows.API.GetKeyState(0x10) < 0) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (Windows.API.GetKeyState(0x11) < 0) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (Windows.API.GetKeyState(0x12) < 0) modifiers |= GLFW.GLFW_MOD_ALT;
        if (Windows.API.GetKeyState(0x5b) < 0 || Windows.API.GetKeyState(0x5c) < 0) modifiers |= GLFW.GLFW_MOD_SUPER;
        if ((Windows.API.GetKeyState(0x14) & 1) != 0) modifiers |= GLFW.GLFW_MOD_CAPS_LOCK;
        if ((Windows.API.GetKeyState(0x90) & 1) != 0) modifiers |= GLFW.GLFW_MOD_NUM_LOCK;
        return modifiers;
    }

    private static void character(char value) {
        if (Character.isHighSurrogate(value)) {
            highSurrogate = value;
            return;
        }
        if (Character.isLowSurrogate(value)) {
            if (highSurrogate != 0) listener.character(Character.toCodePoint(highSurrogate, value));
        } else {
            listener.character(value);
        }
        highSurrogate = 0;
    }

    public static void stop() {
        WindowThreadUtil.run(() -> {
            listener = null;
            highSurrogate = 0;
            // Swallow releases belonging to control, including the Alt+G used to exit.
            if (game != null && !game.equals(Windows.API.GetForegroundWindow())) PRESSED.clear();
            removeIfIdle();
        });
    }

    private static int resolveKey(int virtual, long lParam) {
        int scan = (int) (lParam >>> 16) & 0x1ff;
        int key = scan < SCAN_KEYS.length ? SCAN_KEYS[scan] : GLFW.GLFW_KEY_UNKNOWN;
        if (key != GLFW.GLFW_KEY_UNKNOWN) return key;
        boolean extended = (lParam & 0x01000000L) != 0;
        if (virtual == 0x11) return extended ? GLFW.GLFW_KEY_RIGHT_CONTROL : GLFW.GLFW_KEY_LEFT_CONTROL;
        if (virtual == 0x12) return extended ? GLFW.GLFW_KEY_RIGHT_ALT : GLFW.GLFW_KEY_LEFT_ALT;
        if (virtual == 0x10) return GLFW.GLFW_KEY_LEFT_SHIFT;
        if (virtual >= 0 && virtual < VIRTUAL_KEYS.length) return VIRTUAL_KEYS[virtual];
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    private static void resetPassiveInterceptor() {
        PASSIVE_PRESSED.clear();
        KeyInterceptor interceptor = passiveInterceptor;
        if (interceptor != null) interceptor.reset();
    }

    private static void removeIfIdle() {
        if (listener != null || shortcutListener != null || passiveInterceptor != null) return;
        if (PRESSED.isEmpty() || game == null || !game.equals(Windows.API.GetForegroundWindow())) remove();
    }

    private static void remove() {
        listener = null;
        shortcutListener = null;
        highSurrogate = 0;
        PRESSED.clear();
        resetPassiveInterceptor();
        passiveInterceptor = null;
        if (game != null) Subclass.API.RemoveWindowSubclass(game, PROCEDURE, ID);
        game = null;
    }
}
