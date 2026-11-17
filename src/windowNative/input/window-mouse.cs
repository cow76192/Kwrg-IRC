using System;

public static partial class CheatProgramWindowCapture {
    const int NativeButtonDelivered = 0x8000;
    static int InjectedPointerButtons;

    static bool ForwardNativeButton(int button, bool down, int x, int y) {
        if (!down && (InjectedPointerButtons & (1 << button)) != 0) {
            InjectPointerButton(button, false);
            return true;
        }
        if (!RefreshNativePointer(x, y)) return false;
        InjectPointerButton(button, down);
        return true;
    }

    static void InjectPointerButton(int button, bool down) {
        uint flags;
        switch (button) {
            case 0: flags = down ? 2u : 4u; break;
            case 1: flags = down ? 8u : 16u; break;
            case 2: flags = down ? 32u : 64u; break;
            default: flags = down ? 128u : 256u; break;
        }
        uint data = button >= 3 ? (uint)(button - 2) : 0;
        mouse_event(flags, 0, 0, data, new UIntPtr(PointerMarker));
        if (down) InjectedPointerButtons |= 1 << button;
        else InjectedPointerButtons &= ~(1 << button);
    }

    static void ReleaseNativeButtons() {
        for (int button = 0; button <= 4; button++) {
            if ((InjectedPointerButtons & (1 << button)) != 0) InjectPointerButton(button, false);
        }
    }
}
