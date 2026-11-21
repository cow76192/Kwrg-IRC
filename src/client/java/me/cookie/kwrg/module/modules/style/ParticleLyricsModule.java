package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SliderPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.util.lyrics.NetEaseLyrics;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.window.capture.WindowCapture;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ParticleLyricsModule extends Module {
    public static final int DEFAULT_FONT_SIZE = 24;
    public static final int DEFAULT_DISPERSE_MS = 520;
    public static final int DEFAULT_DISPERSE_RADIUS = 19;
    public static final int MAX_PARTICLE_DENSITY = 200;
    public static final float MIN_PARTICLE_SIZE = 0.20f;
    public static final float DEFAULT_PARTICLE_SIZE = 1.00f;
    public static final float MAX_PARTICLE_SIZE = 2.50f;
    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 160;
    private static final int MAX_GATHER_MS = 1000;
    private static final int MAX_DISPERSE_MS = 2500;
    private static final int MAX_DISPERSE_RADIUS = 64;
    private static final int MIN_PARTICLE_DENSITY = 25;
    private static final int DEFAULT_PARTICLE_DENSITY = 100;
    private static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, settings -> {
        settings.lyricFontSize = Math.clamp(settings.lyricFontSize, MIN_FONT_SIZE, MAX_FONT_SIZE);
        settings.lyricGatherMs = Math.clamp(settings.lyricGatherMs, 0, MAX_GATHER_MS);
        settings.lyricDisperseMs = Math.clamp(settings.lyricDisperseMs, 0, MAX_DISPERSE_MS);
        settings.lyricDisperseRadius = Math.clamp(settings.lyricDisperseRadius, 0, MAX_DISPERSE_RADIUS);
        settings.lyricParticleDensity = Math.clamp(settings.lyricParticleDensity, MIN_PARTICLE_DENSITY, MAX_PARTICLE_DENSITY);
        settings.lyricParticleSize = Float.isFinite(settings.lyricParticleSize)
                ? Math.clamp(settings.lyricParticleSize, MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE) : DEFAULT_PARTICLE_SIZE;
    });
    private static NetEaseLyrics source;
    private static long reportAfter;
    private static String lastWarning;

    public static final class Settings {
        public boolean particleLyrics;
        public boolean lyricWorldSpace;
        public int lyricFontSize = DEFAULT_FONT_SIZE;
        public int lyricGatherMs;
        public int lyricDisperseMs = DEFAULT_DISPERSE_MS;
        public int lyricDisperseRadius = DEFAULT_DISPERSE_RADIUS;
        public int lyricParticleDensity = DEFAULT_PARTICLE_DENSITY;
        public float lyricParticleSize = DEFAULT_PARTICLE_SIZE;
    }

    public ParticleLyricsModule() {
        super("Particle Lyrics", Category.STYLE, PanelItemComponent.ItemType.TOGGLE, () -> CONFIG.get().particleLyrics, CONFIG);
    }

    @Override
    public void onExecute() {
        CONFIG.updateDelayed(400, settings -> settings.particleLyrics = !settings.particleLyrics);
        if (CONFIG.get().particleLyrics && !WindowCapture.supported()) {
            ChatNotifier.send("Particle Lyrics 需要 Windows x64。", NotificationType.WARN);
        }
        tick();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        panel.child(SliderPaneComponent.ofInt("字体大小", MIN_FONT_SIZE, MAX_FONT_SIZE, DEFAULT_FONT_SIZE,
                ParticleLyricsModule::fontSize, value -> CONFIG.get().lyricFontSize = value, value -> value + " px"));

        panel.child(SliderPaneComponent.ofInt("粒子密度", MIN_PARTICLE_DENSITY, MAX_PARTICLE_DENSITY,
                DEFAULT_PARTICLE_DENSITY, ParticleLyricsModule::particleDensity,
                value -> CONFIG.get().lyricParticleDensity = value, value -> value + "%").step(5));

        panel.child(SliderPaneComponent.ofFloat("粒子大小", MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE, DEFAULT_PARTICLE_SIZE, 0.05f,
                ParticleLyricsModule::particleSize, value -> CONFIG.get().lyricParticleSize = value,
                value -> String.format(Locale.ROOT, "%.2f px", value)));

        panel.child(SliderPaneComponent.ofInt("聚集时长", 0, MAX_GATHER_MS, 0,
                ParticleLyricsModule::gatherMs, value -> CONFIG.get().lyricGatherMs = value,
                value -> value == 0 ? "随歌词节奏" : value + " ms").step(10));

        panel.child(SliderPaneComponent.ofInt("扩散时长", 0, MAX_DISPERSE_MS, DEFAULT_DISPERSE_MS,
                ParticleLyricsModule::disperseMs, value -> CONFIG.get().lyricDisperseMs = value,
                value -> value == 0 ? "立即消失" : value + " ms").step(10));

        panel.child(SliderPaneComponent.ofInt("扩散半径", 0, MAX_DISPERSE_RADIUS, DEFAULT_DISPERSE_RADIUS,
                ParticleLyricsModule::disperseRadius, value -> CONFIG.get().lyricDisperseRadius = value,
                value -> value + " px"));

        panel.child(new PanelItemComponent("世界空间渲染", PanelItemComponent.ItemType.TOGGLE,
                (button, click) -> CONFIG.updateDelayed(400, settings -> settings.lyricWorldSpace = !settings.lyricWorldSpace),
                null, ParticleLyricsModule::worldSpace));
    }

    public static int fontSize() {
        return CONFIG.get().lyricFontSize;
    }

    public static int particleDensity() {
        return CONFIG.get().lyricParticleDensity;
    }

    public static float particleSize() {
        return CONFIG.get().lyricParticleSize;
    }

    public static boolean worldSpace() {
        return CONFIG.get().lyricWorldSpace;
    }

    public static int gatherMs() {
        return CONFIG.get().lyricGatherMs;
    }

    public static int disperseMs() {
        return CONFIG.get().lyricDisperseMs;
    }

    public static int disperseRadius() {
        return CONFIG.get().lyricDisperseRadius;
    }

    public static void tick() {
        if (!CONFIG.get().particleLyrics || !WindowCapture.supported()) {
            stop();
            return;
        }

        if (source == null) {
            source = new NetEaseLyrics();
            reportAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        }

        source.updateLyrics();
        if (System.nanoTime() - reportAfter < 0) return;

        String warning = source.warning();
        if (warning != null && !warning.equals(lastWarning)) {
            ChatNotifier.send("Particle Lyrics · " + warning, NotificationType.WARN);
        }

        lastWarning = warning;
        if (!source.isResponsive()) {
            source.close();
            source = null;
        }
    }

    public static NetEaseLyrics.Playback playback() {
        return source == null ? null : source.playback();
    }

    public static void stop() {
        lastWarning = null;
        if (source != null) {
            source.close();
            source = null;
        }
    }
}
