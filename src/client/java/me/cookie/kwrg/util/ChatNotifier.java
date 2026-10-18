package me.cookie.kwrg.util;

import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatNotifier {

    private static final Queue<Component> PENDING_MESSAGES = new ConcurrentLinkedQueue<>();
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    private record StyledPart(Style style, String text) {}
    private record UrlRange(int start, int end, String cleanUrl, URI uri) {}

    public static void sendInChatScreen(String text) {
        if (text == null || text.isEmpty()) return;
        sendInChatScreen(Component.literal(text));
    }

    public static void sendInChatScreen(Component component) {
        if (component == null) return;
        Component protectedComp = me.cookie.kwrg.irc.manager.NickManager.protectComponent(component);
        Component parsed = parseUrls(protectedComp);
        PENDING_MESSAGES.offer(parsed);
        Minecraft.getInstance().execute(ChatNotifier::flush);
    }

    public static Component parseUrls(Component component) {
        String fullText = component.getString();
        Matcher matcher = URL_PATTERN.matcher(fullText);
        if (!matcher.find()) {
            return component;
        }

        List<UrlRange> ranges = new ArrayList<>();
        matcher.reset();
        while (matcher.find()) {
            String rawUrl = matcher.group();
            String cleanUrl = rawUrl.replaceAll("§[0-9a-fk-or]", "").trim();
            try {
                URI uri = URI.create(cleanUrl);
                ranges.add(new UrlRange(matcher.start(), matcher.end(), cleanUrl, uri));
            } catch (Exception ignored) {
            }
        }

        if (ranges.isEmpty()) {
            return component;
        }

        List<StyledPart> parts = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                parts.add(new StyledPart(style, text));
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent root = Component.empty();
        int globalIndex = 0;

        for (StyledPart part : parts) {
            Style baseStyle = part.style();
            String text = part.text();
            int textLen = text.length();
            int partStart = globalIndex;
            int partEnd = globalIndex + textLen;
            globalIndex += textLen;

            int localOffset = 0;
            while (localOffset < textLen) {
                int currentGlobal = partStart + localOffset;

                UrlRange activeRange = null;
                for (UrlRange range : ranges) {
                    if (currentGlobal >= range.start() && currentGlobal < range.end()) {
                        activeRange = range;
                        break;
                    }
                }

                if (activeRange != null) {
                    int subEndInGlobal = Math.min(partEnd, activeRange.end());
                    int subLength = subEndInGlobal - currentGlobal;
                    String slice = text.substring(localOffset, localOffset + subLength);

                    Style urlStyle = baseStyle
                            .withClickEvent(new ClickEvent.OpenUrl(activeRange.uri()))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击打开网页:\n" + activeRange.cleanUrl())));

                    root.append(Component.literal(slice).setStyle(urlStyle));
                    localOffset += subLength;
                } else {
                    int nextUrlStart = partEnd;
                    for (UrlRange range : ranges) {
                        if (range.start() > currentGlobal && range.start() < nextUrlStart) {
                            nextUrlStart = range.start();
                        }
                    }
                    int subLength = nextUrlStart - currentGlobal;
                    String slice = text.substring(localOffset, localOffset + subLength);

                    root.append(Component.literal(slice).setStyle(baseStyle));
                    localOffset += subLength;
                }
            }
        }

        return root;
    }

    public static void send(String message, NotificationType type) {
        send(message, type, false);
    }

    public static void send(String message, NotificationType type, boolean isAutoDuration) {
        if (message == null || message.isBlank()) return;

        String[] split = splitTitleAndSubtitle(message);
        NotificationManager.send(type, split[0], split[1], isAutoDuration);
    }

    public static void send(String message, NotificationType type, long durationMs) {
        if (message == null || message.isBlank()) return;

        String[] split = splitTitleAndSubtitle(message);
        NotificationManager.send(type, split[0], split[1], null, durationMs);
    }

    public static void send(Component component, NotificationType type) {
        send(component, type, false);
    }

    public static void send(Component component, NotificationType type, boolean isAutoDuration) {
        if (component == null) return;

        String raw = component.getString().trim();
        if (raw.isBlank()) return;

        String[] split = splitTitleAndSubtitle(raw);

        if (split[1].isEmpty()) {
            NotificationManager.send(type, component, Component.empty(), isAutoDuration);
        } else {
            NotificationManager.send(type, Component.literal(split[0]), Component.literal(split[1]), isAutoDuration);
        }
    }

    public static void send(Component component, NotificationType type, long durationMs) {
        if (component == null) return;

        String raw = component.getString().trim();
        if (raw.isBlank()) return;

        String[] split = splitTitleAndSubtitle(raw);

        if (split[1].isEmpty()) {
            NotificationManager.send(type, component, Component.empty(), null, durationMs);
        } else {
            NotificationManager.send(type, Component.literal(split[0]), Component.literal(split[1]), null, durationMs);
        }
    }

    private static String[] splitTitleAndSubtitle(String rawMessage) {
        String clean = rawMessage.replaceAll("§[0-9a-fk-or]", "").trim();
        clean = clean.replaceFirst("^(?:\\[IRC\\]|\\[Kwrg\\])\\s*", "");

        String title = clean;
        String subtitle = "";

        if (clean.contains(" | ")) {
            String[] parts = clean.split(" \\| ", 2);
            title = parts[0].trim();
            subtitle = parts[1].trim();
        } else if (clean.contains(" -> ")) {
            String[] parts = clean.split(" -> ", 2);
            title = parts[0].trim();
            subtitle = parts[1].trim();
        } else if (clean.contains(": ")) {
            String[] parts = clean.split(": ", 2);
            title = parts[0].trim();
            subtitle = parts[1].trim();
        }

        return new String[]{title, subtitle};
    }

    public record AnimatedSegment(String text, String styleConfig, boolean isRaw, Style overrideStyle) {
        public AnimatedSegment(String text, String styleConfig, boolean isRaw) {
            this(text, styleConfig, isRaw, null);
        }
    }

    private static Component buildSegment(AnimatedSegment segment) {
        String safeText = me.cookie.kwrg.irc.manager.NickManager.protectText(segment.text());
        if (!segment.isRaw()) return ColorUtil.applyStyle(safeText, segment.styleConfig());
        MutableComponent part = Component.literal(safeText);
        return segment.overrideStyle() != null ? part.setStyle(segment.overrideStyle()) : part;
    }

    private static final Queue<List<AnimatedSegment>> PENDING_ANIMATIONS = new ConcurrentLinkedQueue<>();
    private static final java.util.Map<net.minecraft.client.multiplayer.chat.GuiMessage, List<AnimatedSegment>> ACTIVE_ANIMATIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void sendAnimatedInChatScreen(List<AnimatedSegment> segments) {
        if (segments == null || segments.isEmpty()) return;

        MutableComponent root = Component.empty();
        boolean hasAnimation = false;

        for (AnimatedSegment seg : segments) {
            root.append(buildSegment(seg));
            if (!seg.isRaw() && ColorUtil.isAnimatedStyle(seg.styleConfig())) {
                hasAnimation = true;
            }
        }

        Component parsed = parseUrls(root);

        if (hasAnimation) {
            PENDING_ANIMATIONS.offer(segments);
        }

        PENDING_MESSAGES.offer(parsed);
        Minecraft.getInstance().execute(ChatNotifier::flush);
    }

    public static void onMessageAdded(net.minecraft.client.multiplayer.chat.GuiMessage message) {
        List<AnimatedSegment> segs = PENDING_ANIMATIONS.poll();
        if (segs != null) {
            ACTIVE_ANIMATIONS.put(message, segs);
            if (ACTIVE_ANIMATIONS.size() > 150) {
                ACTIVE_ANIMATIONS.clear();
            }
        }
    }

    public static boolean hasActiveAnimations() {
        return !ACTIVE_ANIMATIONS.isEmpty();
    }

    public static Component getAnimatedFrame(net.minecraft.client.multiplayer.chat.GuiMessage msg) {
        List<AnimatedSegment> segs = ACTIVE_ANIMATIONS.get(msg);
        if (segs == null) return null;

        MutableComponent root = Component.empty();
        for (AnimatedSegment seg : segs) {
            root.append(buildSegment(seg));
        }
        return parseUrls(root);
    }

    public static void updateMessageBinding(net.minecraft.client.multiplayer.chat.GuiMessage oldMsg, net.minecraft.client.multiplayer.chat.GuiMessage newMsg) {
        List<AnimatedSegment> segs = ACTIVE_ANIMATIONS.remove(oldMsg);
        if (segs != null) {
            ACTIVE_ANIMATIONS.put(newMsg, segs);
        }
    }

    private static class MessageLineCache {
        long lastUpdateTime;
        List<FormattedCharSequence> lines;
    }

    private static final java.util.Map<net.minecraft.client.multiplayer.chat.GuiMessage, MessageLineCache> DYNAMIC_LINE_CACHE = new java.util.WeakHashMap<>();

    public static int getChatMaxWidth(Minecraft mc) {
        if (mc == null || mc.options == null) return 320;
        double chatWidthOpt = mc.options.chatWidth().get();
        double chatScale = mc.options.chatScale().get();
        if (chatScale <= 0.0) chatScale = 1.0;

        int chatWidth = net.minecraft.client.gui.components.ChatComponent.getWidth(chatWidthOpt);
        return net.minecraft.util.Mth.ceil((float) chatWidth / (float) chatScale);
    }

    public static boolean hasAnimation(net.minecraft.client.multiplayer.chat.GuiMessage msg) {
        return msg != null && ACTIVE_ANIMATIONS.containsKey(msg);
    }

    public static FormattedCharSequence createDynamicLine(
            net.minecraft.client.multiplayer.chat.GuiMessage parent,
            int lineIndex,
            FormattedCharSequence fallback
    ) {
        return sink -> {
            FormattedCharSequence currentFrameLine = getLineForFrame(parent, lineIndex);
            if (currentFrameLine != null) {
                return currentFrameLine.accept(sink);
            }
            return fallback.accept(sink);
        };
    }

    private static FormattedCharSequence getLineForFrame(net.minecraft.client.multiplayer.chat.GuiMessage parent, int lineIndex) {
        long now = System.currentTimeMillis();
        MessageLineCache cache = DYNAMIC_LINE_CACHE.get(parent);

        if (cache == null || now - cache.lastUpdateTime >= 25L) {
            Component animatedFrame = getAnimatedFrame(parent);
            if (animatedFrame == null) {
                return null;
            }

            Minecraft mc = Minecraft.getInstance();
            int maxWidth = getChatMaxWidth(mc);

            if (parent.tag() != null && parent.tag().icon() != null) {
                maxWidth -= parent.tag().icon().width + 4 + 2;
            }

            cache = new MessageLineCache();
            cache.lastUpdateTime = now;
            cache.lines = net.minecraft.client.gui.components.ComponentRenderUtils.wrapComponents(animatedFrame, maxWidth, mc.font);
            DYNAMIC_LINE_CACHE.put(parent, cache);
        }

        if (cache.lines != null && lineIndex >= 0 && lineIndex < cache.lines.size()) {
            return cache.lines.get(lineIndex);
        }
        return null;
    }

    public static void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Component msg;
            while ((msg = PENDING_MESSAGES.poll()) != null) {
                mc.gui.getChat().addClientSystemMessage(msg);
            }
        }
    }
}