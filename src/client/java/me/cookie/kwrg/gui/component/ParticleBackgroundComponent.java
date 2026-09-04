package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ParticleBackgroundComponent extends BaseUIComponent {

    private static final int PARTICLE_COUNT = 85;
    private static final int PARTICLE_ALPHA = (int) (255 * 0.75f);

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long lastNanoTime = System.nanoTime();

    public ParticleBackgroundComponent() {
        this.sizing(Sizing.fill(100), Sizing.fill(100));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastNanoTime) / 1_000_000_000.0f;
        lastNanoTime = now;
        dt = Mth.clamp(dt, 0.001f, 0.033f);

        float exactMouseX = RenderUtil.getExactMouseX();
        float exactMouseY = RenderUtil.getExactMouseY();

        int w = this.width();
        int h = this.height();

        if (particles.isEmpty() && w > 0 && h > 0) {
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                particles.add(new Particle(
                        random.nextFloat() * w,
                        random.nextFloat() * h,
                        (random.nextFloat() - 0.5f) * 18.0f,
                        random.nextFloat() * 16.0f + 12.0f,
                        random.nextFloat() * 0.8f + 1.2f
                ));
            }
        }

        float connectDist = 85.0f;
        float connectDistSq = connectDist * connectDist;
        int connected = 0;

        for (Particle p : particles) {
            p.update(dt, w, h);

            float px = this.x() + p.x;
            float py = this.y() + p.y;

            float dx = exactMouseX - px;
            float dy = exactMouseY - py;
            float distSq = dx * dx + dy * dy;

            if (distSq < connectDistSq && connected < 8) {
                float dist = (float) Math.sqrt(distSq);
                float factor = 1.0f - dist / connectDist;
                int lineAlpha = (int) (PARTICLE_ALPHA * factor * factor * 0.85f);

                if (lineAlpha > 3) {
                    Color lineColor = RenderUtil.withAlpha(0xFFFFFF, lineAlpha);
                    RenderUtil.drawAALine(graphics, exactMouseX, exactMouseY, px, py, 0.5, lineColor);
                    connected++;
                }
            }

            Color dotSolid = RenderUtil.withAlpha(0xFFFFFF, PARTICLE_ALPHA);
            Color dotFade = RenderUtil.withAlpha(0xFFFFFF, 0);
            RenderUtil.drawAACircle(graphics, px, py, p.radius, dotSolid, dotFade);
        }
    }

    private static class Particle {
        float x, y, vx, vy, radius, wave;

        public Particle(float x, float y, float vx, float vy, float radius) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.wave = (float) (Math.random() * Math.PI * 2.0);
        }

        public void update(float dt, int w, int h) {
            wave += dt * 2.5f;
            x += (vx + (float) Math.sin(wave) * 6.0f) * dt;
            y += vy * dt;

            if (y > h + 5) { y = -5; x = (float) (Math.random() * w); }
            if (x < -5) x = w + 5;
            if (x > w + 5) x = -5;
        }
    }
}