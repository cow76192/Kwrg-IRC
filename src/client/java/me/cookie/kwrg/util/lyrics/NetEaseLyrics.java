package me.cookie.kwrg.util.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.window.capture.WindowCapture;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class NetEaseLyrics implements AutoCloseable {
    private static final String HOST = "https://music.163.com";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    record Song(String title, String artist, String album, long duration, long id) {
        Song(String title, String artist, String album, long duration) {
            this(title, artist, album, duration, 0);
        }
    }

    private record Sample(Song song, long position, long received, boolean playing) {
    }

    public record Playback(List<LyricTimeline.Line> lines, long position) {
    }

    private volatile Sample sample;
    private volatile long lastReport = System.nanoTime();
    private volatile String report = "starting";
    private Process reporter;
    private boolean closed;
    private Song requested;
    private List<LyricTimeline.Line> lyrics = List.of();
    private CompletableFuture<List<LyricTimeline.Line>> pending;
    private long retryAt;

    public NetEaseLyrics() {
        Thread.startVirtualThread(this::readSessions);
    }

    public boolean isResponsive() {
        return System.nanoTime() - lastReport < TimeUnit.SECONDS.toNanos(8);
    }

    public String warning() {
        Sample current = sample;
        if (!isResponsive() && !report.equals("failed")) return "Windows 媒体会话读取超时。";
        return switch (report) {
            case "idle" -> "未检测到网易云播放进度。请尝试播放或切换歌曲。";
            case "timeline" -> "SMTC 和网易云播放日志未提供有效进度。";
            case "retry", "failed" -> "无法读取网易云播放进度。";
            case "media", "log" ->
                    pending == null && current != null && current.song().equals(requested) && lyrics.isEmpty()
                            ? "当前歌曲未匹配到可用歌词。" : null;
            default -> null;
        };
    }

    public Playback playback() {
        Sample current = sample;
        if (current == null || !current.song().equals(requested) || lyrics.isEmpty()) return null;
        long duration = current.song().duration();
        long elapsed = current.playing() ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current.received()) : 0;
        long position = current.position() + elapsed;
        return new Playback(lyrics, duration > 0 ? Math.min(position, duration) : position);
    }

    public void updateLyrics() {
        Sample current = sample;
        if (pending != null && pending.isDone()) {
            lyrics = pending.join();
            pending = null;
            retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        }

        if (current == null || pending != null) return;

        Song song = current.song();
        if (!song.equals(requested) || (lyrics.isEmpty() && System.nanoTime() >= retryAt)) {
            requested = song;
            lyrics = List.of();
            pending = CompletableFuture.supplyAsync(() -> loadLyrics(song));
        }
    }

    private void readSessions() {
        try {
            Process process;
            synchronized (this) {
                if (closed) return;
                reporter = WindowCapture.startMediaSessionReader();
                process = reporter;
                process.getOutputStream().close();
            }

            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\\t", -1);
                    sample = fields.length == 7 ? sample(fields) : null;
                    if (!report.equals(fields[0]) && sample != null) {
                        CookieMain.LOGGER.debug("Particle Lyrics playback source: {}", fields[0]);
                    }

                    report = fields[0];
                    lastReport = System.nanoTime();
                }
            }

        } catch (Exception exception) {
            synchronized (this) {
                if (!closed) CookieMain.LOGGER.debug("NetEase media session reader stopped", exception);
            }
        } finally {
            report = "failed";
            close();
            sample = null;
        }
    }

    private static Sample sample(String[] fields) {
        boolean log = fields[0].equals("log");
        if (!log && !fields[0].equals("media")) return null;

        long duration = Long.parseLong(fields[4]);
        Song song = log ? new Song("", "", "", duration, Long.parseLong(fields[1]))
                : new Song(decode(fields[1]), decode(fields[2]), decode(fields[3]), duration);
        long position = Math.max(0, Long.parseLong(fields[5]));
        return new Sample(song, position, System.nanoTime(), fields[6].equals("1"));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static List<LyricTimeline.Line> loadLyrics(Song song) {
        try {
            if (song.id() > 0) return loadTrackLyrics(song.id());
            String query = URLEncoder.encode(song.title() + " " + song.artist(), StandardCharsets.UTF_8);
            JsonObject results = get("/api/search/get?type=1&limit=15&s=" + query).getAsJsonObject("result");
            if (results == null || !results.has("songs")) return List.of();

            long bestId = 0;
            long bestScore = Long.MAX_VALUE;
            for (var candidate : results.getAsJsonArray("songs")) {
                JsonObject track = candidate.getAsJsonObject();
                long score = matchScore(song, track);
                if (score < bestScore) {
                    bestId = track.get("id").getAsLong();
                    bestScore = score;
                }
            }

            if (bestId <= 0) return List.of();
            return loadTrackLyrics(bestId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            CookieMain.LOGGER.debug("NetEase lyric lookup failed", exception);
        }
        return List.of();
    }

    private static List<LyricTimeline.Line> loadTrackLyrics(long id) throws IOException, InterruptedException {
        JsonObject response = get("/api/song/lyric/v1?id=" + id + "&cp=false&lv=0&kv=0&rv=0&tv=0&yv=0&ytv=0&yrv=0");
        return LyricTimeline.parse(lyric(response, "yrc"), lyric(response, "lrc"));
    }

    static long matchScore(Song song, JsonObject track) {
        if (!normalize(song.title()).equals(normalize(string(track, "name")))) return Long.MAX_VALUE;

        String artist = normalize(song.artist());
        var artists = track.getAsJsonArray("artists");
        if (!artist.isEmpty() && (artists == null || artists.asList().stream().noneMatch(value -> {
            String name = normalize(string(value.getAsJsonObject(), "name"));
            return !name.isEmpty() && (" " + artist + " ").contains(" " + name + " ");
        }))) return Long.MAX_VALUE;

        long duration = track.get("duration").getAsLong();
        if (duration <= 0) return Long.MAX_VALUE;
        long difference = song.duration() > 0 ? Math.abs(duration - song.duration()) : 0;
        if (difference > 5000) return Long.MAX_VALUE;

        String album = normalize(song.album());
        boolean sameAlbum = album.isEmpty() || album.equals(normalize(string(track.getAsJsonObject("album"), "name")));
        return difference + (sameAlbum ? 0 : 1500);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M}]+", " ").strip();
    }

    private static String lyric(JsonObject object, String key) {
        var section = object.get(key);
        return section != null && section.isJsonObject() ? string(section.getAsJsonObject(), "lyric") : "";
    }

    private static String string(JsonObject object, String key) {
        var value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static JsonObject get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(HOST + path)).timeout(Duration.ofSeconds(6))
                .header("Referer", HOST + "/").build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().length() > 1_000_000) {
            throw new IOException("Unexpected NetEase lyric API response: " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (reporter != null) reporter.destroyForcibly();
    }
}
