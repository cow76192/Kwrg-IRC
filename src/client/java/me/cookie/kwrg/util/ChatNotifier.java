package me.cookie.kwrg.util;

import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatNotifier {

    private static final Queue<Component> PENDING_MESSAGES = new ConcurrentLinkedQueue<>();
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    public record AnimationMeta(String rawText, String styleConfig) {}

    private static final Map<GuiMessage, AnimationMeta> ACTIVE_ANIMATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Component, AnimationMeta> PENDING_ANIM_META = new ConcurrentHashMap<>();

    private record StyledPart(Style style, String text) {}
    private record UrlRange(int start, int end, String cleanUrl, URI uri) {}


    public static void sendInChatScreen(String text, String styleConfig) {
        if (text == null || text.isEmpty()) return;

        if (styleConfig != null && (styleConfig.contains("rainbow") || styleConfig.contains("flow")
                || styleConfig.contains("wave") || styleConfig.contains("animate") || styleConfig.contains("scroll"))) {
            Component initial = parseUrls(ColorUtil.applyStyle(text, styleConfig));
            PENDING_ANIM_META.put(initial, new AnimationMeta(text, styleConfig));
            sendInChatScreen(initial);
        } else {
            sendInChatScreen(parseUrls(ColorUtil.applyStyle(text, styleConfig)));
        }
    }

    public static void sendInChatScreen(String text) {
        if (text == null || text.isEmpty()) return;
        sendInChatScreen(Component.literal(text));
    }

    public static void sendInChatScreen(Component component) {
        if (component == null) return;
        Component parsed = parseUrls(component);

        AnimationMeta meta = PENDING_ANIM_META.remove(component);
        if (meta != null) {
            PENDING_ANIM_META.put(parsed, meta);
        }

        PENDING_MESSAGES.offer(parsed);
        Minecraft.getInstance().execute(ChatNotifier::flush);
    }

    public static void onMessageAdded(GuiMessage message) {
        AnimationMeta meta = PENDING_ANIM_META.remove(message.content());
        if (meta != null) {
            ACTIVE_ANIMATIONS.put(message, meta);
        }
    }

    public static void updateMessageBinding(GuiMessage oldMsg, GuiMessage newMsg) {
        AnimationMeta meta = ACTIVE_ANIMATIONS.remove(oldMsg);
        if (meta != null) {
            ACTIVE_ANIMATIONS.put(newMsg, meta);
        }
    }

    public static boolean hasActiveAnimations() {
        return !ACTIVE_ANIMATIONS.isEmpty();
    }

    public static Component getAnimatedFrame(GuiMessage msg) {
        AnimationMeta meta = ACTIVE_ANIMATIONS.get(msg);
        if (meta == null) return null;

        // 每帧重新计算流动颜色，并无损挂载 URL 事件
        return parseUrls(ColorUtil.applyStyle(meta.rawText(), meta.styleConfig()));
    }

    /**
     * 深度保留所有流光 Style 并挂载 URL 点击/悬停事件
     */
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

    public static void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Component msg;
            while ((msg = PENDING_MESSAGES.poll()) != null) {
                mc.gui.getChat().addClientSystemMessage(msg);
            }
        }
    }

    public static void send(String message) {
        if (message == null || message.isBlank()) return;

        String raw = message.trim();
        String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();

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

        if (raw.contains("§c") || raw.contains("失败") || raw.contains("错误") || raw.contains("不可") || raw.contains("不存在")) {
            NotificationManager.send(NotificationType.ERR, title, subtitle);
        } else if (raw.contains("§e") || raw.contains("§6") || raw.contains("警告") || raw.contains("移除") || raw.contains("清空")) {
            NotificationManager.send(NotificationType.WARN, title, subtitle);
        } else if (raw.contains("§a") || raw.contains("成功") || raw.contains("已新建") || raw.contains("已保存") || raw.contains("已复制") || raw.contains("已更新")) {
            NotificationManager.send(NotificationType.SUCCESS, title, subtitle);
        } else {
            NotificationManager.send(NotificationType.INFO, title, subtitle);
        }
    }

    public static void send(Component component) {
        if (component == null) return;

        String raw = component.getString().trim();
        if (raw.isBlank()) return;

        String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();
        clean = clean.replaceFirst("^(?:\\[IRC\\]|\\[Kwrg\\])\\s*", "");

        String titleStr = clean;
        String subtitleStr = "";

        if (clean.contains(" | ")) {
            String[] parts = clean.split(" \\| ", 2);
            titleStr = parts[0].trim();
            subtitleStr = parts[1].trim();
        } else if (clean.contains(" -> ")) {
            String[] parts = clean.split(" -> ", 2);
            titleStr = parts[0].trim();
            subtitleStr = parts[1].trim();
        } else if (clean.contains(": ")) {
            String[] parts = clean.split(": ", 2);
            titleStr = parts[0].trim();
            subtitleStr = parts[1].trim();
        }

        NotificationType type = NotificationType.INFO;
        if (raw.contains("§c") || raw.contains("失败") || raw.contains("错误") || raw.contains("不可") || raw.contains("不存在")) {
            type = NotificationType.ERR;
        } else if (raw.contains("§e") || raw.contains("§6") || raw.contains("警告") || raw.contains("移除") || raw.contains("清空")) {
            type = NotificationType.WARN;
        } else if (raw.contains("§a") || raw.contains("成功") || raw.contains("已新建") || raw.contains("已保存") || raw.contains("已复制") || raw.contains("已更新")) {
            type = NotificationType.SUCCESS;
        }

        if (subtitleStr.isEmpty()) {
            NotificationManager.send(type, component, Component.empty());
        } else {
            NotificationManager.send(type, Component.literal(titleStr), Component.literal(subtitleStr));
        }
    }
}