package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cookie.kwrg.util.lyrics.LyricTimeline;
import me.cookie.kwrg.module.modules.style.ParticleLyricsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class ParticleLyricsRenderer {
    private static final int FONT_SIZE = ParticleLyricsModule.DEFAULT_FONT_SIZE;
    private static final float LATIN_FONT_SCALE = 1.4f;
    private static final int MAX_AUTO_GATHER_MS = 320;
    private static final int INDENT = 14;
    private static final int LINE_SPACING = 30;
    private static final int GATHER_RADIUS = 19;
    private static final int MAX_AUTOMATIC_DENSITY = 1000;
    private static final int MAX_EFFECTIVE_DENSITY = ParticleLyricsModule.MAX_PARTICLE_DENSITY + MAX_AUTOMATIC_DENSITY;
    private static final float ARRIVAL_SPREAD = 0.78f;
    private static final float JITTER = 0.32f;
    private static final Font FONT = new Font(Font.DIALOG, Font.BOLD, FONT_SIZE);
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
    private static final Map<String, Mask> MASKS = new HashMap<>();
    private static Font[] fallbackFonts;
    private static List<LyricTimeline.Line> track;
    private static final Map<Integer, Pair> PAIRS = new HashMap<>();
    private static int lastLine = -1;
    private static long lastPosition;
    private static int lastWidth;
    private static int lastHeight;

    private ParticleLyricsRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (ParticleLyricsModule.worldSpace()) {
            PAIRS.clear();
            track = null;
            return;
        }

        var playback = ParticleLyricsModule.playback();
        List<LyricTimeline.Line> lines = playback == null ? null : playback.lines();
        long position = playback == null ? 0 : playback.position();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int playingLine = lines == null ? -1 : LyricTimeline.lineAt(lines, position);
        if (track != lines || width != lastWidth || height != lastHeight
                || Math.abs(playingLine - lastLine) > 1 || position < lastPosition - 300) {
            PAIRS.clear();
        }

        track = lines;
        lastLine = playingLine;
        lastPosition = position;
        lastWidth = width;
        lastHeight = height;
        if (lines == null || width < 64 || height < 64) return;

        int gatherMs = ParticleLyricsModule.gatherMs();
        int index = lastGatheringLine(lines, playingLine, position, gatherMs);
        if (index < 0) return;

        int disperseMs = ParticleLyricsModule.disperseMs();
        int disperseRadius = ParticleLyricsModule.disperseRadius();
        int firstVisible = firstVisibleLine(lines, index, position, disperseMs);
        PAIRS.keySet().removeIf(key -> key < firstVisible / 2 || key > index / 2);

        var pose = new Matrix3x2f(graphics.pose());
        for (int line = firstVisible; line <= index; line++) {
            drawLine(graphics, pose, lines, line, position, gatherMs, disperseMs, disperseRadius);
        }
    }

    static int lastGatheringLine(List<LyricTimeline.Line> lines, int playingLine, long position, int gatherMs) {
        int index = playingLine;
        long lookAhead = position + (gatherMs > 0 ? gatherMs : MAX_AUTO_GATHER_MS);
        while (index + 1 < lines.size()) {
            var next = lines.get(index + 1);
            if (next.start() > lookAhead || next.glyphs().stream().noneMatch(glyph ->
                    !glyph.text().isBlank() && gatheringStart(glyph, gatherMs) <= position)) break;
            index++;
        }
        return index;
    }

    static int firstVisibleLine(List<LyricTimeline.Line> lines, int index, long position, int disperseMs) {
        while (index > 0 && position - lines.get(index - 1).end() < disperseMs) index--;
        return index;
    }

    private static void drawLine(GuiGraphicsExtractor graphics, Matrix3x2f pose, List<LyricTimeline.Line> lines,
                                 int index, long position, int gatherMs, int disperseMs, int disperseRadius) {
        LyricTimeline.Line line = lines.get(index);
        if (position - line.end() >= disperseMs) return;

        Pair pair = PAIRS.computeIfAbsent(index / 2, key -> layout(lines, key * 2));
        Row row = (index & 1) == 0 ? pair.first() : pair.second();
        Animation animation = animate(row, line, position, gatherMs, disperseMs, disperseRadius);
        if (animation == null) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        float span = Math.max(pair.first().width(), pair.second().width() + INDENT);
        float requested = fontScale();
        float particleSize = ParticleLyricsModule.particleSize();
        float particleRadius = particleSize * 0.5f;

        float padding = maximumMovement(disperseRadius) + JITTER + particleRadius + 2;
        float ascent = Math.max(FONT_SIZE, -pair.first().top());
        float lineSpacing = Math.max(LINE_SPACING, pair.first().bottom() - pair.second().top() + 4);
        float pairHeight = ascent + lineSpacing + Math.max(8, pair.second().bottom());

        float scale = Math.min(requested, Math.min((width - 8f) / (span + 2 * padding),
                (height - 8f) / (pairHeight + 2 * padding)));
        float margin = 4 + padding * scale;

        float x = margin + pair.anchor().horizontal() * Math.max(0, width - 2 * margin - span * scale)
                + (index & 1) * INDENT * scale;
        float y = margin + ascent * scale
                + pair.anchor().vertical() * Math.max(0, height - 2 * margin - pairHeight * scale)
                + (index & 1) * lineSpacing * scale;

        float pixelRadius = 0.5f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        var quad = ParticleQuad.forPixel(particleRadius * scale, pixelRadius);
        float renderPadding = padding * scale + quad.radius() - particleRadius * scale;

        int left = (int) Math.floor(x - renderPadding);
        int top = (int) Math.floor(y + row.top() * scale - renderPadding);
        int right = (int) Math.ceil(x + row.width() * scale + renderPadding);
        int bottom = (int) Math.ceil(y + row.bottom() * scale + renderPadding);

        var bounds = new ScreenRectangle(left, top, right - left, bottom - top).transformMaxBounds(pose);
        var scissor = graphics.scissorStack.peek();
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;

        graphics.guiRenderState.addGuiElement(new ParticleMesh(animation, pose, bounds, scissor, x, y, scale,
                effectiveDensity(ParticleLyricsModule.particleDensity(), particleSize), quad));
    }

    private static Pair layout(List<LyricTimeline.Line> lines, int index) {
        Row first = row(lines.get(index));
        Row second = index + 1 < lines.size() ? row(lines.get(index + 1)) : new Row(List.of(), 0, 0, 0);
        return new Pair(first, second, pickHudAnchor(ThreadLocalRandom.current()));
    }

    static HudAnchor pickHudAnchor(RandomGenerator random) {
        float x = random.nextFloat();
        float y = random.nextFloat();
        if (random.nextInt(5) == 0) return new HudAnchor(x, y);
        return new HudAnchor(nearEdge(x), nearEdge(y));
    }

    private static float nearEdge(float coordinate) {
        return coordinate < 0.5f ? coordinate * 0.36f : 1 - (1 - coordinate) * 0.36f;
    }

    static float fontScale() {
        return ParticleLyricsModule.fontSize() / (float) FONT_SIZE;
    }

    static int effectiveDensity(int manualDensity, float particleSize) {
        float normal = ParticleLyricsModule.DEFAULT_PARTICLE_SIZE;
        float fraction = Math.clamp((normal - particleSize) / (normal - ParticleLyricsModule.MIN_PARTICLE_SIZE), 0f, 1f);
        return manualDensity + Math.round(fraction * MAX_AUTOMATIC_DENSITY);
    }

    static Animation animate(Row row, LyricTimeline.Line line, long position, int gatherMs, int disperseMs,
                             int disperseRadius) {
        long glyphPosition = Math.min(position, line.end());
        long dispersedFor = position - line.end();
        if (dispersedFor >= disperseMs || row.letters().stream().noneMatch(letter ->
                gatheringStart(letter.glyph(), gatherMs) <= glyphPosition)) return null;

        float fade = dispersedFor < 0 ? 0 : dispersedFor / (float) disperseMs;
        return new Animation(row, position, glyphPosition, fade, gatherMs, disperseRadius);
    }

    static long gatheringStart(LyricTimeline.Glyph glyph, int gatherMs) {
        long duration = gatherMs > 0 ? gatherMs : Mth.clamp(glyph.duration(), 95L, MAX_AUTO_GATHER_MS);
        return Math.max(0, glyph.start() - duration);
    }

    private static float maximumMovement(int disperseRadius) {
        return GATHER_RADIUS * ARRIVAL_SPREAD + disperseRadius;
    }

    static Row row(LyricTimeline.Line line) {
        var letters = new ArrayList<Letter>();
        float width = 0;
        float top = 0;
        float bottom = 0;
        for (var glyph : line.glyphs()) {
            if (MASKS.size() >= 512) MASKS.clear();

            Mask mask = MASKS.computeIfAbsent(glyph.text(), ParticleLyricsRenderer::rasterize);
            if (!mask.dots().isEmpty()) {
                letters.add(new Letter(glyph, mask, width));
                top = Math.min(top, mask.top());
                bottom = Math.max(bottom, mask.bottom());
            }

            width += mask.advance();
        }
        return new Row(List.copyOf(letters), width, top, bottom);
    }

    static float glyphFontScale(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN)
                ? LATIN_FONT_SCALE : 1f;
    }

    private static Mask rasterize(String text) {
        Font font = FONT;
        if (font.canDisplayUpTo(text) >= 0) {
            if (fallbackFonts == null) fallbackFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            for (Font fallback : fallbackFonts) {
                if (fallback.canDisplayUpTo(text) == -1) {
                    font = fallback.deriveFont(Font.BOLD, (float) FONT_SIZE);
                    break;
                }
            }
        }

        float scale = glyphFontScale(text);
        if (scale != 1f) font = font.deriveFont(FONT_SIZE * scale);
        var glyph = font.layoutGlyphVector(FONT_CONTEXT, text.toCharArray(), 0, text.length(), Font.LAYOUT_LEFT_TO_RIGHT);
        var bounds = glyph.getPixelBounds(FONT_CONTEXT, 0, 0);

        float advance = (float) glyph.getGlyphPosition(glyph.getNumGlyphs()).getX() + 0.9f;
        if (bounds.isEmpty()) return new Mask(List.of(), advance, 0, 0);
        var image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        var pen = image.createGraphics();
        try {
            pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            pen.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            pen.setColor(Color.WHITE);
            pen.drawGlyphVector(glyph, -bounds.x, -bounds.y);
        } finally {
            pen.dispose();
        }

        var dots = new ArrayList<Dot>();
        var random = new Random(text.hashCode());
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha < 40) continue;

                float order = ((x + y) & 1) + random.nextFloat();
                dots.add(new Dot(bounds.x + x + 0.5f, bounds.y + y + 0.5f, alpha / 255f,
                        random.nextFloat() * 6.283185f, random.nextFloat(0.35f, 1), order));
            }
        }

        dots.sort(Comparator.comparingDouble(Dot::order));
        // Subpixel samples inside each mask pixel keep automatic density effective above 200%.
        int baseSamples = dots.size();
        int totalSamples = baseSamples * MAX_EFFECTIVE_DENSITY / ParticleLyricsModule.MAX_PARTICLE_DENSITY;
        dots.ensureCapacity(totalSamples);

        for (int i = baseSamples; i < totalSamples; i++) {
            Dot base = dots.get(i % baseSamples);
            dots.add(new Dot(base.x() + random.nextFloat(-0.32f, 0.32f),
                    base.y() + random.nextFloat(-0.32f, 0.32f), base.coverage(),
                    random.nextFloat() * 6.283185f, random.nextFloat(0.35f, 1), 0));
        }
        return new Mask(List.copyOf(dots), advance, bounds.y, bounds.y + bounds.height);
    }

    private record Dot(float x, float y, float coverage, float angle, float spread, float order) {
    }

    private record Mask(List<Dot> dots, float advance, float top, float bottom) {
    }

    private record Letter(LyricTimeline.Glyph glyph, Mask mask, float offset) {
    }

    record Footprint(float left, float top, float right, float bottom) {
    }

    record Row(List<Letter> letters, float width, float top, float bottom) {
        Footprint footprint(int disperseRadius, float particleRadius) {
            float left = 0, right = 0, top = 0, bottom = 0;
            for (Letter letter : letters) {
                for (Dot dot : letter.mask().dots()) {
                    float x = letter.offset() + dot.x() - width / 2;
                    float y = dot.y();
                    float scatter = maximumMovement(disperseRadius) * dot.spread();
                    float padX = Math.abs(Mth.cos(dot.angle() + letter.offset() * 0.15f)) * scatter
                            + JITTER + particleRadius;
                    float padY = Math.abs(Mth.sin(dot.angle() + letter.offset() * 0.15f)) * scatter
                            + JITTER + particleRadius;
                    left = Math.min(left, x - padX);
                    right = Math.max(right, x + padX);
                    top = Math.min(top, y - padY);
                    bottom = Math.max(bottom, y + padY);
                }
            }
            return new Footprint(left, top, right, bottom);
        }
    }

    record HudAnchor(float horizontal, float vertical) {
    }

    private record Pair(Row first, Row second, HudAnchor anchor) {
    }

    @FunctionalInterface
    interface ParticleSink {
        void draw(float x, float y, int color);
    }

    record Animation(Row row, long position, long glyphPosition, float fade, int gatherMs, int disperseRadius) {
        void draw(int density, ParticleSink sink) {
            float phase = (position % 300_000) * 0.005f;
            for (Letter letter : row.letters()) {
                long start = gatheringStart(letter.glyph(), gatherMs);
                long age = glyphPosition - start;
                if (age < 0) continue;

                long duration = letter.glyph().start() - start;
                float progress = duration > 0 ? Mth.clamp(age / (float) duration, 0, 1) : 1;
                float unsettled = 1 - progress;

                float spread = GATHER_RADIUS * unsettled * unsettled * ARRIVAL_SPREAD + disperseRadius * fade * fade;
                int settled = ARGB.srgbLerp(Mth.clamp(letter.offset() / row.width(), 0, 1), 0xFF88E8FF, 0xFFFFD7F6);
                int rgb = ARGB.srgbLerp(progress, 0xFF8772F1, settled) & 0xFFFFFF;
                float opacity = Math.min(1, progress * 2 + 0.25f) * (1 - fade);

                var dots = letter.mask().dots();
                int count = Math.clamp(Math.round(dots.size() * (density / (float) MAX_EFFECTIVE_DENSITY)), 1, dots.size());
                for (int i = 0; i < count; i++) {
                    Dot dot = dots.get(i);
                    float angle = dot.angle() + letter.offset() * 0.15f;
                    float radius = spread * dot.spread();
                    float px = letter.offset() + dot.x() + Mth.cos(angle) * radius
                            + Mth.sin(phase + angle) * JITTER;
                    float py = dot.y() + Mth.sin(angle) * radius
                            + Mth.cos(phase * 0.83f + angle) * JITTER;
                    int color = rgb | (Math.round(255 * opacity * dot.coverage()) << 24);
                    sink.draw(px, py, color);
                }
            }
        }
    }

    record ParticleQuad(float radius, float coverage) {
        static ParticleQuad forPixel(float desiredRadius, float pixelRadius) {
            // For subpixel dots, preserve integrated opacity instead of dropping raster samples.
            float radius = Math.max(desiredRadius, pixelRadius);
            float ratio = desiredRadius / radius;
            return new ParticleQuad(radius, ratio * ratio);
        }

        int color(int argb) {
            return ARGB.multiplyAlpha(argb, coverage);
        }
    }

    private record ParticleMesh(Animation animation, Matrix3x2f pose, ScreenRectangle bounds,
                                ScreenRectangle scissorArea, float x, float y, float scale, int density,
                                ParticleQuad quad)
            implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            float size = quad.radius();
            animation.draw(density, (localX, localY, color) -> {
                float px = x + localX * scale;
                float py = y + localY * scale;
                int argb = quad.color(color);
                vertices.addVertexWith2DPose(pose, px - size, py - size).setColor(argb);
                vertices.addVertexWith2DPose(pose, px - size, py + size).setColor(argb);
                vertices.addVertexWith2DPose(pose, px + size, py + size).setColor(argb);
                vertices.addVertexWith2DPose(pose, px + size, py - size).setColor(argb);
            });
        }
    }
}
