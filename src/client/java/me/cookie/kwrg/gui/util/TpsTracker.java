package me.cookie.kwrg.gui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class TpsTracker {

    private static float currentTps = 20.0f;
    private static long lastGameTime = -1L;
    private static long lastCheckTimestamp = System.currentTimeMillis();

    private static final float[] SAMPLES = new float[6];
    private static int sampleIndex = 0;
    private static int sampleCount = 0;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            currentTps = 20.0f;
            lastGameTime = -1L;
            return;
        }

        long now = System.currentTimeMillis();
        long gameTime = mc.level.getGameTime();

        if (lastGameTime == -1L) {
            lastGameTime = gameTime;
            lastCheckTimestamp = now;
            return;
        }

        long timeDiff = now - lastCheckTimestamp;

        if (timeDiff >= 1000L) {
            long ticksDiff = gameTime - lastGameTime;

            if (ticksDiff >= 0) {
                float instantTps = (ticksDiff * 1000.0f) / (float) timeDiff;
                instantTps = Mth.clamp(instantTps, 0.0f, 20.0f);

                SAMPLES[sampleIndex] = instantTps;
                sampleIndex = (sampleIndex + 1) % SAMPLES.length;
                if (sampleCount < SAMPLES.length) {
                    sampleCount++;
                }

                float sum = 0.0f;
                for (int i = 0; i < sampleCount; i++) {
                    sum += SAMPLES[i];
                }
                currentTps = sum / sampleCount;
            }

            lastGameTime = gameTime;
            lastCheckTimestamp = now;
        } else if (timeDiff > 3500L) {
            currentTps = 0.0f;
        }
    }

    public static float getTps() {
        return currentTps;
    }
}