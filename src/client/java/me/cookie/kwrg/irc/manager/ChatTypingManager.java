package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChatTypingManager {

    private static final long TYPING_REFRESH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Chat-Typing-Watcher");
        t.setDaemon(true);
        return t;
    });

    private static ScheduledFuture<?> stopTask = null;
    private static volatile boolean isTyping = false;
    private static String lastText = "";
    private static long lastTypingSentNanos;

    public static synchronized void onTextChanged(String newText) {
        String currentText = newText != null ? newText : "";

        if (currentText.equals(lastText)) {
            return;
        }
        lastText = currentText;

        if (currentText.trim().isEmpty()) {
            stopTyping();
            return;
        }

        // Refresh only while editing, before the receiver's 25-second fallback expires.
        long now = System.nanoTime();
        if (!isTyping || now - lastTypingSentNanos >= TYPING_REFRESH_INTERVAL_NANOS) {
            isTyping = true;
            lastTypingSentNanos = now;
            IrcClient.sendTypingStatus(true);
        }

        if (stopTask != null && !stopTask.isDone()) {
            stopTask.cancel(false);
        }

        stopTask = EXECUTOR.schedule(() -> {
            synchronized (ChatTypingManager.class) {
                if (isTyping) {
                    stopTyping();
                }
            }
        }, 3, TimeUnit.SECONDS);
    }

    public static synchronized void onChatClosed() {
        stopTyping();
        lastText = "";
    }

    private static void stopTyping() {
        if (stopTask != null && !stopTask.isDone()) {
            stopTask.cancel(false);
        }
        stopTask = null;

        if (isTyping) {
            isTyping = false;
            IrcClient.sendTypingStatus(false);
        }
    }
}
