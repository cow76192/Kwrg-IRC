package me.cookie.kwrg.util;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

public final class ChatAnimationQueue {

    public record Pending(Component message, List<ChatNotifier.AnimatedSegment> segments) {}

    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static final Map<GuiMessage, List<ChatNotifier.AnimatedSegment>> ACTIVE = new ConcurrentHashMap<>();
    private static volatile List<ChatNotifier.AnimatedSegment> armed;
    private static volatile boolean armedConsumed;

    private ChatAnimationQueue() {}

    public static void enqueue(Component message, List<ChatNotifier.AnimatedSegment> segments) {
        PENDING.offer(new Pending(message, segments));
    }

    public static Pending poll() {
        return PENDING.poll();
    }

    public static void arm(List<ChatNotifier.AnimatedSegment> segments) {
        armed = segments;
        armedConsumed = false;
    }

    public static void disarm() {
        armed = null;
        armedConsumed = false;
    }

    public static void bind(GuiMessage message) {
        if (message == null) return;
        List<ChatNotifier.AnimatedSegment> segments = armed;
        if (segments == null || armedConsumed) return;
        armedConsumed = true;
        ACTIVE.put(message, segments);
        if (ACTIVE.size() > 150) {
            ACTIVE.clear();
        }
    }

    public static boolean has(GuiMessage message) {
        return message != null && ACTIVE.containsKey(message);
    }

    public static List<ChatNotifier.AnimatedSegment> get(GuiMessage message) {
        return ACTIVE.get(message);
    }

    public static void rebind(GuiMessage oldMessage, GuiMessage newMessage) {
        List<ChatNotifier.AnimatedSegment> segments = ACTIVE.remove(oldMessage);
        if (segments != null) {
            ACTIVE.put(newMessage, segments);
        }
    }
}
