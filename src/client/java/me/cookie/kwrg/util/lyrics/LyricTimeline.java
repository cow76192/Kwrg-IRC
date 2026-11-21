package me.cookie.kwrg.util.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class LyricTimeline {
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d{1,9}),(\\d{1,9})]");
    private static final Pattern YRC_SEGMENT = Pattern.compile("\\((\\d{1,9}),(\\d{1,9}),-?\\d+\\)");
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,4}):([0-5]?\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern LRC_OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d{1,8})]\\s*$");
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final Pattern TIMED_UNIT = Pattern.compile(
            "[\\p{Ps}\\p{Pi}\"']*\\d*\\p{IsLatin}[\\p{IsLatin}\\p{M}\\d]*"
                    + "(?:['’-][\\p{IsLatin}\\p{M}\\d]+)*[\\p{Pe}\\p{Pf}.,!?…;:\"']*|\\X");

    public record Glyph(String text, long start, long duration) {
    }

    public record Line(long start, long end, List<Glyph> glyphs) {
    }

    private LyricTimeline() {
    }

    public static List<Line> parse(String yrc, String lrc) {
        var lines = new TreeMap<Long, Line>();
        for (String text : yrc.lines().toList()) {
            var header = YRC_LINE.matcher(text);
            if (!header.find()) continue;

            long start = Long.parseLong(header.group(1));
            long end = start + Long.parseLong(header.group(2));

            var segment = YRC_SEGMENT.matcher(text).region(header.end(), text.length());
            var glyphs = new ArrayList<Glyph>();

            int contentStart = -1;
            long segmentStart = 0;
            long duration = 0;
            while (segment.find()) {
                if (contentStart >= 0) {
                    addGlyphs(glyphs, text.substring(contentStart, segment.start()), segmentStart, duration);
                }

                contentStart = segment.end();
                segmentStart = Long.parseLong(segment.group(1));
                duration = Long.parseLong(segment.group(2));
            }

            if (contentStart >= 0) addGlyphs(glyphs, text.substring(contentStart), segmentStart, duration);
            if (hasText(glyphs)) lines.put(start, new Line(start, end, List.copyOf(glyphs)));
        }

        if (!lines.isEmpty()) {
            return lines.values().stream().map(line -> {
                Long next = lines.higherKey(line.start());
                return next == null ? line : new Line(line.start(), next, line.glyphs());
            }).toList();
        }

        var offsetTag = LRC_OFFSET.matcher(lrc);
        long offset = offsetTag.find() ? Long.parseLong(offsetTag.group(1)) : 0;
        var texts = new TreeMap<Long, String>();
        for (String text : lrc.lines().toList()) {
            var tag = LRC_TIME.matcher(text);
            var starts = new ArrayList<Long>();
            int end = 0;
            while (tag.find() && tag.start() == end) {
                String fraction = tag.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
                starts.add(Math.max(0, Long.parseLong(tag.group(1)) * 60_000 + Long.parseLong(tag.group(2)) * 1000 + millis - offset));
                end = tag.end();
            }

            if (!starts.isEmpty()) {
                String content = text.substring(end).strip();
                for (long start : starts) texts.merge(start, content, (old, next) -> old.isEmpty() ? next : old);
            }
        }

        for (var entry : texts.entrySet()) {
            if (entry.getValue().isEmpty()) continue;

            long start = entry.getKey();
            Long next = texts.higherKey(start);
            long end = next == null ? start + 4000 : next;
            var glyphs = new ArrayList<Glyph>();
            // shortcut: LRC only has line timestamps, use source timings when YRC is available.
            addGlyphs(glyphs, entry.getValue(), start, Math.min(6500, end - start));

            if (hasText(glyphs)) lines.put(start, new Line(start, end, List.copyOf(glyphs)));
        }
        return List.copyOf(lines.values());
    }

    public static int lineAt(List<Line> lines, long position) {
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).start() <= position) low = mid + 1;
            else high = mid;
        }
        return low - 1;
    }

    private static void addGlyphs(List<Glyph> glyphs, String text, long start, long duration) {
        // Latin words share one onset, even when a singer holds the word over a long note.
        List<String> units = TIMED_UNIT.matcher(text).results().map(match -> match.group())
                .filter(unit -> unit.isBlank() || unit.codePoints().noneMatch(Character::isISOControl))
                .toList();

        long count = units.stream().filter(unit -> !unit.isBlank()).count();
        long index = 0;
        for (String unit : units) {
            if (glyphs.size() >= 160) break;

            if (unit.isBlank()) {
                glyphs.add(new Glyph(" ", start, 0));
            } else {
                long from = duration * index / count;
                long to = duration * ++index / count;
                var letters = GRAPHEME.matcher(unit);
                while (glyphs.size() < 160 && letters.find()) {
                    glyphs.add(new Glyph(letters.group(), start + from, to - from));
                }
            }
        }
    }

    private static boolean hasText(List<Glyph> glyphs) {
        return glyphs.stream().anyMatch(glyph -> !glyph.text().isBlank());
    }
}
