package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class IrcLink {

    static final Gson GSON = new Gson();
    static final String HOST = "47.96.141.237";
    static final int PORT = 13140;

    static volatile boolean isRunning = false;
    static volatile long lastPingTimestamp = 0L;
    static volatile long lastLatency = -1L;
    static final List<Consumer<Long>> pingCallbacks = new CopyOnWriteArrayList<>();

    static final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    static volatile boolean shouldAutoReconnect = false;
    static final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private IrcLink() {}
}
