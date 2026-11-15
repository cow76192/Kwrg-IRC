using System;
using System.Runtime.InteropServices;

public static partial class CheatProgramWindowCapture {
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr SendMessageTimeoutW(IntPtr window, uint message, UIntPtr w, IntPtr l,
        uint flags, uint timeout, out UIntPtr result);

    static int KeyboardModifiers() {
        int modifiers = 0;
        if (HeldKeys.Contains(0x10) || HeldKeys.Contains(0xA0) || HeldKeys.Contains(0xA1)) modifiers |= 1;
        if (HeldKeys.Contains(0x11) || HeldKeys.Contains(0xA2) || HeldKeys.Contains(0xA3)) modifiers |= 2;
        if (HeldKeys.Contains(0x12) || HeldKeys.Contains(0xA4) || HeldKeys.Contains(0xA5)) modifiers |= 4;
        if (HeldKeys.Contains(0x5B) || HeldKeys.Contains(0x5C)) modifiers |= 8;
        return modifiers;
    }

    static void ModifierState(byte[] state, int generic, int left, int right, bool down) {
        state[generic] = (byte)((state[generic] & 1) | (down ? 128 : 0));
        bool rightDown = down && HeldKeys.Contains(right);
        bool leftDown = down && (!rightDown || HeldKeys.Contains(left));
        state[right] = (byte)((state[right] & 1) | (rightDown ? 128 : 0));
        state[left] = (byte)((state[left] & 1) | (leftDown ? 128 : 0));
    }

    static bool SendKey(IntPtr target, uint message, int key, IntPtr bits, int modifiers) {
        if (!ValidTarget(target)) return false;
        uint pid;
        uint targetThread = GetWindowThreadProcessId(target, out pid);
        uint sender = GetCurrentThreadId();
        MESSAGE ignored;
        PeekMessageW(out ignored, IntPtr.Zero, 0, 0, 0);
        if (targetThread == 0 || !AttachThreadInput(sender, targetThread, true)) return false;

        byte[] saved = new byte[256];
        bool captured = GetKeyboardState(saved);
        try {
            if (!captured) return false;
            byte[] state = (byte[])saved.Clone();
            foreach (int held in HeldKeys) state[held] |= 128;
            state[key] = (byte)((state[key] & 1) | (HeldKeys.Contains(key) ? 128 : 0));
            ModifierState(state, 0x10, 0xA0, 0xA1, (modifiers & 1) != 0);
            ModifierState(state, 0x11, 0xA2, 0xA3, (modifiers & 2) != 0);
            ModifierState(state, 0x12, 0xA4, 0xA5, (modifiers & 4) != 0);
            if (!SetKeyboardState(state)) return false;

            // Sent key messages do not pass through TranslateMessage: text arrives exactly once via C/T.
            UIntPtr result;
            bool delivered = SendMessageTimeoutW(target, message, new UIntPtr((uint)key), bits, 2, 100, out result) != IntPtr.Zero;
            if (!delivered) return false;

            // WinForms handles Select All in its message pump, before the edit window procedure.
            if (message == 0x100 && key == 0x41 && (modifiers & 15) == 2 && NativeEditor(target))
                return SendMessageTimeoutW(target, 0xB1, UIntPtr.Zero, new IntPtr(-1), 2, 100, out result) != IntPtr.Zero;
            return true;
        } finally {
            if (captured) SetKeyboardState(saved);
            AttachThreadInput(sender, targetThread, false);
        }
    }

    static bool NativeEditor(IntPtr target) {
        string kind = WindowClass(target);
        return kind.Equals("Edit", StringComparison.OrdinalIgnoreCase) ||
            kind.StartsWith("RichEdit", StringComparison.OrdinalIgnoreCase) ||
            kind.StartsWith("WindowsForms10.EDIT.", StringComparison.OrdinalIgnoreCase) ||
            kind.StartsWith("WindowsForms10.RichEdit", StringComparison.OrdinalIgnoreCase);
    }

    static bool SendCharacter(IntPtr target, int character) {
        if (!ValidTarget(target)) return false;
        UIntPtr result;
        return SendMessageTimeoutW(target, 0x102, new UIntPtr((uint)character), new IntPtr(1), 2, 100, out result) != IntPtr.Zero;
    }
}
