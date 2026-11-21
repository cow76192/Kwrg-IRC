using System;
using System.Globalization;
using System.IO;
using System.Text;
using System.Threading;
using Windows.Foundation;
using Windows.Media.Control;

internal static class MediaSessionReader {
    private static T Await<T>(IAsyncOperation<T> operation) {
        int started = Environment.TickCount;
        while (operation.Status == AsyncStatus.Started) {
            if (unchecked(Environment.TickCount - started) >= 2000) {
                operation.Cancel();
                throw new TimeoutException();
            }
            Thread.Sleep(10);
        }
        return operation.GetResults();
    }

    internal static int Run() {
        using (var output = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false))) {
            output.AutoFlush = true;
            var log = new PlaybackLog(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "NetEase", "CloudMusic", "cloudmusic.elog"));
            GlobalSystemMediaTransportControlsSessionManager manager = null;
            int lastAttempt = unchecked(Environment.TickCount - 5000);
            while (true) {
                string report;
                try {
                    if (manager == null && unchecked(Environment.TickCount - lastAttempt) >= 5000) {
                        lastAttempt = Environment.TickCount;
                        manager = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync());
                    }
                    report = manager == null ? "retry" : Read(manager, log);
                } catch (Exception) {
                    // A session can disappear between enumeration and reading its properties.
                    report = "retry";
                }
                if (report == "idle" || report == "retry") report = log.Report() ?? report;
                output.WriteLine(report);
                Thread.Sleep(250);
            }
        }
    }

    private static string Read(GlobalSystemMediaTransportControlsSessionManager manager, PlaybackLog log) {
        GlobalSystemMediaTransportControlsSession paused = null;
        foreach (var session in manager.GetSessions()) {
            string app = session.SourceAppUserModelId.ToLowerInvariant();
            if (!app.Contains("cloudmusic") && !app.Contains("netease") && !app.Contains("orpheus")) continue;
            var status = session.GetPlaybackInfo().PlaybackStatus;
            if (status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                return Report(session, true, log);
            if (status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused) paused = session;
        }
        return paused == null ? "idle" : Report(paused, false, log);
    }

    private static string Report(GlobalSystemMediaTransportControlsSession session, bool playing, PlaybackLog log) {
        var media = Await(session.TryGetMediaPropertiesAsync());
        if (String.IsNullOrWhiteSpace(media.Title)) return "idle";
        var timeline = session.GetTimelineProperties();
        var updated = timeline.LastUpdatedTime;
        var captured = DateTimeOffset.UtcNow;
        long duration = (long)timeline.EndTime.TotalMilliseconds;
        long position = (long)timeline.Position.TotalMilliseconds;
        if (updated.Year < 2000 || (duration > 0 && position > duration)) {
            string logged = log.Report();
            if (logged != null) return logged;
            int progress = WindowProgress.Read(media.Title);
            return progress < 0 ? "timeline" : Format("progress", media, 0, progress, playing);
        }
        if (playing) {
            position += (long)Math.Max(0, (captured - updated).TotalMilliseconds);
        }
        return Format("media", media, duration, position, playing);
    }

    private static string Format(string source, GlobalSystemMediaTransportControlsSessionMediaProperties media,
            long duration, long position, bool playing) {
        return String.Join("\t", source, Encode(media.Title), Encode(media.Artist), Encode(media.AlbumTitle),
            duration.ToString(CultureInfo.InvariantCulture), position.ToString(CultureInfo.InvariantCulture),
            playing ? "1" : "0");
    }

    private static string Encode(string value) {
        return Convert.ToBase64String(Encoding.UTF8.GetBytes(value ?? ""));
    }
}
