using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;

internal static class WindowProgress {
    [StructLayout(LayoutKind.Sequential)]
    private struct Rect { internal int Left, Top, Right, Bottom; }
    private delegate bool Callback(IntPtr window, IntPtr unused);
    [DllImport("user32.dll")] private static extern bool EnumWindows(Callback callback, IntPtr unused);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool PrintWindow(IntPtr window, IntPtr dc, uint flags);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetClassName(IntPtr window, StringBuilder name, int size);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetWindowText(IntPtr window, StringBuilder title, int size);

    private static IntPtr player;
    private static string lastTitle;
    private static int lastRead;
    private static int lastProgress;

    internal static int Read(string title) {
        int now = Environment.TickCount;
        if (title == lastTitle && unchecked(now - lastRead) < 400) return lastProgress;
        if (title != lastTitle || !Matches(player, title)) {
            player = IntPtr.Zero;
            EnumWindows(delegate(IntPtr window, IntPtr unused) {
                if (!Matches(window, title)) return true;
                player = window;
                return false;
            }, IntPtr.Zero);
        }
        lastTitle = title;
        lastRead = now;
        lastProgress = Capture(player);
        return lastProgress;
    }

    private static bool Matches(IntPtr window, string title) {
        if (window == IntPtr.Zero || !IsWindowVisible(window) || IsIconic(window)) return false;
        var buffer = new StringBuilder(512);
        GetClassName(window, buffer, buffer.Capacity);
        if (buffer.ToString() != "OrpheusBrowserHost") return false;
        GetWindowText(window, buffer, buffer.Capacity);
        return buffer.ToString().IndexOf(title, StringComparison.OrdinalIgnoreCase) >= 0;
    }

    private static int Capture(IntPtr window) {
        Rect rect;
        if (window == IntPtr.Zero || !GetClientRect(window, out rect)
            || rect.Right < 420 || rect.Bottom < 280 || (long)rect.Right * rect.Bottom > 12000000) return -1;
        using (var bitmap = new Bitmap(rect.Right, rect.Bottom, PixelFormat.Format32bppRgb)) {
            using (var graphics = Graphics.FromImage(bitmap)) {
                var dc = graphics.GetHdc();
                try {
                    if (!PrintWindow(window, dc, 3)) return -1;
                } finally {
                    graphics.ReleaseHdc(dc);
                }
            }
            float scale = Math.Max(1f, GetDpiForWindow(window) / 96f);
            return Locate(bitmap, scale);
        }
    }

    // The NetEase footer draws a narrow, full-width track with a single contrasting thumb.
    internal static int Locate(Bitmap bitmap, float scale) {
        int width = bitmap.Width;
        int rows = Math.Min(bitmap.Height, (int)(150 * scale));
        var area = new Rectangle(0, bitmap.Height - rows, width, rows);
        var data = bitmap.LockBits(area, ImageLockMode.ReadOnly, PixelFormat.Format32bppRgb);
        try {
            var pixels = new int[width];
            int previous = -1;
            for (int row = 0; row < rows - (int)(60 * scale); row++) {
                Marshal.Copy(IntPtr.Add(data.Scan0, row * data.Stride), pixels, 0, width);
                int center = Thumb(pixels, (int)(15 * scale));
                if (center >= 0 && previous >= 0 && Math.Abs(center - previous) <= 2
                    && SeparatesFooter(data, pixels, row + (int)Math.Ceiling(4 * scale)))
                    return (int)((long)(center + previous) * 500000 / (width - 1));
                previous = center;
            }
            return -1;
        } finally {
            bitmap.UnlockBits(data);
        }
    }

    private static int Thumb(int[] pixels, int maxWidth) {
        int right = pixels.Length - 4;
        int baseColor = pixels[3];
        int center = -1, boundaries = 0;
        // Buffering can split the track into long bands of different colors.
        for (int x = 4; x <= right; x++) {
            if (Distance(pixels[x], baseColor) <= 7) continue;
            int limit = Math.Min(right, x + maxWidth);
            int edge = x + 1;
            int contrast = Distance(pixels[x], baseColor);
            while (edge <= limit && Distance(pixels[edge], baseColor) > 7) {
                contrast = Math.Max(contrast, Distance(pixels[edge], baseColor));
                edge++;
            }
            if (edge <= limit) {
                if (edge - x >= 2 && contrast >= 22) {
                    if (center >= 0) return -1;
                    center = (x + edge - 1) / 2;
                }
                x = edge;
            } else {
                if (++boundaries > 2) return -1;
                baseColor = pixels[limit];
                x = limit;
            }
        }
        return center;
    }

    private static bool SeparatesFooter(BitmapData data, int[] pixels, int row) {
        int different = 0;
        for (int i = 1; i < 8; i += 2) {
            int x = pixels.Length * i / 8;
            int footer = Marshal.ReadInt32(data.Scan0, row * data.Stride + x * 4);
            if (Distance(pixels[x], footer) > 10) different++;
        }
        return different >= 3;
    }

    private static int Distance(int first, int second) {
        int red = Math.Abs(((first >> 16) & 255) - ((second >> 16) & 255));
        int green = Math.Abs(((first >> 8) & 255) - ((second >> 8) & 255));
        int blue = Math.Abs((first & 255) - (second & 255));
        return Math.Max(red, Math.Max(green, blue));
    }
}
