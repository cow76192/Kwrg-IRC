using System;
using System.Runtime.InteropServices;
using System.Text;

public static partial class CheatProgramWindowCapture {
    static string lastGamePointer = "0";

    static void WriteGamePointer() {
        string position = "0";
        var cursor = new CURSORINFO { Size = Marshal.SizeOf(typeof(CURSORINFO)) };

        if (NativeGameInput && ValidTarget(GetForegroundWindow()) &&
            GetCursorInfo(ref cursor) && (cursor.Flags & 1) != 0 && cursor.Cursor != IntPtr.Zero) {
            POINT point = cursor.Position;
            if (ScreenToClient(Selected, ref point) &&
                point.X >= -MaxEdge && point.X <= MaxEdge &&
                point.Y >= -MaxEdge && point.Y <= MaxEdge) {
                position = "1\t" + point.X + "\t" + point.Y;
            }
        }

        if (position == lastGamePointer) return;
        lastGamePointer = position;
        byte[] bytes = Encoding.UTF8.GetBytes(position);
        WriteRecord(Record(23, 0, 0, 0, 0, bytes, 0, 0, 0));
    }
}
