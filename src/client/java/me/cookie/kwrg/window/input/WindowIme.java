package me.cookie.kwrg.window.input;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.input.PreeditEvent;
import me.cookie.kwrg.util.WindowThreadUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.HashSet;
import java.util.Set;

/**
 * Uses the original system popup without changing its shared compositor state.
 */
public final class WindowIme {
    private interface Procedure extends StdCallLibrary.StdCallCallback {
        Pointer invoke(Pointer window, int message, Pointer w, Pointer l, Pointer id, Pointer data);
    }

    private interface EnumWindows extends StdCallLibrary.StdCallCallback {
        boolean invoke(Pointer window, Pointer data);
    }

    private interface Windows extends StdCallLibrary {
        Windows API = Native.load("user32", Windows.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean EnumWindows(EnumWindows callback, Pointer data);

        boolean EnumChildWindows(Pointer parent, EnumWindows callback, Pointer data);

        boolean IsWindowVisible(Pointer window);

        boolean IsWindow(Pointer window);

        int GetClassNameW(Pointer window, char[] name, int size);

        int GetWindowThreadProcessId(Pointer window, IntByReference pid);

        boolean GetClientRect(Pointer window, WindowInputFocus.Rect rect);

        boolean GetCursorPos(WindowInputFocus.Point point);

        boolean ClientToScreen(Pointer window, WindowInputFocus.Point point);

        boolean ScreenToClient(Pointer window, WindowInputFocus.Point point);

        boolean SetPropW(Pointer window, String name, Pointer value);

        Pointer RemovePropW(Pointer window, String name);

        Pointer WindowFromPoint(ScreenPoint point);

        Pointer GetAncestor(Pointer window, int flag);

        Pointer GetForegroundWindow();

        Pointer DefWindowProcW(Pointer window, int message, Pointer w, Pointer l);

        Pointer SendMessageW(Pointer window, int message, Pointer w, Pointer l);
    }

    private interface Subclass extends StdCallLibrary {
        Subclass API = Native.load("comctl32", Subclass.class);

        boolean SetWindowSubclass(Pointer window, Procedure procedure, Pointer id, Pointer data);

        boolean RemoveWindowSubclass(Pointer window, Procedure procedure, Pointer id);

        Pointer DefSubclassProc(Pointer window, int message, Pointer w, Pointer l);
    }

    @Structure.FieldOrder({"x", "y"})
    public static class ScreenPoint extends Structure implements Structure.ByValue {
        public int x, y;
    }

    private interface Dwm extends StdCallLibrary {
        Dwm API = Native.load("dwmapi", Dwm.class);

        int DwmGetWindowAttribute(Pointer window, int attribute, IntByReference value, int size);
    }

    private interface Imm extends StdCallLibrary {
        Imm API = Native.load("imm32", Imm.class);

        Pointer ImmGetContext(Pointer window);

        boolean ImmReleaseContext(Pointer window, Pointer context);

        boolean ImmGetOpenStatus(Pointer context);

        boolean ImmGetConversionStatus(Pointer context, IntByReference conversion, IntByReference sentence);
    }

    public record Candidate(Pointer window, int pid, long area) {
    }

    private static final Set<Integer> CONSUMED = new HashSet<>();
    private static final Pointer SUBCLASS_ID = new Pointer(0x4B57494D);
    private static final Procedure PROCEDURE = WindowIme::message;
    private static volatile Pointer game;
    private static Pointer candidate;
    private static long glfwWindow, nextSearch;
    private static int previousMode, caretX, caretY, anchorX = -1, anchorY = -1, anchorHeight = -1;
    private static String preedit = "";
    private static PreeditEvent composition;

    private WindowIme() {
    }

    public static void start(long window) {
        WindowThreadUtil.run(() -> startOnWindowThread(window));
    }

    private static void startOnWindowThread(long window) {
        glfwWindow = window;
        game = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
        previousMode = GLFW.glfwGetInputMode(window, GLFW.GLFW_IME);
        if (!Subclass.API.SetWindowSubclass(game, PROCEDURE, SUBCLASS_ID, null))
            throw new IllegalStateException("System IME callback unavailable");

        GLFW.glfwSetInputMode(window, GLFW.GLFW_IME, GLFW.GLFW_TRUE);
        Windows.API.DefWindowProcW(game, 0x281, new Pointer(1), new Pointer(0xC000000FL));
    }

    private static Pointer message(Pointer window, int message, Pointer w, Pointer l, Pointer id, Pointer data) {
        // GLFW consumes candidate notifications for app-managed UI. Let Windows draw its original UI as well.
        if (message == 0x281) return Windows.API.DefWindowProcW(window, message, w, l);

        Pointer result = Subclass.API.DefSubclassProc(window, message, w, l);
        int notification = (int) Pointer.nativeValue(w);
        if (message == 0x282 && (notification == 3 || notification == 4 || notification == 5)
                || message == 0x10D || message == 0x10E || message == 0x10F && (Pointer.nativeValue(l) & 0x800) == 0)
            return Windows.API.DefWindowProcW(window, message, w, l);
        return result;
    }

    public static void preedit(PreeditEvent event) {
        composition = event;
        preedit = event == null ? "" : event.fullText();
    }

    public static boolean textInput() {
        if (game == null) return false;

        Pointer context = Imm.API.ImmGetContext(game);
        if (context == null) return false;

        try {
            IntByReference conversion = new IntByReference(), sentence = new IntByReference();
            return Imm.API.ImmGetOpenStatus(context) && Imm.API.ImmGetConversionStatus(context, conversion, sentence) && (conversion.getValue() & 1) != 0;
        } finally {
            Imm.API.ImmReleaseContext(game, context);
        }
    }

    public static boolean consumes(int key, int action, int modifiers) {
        if (action == GLFW.GLFW_RELEASE) return CONSUMED.remove(key);

        if (CONSUMED.contains(key) || consumes(key, modifiers, textInput(), !preedit.isEmpty())) {
            CONSUMED.add(key);
            return true;
        }
        return false;
    }

    public static boolean consumes(int key, int modifiers, boolean nativeInput, boolean composing) {
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        return composing && (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key >= GLFW.GLFW_KEY_BACKSPACE && key <= GLFW.GLFW_KEY_END)
                || nativeInput && (key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL || key == GLFW.GLFW_KEY_UNKNOWN);
    }

    public static boolean candidateClass(String name) {
        return name.startsWith("Microsoft.IME.") || name.regionMatches(true, 0, "mscandui", 0, 8) || name.startsWith("MSCTFIME ") || name.equals("CiceroUIWndFrame")
                || name.equals("Windows.UI.Core.CoreWindow") || name.equals("Windows.UI.Input.InputSite.WindowClass");
    }

    public static Candidate findCandidate() {
        Candidate[] found = {null};
        Set<Integer> systemProcesses = systemProcesses();
        EnumWindows inspect = (window, data) -> {
            if (!Windows.API.IsWindowVisible(window) || window.equals(game)) return true;

            char[] name = new char[256];
            Windows.API.GetClassNameW(window, name, name.length);
            boolean known = candidateClass(Native.toString(name));
            IntByReference pid = new IntByReference();
            Windows.API.GetWindowThreadProcessId(window, pid);
            if (pid.getValue() <= 0) return true;

            boolean own = pid.getValue() == ProcessHandle.current().pid();
            if (own && !known) return true;
            if (!own && !systemProcesses.contains(pid.getValue())) return true;
            WindowInputFocus.Rect rect = new WindowInputFocus.Rect();
            if (!Windows.API.GetClientRect(window, rect) || rect.right < 2 || rect.bottom < 2 || rect.right > 8192 || rect.bottom > 8192
                    || (long) rect.right * rect.bottom > 33554432) return true;

            IntByReference cloak = new IntByReference();
            Dwm.API.DwmGetWindowAttribute(window, 14, cloak, 4);
            if (cloak.getValue() != 0) return true;

            long area = (long) rect.right * rect.bottom;
            if (found[0] == null || area < found[0].area) found[0] = new Candidate(window, pid.getValue(), area);
            return true;
        };

        Windows.API.EnumWindows((root, data) -> {
            inspect.invoke(root, null);
            IntByReference pid = new IntByReference();
            Windows.API.GetWindowThreadProcessId(root, pid);
            if (pid.getValue() == ProcessHandle.current().pid() || systemProcesses.contains(pid.getValue()))
                Windows.API.EnumChildWindows(root, inspect, null);
            return true;
        }, null);
        // Immersive IME popups are not always included in EnumWindows' desktop-app list.
        Set<Pointer> inspected = new HashSet<>();
        for (int dy : new int[]{32, 64, 96, -32, -64, -96})
            for (int dx : new int[]{8, 80, 160, 320, -8, -80, -160}) {
                ScreenPoint point = new ScreenPoint();
                point.x = caretX + dx;
                point.y = caretY + dy;
                Pointer hit = Windows.API.WindowFromPoint(point);
                if (hit != null && inspected.add(hit)) inspect.invoke(hit, null);
            }
        return found[0];
    }

    private static Set<Integer> systemProcesses() {
        Set<Integer> result = new HashSet<>();
        var snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (WinBase.INVALID_HANDLE_VALUE.equals(snapshot)) return result;

        try {
            Tlhelp32.PROCESSENTRY32 entry = new Tlhelp32.PROCESSENTRY32();
            if (Kernel32.INSTANCE.Process32First(snapshot, entry)) do {
                String name = Native.toString(entry.szExeFile);
                if (name.equalsIgnoreCase("TextInputHost.exe") || name.equalsIgnoreCase("ChsIME.exe") || name.equalsIgnoreCase("ctfmon.exe"))
                    result.add(entry.th32ProcessID.intValue());
            } while (Kernel32.INSTANCE.Process32Next(snapshot, entry));
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return result;
    }

    public static void update(double screenU, double screenV) {
        update(screenU, screenV, 24);
    }

    public static void update(double screenU, double screenV, int lineHeight) {
        if (game == null || !game.equals(Windows.API.GetForegroundWindow())) return;

        WindowInputFocus.Rect client = new WindowInputFocus.Rect();
        if (!Windows.API.GetClientRect(game, client) || client.right < 2 || client.bottom < 2) return;

        int height = Math.clamp(lineHeight, 1, Math.max(1, client.bottom));
        int x = (int) Math.clamp(screenU * client.right, 0, client.right - 2), y = (int) Math.clamp(screenV * client.bottom, 0, Math.max(0, client.bottom - height));
        if (x != anchorX || y != anchorY || height != anchorHeight) {
            GLFW.glfwSetPreeditCursorRectangle(glfwWindow, x, y, 2, height);
            anchorX = x;
            anchorY = y;
            anchorHeight = height;
        }

        WindowInputFocus.Point caret = new WindowInputFocus.Point();
        caret.x = x;
        caret.y = y;
        if (Windows.API.ClientToScreen(game, caret)) {
            caretX = caret.x;
            caretY = caret.y;
        }

        if (preedit.isEmpty()) {
            clearCandidate();
            return;
        }

        if (candidate != null && !Windows.API.IsWindowVisible(candidate)) clearCandidate();

        long now = System.nanoTime();
        if (now >= nextSearch) {
            nextSearch = now + 100_000_000L;
            Candidate next = findCandidate();
            if (next != null) candidate = next.window;
        }

        if (candidate != null) Windows.API.SetPropW(game, "kwrg_projection_ime", candidate);
        else Windows.API.RemovePropW(game, "kwrg_projection_ime");
    }

    public static boolean composing() {
        return !preedit.isEmpty();
    }

    public static PreeditEvent composition() {
        return composition;
    }

    public static int caretIndex(PreeditEvent event) {
        String text = event.fullText();
        return text.offsetByCodePoints(0, Math.clamp(event.caretPosition(), 0, text.codePointCount(0, text.length())));
    }

    public static WindowInputFocus.Point point(double screenU, double screenV) {
        if (candidate == null || !game.equals(Windows.API.GetForegroundWindow())) return null;

        WindowInputFocus.Rect client = new WindowInputFocus.Rect(), popup = new WindowInputFocus.Rect();
        if (!Windows.API.GetClientRect(game, client) || !Windows.API.GetClientRect(candidate, popup)) return null;

        WindowInputFocus.Point point = new WindowInputFocus.Point();
        point.x = (int) Math.round(screenU * client.right);
        point.y = (int) Math.round(screenV * client.bottom);
        if (!Windows.API.ClientToScreen(game, point) || !Windows.API.ScreenToClient(candidate, point)
                || point.x < 0 || point.y < 0 || point.x >= popup.right || point.y >= popup.bottom) return null;
        return point;
    }

    public static boolean pointerOnCandidate() {
        if (candidate == null) return false;

        WindowInputFocus.Point point = new WindowInputFocus.Point();
        if (!Windows.API.GetCursorPos(point)) return false;

        ScreenPoint screen = new ScreenPoint();
        screen.x = point.x;
        screen.y = point.y;
        Pointer under = Windows.API.WindowFromPoint(screen);
        return candidate.equals(under) || candidate.equals(Windows.API.GetAncestor(under, 2));
    }

    private static void clearCandidate() {
        if (game != null) Windows.API.RemovePropW(game, "kwrg_projection_ime");
        candidate = null;
    }

    public static void stop() {
        WindowThreadUtil.run(WindowIme::stopOnWindowThread);
    }

    private static void stopOnWindowThread() {
        clearCandidate();
        if (game != null) {
            try {
                GLFW.glfwResetPreeditText(glfwWindow);
                GLFW.glfwSetInputMode(glfwWindow, GLFW.GLFW_IME, previousMode);
            } finally {
                Subclass.API.RemoveWindowSubclass(game, PROCEDURE, SUBCLASS_ID);
                Windows.API.SendMessageW(game, 0x281, game.equals(Windows.API.GetForegroundWindow()) ? new Pointer(1) : null, new Pointer(0xC000000FL));
                game = null;
            }
        }
        preedit = "";
        composition = null;
        CONSUMED.clear();
        nextSearch = 0;
        anchorX = anchorY = anchorHeight = -1;
    }
}
