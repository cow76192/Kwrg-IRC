package me.cookie.kwrg.window.input;

import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Memory;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import me.cookie.kwrg.util.WindowThreadUtil;

/**
 * Owns pointer routing only during explicit control; never brings the game back after Alt+Tab.
 */
public final class WindowInputFocus {
    public static boolean keyDown(int virtualKey) {
        return Platform.isWindows() && (Windows.API.GetAsyncKeyState(virtualKey) & 0x8000) != 0;
    }

    public interface Listener {
        void move(double x, double y);

        boolean button(int button, int action, boolean nativeDelivered);

        boolean scroll(double x, double y);

        default boolean nativePointer() {
            return false;
        }
    }

    private interface WindowProcedure extends StdCallLibrary.StdCallCallback {
        Pointer callback(Pointer window, int message, Pointer w, Pointer l);
    }

    @Structure.FieldOrder({"x", "y"})
    public static class Point extends Structure {
        public int x, y;
    }

    @Structure.FieldOrder({"left", "top", "right", "bottom"})
    public static class Rect extends Structure {
        public int left, top, right, bottom;
    }

    @Structure.FieldOrder({"page", "usage", "flags", "target"})
    public static class RawDevice extends Structure {
        public short page = 1, usage = 2;
        public int flags;
        public Pointer target;
    }

    private interface Windows extends StdCallLibrary {
        Windows API = Native.load("user32", Windows.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GetForegroundWindow();

        int GetWindowThreadProcessId(Pointer window, IntByReference process);

        boolean IsWindow(Pointer window);

        Pointer GetWindowLongPtrW(Pointer window, int index);

        Pointer SetWindowLongPtrW(Pointer window, int index, Pointer value);

        Pointer CreateWindowExW(int exStyle, String type, String name, int style, int x, int y, int width, int height,
                                Pointer parent, Pointer menu, Pointer instance, Pointer data);

        boolean DestroyWindow(Pointer window);

        Pointer GetCapture();

        boolean ReleaseCapture();

        short GetAsyncKeyState(int key);

        boolean IsIconic(Pointer window);

        int GetClassNameW(Pointer window, char[] name, int size);

        boolean LockSetForegroundWindow(int mode);

        boolean SetPropW(Pointer window, String name, Pointer value);

        Pointer RemovePropW(Pointer window, String name);

        Pointer GetPropW(Pointer window, String name);

        boolean GetCursorPos(Point point);

        boolean SetCursorPos(int x, int y);

        boolean GetClipCursor(Rect bounds);

        boolean ClipCursor(Rect bounds);

        Pointer CallWindowProcW(Pointer previous, Pointer window, int message, Pointer w, Pointer l);

        boolean RegisterRawInputDevices(RawDevice device, int count, int size);

        int GetRegisteredRawInputDevices(Pointer devices, IntByReference count, int size);

        int GetRawInputData(Pointer input, int command, Pointer data, IntByReference size, int headerSize);

        int GetSystemMetrics(int index);
    }

    private interface Magnification extends StdCallLibrary {
        Magnification API = Native.load("Magnification", Magnification.class);

        boolean MagInitialize();

        boolean MagShowSystemCursor(boolean show);

        boolean MagUninitialize();
    }

    private static final WindowProcedure RELAY = WindowInputFocus::relayMessage;
    private static final GLFWWindowFocusCallback FOCUS = GLFWWindowFocusCallback.create(WindowInputFocus::focusChanged);
    private static volatile GLFWWindowFocusCallback previousFocus;
    private static boolean focusHook, addedTransparent, modeChanged, magnification;
    private static volatile boolean awaitingEntryRelease;
    private static volatile Pointer gameWindow;
    private static Pointer relayWindow, previousRelayProcedure;
    private static volatile Listener listener;
    private static long glfwWindow;
    private static int previousMode;
    private static RawDevice previousDevice;
    private static final Memory RAW = new Memory(48);
    private static volatile double mouseX, mouseY;
    private static int absoluteX, absoluteY;
    private static boolean absoluteKnown;
    private static Point previousCursor;
    private static Rect previousClip;
    private static long foregroundSerial, controlProtection;

    public static boolean nativePointer(long window) {
        char[] name = new char[128];
        Windows.API.GetClassNameW(new Pointer(window), name, name.length);
        String kind = Native.toString(name);
        return kind.equals("CabinetWClass") || kind.equals("Chrome_WidgetWin_1") || Windows.API.GetPropW(new Pointer(window), "kwrg_projection_owned_input") != null;
    }

    public static long protectRestore(long window) {
        return Windows.API.IsIconic(new Pointer(window)) ? protectForeground() : 0;
    }

    private static synchronized long protectForeground() {
        IntByReference owner = new IntByReference();
        Windows.API.GetWindowThreadProcessId(Windows.API.GetForegroundWindow(), owner);
        if (owner.getValue() != ProcessHandle.current().pid()) return 0;
        return Windows.API.LockSetForegroundWindow(1) ? ++foregroundSerial : 0;
    }

    public static synchronized void releaseForeground(long protection) {
        if (protection != 0 && protection == foregroundSerial) {
            foregroundSerial++;
            Windows.API.LockSetForegroundWindow(2);
        }
    }

    private static void focusChanged(long window, boolean focused) {
        GLFWWindowFocusCallback previous = previousFocus;
        try {
            if (previous != null) previous.invoke(window, focused);
        } finally {
            if (!focused && window == glfwWindow) release();
        }
    }

    public static boolean keepGameForeground(long window, Listener input) {
        if (!Platform.isWindows()) return false;
        try {
            Pointer foreground = Windows.API.GetForegroundWindow();
            Pointer expected = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
            if (!expected.equals(foreground)) {
                release();
                return false;
            }

            IntByReference process = new IntByReference();
            Windows.API.GetWindowThreadProcessId(foreground, process);
            if (process.getValue() != ProcessHandle.current().pid()) {
                release();
                return false;
            }

            if (gameWindow != null && !gameWindow.equals(expected)) release();

            if (gameWindow == null) WindowThreadUtil.run(() -> acquireMouse(window, expected));

            listener = input;
            if (input != null && input.nativePointer() && (Windows.API.GetAsyncKeyState(0x12) & 0x8000) == 0 && (Windows.API.GetAsyncKeyState(0x5B) & 0x8000) == 0 && (Windows.API.GetAsyncKeyState(0x5C) & 0x8000) == 0) {
                long protection = protectForeground();
                if (protection != 0) controlProtection = protection;
            } else {
                releaseForeground(controlProtection);
                controlProtection = 0;
            }
            return true;
        } catch (LinkageError | RuntimeException unsupported) {
            release();
            return false;
        }
    }

    private static void acquireMouse(long window, Pointer game) {
        glfwWindow = window;
        gameWindow = game;
        previousMode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        Point cursor = new Point();
        Rect clip = new Rect();
        if (!Windows.API.GetCursorPos(cursor) || !Windows.API.GetClipCursor(clip)) throw new IllegalStateException("Mouse capture unavailable");

        previousCursor = cursor;
        previousClip = clip;
        previousDevice = registeredMouse();
        // The child owns raw input and identifies this explicit control session to the helper.
        relayWindow = Windows.API.CreateWindowExW(0x08000020, "STATIC", "kwrg_projection_mouse",
                0x40000000, 0, 0, 0, 0, game, null, null, null);
        if (relayWindow == null) throw new IllegalStateException("Mouse relay unavailable");

        previousRelayProcedure = Windows.API.GetWindowLongPtrW(relayWindow, -4);
        if (Windows.API.SetWindowLongPtrW(relayWindow, -4, CallbackReference.getFunctionPointer(RELAY)) == null)
            throw new IllegalStateException("Mouse relay setup failed");

        // GLFW's disabled mode recenters the OS pointer every poll, clearing browser hover.
        modeChanged = true;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        magnification = Magnification.API.MagInitialize();
        if (!magnification || !Magnification.API.MagShowSystemCursor(false))
            throw new IllegalStateException("System cursor hiding failed");

        RawDevice device = new RawDevice();
        device.target = relayWindow;
        if (!Windows.API.RegisterRawInputDevices(device, 1, device.size()))
            throw new IllegalStateException("Raw mouse registration failed");

        long style = Pointer.nativeValue(Windows.API.GetWindowLongPtrW(game, -20));
        addedTransparent = (style & 0x20) == 0;
        awaitingEntryRelease = (Windows.API.GetAsyncKeyState(2) & 0x8000) != 0;
        if (awaitingEntryRelease) {
            Windows.API.SetPropW(game, "kwrg_projection_entry_release", new Pointer(1));
            if (previousCursor != null) Windows.API.SetCursorPos(previousCursor.x, previousCursor.y);
        } else entryReleased();

        Windows.API.ClipCursor(null);
        // Input interception runs in the helper, independently of Minecraft's frame/message rate.
        previousFocus = GLFW.glfwSetWindowFocusCallback(window, FOCUS);
        focusHook = true;
        if (awaitingEntryRelease && (Windows.API.GetAsyncKeyState(2) & 0x8000) == 0) entryReleased();
    }

    public static boolean entryPending() {
        return awaitingEntryRelease;
    }

    public static void entryReleased() {
        WindowThreadUtil.run(WindowInputFocus::entryReleasedOnWindowThread);
    }

    private static void entryReleasedOnWindowThread() {
        if (gameWindow == null) return;

        awaitingEntryRelease = false;
        Windows.API.RemovePropW(gameWindow, "kwrg_projection_entry_release");
        if (gameWindow.equals(Windows.API.GetCapture())) Windows.API.ReleaseCapture();

        if (addedTransparent) {
            long style = Pointer.nativeValue(Windows.API.GetWindowLongPtrW(gameWindow, -20));
            Windows.API.SetWindowLongPtrW(gameWindow, -20, new Pointer(style | 0x20));
        }
    }

    private static Pointer relayMessage(Pointer window, int message, Pointer w, Pointer l) {
        if (gameWindow != null && message == 0xFF) {
            IntByReference length = new IntByReference(48);
            int size = Windows.API.GetRawInputData(l, 0x10000003, RAW, length, 24);
            if (size >= 48 && RAW.getInt(0) == 0 && RAW.getInt(44) != 0x4B575247)
                readMouse(RAW.getShort(24), RAW.getInt(36), RAW.getInt(40));
        }

        if (gameWindow != null && message == 0x8001 && gameWindow.equals(Windows.API.GetForegroundWindow())) {
            mouseMessage((int) Pointer.nativeValue(w), (int) Pointer.nativeValue(l));
            return null;
        }
        return Windows.API.CallWindowProcW(previousRelayProcedure, window, message, w, l);
    }

    private static void mouseMessage(int message, int detail) {
        if (message >= 0x201 && message <= 0x20E && listener != null) {
            if (message == 0x20A) listener.scroll(0, (short) (detail >>> 16) / 120.0);
            else if (message == 0x20E) listener.scroll((short) (detail >>> 16) / 120.0, 0);
            else {
                int button = message <= 0x203 ? 0 : message <= 0x206 ? 1 : message <= 0x209 ? 2 : (detail >>> 16) + 2;
                boolean up = message == 0x202 || message == 0x205 || message == 0x208 || message == 0x20C;
                listener.button(button, up ? GLFW.GLFW_RELEASE : GLFW.GLFW_PRESS, (detail & 0x8000) != 0);
            }
        }
    }

    private static RawDevice registeredMouse() {
        RawDevice layout = new RawDevice();
        IntByReference count = new IntByReference();
        if (Windows.API.GetRegisteredRawInputDevices(null, count, layout.size()) != 0 || count.getValue() > 512)
            throw new IllegalStateException("Raw input inventory unavailable");

        if (count.getValue() == 0) return null;

        try (Memory devices = new Memory((long) count.getValue() * layout.size())) {
            int size = Windows.API.GetRegisteredRawInputDevices(devices, count, layout.size());
            if (size < 0) throw new IllegalStateException("Raw input inventory changed");

            for (int i = 0; i < size; i++) {
                long at = (long) i * layout.size();
                if (devices.getShort(at) == 1 && devices.getShort(at + 2) == 2) {
                    layout.flags = devices.getInt(at + 4);
                    layout.target = devices.getPointer(at + 8);
                    return layout;
                }
            }
        }
        return null;
    }

    public static void readMouse(int flags, int x, int y) {
        if ((flags & 1) == 0) {
            mouseX += x;
            mouseY += y;
            absoluteKnown = false;
        } else {
            if (absoluteKnown) {
                mouseX += (x - absoluteX) * Windows.API.GetSystemMetrics((flags & 2) != 0 ? 78 : 0) / 65535.0;
                mouseY += (y - absoluteY) * Windows.API.GetSystemMetrics((flags & 2) != 0 ? 79 : 1) / 65535.0;
            }
            absoluteX = x;
            absoluteY = y;
            absoluteKnown = true;
        }
        if (listener != null) listener.move(mouseX, mouseY);
    }

    public static double mouseX() {
        return mouseX;
    }

    public static double mouseY() {
        return mouseY;
    }

    public static void release() {
        WindowThreadUtil.run(WindowInputFocus::releaseOnWindowThread);
    }

    private static void releaseOnWindowThread() {
        releaseForeground(controlProtection);
        controlProtection = 0;
        listener = null;
        Pointer game = gameWindow;
        if (game != null) {
            try {
                if (focusHook) {
                    GLFW.glfwSetWindowFocusCallback(glfwWindow, previousFocus);
                    focusHook = false;
                    previousFocus = null;
                }

                boolean active = game.equals(Windows.API.GetForegroundWindow());
                Windows.API.RemovePropW(game, "kwrg_projection_entry_release");
                awaitingEntryRelease = false;
                try {
                    RawDevice device = registeredMouse();
                    if (relayWindow != null && device != null && relayWindow.equals(device.target)) {
                        device.flags = 1;
                        device.target = null;
                        Windows.API.RegisterRawInputDevices(device, 1, device.size());
                    }
                } catch (LinkageError | RuntimeException ignored) {
                }

                if (relayWindow != null) Windows.API.DestroyWindow(relayWindow);

                if (Windows.API.IsWindow(game)) {
                    if (addedTransparent) {
                        long style = Pointer.nativeValue(Windows.API.GetWindowLongPtrW(game, -20));
                        Windows.API.SetWindowLongPtrW(game, -20, new Pointer(style & ~0x20L));
                    }

                    try {
                        if (modeChanged) GLFW.glfwSetInputMode(glfwWindow, GLFW.GLFW_CURSOR, previousMode);
                    } catch (LinkageError | RuntimeException ignored) {
                    }

                    if (previousDevice != null) Windows.API.RegisterRawInputDevices(previousDevice, 1, previousDevice.size());

                    if (active) {
                        if (previousClip != null) Windows.API.ClipCursor(previousClip);
                        if (previousCursor != null) Windows.API.SetCursorPos(previousCursor.x, previousCursor.y);
                    }
                }
            } catch (LinkageError | RuntimeException ignored) {
            }

            if (magnification) {
                Magnification.API.MagShowSystemCursor(true);
                Magnification.API.MagUninitialize();
                magnification = false;
            }

            gameWindow = relayWindow = previousRelayProcedure = null;
            previousCursor = null;
            previousClip = null;
            previousDevice = null;
            addedTransparent = modeChanged = absoluteKnown = false;
        }
    }
}
