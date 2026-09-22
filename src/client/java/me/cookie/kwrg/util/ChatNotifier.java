package me.cookie.kwrg.util;

import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatNotifier {

    private static final int FALLBACK_CHAT_WIDTH = 320;
    private static final long ANIM_FRAME_INTERVAL_MS = 25L;
    private static final int MAX_ACTIVE_ANIMATIONS = 150;
    private static final int ICON_EXTRA_PADDING = 6;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(?:\\[IRC\\]|\\[Kwrg\\])\\s*");
    private static final String TRAILING_URL_PUNCTUATION = ".,;:!?)]}'\"、。，；：！？】》〉」』";

    private static final Queue<Component> PENDING_MESSAGES = new ConcurrentLinkedQueue<>();

    private static final Map<Component, List<AnimatedSegment>> PENDING_ANIMATIONS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<GuiMessage, List<AnimatedSegment>> ACTIVE_ANIMATIONS = new IdentityHashMap<>();
    private static final ArrayDeque<GuiMessage> ANIMATION_ORDER = new ArrayDeque<>();
    private static final Map<GuiMessage, MessageLineCache> DYNAMIC_LINE_CACHE = new WeakHashMap<>();

    private record UrlRange(int start, int end, String cleanUrl, URI uri) {}
    private record TitleSubtitle(String title, String subtitle, int separatorIndex, int separatorLength) {}

    public record AnimatedSegment(String text, String styleConfig, boolean isRaw) {}

    private static final class MessageLineCache {
        long lastUpdateTime;
        List<FormattedCharSequence> lines;
    }

    public static void sendInChatScreen(String text) {
        if (text == null || text.isEmpty()) return;
        sendInChatScreen(Component.literal(text));
    }

    public static void sendInChatScreen(Component component) {
        if (component == null) return;
        Component parsed = parseUrls(component);
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
            int start = matcher.start();
            int end = matcher.end();

            int trim = 0;
            while (trim < rawUrl.length()
                    && TRAILING_URL_PUNCTUATION.indexOf(rawUrl.charAt(rawUrl.length() - 1 - trim)) >= 0) {
                trim++;
            }
            if (trim > 0) {
                rawUrl = rawUrl.substring(0, rawUrl.length() - trim);
                end -= trim;
            }
            if (rawUrl.isEmpty()) continue;

            String cleanUrl = NotificationManager.stripColorCodes(rawUrl).trim();
            if (cleanUrl.isEmpty()) continue;
            try {
                ranges.add(new UrlRange(start, end, cleanUrl, URI.create(cleanUrl)));
            } catch (Exception ignored) {
            }
        }

        if (ranges.isEmpty()) {
            return component;
        }

        TreeSet<Integer> cuts = new TreeSet<>();
        cuts.add(0);
        cuts.add(fullText.length());
        for (UrlRange r : ranges) {
            cuts.add(r.start());
            cuts.add(r.end());
        }

        MutableComponent root = Component.empty();
        int prev = -1;
        for (int cut : cuts) {
            if (prev >= 0 && cut > prev) {
                final int a = prev, b = cut;
                UrlRange active = null;
                for (UrlRange r : ranges) {
                    if (r.start() <= a && b <= r.end()) {
                        active = r;
                        break;
                    }
                }
                if (active != null) {
                    final UrlRange fr = active;
                    root.append(sliceComponent(component, a, b, style -> style
                            .withClickEvent(new ClickEvent.OpenUrl(fr.uri()))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击打开网页:\n" + fr.cleanUrl())))));
                } else {
                    root.append(sliceComponent(component, a, b, UnaryOperator.identity()));
                }
            }
            prev = cut;
        }
        return root;
    }

    private static Component sliceComponent(Component component, int start, int end, UnaryOperator<Style> decorator) {
        if (start >= end) return Component.empty();
        MutableComponent out = Component.empty();
        int[] cursor = new int[1];
        component.visit((style, text) -> {
            int pieceStart = cursor[0];
            int pieceEnd = pieceStart + text.length();
            cursor[0] = pieceEnd;
            int a = Math.max(start, pieceStart);
            int b = Math.min(end, pieceEnd);
            if (a < b) {
                out.append(Component.literal(text.substring(a - pieceStart, b - pieceStart))
                        .setStyle(decorator.apply(style)));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static NotificationType resolveTypeFromCodes(String text) {
        return NotificationManager.resolveTypeFromCodes(text);
    }

    private static NotificationType resolveType(Component component) {
        NotificationType fromCodes = resolveTypeFromCodes(component.getString());
        if (fromCodes != NotificationType.INFO) return fromCodes;

        final NotificationType[] result = {NotificationType.INFO};
        final int[] depth = {0};
        component.visit((style, text) -> {
            if (result[0] == NotificationType.INFO && depth[0] == 0) {
                TextColor color = style.getColor();
                if (color != null) {
                    ChatFormatting fmt = legacyFromRgb(color.getValue());
                    if (fmt == ChatFormatting.RED) result[0] = NotificationType.ERR;
                    else if (fmt == ChatFormatting.YELLOW || fmt == ChatFormatting.GOLD) result[0] = NotificationType.WARN;
                    else if (fmt == ChatFormatting.GREEN) result[0] = NotificationType.SUCCESS;
                }
            }
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '[') depth[0]++;
                else if (ch == ']' && depth[0] > 0) depth[0]--;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result[0];
    }

    private static ChatFormatting legacyFromRgb(int rgb) {
        for (ChatFormatting fmt : ChatFormatting.values()) {
            Integer c = fmt.getColor();
            if (c != null && c == rgb) return fmt;
        }
        return null;
    }

    private static String stripColorCodes(String text) {
        return NotificationManager.stripColorCodes(text);
    }
    private static TitleSubtitle splitTitleSubtitle(String text) {
        final String[] separators = {" | ", " -> ", ": "};
        for (String sep : separators) {
            int idx = text.indexOf(sep);
            if (idx >= 0) {
                return new TitleSubtitle(
                        text.substring(0, idx).trim(),
                        text.substring(idx + sep.length()).trim(),
                        idx, sep.length());
            }
        }
        return new TitleSubtitle(text, "", -1, 0);
    }

    public static void send(String message) {
        if (message == null || message.isBlank()) return;

        String raw = message.trim();
        String clean = stripColorCodes(raw).trim();
        clean = PREFIX_PATTERN.matcher(clean).replaceFirst("");

        TitleSubtitle ts = splitTitleSubtitle(clean);

        NotificationType type = resolveTypeFromCodes(raw);
        NotificationManager.send(type, ts.title(), ts.subtitle());
    }

    public static void send(Component component) {
        if (component == null) return;

        String full = component.getString();
        if (full.isBlank()) return;

        NotificationType type = resolveType(component);

        int prefixLen = 0;
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(full);
        if (prefixMatcher.lookingAt()) {
            prefixLen = prefixMatcher.end();
        }

        TitleSubtitle ts = splitTitleSubtitle(full.substring(prefixLen));

        if (ts.separatorIndex() < 0) {
            NotificationManager.send(type, component, Component.empty());
            return;
        }

        int titleStart = prefixLen;
        int titleEnd = prefixLen + ts.separatorIndex();
        int subtitleStart = titleEnd + ts.separatorLength();
        int subtitleEnd = full.length();

        while (titleEnd > titleStart && Character.isWhitespace(full.charAt(titleEnd - 1))) titleEnd--;
        while (subtitleStart < subtitleEnd && Character.isWhitespace(full.charAt(subtitleStart))) subtitleStart++;

        NotificationManager.send(type,
                sliceComponent(component, titleStart, titleEnd, UnaryOperator.identity()),
                sliceComponent(component, subtitleStart, subtitleEnd, UnaryOperator.identity()));
    }

    public static void sendAnimatedInChatScreen(List<AnimatedSegment> segments) {
        if (segments == null || segments.isEmpty()) return;

        MutableComponent root = Component.empty();
        boolean hasAnimation = false;

        for (AnimatedSegment seg : segments) {
            if (seg.isRaw()) {
                root.append(Component.literal(seg.text()));
            } else {
                root.append(ColorUtil.applyStyle(seg.text(), seg.styleConfig()));
                if (ColorUtil.isAnimatedStyle(seg.styleConfig())) {
                    hasAnimation = true;
                }
            }
        }

        Component parsed = parseUrls(root);

        if (hasAnimation) {
            PENDING_ANIMATIONS.put(parsed, segments);
        }

        PENDING_MESSAGES.offer(parsed);
        Minecraft.getInstance().execute(ChatNotifier::flush);
    }

    public static void onMessageAdded(GuiMessage message) {
        List<AnimatedSegment> segs = PENDING_ANIMATIONS.remove(message.content());
        if (segs != null) {
            ACTIVE_ANIMATIONS.put(message, segs);
            ANIMATION_ORDER.addLast(message);
            while (ANIMATION_ORDER.size() > MAX_ACTIVE_ANIMATIONS) {
                GuiMessage oldest = ANIMATION_ORDER.pollFirst();
                if (oldest != null) {
                    ACTIVE_ANIMATIONS.remove(oldest);
                    DYNAMIC_LINE_CACHE.remove(oldest);
                }
            }
        }
    }

    public static boolean hasActiveAnimations() {
        return !ACTIVE_ANIMATIONS.isEmpty();
    }

    public static Component getAnimatedFrame(GuiMessage msg) {
        List<AnimatedSegment> segs = ACTIVE_ANIMATIONS.get(msg);
        if (segs == null) return null;

        MutableComponent root = Component.empty();
        for (AnimatedSegment seg : segs) {
            if (seg.isRaw()) {
                root.append(Component.literal(seg.text()));
            } else {
                root.append(ColorUtil.applyStyle(seg.text(), seg.styleConfig()));
            }
        }
        return parseUrls(root);
    }

    public static void updateMessageBinding(GuiMessage oldMsg, GuiMessage newMsg) {
        List<AnimatedSegment> segs = ACTIVE_ANIMATIONS.remove(oldMsg);
        if (segs != null) {
            ACTIVE_ANIMATIONS.put(newMsg, segs);
            ANIMATION_ORDER.remove(oldMsg);
            ANIMATION_ORDER.addLast(newMsg);
        }
    }

    public static boolean hasAnimation(GuiMessage msg) {
        return msg != null && ACTIVE_ANIMATIONS.containsKey(msg);
    }

    public static int getChatMaxWidth(Minecraft mc) {
        if (mc == null || mc.options == null) return FALLBACK_CHAT_WIDTH;
        double chatWidthOpt = mc.options.chatWidth().get();
        double chatScale = mc.options.chatScale().get();
        if (chatScale <= 0.0) chatScale = 1.0;

        int chatWidth = ChatComponent.getWidth(chatWidthOpt);
        return Mth.ceil((float) chatWidth / (float) chatScale);
    }

    public static FormattedCharSequence createDynamicLine(
            GuiMessage parent,
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

    private static FormattedCharSequence getLineForFrame(GuiMessage parent, int lineIndex) {
        long now = System.currentTimeMillis();
        MessageLineCache cache = DYNAMIC_LINE_CACHE.get(parent);

        if (cache == null || now - cache.lastUpdateTime >= ANIM_FRAME_INTERVAL_MS) {
            Component animatedFrame = getAnimatedFrame(parent);
            if (animatedFrame == null) {
                return null;
            }

            Minecraft mc = Minecraft.getInstance();
            int maxWidth = getChatMaxWidth(mc);

            if (parent.tag() != null && parent.tag().icon() != null) {
                maxWidth -= parent.tag().icon().width + ICON_EXTRA_PADDING;
            }

            cache = new MessageLineCache();
            cache.lastUpdateTime = now;
            cache.lines = ComponentRenderUtils.wrapComponents(animatedFrame, maxWidth, mc.font);
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