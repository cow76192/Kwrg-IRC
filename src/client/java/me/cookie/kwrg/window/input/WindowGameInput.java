package me.cookie.kwrg.window.input;

import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import me.cookie.kwrg.util.WindowThreadUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * Gives the source real focus while keeping the projection visible above it.
 */
public final class WindowGameInput {
    private interface Procedure extends StdCallLibrary.StdCallCallback {
        Pointer invoke(Pointer window, int message, Pointer w, Pointer l);
    }

    private interface User extends StdCallLibrary {
        User API = Native.load("user32", User.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GetForegroundWindow();

        int GetWindowThreadProcessId(Pointer window, IntByReference process);

        Pointer GetWindow(Pointer window, int command);

        Pointer GetAncestor(Pointer window, int flag);

        boolean IsWindow(Pointer window);

        Pointer GetWindowLongPtrW(Pointer window, int index);

        Pointer SetWindowLongPtrW(Pointer window, int index, Pointer value);

        boolean SetWindowPos(Pointer window, Pointer after, int x, int y, int width, int height, int flags);

        boolean SetForegroundWindow(Pointer window);

        Pointer SendMessageTimeoutW(Pointer window, int message, Pointer w, Pointer l, int flags, int timeout, Pointer result);

        Pointer CreateWindowExW(int exStyle, String type, String name, int style, int x, int y, int width, int height,
                                Pointer parent, Pointer menu, Pointer instance, Pointer data);

        boolean DestroyWindow(Pointer window);

        boolean RegisterHotKey(Pointer window, int id, int modifiers, int key);

        boolean UnregisterHotKey(Pointer window, int id);

        Pointer CallWindowProcW(Pointer previous, Pointer window, int message, Pointer w, Pointer l);
    }

    private static final int HOTKEY = 0x4B57;
    private static final int SWP_ASYNCWINDOWPOS = 0x4000;
    private static final Procedure PROCEDURE = WindowGameInput::message;
    private static Pointer game, source, relay, previous;
    private static int sourcePid;
    private static long addedStyles;
    private static boolean wasTopmost;
    private static volatile boolean running, exitRequested;
    private static Runnable releaseInput;

    private WindowGameInput() {
    }

    public static void start(long window, long target, int pid, Runnable release) {
        WindowThreadUtil.run(() -> {
            if (running) throw new IllegalStateException("游戏控制已启动");

            if (GLFW.glfwGetWindowMonitor(window) != 0)
                throw new IllegalStateException("游戏控制需使用窗口化或无边框模式");

            game = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
            source = new Pointer(target);
            sourcePid = pid;
            IntByReference actual = new IntByReference();
            User.API.GetWindowThreadProcessId(source, actual);
            if (!game.equals(User.API.GetForegroundWindow()) || actual.getValue() != pid || pid == ProcessHandle.current().pid()) {
                game = source = null;
                throw new IllegalStateException("游戏窗口或焦点已改变");
            }

            long style = Pointer.nativeValue(User.API.GetWindowLongPtrW(game, -20));

            wasTopmost = (style & 8) != 0;
            addedStyles = 0x08000020L & ~style;
            exitRequested = false;
            releaseInput = release;

            try {
                relay = User.API.CreateWindowExW(0x08000000, "STATIC", "kwrg_projection_game_input", 0x40000000,
                        0, 0, 0, 0, game, null, null, null);
                if (relay == null) throw new IllegalStateException("游戏控制快捷键窗口创建失败");

                previous = User.API.SetWindowLongPtrW(relay, -4, CallbackReference.getFunctionPointer(PROCEDURE));
                if (previous == null || !User.API.RegisterHotKey(relay, HOTKEY, 0x4001, 0x47))
                    throw new IllegalStateException("Alt+G 已被其他程序占用");

                User.API.SetWindowLongPtrW(game, -20, new Pointer(style | addedStyles));
                if (!User.API.SetWindowPos(game, new Pointer(-1), 0, 0, 0, 0, 0x13))
                    throw new IllegalStateException("无法保持投影显示");

                running = true;
                boolean requested = User.API.SetForegroundWindow(source);
                // Cross-queue activation is asynchronous; WM_NULL waits for its processing without joining input queues.
                if (requested) User.API.SendMessageTimeoutW(source, 0, null, null, 2, 1000, null);
                if (!sourceForeground())
                    throw new IllegalStateException(requested ? "目标游戏未及时完成激活，请稍后重试" : "目标游戏拒绝获得焦点");

                User.API.SetWindowPos(source, game, 0, 0, 0, 0, 0x13 | SWP_ASYNCWINDOWPOS);
            } catch (RuntimeException | LinkageError failure) {
                stopOnWindowThread();
                throw failure;
            }
        });
    }

    private static Pointer message(Pointer window, int message, Pointer w, Pointer l) {
        if (message == 0x312 && Pointer.nativeValue(w) == HOTKEY) {
            exitRequested = true;
            Runnable release = releaseInput;
            stopOnWindowThread();
            if (release != null) release.run();
            return null;
        }
        return User.API.CallWindowProcW(previous, window, message, w, l);
    }

    public static boolean sourceForeground() {
        Pointer current = User.API.GetForegroundWindow();
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current.equals(source)) {
                IntByReference pid = new IntByReference();
                User.API.GetWindowThreadProcessId(current, pid);
                return pid.getValue() == sourcePid;
            }
            current = User.API.GetWindow(User.API.GetAncestor(current, 2), 4);
        }
        return false;
    }

    public static boolean active() {
        return running && !exitRequested && sourceForeground();
    }

    public static void stop() {
        WindowThreadUtil.run(WindowGameInput::stopOnWindowThread);
    }

    private static void stopOnWindowThread() {
        Pointer foreground = User.API.GetForegroundWindow();
        boolean returnFocus = running && (sourceForeground() || foreground == null || foreground.equals(game));
        running = false;
        if (game != null && User.API.IsWindow(game)) {
            long style = Pointer.nativeValue(User.API.GetWindowLongPtrW(game, -20));
            User.API.SetWindowLongPtrW(game, -20, new Pointer(style & ~addedStyles));
            if (returnFocus) User.API.SetForegroundWindow(game);
            if (!wasTopmost) User.API.SetWindowPos(game, new Pointer(-2), 0, 0, 0, 0, 0x13);
        }

        if (relay != null) {
            User.API.UnregisterHotKey(relay, HOTKEY);
            User.API.DestroyWindow(relay);
        }

        game = source = relay = previous = null;
        releaseInput = null;
    }
}
