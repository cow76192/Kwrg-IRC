package me.cookie.kwrg.render;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.screens.Overlay;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public final class LoadingFramePacer {
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLIS = 1_000_000L;
    private static final int[] RATE_TIERS = {60, 40, 30, 20, 15};
    private static final int MAX_JOBS_PER_FRAME = 24;
    private static final int RECENT_FRAMES_MASK = 0xffff;

    // The shader clock follows the presentation cadence even when frame extraction moves around within a slot.
    private static Overlay animationOverlay;
    private static long animationPeriod;

    private final long[] jobCosts = {MILLIS / 2, MILLIS, 2 * MILLIS};
    private Overlay overlay;
    private long lastPresentation;
    private long renderCost;
    private long period;
    private int displayRefresh;
    private int refreshCountdown;
    private int tier;
    private int recentMisses;
    private int stableFrames;
    private int shortIntervals;
    private int nextLane;
    private boolean vsyncRequested;
    private boolean vsyncObserved;

    public void beforePresent(Overlay current, Window window, boolean vsync, int vanillaLimit, long renderNanos) {
        if (current != overlay) {
            reset();
            overlay = current;
        }

        if (vsync != vsyncRequested || refreshCountdown-- <= 0) {
            int refresh = window.getRefreshRate();
            if (vsync != vsyncRequested || refresh != displayRefresh) {
                lastPresentation = 0L;
                shortIntervals = 0;
                vsyncObserved = vsync;
            }

            vsyncRequested = vsync;
            displayRefresh = refresh;
            refreshCountdown = 120;
        }

        renderCost = Math.max(Math.min(renderNanos, 50 * MILLIS), renderCost * 9 / 10);
        period = periodFor(tier, vanillaLimit);
        if (lastPresentation == 0L || window.isMinimized()) {
            return;
        }

        long wake = lastPresentation + period;
        if (vsyncObserved && displayRefresh > 0) {
            wake -= SECOND / displayRefresh - MILLIS;
        }

        long remaining;
        while ((remaining = wake - System.nanoTime()) > 0L && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(remaining);
        }
    }

    public void presented(long now, int vanillaLimit) {
        if (lastPresentation != 0L) {
            long interval = now - lastPresentation;
            if (vsyncObserved && displayRefresh > 0) {
                shortIntervals = interval + 3 * MILLIS < period ? shortIntervals + 2 : Math.max(0, shortIntervals - 1);
                if (shortIntervals >= 6) {
                    vsyncObserved = false;
                    period = periodFor(tier, vanillaLimit);
                }
            }

            boolean late = interval > period + Math.max(2 * MILLIS, period / 8);
            recentMisses = ((recentMisses << 1) | (late ? 1 : 0)) & RECENT_FRAMES_MASK;
            stableFrames = late ? 0 : Math.min(72, stableFrames + 1);
            if (Integer.bitCount(recentMisses) >= 3) {
                int slower = tier + 1;
                while (slower < RATE_TIERS.length && periodFor(slower, vanillaLimit) == period) {
                    slower++;
                }

                if (slower < RATE_TIERS.length) {
                    changeTier(slower);
                }
            } else if (stableFrames >= 72 && tier > 0) {
                int faster = tier - 1;
                while (faster > 0 && periodFor(faster, vanillaLimit) == period) {
                    faster--;
                }

                if (renderCost + highestJobCost() + 3 * MILLIS < periodFor(faster, vanillaLimit)) {
                    changeTier(faster);
                }
            }
        }
        lastPresentation = now;
        period = periodFor(tier, vanillaLimit);
        animationOverlay = overlay;
        animationPeriod = period;
    }

    private void changeTier(int next) {
        tier = next;
        recentMisses = 0;
        stableFrames = 0;
    }

    public static long animationStep(Overlay current, long elapsedMillis) {
        return current == animationOverlay ? Math.min(SECOND / 15, animationPeriod) : Math.min(50L, elapsedMillis) * MILLIS;
    }

    public void runWork(BooleanSupplier tasks, BooleanSupplier atlas, BooleanSupplier fonts) {
        BooleanSupplier[] lanes = {tasks, atlas, fonts};
        long reserve = Math.max(5 * MILLIS, renderCost + 2 * MILLIS);
        long workBudget = Math.clamp(period - reserve, MILLIS, Math.max(MILLIS, period / 2));
        long deadline = System.nanoTime() + workBudget;
        int idleLanes = 0;
        for (int jobs = 0; jobs < MAX_JOBS_PER_FRAME && idleLanes < lanes.length; ) {
            int lane = nextLane;
            nextLane = (nextLane + 1) % lanes.length;
            long start = System.nanoTime();
            // Admit one runnable even on an over-budget frame so reloads can always make progress.
            if (jobs > 0 && deadline - start < jobCosts[lane]) {
                idleLanes++;
                continue;
            }

            if (!lanes[lane].getAsBoolean()) {
                jobCosts[lane] = Math.max(MILLIS / 4, jobCosts[lane] * 15 / 16);
                idleLanes++;
                continue;
            }

            long cost = Math.min(System.nanoTime() - start, 50 * MILLIS);
            jobCosts[lane] = Math.max(Math.max(cost, MILLIS / 4), jobCosts[lane] * 7 / 8);
            idleLanes = 0;
            jobs++;

            if (System.nanoTime() >= deadline) {
                break;
            }
        }
    }

    private long periodFor(int rateTier, int vanillaLimit) {
        int rate = Math.max(1, Math.min(vanillaLimit, RATE_TIERS[rateTier]));

        if (vsyncObserved && displayRefresh > 0) {
            return SECOND * Math.ceilDiv(displayRefresh, rate) / displayRefresh;
        }

        return SECOND / rate;
    }

    private long highestJobCost() {
        return Math.max(jobCosts[0], Math.max(jobCosts[1], jobCosts[2]));
    }

    public void reset() {
        if (overlay == null) {
            return;
        }

        if (animationOverlay == overlay) {
            animationOverlay = null;
        }

        overlay = null;
        lastPresentation = 0L;
        renderCost = 0L;
        refreshCountdown = 0;
        tier = 0;
        recentMisses = 0;
        stableFrames = 0;
        shortIntervals = 0;
        vsyncObserved = false;
        vsyncRequested = false;
        nextLane = 0;
        jobCosts[0] = MILLIS / 2;
        jobCosts[1] = MILLIS;
        jobCosts[2] = 2 * MILLIS;
    }
}
