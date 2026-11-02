package me.cookie.kwrg.irc.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class SaveScheduler {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kwrg-config-saver");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingTask = null;

    private SaveScheduler() {}

    static void schedule(Runnable task, long delayMs) {
        cancelPending();
        pendingTask = EXECUTOR.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    static void cancelPending() {
        if (pendingTask != null && !pendingTask.isDone()) {
            pendingTask.cancel(false);
        }
    }
}
