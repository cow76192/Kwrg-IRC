using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

internal sealed class PlaybackLog {
    private const int Backlog = 2 * 1024 * 1024;
    private const int MaxLine = 256 * 1024;
    private const string Starting = "\"native播放资源开始load\"";
    private const string Loaded = "\"native播放资源load完成，开始播放\"";
    private const string State = "\"native播放state\"";
    private const string Seek = "\"setPlayingPosition\"";
    private static readonly Regex Header = new Regex(@"^\[(\d{1,10}):\d+:\d{4}/\d{6}:(\d{1,10}):");
    private static readonly Regex Id = new Regex(@"""songId""\s*:\s*""?(\d{1,18})\b");
    private static readonly Regex Duration = new Regex(@"""songDuration""\s*:\s*(\d{1,10})\b");
    private static readonly Regex StateValue = new Regex(State + @"\s*,\s*([12])\b");
    private static readonly Regex SeekValue = new Regex(Seek + @"\s*,\s*(-?\d{1,9}(?:\.\d+)?)");
    private static readonly byte[] Decode = BuildDecodeTable();

    private readonly string path;
    private readonly byte[] chunk = new byte[8192];
    private readonly MemoryStream line = new MemoryStream();
    private DateTime created;
    private long offset;
    private bool skipLine;
    private int pid, tick, loadedTick;
    private int loadingPid;
    private long loadingId, loadingDuration, songId, duration, position;
    private bool playing;

    internal PlaybackLog(string path) {
        this.path = path;
    }

    internal string Report() {
        try {
            Read();
            if (songId <= 0 || !IsCurrentProcess()) return null;
            return String.Join("\t", "log", songId.ToString(CultureInfo.InvariantCulture), "", "",
                duration.ToString(CultureInfo.InvariantCulture),
                PositionAt(Environment.TickCount).ToString(CultureInfo.InvariantCulture), playing ? "1" : "0");
        } catch (IOException) {
            return null;
        } catch (UnauthorizedAccessException) {
            return null;
        }
    }

    internal void Read() {
        using (var file = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete)) {
            long end = file.Length;
            var creation = File.GetCreationTimeUtc(path);
            if (creation != created || offset > end || end - offset > Backlog) {
                created = creation;
                offset = Math.Max(0, end - Backlog);
                skipLine = offset > 0;
                line.SetLength(0);
                songId = loadingId = 0;
            }
            file.Position = offset;
            while (offset < end) {
                int length = file.Read(chunk, 0, (int)Math.Min(chunk.Length, end - offset));
                if (length == 0) break;
                offset += length;
                for (int i = 0; i < length; i++) Accept(Decode[chunk[i]]);
            }
        }
    }

    private void Accept(byte value) {
        if (value == '\n') {
            if (!skipLine) ReadEvent(Encoding.UTF8.GetString(line.GetBuffer(), 0, (int)line.Length));
            line.SetLength(0);
            skipLine = false;
        } else if (!skipLine) {
            if (line.Length == MaxLine) {
                line.SetLength(0);
                skipLine = true;
            } else {
                line.WriteByte(value);
            }
        }
    }

    private void ReadEvent(string text) {
        bool starting = text.Contains(Starting);
        bool loaded = text.Contains(Loaded);
        if (!starting && !loaded && !text.Contains(State) && !text.Contains(Seek)) return;
        var header = Header.Match(text);
        int eventPid;
        uint eventTick;
        if (!header.Success || !Int32.TryParse(header.Groups[1].Value, out eventPid)
            || !UInt32.TryParse(header.Groups[2].Value, out eventTick)) return;
        int at = unchecked((int)eventTick);
        if (starting || loaded) {
            long id = Number(Id, text);
            if (id <= 0) {
                songId = loadingId = 0;
                return;
            }
            if (starting) {
                loadingPid = eventPid;
                loadingId = id;
                loadingDuration = Number(Duration, text);
                if (songId != id) songId = 0;
                return;
            }
            pid = eventPid;
            tick = loadedTick = at;
            songId = id;
            duration = Number(Duration, text);
            if (duration == 0 && loadingPid == pid && loadingId == id) duration = loadingDuration;
            position = 0;
            playing = true;
        } else if (songId > 0 && pid == eventPid && unchecked(at - tick) >= 0) {
            var seek = SeekValue.Match(text);
            var status = StateValue.Match(text);
            if (seek.Success) {
                position = Math.Max(0, (long)Math.Round(Double.Parse(seek.Groups[1].Value, CultureInfo.InvariantCulture) * 1000));
            } else if (status.Success) {
                position = PositionAt(at);
                playing = status.Groups[1].Value == "1";
            } else {
                return;
            }
            tick = at;
        }
    }

    internal long PositionAt(int now) {
        long current = position + (playing ? Math.Max(0, unchecked(now - tick)) : 0);
        return duration > 0 ? Math.Min(current, duration) : current;
    }

    private bool IsCurrentProcess() {
        int age = unchecked(Environment.TickCount - loadedTick);
        if (age < 0) return false;
        try {
            using (var process = Process.GetProcessById(pid)) {
                return process.ProcessName.Equals("cloudmusic", StringComparison.OrdinalIgnoreCase)
                    && age <= (DateTime.UtcNow - process.StartTime.ToUniversalTime()).TotalMilliseconds + 2000;
            }
        } catch (ArgumentException) {
            return false;
        } catch (InvalidOperationException) {
            return false;
        } catch (System.ComponentModel.Win32Exception) {
            return false;
        }
    }

    private static long Number(Regex pattern, string text) {
        var value = pattern.Match(text);
        return value.Success ? Int64.Parse(value.Groups[1].Value, CultureInfo.InvariantCulture) : 0;
    }

    private static byte[] BuildDecodeTable() {
        // Each byte of the client's UTF-8 event log uses a fixed substitution.
        var table = new byte[256];
        for (int i = 0; i < table.Length; i++) {
            int high = i >> 4;
            table[i] = (byte)(((high ^ ((i + 8) & 15)) << 4) | ((i >> 6) << 2) | (3 - (high & 3)));
        }
        return table;
    }
}
