package me.cookie.kwrg.window.capture;

import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.window.input.WindowInputFocus;
import me.cookie.kwrg.window.input.WindowInputKeys;
import me.cookie.kwrg.window.input.WindowGameInput;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class WindowCapture implements AutoCloseable {
    public record Window(long handle, int pid, String process, String title, boolean minimized) {
        public String label() {
            return title + " · " + process + (minimized ? "（最小化）" : "");
        }
    }

    public record Frame(byte[] pixels, int width, int height, int clientWidth, int clientHeight, long view, int originX,
                        int originY, int backend) {
        public Frame(byte[] pixels, int width, int height, int clientWidth, int clientHeight, long view, int originX, int originY) {
            this(pixels, width, height, clientWidth, clientHeight, view, originX, originY, 2);
        }
    }

    public record Caret(int x, int y, int height) {
    }

    public record Cursor(int x, int y) {
    }

    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();

    private static final ScheduledExecutorService WATCH = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kwrg-window-watch");
        t.setDaemon(true);
        return t;
    });

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "dynamic/window");
    private static Path helper;

    private final AtomicReference<Frame> pending = new AtomicReference<>();
    private final AtomicReference<String> move = new AtomicReference<>();
    private final ArrayBlockingQueue<String> commands = new ArrayBlockingQueue<>(256);

    private final Process process;
    private final Identifier textureId;
    private final boolean ime;
    private final long sourceHandle;
    private final int sourcePid;
    private final long restoreProtection;
    private final Thread writer;
    private final ScheduledFuture<?> watchdog;
    private volatile boolean closed, ended, gameInputPrepared;
    private volatile long lastPacket = System.nanoTime(), activeView;
    private volatile String status = "正在获取画面";
    private DynamicTexture texture;
    private Frame displayed;
    private volatile Caret caret;
    private volatile Cursor cursor;
    private double wheelX, wheelY;

    private final FrameRate received = new FrameRate(System.nanoTime());
    private final FrameRate uploaded = new FrameRate(System.nanoTime());

    public static final class FrameRate {
        private long start, frames;
        private volatile long last;
        private volatile double rate;

        public FrameRate(long now) {
            start = now;
        }

        public void frame(long now) {
            last = now;
            frames++;
            if (now - start >= 1_000_000_000L) {
                rate = frames * 1_000_000_000.0 / (now - start);
                frames = 0;
                start = now;
            }
        }

        public double value(long now) {
            return now - last > 1_500_000_000L ? 0 : rate;
        }
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")
                && System.getProperty("os.arch", "").equals("amd64");
    }

    private static synchronized Path helper() throws Exception {
        if (!supported()) throw new IOException("窗口投影需要 Windows x64");

        if (helper != null && Files.isRegularFile(helper)) return helper;

        byte[] bytes;
        try (InputStream in = WindowCapture.class.getResourceAsStream("/assets/kwrg/window/window-capture.exe")) {
            if (in == null) throw new IOException("缺少窗口抓取助手");
            bytes = in.readAllBytes();
        }

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("kwrg/window");
        Files.createDirectories(directory);

        Path target = directory.resolve("capture-" + hash + ".exe");
        if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bytes))
            Files.write(target, bytes);
        return helper = target;
    }

    public static Process startMediaSessionReader() throws Exception {
        return new ProcessBuilder(helper().toString(), "media-session")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    public static CompletableFuture<List<Window>> windows() {
        return CompletableFuture.supplyAsync(() -> {
            Process p = null;
            try {
                p = new ProcessBuilder(helper().toString(), "list", Long.toString(ProcessHandle.current().pid()))
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                Process running = p;
                ScheduledFuture<?> timeout = WATCH.schedule(running::destroyForcibly, 10, TimeUnit.SECONDS);
                List<Window> windows = new ArrayList<>();
                try (BufferedReader reader = p.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] f = line.split("\t", -1);
                        if (f.length != 7 || !f[5].equals("window")) continue;
                        windows.add(new Window(Long.parseLong(f[0]), Integer.parseInt(f[1]), decode(f[2]), decode(f[3]), f[4].equals("1")));
                    }
                    if (!p.waitFor(2, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IOException("枚举窗口失败");
                } finally {
                    timeout.cancel(false);
                }

                windows.sort(Comparator.comparing(Window::title, String.CASE_INSENSITIVE_ORDER));
                return List.copyOf(windows);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (p != null && p.isAlive()) p.destroyForcibly();
            }
        }, IO);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public WindowCapture(Window window) throws Exception {
        this(window, false);
    }

    WindowCapture(Window window, boolean ime) throws Exception {
        this.ime = ime;
        sourceHandle = window.handle;
        sourcePid = window.pid;
        textureId = ime ? Identifier.fromNamespaceAndPath("kwrg", "dynamic/window_ime") : TEXTURE;
        activeView = window.handle;

        List<String> arguments = new ArrayList<>(List.of(helper().toString(), "capture", Long.toString(window.handle),
                Integer.toString(window.pid), "1920", "0", Long.toString(ProcessHandle.current().pid()),
                "0", "2073600", ime ? "0" : "1", "auto"));
        if (ime) {
            arguments.add("ime");
            arguments.add("");
        }

        restoreProtection = ime ? 0 : WindowInputFocus.protectRestore(window.handle);
        if (restoreProtection != 0)
            WATCH.schedule(() -> WindowInputFocus.releaseForeground(restoreProtection), 1, TimeUnit.SECONDS);

        try {
            process = new ProcessBuilder(arguments).start();
        } catch (Exception failure) {
            WindowInputFocus.releaseForeground(restoreProtection);
            throw failure;
        }

        writer = Thread.ofVirtual().name("kwrg-window-input").start(this::writeCommands);
        IO.execute(this::readErrors);
        IO.execute(this::readFrames);

        watchdog = WATCH.scheduleAtFixedRate(() -> {
            if (!closed && !ended && System.nanoTime() - lastPacket > 15_000_000_000L) {
                status = "抓取助手无响应，请重新创建";
                ended = true;
                shutdownProcess();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * Parses and bounds every allocation before accepting bytes from the native pipe.
     */
    public static Frame readRecord(DataInputStream in) throws IOException {
        int magic = readInt(in), code = readInt(in), w = readInt(in), h = readInt(in);
        int cw = readInt(in), ch = readInt(in), length = readInt(in);
        int backend = readInt(in), coordinates = readInt(in);
        long view = Long.reverseBytes(in.readLong());
        Long.reverseBytes(in.readLong());

        if (magic != 0x33534341 || code <= 0 || w < 0 || h < 0 || w > 8192 || h > 8192
                || cw < 0 || ch < 0 || cw > 8192 || ch > 8192 || length < 0 || length > 1920 * 1080 * 4)
            throw new IOException("无效的窗口抓取数据");

        if (code == 20 || code == 22 || code == 23) {
            if (length < 1 || length > 16384 || code == 20 && view == 0 || code == 23 && length > 64)
                throw new IOException("无效的窗口元数据");

            byte[] metadata = new byte[length];
            in.readFully(metadata);
            return new Frame(metadata, -code, 0, 0, 0, view, 0, 0);
        }

        if (code == 1 || code == 8) {
            if (w < 2 || h < 2 || cw < 2 || ch < 2 || length != (long) w * h * 4)
                throw new IOException("无效的窗口画面尺寸");

            byte[] pixels = new byte[length];
            in.readFully(pixels);
            int x = backend == 5 ? (short) (coordinates & 65535) : backend == 3 || backend == 4 ? coordinates & 65535 : 0, y = backend == 5 ? (short) (coordinates >>> 16) : backend == 3 || backend == 4 ? coordinates >>> 16 : 0;
            if (x < -8192 || y < -8192 || x > 8192 || y > 8192 || x + cw > 8192 || y + ch > 8192)
                throw new IOException("无效的窗口裁剪位置");
            return new Frame(pixels, w, h, cw, ch, 0, x, y, backend);
        }

        if (length != 0) throw new IOException("无效的窗口状态记录");
        return new Frame(null, -code, 0, 0, 0, 0, 0, 0);
    }

    private static int readInt(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }

    private void readFrames() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(process.getInputStream(), 256 * 1024))) {
            while (!closed) {
                Frame frame = readRecord(in);
                lastPacket = System.nanoTime();

                if (frame.width == -23) {
                    cursor = readCursor(new String(frame.pixels, StandardCharsets.UTF_8));
                } else if (frame.width == -22) {
                    String[] fields = new String(frame.pixels, StandardCharsets.UTF_8).split("\t", -1);
                    if (fields.length == 1 && fields[0].equals("0")) {
                        caret = null;
                    } else if (fields.length == 3) {
                        int x = Integer.parseInt(fields[0]);
                        int y = Integer.parseInt(fields[1]);
                        int height = Integer.parseInt(fields[2]);

                        if (Math.abs((long) x) > 8192 || Math.abs((long) y) > 8192 || height < 1 || height > 1024)
                            throw new IOException("无效的文字插入点");
                        caret = new Caret(x, y, height);
                    } else throw new IOException("无效的文字插入点");
                } else if (frame.width == -20) {
                    String[] fields = new String(frame.pixels, StandardCharsets.UTF_8).split("\t", -1);
                    if (fields.length != 4) throw new IOException("无效的窗口身份");

                    int pid = Integer.parseInt(fields[0]);
                    if (pid <= 4 || pid == ProcessHandle.current().pid()) throw new IOException("无效的窗口进程");

                    if (activeView != frame.view) status = "正在获取画面";
                    activeView = frame.view;
                    pending.set(null);
                    releaseInput();
                } else if (frame.width > 0) {
                    WindowInputFocus.releaseForeground(restoreProtection);
                    received.frame(System.nanoTime());
                    convertToRgba(frame.pixels, ime || frame.backend == 5);

                    if (ime) frame = cropAlpha(frame);
                    if (!closed)
                        pending.set(new Frame(frame.pixels, frame.width, frame.height, frame.clientWidth, frame.clientHeight, activeView, frame.originX, frame.originY, frame.backend));
                    status = "实时画面";
                } else {
                    int code = -frame.width;
                    if (code >= 100) {
                        if (code == 108) gameInputPrepared = true;
                        if (code == 109) gameInputPrepared = false;
                        if (code == 101 || code == 102 || code == 104 || code == 105)
                            status = "目标程序拒绝输入（权限或后台输入限制）";
                        continue;
                    }

                    status = switch (code) {
                        case 2 -> "正在还原最小化窗口";
                        case 3 -> "目标窗口已关闭";
                        case 4 -> "窗口被系统隐藏，请移回游戏所在桌面";
                        case 5 -> "目标窗口禁止捕获";
                        case 12 -> "实时画面".equals(status) ? status : "等待窗口画面";
                        case 13 -> "WGC 不可用，正在重试";
                        default -> "等待捕获（状态 " + code + "）";
                    };

                    if (code == 3) break;
                }
            }
        } catch (Exception e) {
            if (!closed) {
                status = "窗口抓取结束：" + e.getMessage();
                CookieMain.LOGGER.warn("Window capture failed", e);
            }
        } finally {
            ended = true;
            shutdownProcess();
        }
    }

    private void readErrors() {
        try (BufferedReader errors = process.errorReader(StandardCharsets.UTF_8)) {
            String line, previous = "";
            while ((line = errors.readLine()) != null) {
                if (line.length() > 1024) line = line.substring(0, 1024);
                if (line.equals(previous)) continue;
                previous = line;
                CookieMain.LOGGER.warn("Window capture helper: {}", line);
            }
        } catch (IOException failure) {
            if (!closed) CookieMain.LOGGER.debug("Window capture error stream closed", failure);
        }
    }

    private void writeCommands() {
        try (BufferedWriter out = process.outputWriter(StandardCharsets.US_ASCII)) {
            while (!closed && !ended && process.isAlive()) {
                String command = commands.poll();
                if (command == null) command = move.getAndSet(null);
                if (command == null) {
                    LockSupport.parkNanos(100_000_000L);
                    continue;
                }
                out.write(command);
                out.newLine();
                out.flush();
            }

            out.write("R\nQ\n");
            out.flush();
        } catch (IOException e) {
            if (!closed && !ended) {
                status = "窗口输入通道已关闭";
                ended = true;
            }
        }
    }

    /**
     * Convert on the pipe reader, keeping pixel work off the game/render thread.
     */
    public static void convertToRgba(byte[] pixels) {
        convertToRgba(pixels, false);
    }

    public static void convertToRgba(byte[] pixels, boolean alpha) {
        for (int i = 0; i < pixels.length; i += 4) {
            byte blue = pixels[i];
            pixels[i] = pixels[i + 2];
            pixels[i + 2] = blue;
            if (!alpha) pixels[i + 3] = (byte) 255;
        }
    }

    public static Frame cropAlpha(Frame frame) {
        int left = frame.width;
        int top = frame.height;
        int right = 0;
        int bottom = 0;

        for (int y = 0; y < frame.height; y++) {
            for (int x = 0; x < frame.width; x++) {
                if ((frame.pixels[(y * frame.width + x) * 4 + 3] & 255) != 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x + 1);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y + 1);
                }
            }
        }

        if (right == 0) return new Frame(null, 0, 0, 0, 0, frame.view, 0, 0);

        int width = right - left;
        int height = bottom - top;
        byte[] pixels = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            System.arraycopy(frame.pixels, ((top + y) * frame.width + left) * 4, pixels, y * width * 4, width * 4);
        }

        return new Frame(pixels, width, height, Math.max(1, width * frame.clientWidth / frame.width), Math.max(1, height * frame.clientHeight / frame.height), frame.view,
                frame.originX + left * frame.clientWidth / frame.width, frame.originY + top * frame.clientHeight / frame.height);
    }

    private synchronized void command(String command, boolean motion) {
        if (closed || ended) return;

        if (motion) {
            move.set(command);
        } else {
            String preceding = move.getAndSet(null);
            if (preceding != null && !commands.offer(preceding) || !commands.offer(command)) {
                releaseInput();
                status = "输入过快，已释放按键";
            }
        }
        LockSupport.unpark(writer);
    }

    public synchronized void releaseInput() {
        gameInputPrepared = false;
        cursor = null;
        wheelX = wheelY = 0;
        move.set(null);
        commands.clear();
        commands.offer("R");
        LockSupport.unpark(writer);
    }

    private String point(double u, double v) {
        Frame f = displayed;
        if (f == null || f.view != activeView) return null;
        return inputPoint(f, u, v);
    }

    public static String inputPoint(Frame f, double u, double v) {
        return (f.originX + Math.clamp((int) (u * f.clientWidth), 0, f.clientWidth - 1)) + "\t" + (f.originY + Math.clamp((int) (v * f.clientHeight), 0, f.clientHeight - 1));
    }

    public void move(double u, double v) {
        String p = point(u, v);
        if (p != null) command("M\t" + p, true);

    }

    public void prepareGameInput() {
        releaseInput();
        command("P", false);
    }

    public boolean gameInputPrepared() { return gameInputPrepared; }

    public void startGameInput(long game) {
        WindowGameInput.start(game, sourceHandle, sourcePid, this::releaseInput);
    }

    public Caret caret() {
        return caret;
    }

    public Cursor cursor() {
        return cursor;
    }

    public static Cursor readCursor(String metadata) throws IOException {
        if (metadata.equals("0")) return null;
        String[] fields = metadata.split("\t", -1);
        if (fields.length != 3 || !fields[0].equals("1")) throw new IOException("无效的游戏光标状态");

        try {
            int x = Integer.parseInt(fields[1]);
            int y = Integer.parseInt(fields[2]);
            if (Math.abs((long) x) > 8192 || Math.abs((long) y) > 8192)
                throw new IOException("无效的游戏光标位置");
            return new Cursor(x, y);
        } catch (NumberFormatException invalid) {
            throw new IOException("无效的游戏光标位置", invalid);
        }
    }

    public void requestCaret() {
        caret = null;
        command("F", false);
    }

    public void refreshCaret() {
        command("F", false);
    }

    public double clientWidth() {
        return displayed == null ? width() : displayed.clientWidth;
    }

    public double clientHeight() {
        return displayed == null ? height() : displayed.clientHeight;
    }

    public int background(double u, double v) {
        Frame frame = displayed;
        if (frame == null) return 0xff202020;
        int x = (int) Math.clamp(u * frame.width, 0, frame.width - 1), y = (int) Math.clamp(v * frame.height, 0, frame.height - 1), p = (y * frame.width + x) * 4;
        return 0xff000000 | ((frame.pixels[p] & 255) << 16) | ((frame.pixels[p + 1] & 255) << 8) | (frame.pixels[p + 2] & 255);
    }

    public boolean nativePointer() {
        return !ime && WindowInputFocus.nativePointer(sourceHandle);
    }

    public void moveIme(int x, int y) {
        command("I\t" + x + "\t" + y, true);
    }

    public void button(int button, boolean down, double u, double v, boolean nativeDelivered) {
        String p = point(u, v);
        if (p != null && button >= 0 && button <= 4)
            command("B\t" + button + "\t" + (down ? 1 : 0) + "\t" + p + "\t" + (nativeDelivered ? 1 : 0), false);
    }

    public void scroll(double x, double y, double u, double v) {
        String p = point(u, v);
        if (p == null || !Double.isFinite(x) || !Double.isFinite(y)) return;

        wheelX += Math.clamp(x, -100, 100) * 120;
        wheelY += Math.clamp(y, -100, 100) * 120;
        int dx = (int) wheelX, dy = (int) wheelY;
        wheelX -= dx;
        wheelY -= dy;
        if (dx != 0 || dy != 0) command("W\t" + dx + "\t" + dy + "\t" + p, false);
    }

    public void key(int key, int action) {
        if (!ready()) return;
        int vk = WindowInputKeys.virtualKey(key);
        if (vk != 0) command("K\t" + vk + "\t" + (action == 0 ? 0 : 1) + "\t" + (action == 2 ? 1 : 0), false);
    }

    public void key(int key, int action, int modifiers) {
        if (!ready()) return;
        int vk = WindowInputKeys.virtualKey(key);
        if (vk != 0) command("K\t" + vk + "\t" + (action == 0 ? 0 : 1) + "\t" + (action == 2 ? 1 : 0) + "\t" + (modifiers & 63), false);
    }

    public void character(int codepoint) {
        text(codepoint);
    }

    public void text(int codepoint) {
        if (!ready() || !Character.isValidCodePoint(codepoint) || codepoint < 32 || codepoint == 127) return;
        for (char c : Character.toChars(codepoint)) command("C\t" + (int) c, false);
    }

    public Identifier upload() {
        Frame f = pending.getAndSet(null);
        if (f != null && f.view == activeView) {
            if (f.width == 0) {
                displayed = null;
                return null;
            }

            if (texture == null || displayed == null || displayed.width != f.width || displayed.height != f.height) {
                if (texture != null) Minecraft.getInstance().getTextureManager().release(textureId);
                texture = new DynamicTexture("kwrg-window", f.width, f.height, false);
                Minecraft.getInstance().getTextureManager().register(textureId, texture);
            }

            MemoryUtil.memByteBuffer(texture.getPixels().getPointer(), f.pixels.length).put(f.pixels);
            texture.upload();
            uploaded.frame(System.nanoTime());
            displayed = f;
        }
        return displayed != null && displayed.view == activeView ? textureId : null;
    }

    public int width() {
        return displayed == null ? 1920 : displayed.clientWidth;
    }

    public int height() {
        return displayed == null ? 1080 : displayed.clientHeight;
    }

    public int originX() {
        return displayed == null ? 0 : displayed.originX;
    }

    public int originY() {
        return displayed == null ? 0 : displayed.originY;
    }

    public boolean ready() {
        return !closed && !ended && displayed != null && displayed.view == activeView;
    }

    public boolean ended() {
        return ended;
    }

    public String status() {
        long now = System.nanoTime();
        return status + String.format(Locale.ROOT, " · 接收 %.0f / 上传 %.0f FPS", received.value(now), uploaded.value(now));
    }

    private void shutdownProcess() {
        WindowInputFocus.releaseForeground(restoreProtection);
        java.util.concurrent.locks.LockSupport.unpark(writer);
        WATCH.schedule(() -> {
            if (process.isAlive()) process.destroyForcibly();
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        closed = true;
        watchdog.cancel(false);
        shutdownProcess();
        pending.set(null);
        if (texture != null) Minecraft.getInstance().getTextureManager().release(textureId);
        texture = null;
        displayed = null;
    }
}
