package me.cookie.kwrg.gui.title;

import me.cookie.kwrg.render.LoadingOverlayRenderer.SwitchFrame;
import net.minecraft.util.Util;

public final class TitleModeTransition {
    private static final float IMPACT = 0.35f;
    private static final float REVEAL = 0.43f;
    private static final float ENTER_END = 0.74f;
    private static final float EXIT_END = 0.94f;

    private static boolean preferredClassic;

    private boolean classic = preferredClassic;
    private boolean switching;
    private long start;
    private float elapsed;

    public void start() {
        switching = true;
        start = Util.getMillis();
        elapsed = 0.0f;
    }

    public void update() {
        if (!switching) return;
        elapsed = (Util.getMillis() - start) * 0.001f;
        if (elapsed >= (classic ? EXIT_END : ENTER_END)) {
            classic = !classic;
            preferredClassic = classic;
            switching = false;
        }
    }

    public boolean classic() {
        return classic;
    }

    public boolean switching() {
        return switching;
    }

    public boolean classicUi() {
        if (!switching) return classic;
        return classic ? elapsed < 0.25f : elapsed >= REVEAL;
    }

    public boolean panorama() {
        if (!switching) return classic;
        return classic ? elapsed < 0.52f : elapsed >= REVEAL;
    }

    public float customUi() {
        if (!switching) return 1.0f;
        return classic ? smooth(0.57f, EXIT_END, elapsed) : 1.0f - smooth(0.0f, 0.19f, elapsed);
    }

    public float customSlide() {
        if (!switching) return 0.0f;
        if (classic) return 0.56f * (1.0f - customUi());
        float t = Math.clamp(elapsed / 0.19f, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    public float classicAlpha() {
        if (!switching) return 1.0f;
        return classic ? 1.0f - smooth(0.02f, 0.25f, elapsed) : smooth(REVEAL, 0.62f, elapsed);
    }

    public float classicScale() {
        if (!switching) return 1.0f;
        return classic ? 1.0f : 1.0f + 0.05f * (1.0f - smooth(REVEAL, ENTER_END, elapsed));
    }

    public float classicDrop() {
        return switching && classic ? 14.0f * smooth(0.02f, 0.25f, elapsed) : 0.0f;
    }

    public float shake() {
        if (!switching || !classic) return 0.0f;
        return (float) Math.sin(elapsed * 132.0f) * (1.0f - smooth(0.0f, 0.16f, elapsed));
    }

    public float flash() {
        if (!switching || classic) return 0.0f;
        return 0.96f * smooth(IMPACT, REVEAL, elapsed) * (1.0f - smooth(REVEAL, 0.62f, elapsed));
    }

    public SwitchFrame scene() {
        if (!switching) return SwitchFrame.NONE;
        if (classic) {
            float gather = smooth(0.08f, 0.70f, elapsed);
            return new SwitchFrame(0.82f * (1.0f - smooth(0.11f, 0.73f, elapsed)),
                    0.92f * (1.0f - gather), gather, panorama() ? 1.0f : 0.0f,
                    smooth(0.08f, 0.52f, elapsed), (1.0f - smooth(0.0f, 0.19f, elapsed)) * 0.006f, shake(), 0.0f);
        }

        float t = Math.min(1.0f, elapsed / IMPACT);
        float approach = ((float) Math.pow(2.0, 10.0f * t) - 1.0f) / 1023.0f;
        float burst = elapsed < IMPACT ? 0.0f : Math.clamp((elapsed - IMPACT) / 0.16f, 0.001f, 1.0f);
        return new SwitchFrame(approach, burst, -1.0f, panorama() ? classicScale() : 0.0f, 0.0f, 0.0f, 0.0f, elapsed);
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
