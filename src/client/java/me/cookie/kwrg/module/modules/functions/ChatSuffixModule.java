package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.Consumer;

public class ChatSuffixModule extends Module {

    public record IrcDecoration(String content, String prefix, String suffix) {
        public static IrcDecoration plain(String content) {
            return new IrcDecoration(content, "", "");
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc-chatsuffix");

    private static boolean failureReported = false;

    private static final Minecraft mc = Minecraft.getInstance();
    private static final String SAMPLE_MESSAGE = mc.player != null ? mc.player.getName().getString() : "Player";
    private static final String ENGLISH_SUFFIX = "&o&d meow~";
    private static final String STYLE_RESET = "§r";

    private static final int MAX_MESSAGE_LENGTH = 256;

    private static final String[] PRESETS = new String[]{
            "&o&d喵~",
            ENGLISH_SUFFIX,
            "&dCiallo～(∠・ω< )⌒★",
            "&o&l&b喵呜~",
            "&6nya~",
            "&d✧˖°",
            "&a(´・ω・`)",
            "&c♥",
            "&b❀",
            "&5♡"
    };

    public ChatSuffixModule() {
        super("chatSuffix", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().chatSuffixEnabled);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.chatSuffixEnabled = !config.chatSuffixEnabled;
        IrcConfig.save();

        if (config.chatSuffixEnabled) {
            ChatNotifier.send("§a自动语气词已: §a开启 §7(前缀: §f" + getConfiguredPrefix()
                    + "§7 后缀: §f" + getConfiguredSuffix() + "§7)", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e自动语气词已: §c关闭 §7", NotificationType.ERR);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String[] editingPrefix = { getConfiguredPrefix() };
        String[] editingSuffix = { getConfiguredSuffix() };

        panel.child(new PanelItemComponent(
                "公屏前后缀同步",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.chatSuffixPublicChat = !config.chatSuffixPublicChat;
                    IrcConfig.save();
                    btn.setToggled(config.chatSuffixPublicChat);
                    ChatNotifier.send(config.chatSuffixPublicChat
                            ? "§a 公屏前后缀同步 §7(公屏无颜色)"
                            : "§e 公屏前后缀同步", NotificationType.INFO);
                },
                null,
                () -> IrcConfig.get().chatSuffixPublicChat
        ));

        panel.child(createValuePane("前缀值", "前缀", getConfiguredPrefix(),
                value -> {
                    IrcConfig.get().chatSuffixPrefix = value;
                    IrcConfig.save();
                },
                value -> editingPrefix[0] = value));

        panel.child(new TextInfoPaneComponent(
                "前缀效果 (点击复制)",
                () -> buildPreview(editingPrefix[0], ""),
                () -> copyValue("前缀", editingPrefix[0])
        ));

        TextInputPaneComponent suffixPane = createValuePane("后缀值", "后缀", getConfiguredSuffix(),
                value -> {
                    IrcConfig.get().chatSuffix = value;
                    IrcConfig.save();
                },
                value -> editingSuffix[0] = value);
        panel.child(suffixPane);

        panel.child(new TextInfoPaneComponent(
                "后缀效果 (点击复制)",
                () -> buildPreview("", editingSuffix[0]),
                () -> copyValue("后缀", editingSuffix[0])
        ));

        panel.child(new PanelItemComponent(
                "跟随 messageStyle",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.chatSuffixFollowMessageStyle = !config.chatSuffixFollowMessageStyle;
                    IrcConfig.save();
                    btn.setToggled(config.chatSuffixFollowMessageStyle);
                    ChatNotifier.send(config.chatSuffixFollowMessageStyle
                            ? "§a 语气词已跟随 messageStyle"
                            : "§e 语气词已使用自身颜色", NotificationType.INFO);
                },
                null,
                () -> IrcConfig.get().chatSuffixFollowMessageStyle
        ));

        panel.child(TextInfoPaneComponent.header("预设后缀"));

        for (String preset : PRESETS) {
            PanelItemComponent item = new PanelItemComponent(
                    preset,
                    PanelItemComponent.ItemType.TRIGGER,
                    (clicked, click) -> {
                        suffixPane.setText(preset);
                        editingSuffix[0] = preset;
                        IrcConfig.get().chatSuffix = preset;
                        IrcConfig.save();
                        ChatNotifier.send("§a 已应用后缀预设: §f" + preset, NotificationType.SUCCESS);
                    },
                    clicked -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(preset);
                        ChatNotifier.send("§a 已复制预设: §f" + preset, NotificationType.SUCCESS);
                    }
            );
            item.withStyledTitle(() -> Component.literal(convertCodes(preset, true)));
            panel.child(item);
        }
    }

    private static TextInputPaneComponent createValuePane(String rightHint, String label, String initialValue,
                                                          Consumer<String> saver, Consumer<String> editingConsumer) {
        TextInputPaneComponent pane = new TextInputPaneComponent( "Enter保存|Shift点击清空|右键复制", submitted -> {
            saver.accept(submitted);
            ChatNotifier.send("§a 已保存 " + label + ": §f" + submitted, NotificationType.SUCCESS);
        }, rightHint) {
            @Override
            public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
                boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
                if (click.button() == 0 && shift) {
                    setText("");
                    saver.accept("");
                    ChatNotifier.send("§e 已清空 " + label + " §7", NotificationType.INFO);
                    return true;
                }
                return super.onMouseDown(click, doubled);
            }
        };

        if (!initialValue.isEmpty()) {
            pane.setText(initialValue);
        }
        pane.onTextChanged(editingConsumer);
        pane.onRightClick(() -> copyValue(label, pane.getText()));
        return pane;
    }

    private static String getConfiguredPrefix() {
        String value = IrcConfig.get().chatSuffixPrefix;
        return value != null ? value : "";
    }

    private static String getConfiguredSuffix() {
        String value = IrcConfig.get().chatSuffix;
        return value != null ? value : "";
    }

    private static Component buildPreview(String prefixValue, String suffixValue) {
        IrcConfig config = IrcConfig.get();
        String prefix = prefixValue == null ? "" : prefixValue;
        String suffix = suffixValue == null ? "" : suffixValue;
        String messageStyle = config.chatstyle != null ? config.chatstyle : "";
        String suffixText = convertCodes(suffix, false);
        String gap = needsSpace(SAMPLE_MESSAGE, suffixText) ? " " : "";

        if (config.chatSuffixFollowMessageStyle) {
            String line = convertCodes(prefix, false) + SAMPLE_MESSAGE + gap + suffixText;
            return ColorUtil.applyStyle(line, messageStyle);
        }

        return Component.empty()
                .append(legacyComponent(prefix))
                .append(ColorUtil.applyStyle(SAMPLE_MESSAGE, messageStyle))
                .append(legacyComponent(gap + suffix));
    }

    private static Component legacyComponent(String value) {
        if (value == null || value.isEmpty()) return Component.empty();
        return Component.literal(styledText(value));
    }

    private static String styledText(String value) {
        String sent = convertCodes(value, true);
        return sent.indexOf('§') < 0 ? sent : sent + STYLE_RESET;
    }

    private static void copyValue(String label, String value) {
        String text = value != null ? value : "";
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        ChatNotifier.send("§a 已复制 " + label + ": §f" + text, NotificationType.SUCCESS);
    }

    public static IrcDecoration decorateIrcSafely(String message) {
        return safe(() -> decorateIrc(message), IrcDecoration.plain(message));
    }

    public static String applyPublicSuffixSafely(String message) {
        return safe(() -> applyPublicSuffix(message), message);
    }

    public static String applyPublicCommandSuffixSafely(String command) {
        return safe(() -> applyPublicCommandSuffix(command), command);
    }

    private static <T> T safe(java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Throwable t) {
            if (!failureReported) {
                failureReported = true;
                LOGGER.warn("[Kwrg] 语气词处理失败，已跳过装饰并保持原消息", t);
            }
            return fallback;
        }
    }

    public static String applyPublicSuffix(String message) {
        if (message == null || message.isBlank()) return message;
        if (message.startsWith("/")) return message;

        IrcConfig config = IrcConfig.get();
        if (!config.chatSuffixEnabled || !config.chatSuffixPublicChat) return message;

        return decoratePlain(config, message);
    }

    public static String applyPublicCommandSuffix(String command) {
        if (command == null || command.isEmpty()) return command;

        IrcConfig config = IrcConfig.get();
        if (!config.chatSuffixEnabled || !config.chatSuffixPublicChat) return command;

        String lower = command.toLowerCase(Locale.ROOT);
        if (!lower.equals("ac") && !lower.startsWith("ac ")) return command;

        int space = command.indexOf(' ');
        if (space < 0 || space + 1 >= command.length()) return command;

        String text = command.substring(space + 1);
        String decorated = decoratePlain(config, text);
        return decorated.equals(text) ? command : command.substring(0, space + 1) + decorated;
    }

    private static String resolveSuffixValue(IrcConfig config, String message) {
        if (isPureEnglish(message) && isMeowSuffix(config.chatSuffix)) return ENGLISH_SUFFIX;
        return config.chatSuffix;
    }

    private static boolean isMeowSuffix(String value) {
        if (value == null) return false;
        return convertCodes(value, false).trim().equals("喵~");
    }

    private static String decoratePlain(IrcConfig config, String message) {
        String suffixValue = resolveSuffixValue(config, message);
        String prefix = convertCodes(config.chatSuffixPrefix, false);
        String suffix = convertCodes(suffixValue, false);
        if (prefix.isEmpty() && suffix.isEmpty()) return message;

        String gap = needsSpace(message, suffix) ? " " : "";
        if (exceedsLimit(prefix, message, gap, suffix)) return message;

        return (prefix + message + gap + suffix).replace("§", "");
    }

    private static boolean exceedsLimit(String prefix, String message, String gap, String suffix) {
        return prefix.length() + message.length() + gap.length() + suffix.length() > MAX_MESSAGE_LENGTH;
    }

    private static boolean needsSpace(String message, String suffixText) {
        if (message == null || message.isEmpty() || suffixText == null || suffixText.isEmpty()) return false;
        if (Character.isWhitespace(message.charAt(message.length() - 1))) return false;
        if (Character.isWhitespace(suffixText.charAt(0))) return false;

        char c = suffixText.charAt(0);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static IrcDecoration decorateIrc(String message) {
        if (message == null || message.isBlank()) return IrcDecoration.plain(message);
        if (message.startsWith("/")) return IrcDecoration.plain(message);

        IrcConfig config = IrcConfig.get();
        if (!config.chatSuffixEnabled) return IrcDecoration.plain(message);

        String suffixValue = resolveSuffixValue(config, message);
        boolean follow = config.chatSuffixFollowMessageStyle;

        String suffixText = convertCodes(suffixValue, false);
        String gap = needsSpace(message, suffixText) ? " " : "";
        String prefix = follow ? convertCodes(config.chatSuffixPrefix, false) : buildSentValue(config.chatSuffixPrefix);
        String suffix = follow ? suffixText : buildSentValue(suffixValue);

        if (prefix.isEmpty() && suffix.isEmpty()) return IrcDecoration.plain(message);
        if (exceedsLimit(prefix, message, gap, suffix)) return IrcDecoration.plain(message);

        if (follow) return IrcDecoration.plain(prefix + message + gap + suffix);
        return new IrcDecoration(message, prefix, buildSentValue(gap + suffixValue));
    }

    private static String buildSentValue(String value) {
        if (value == null || value.isBlank()) return "";
        return styledText(value);
    }

    private static boolean isPureEnglish(String message) {
        boolean hasLetter = false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
            } else if (c > 0x7F) {
                return false;
            }
        }
        return hasLetter;
    }

    private static String convertCodes(String value, boolean keepCodes) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < value.length() && isLegacyCode(value.charAt(i + 1))) {
                if (keepCodes) {
                    sb.append('§').append(value.charAt(i + 1));
                }
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isLegacyCode(char c) {
        char lower = (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }
}
