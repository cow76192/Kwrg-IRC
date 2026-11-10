package me.cookie.kwrg.render.utils;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.util.profiling.Profiler;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public final class LoadingFramePacer {
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLIS = 1_000_000L;
    private static final int MAX_LOADING_FPS = 60;
    private static final int MAX_JOBS_PER_FRAME = 24;
    private static final int RELOAD_TASKS = 3;
    private static final String[] WORK_ZONES = {"kwrgAtlasBatch", "kwrgFontStep", "kwrgShaderPipeline", "kwrgReloadTask"};

    // The shader clock follows the presentation cadence even when frame extraction moves around within a slot.
    private static Overlay animationOverlay;
    private static long animationPeriod;

    private final long[] jobCosts = {MILLIS, 2 * MILLIS, 2 * MILLIS, MILLIS};
    private Overlay overlay;
    private long lastPresentation;
    private long renderCost;
    private long period;
    private int displayRefresh;
    private int refreshCountdown;
    private long workReserve;
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
        period = periodFor(vanillaLimit);
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
                    period = periodFor(vanillaLimit);
                }
            }

            boolean late = interval > period + Math.max(2 * MILLIS, period / 8);
            // Recover headroom for drawing by cutting reload work after a miss.
            workReserve = late ? Math.min(period / 2, workReserve + MILLIS)
                    : Math.max(0, workReserve - MILLIS / 4);
        }
        lastPresentation = now;
        period = periodFor(vanillaLimit);
        animationOverlay = overlay;
        animationPeriod = period;
    }

    public static long animationStep(Overlay current, long elapsedMillis) {
        return current == animationOverlay ? Math.min(SECOND / 15, animationPeriod) : Math.min(50L, elapsedMillis) * MILLIS;
    }

    public void runWork(BooleanSupplier atlas, BooleanSupplier fonts, BooleanSupplier shaders, BooleanSupplier tasks) {
        BooleanSupplier[] lanes = {atlas, fonts, shaders, tasks};
        long deadline = lastPresentation + period - renderCost - 3 * MILLIS - workReserve;
        var profiler = Profiler.get();
        int idleLanes = 0;
        boolean reloadSpent = false;
        for (int jobs = 0; jobs < MAX_JOBS_PER_FRAME && idleLanes < lanes.length; ) {
            int lane = nextLane;
            nextLane = (nextLane + 1) % lanes.length;
            long start = System.nanoTime();
            // Allow one ready job on every frame, then fit more work before the next draw.
            if ((lane == RELOAD_TASKS && reloadSpent) || (jobs > 0 && deadline - start < jobCosts[lane])) {
                idleLanes++;
                continue;
            }

            boolean worked;
            try (var ignored = profiler.zone(WORK_ZONES[lane])) {
                worked = lanes[lane].getAsBoolean();
            }
            if (!worked) {
                idleLanes++;
                continue;
            }

            long finished = System.nanoTime();
            // The client queue mixes long applies and cheap continuations. One can't predict the next.
            if (lane != RELOAD_TASKS) {
                long cost = Math.min(finished - start, 50 * MILLIS);
                jobCosts[lane] = Math.max(Math.max(cost, MILLIS / 4), jobCosts[lane] * 7 / 8);
            } else if (finished - start >= 2 * MILLIS) {
                reloadSpent = true;
            }
            idleLanes = 0;
            jobs++;

            if (finished >= deadline) {
                break;
            }
        }
    }

    private long periodFor(int vanillaLimit) {
        int rate = Math.max(1, Math.min(vanillaLimit, MAX_LOADING_FPS));

        if (vsyncObserved && displayRefresh > 0) {
            return SECOND * Math.ceilDiv(displayRefresh, rate) / displayRefresh;
        }

        return SECOND / rate;
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
        workReserve = 0L;
        shortIntervals = 0;
        vsyncObserved = false;
        vsyncRequested = false;
        nextLane = 0;
        jobCosts[0] = MILLIS;
        jobCosts[1] = 2 * MILLIS;
        jobCosts[2] = 2 * MILLIS;
    }
}
