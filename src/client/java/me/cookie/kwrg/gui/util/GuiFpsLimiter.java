package me.cookie.kwrg.gui.util;

import me.cookie.kwrg.irc.IrcConfig;
import java.util.concurrent.locks.LockSupport;

public class GuiFpsLimiter {

    private static long lastFrameTimeNanos = 0;
    private static long lastThrottledNanoTime = 0;

    public static void apply() {
        int maxFps = IrcConfig.get().guiFpsLimit;
        if (maxFps <= 0) return;

        long now = System.nanoTime();
        if (now - lastThrottledNanoTime < 1_000_000L) {
            return;
        }

        long frameIntervalNanos = 1_000_000_000L / maxFps;

        if (lastFrameTimeNanos != 0) {
            long elapsed = now - lastFrameTimeNanos;
            long timeToWait = frameIntervalNanos - elapsed;
            if (timeToWait > 0) {
                LockSupport.parkNanos(timeToWait);
            }
        }
        long finishTime = System.nanoTime();
        lastFrameTimeNanos = finishTime;
        lastThrottledNanoTime = finishTime;
    }
}