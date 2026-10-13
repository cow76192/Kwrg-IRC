package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc-chatsuffix");

    private static boolean failureReported = false;

    private static final String SAMPLE_MESSAGE = "2v88!";

    private static final String ENGLISH_SUFFIX = "&o&dmeow~";

    private static final String STYLE_RESET = "§r";

    private static final String[] PRESETS = new String[]{
            "&o&d喵~",
            ENGLISH_SUFFIX,
            "&o rainbow Ciallo～(∠・ω< )⌒★",
            "&o&l&b喵呜~",
            "&6nya~",
            "&d✧˖°",
            "&a(´・ω・`)",
            "&c♥",
            "sakura ✿",
            "#FF55AA♡"
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
            ChatNotifier.send("§a[IRC] 自动语气词已: §a开启 §7(前缀: §f" + getConfiguredPrefix()
                    + "§7 后缀: §f" + getConfiguredSuffix() + "§7)", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§e[IRC] 自动语气词已: §c关闭 §7", NotificationType.ERR);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String[] editingPrefix = { getConfiguredPrefix() };
        String[] editingSuffix = { getConfiguredSuffix() };

        panel.child(new PanelItemComponent(
                "开启 / 关闭",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    onExecute();
                    btn.setToggled(IrcConfig.get().chatSuffixEnabled);
                },
                null,
                () -> IrcConfig.get().chatSuffixEnabled
        ));

        panel.child(new TextInfoPaneComponent(
                "",
                () -> buildPreview(editingPrefix[0], editingSuffix[0]),
                false
        ));

        panel.child(createValuePane("前缀值", "前缀", getConfiguredPrefix(),
                value -> {
                    IrcConfig.get().chatSuffixPrefix = value;
                    IrcConfig.save();
                },
                value -> editingPrefix[0] = value));

        TextInputPaneComponent suffixPane = createValuePane("后缀值", "后缀", getConfiguredSuffix(),
                value -> {
                    IrcConfig.get().chatSuffix = value;
                    IrcConfig.save();
                },
                value -> editingSuffix[0] = value);
        panel.child(suffixPane);

        panel.child(new PanelItemComponent(
                "跟随 messageStyle",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    IrcConfig config = IrcConfig.get();
                    config.chatSuffixFollowMessageStyle = !config.chatSuffixFollowMessageStyle;
                    IrcConfig.save();
                    btn.setToggled(config.chatSuffixFollowMessageStyle);
                    ChatNotifier.send(config.chatSuffixFollowMessageStyle
                            ? "§a[IRC] 语气词已跟随 messageStyle"
                            : "§e[IRC] 语气词已使用自身颜色", NotificationType.INFO);
                },
                null,
                () -> IrcConfig.get().chatSuffixFollowMessageStyle
        ));

        panel.child(TextInfoPaneComponent.header("预设后缀"));

        for (String preset : PRESETS) {
            panel.child(new PanelItemComponent(
                    preset,
                    PanelItemComponent.ItemType.TRIGGER,
                    (item, click) -> {
                        suffixPane.setText(preset);
                        editingSuffix[0] = preset;
                        IrcConfig.get().chatSuffix = preset;
                        IrcConfig.save();
                        ChatNotifier.send("§a[IRC] 已应用后缀预设: §f" + preset, NotificationType.SUCCESS);
                    },
                    item -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(preset);
                        ChatNotifier.send("§a[IRC] 已复制预设: §f" + preset, NotificationType.SUCCESS);
                    }
            ));
        }
    }

    private static TextInputPaneComponent createValuePane(String placeholder, String label, String initialValue,
                                                          Consumer<String> saver, Consumer<String> editingConsumer) {
        TextInputPaneComponent pane = new TextInputPaneComponent(placeholder, submitted -> {
            saver.accept(submitted);
            ChatNotifier.send("§a[IRC] 已保存 " + label + ": §f" + submitted, NotificationType.SUCCESS);
        }, "Enter保存|Shift点击清空|右键复制") {
            @Override
            public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
                boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
                if (click.button() == 0 && shift) {
                    setText("");
                    saver.accept("");
                    ChatNotifier.send("§e[IRC] 已清空 " + label + " §7", NotificationType.INFO);
                    return true;
                }
                return super.onMouseDown(click, doubled);
            }
        };

        if (!initialValue.isEmpty()) {
            pane.setText(initialValue);
        }
        pane.onTextChanged(editingConsumer);
        pane.onRightClick(() -> {
            String copied = pane.getText();
            Minecraft.getInstance().keyboardHandler.setClipboard(copied);
            ChatNotifier.send("§a[IRC] 已复制 " + label + ": §f" + copied, NotificationType.SUCCESS);
        });
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
        String[] prefixParts = splitStyleAndText(prefixValue == null ? "" : prefixValue);
        String[] suffixParts = splitStyleAndText(suffixValue == null ? "" : suffixValue);

        String messageStyle = config.chatstyle != null ? config.chatstyle : "";
        String prefixStyle = config.chatSuffixFollowMessageStyle ? messageStyle : prefixParts[0];
        String suffixStyle = config.chatSuffixFollowMessageStyle ? messageStyle : suffixParts[0];

        return Component.empty()
                .append(ColorUtil.applyStyle(prefixParts[1], prefixStyle))
                .append(ColorUtil.applyStyle(SAMPLE_MESSAGE, ""))
                .append(ColorUtil.applyStyle(suffixParts[1], suffixStyle));
    }

    public static String applySuffixSafely(String message) {
        try {
            return applySuffix(message);
        } catch (Throwable t) {
            if (!failureReported) {
                failureReported = true;
                LOGGER.warn("[Kwrg] 语气词处理失败，已跳过装饰并保持原消息", t);
            }
            return message;
        }
    }

    public static String applySuffix(String message) {
        if (message == null || message.isBlank()) return message;
        if (message.startsWith("/")) return message;

        IrcConfig config = IrcConfig.get();
        if (!config.chatSuffixEnabled) return message;
        if (!IrcClient.isIrcChatActive) return message;

        boolean allowStyleCodes = !config.chatSuffixFollowMessageStyle && !chatStyleSplitsPerChar(config);
        String suffixValue = isPureEnglish(message) ? ENGLISH_SUFFIX : config.chatSuffix;
        String prefix = buildSentValue(config.chatSuffixPrefix, allowStyleCodes);
        String suffix = buildSentValue(suffixValue, allowStyleCodes);
        if (prefix.isEmpty() && suffix.isEmpty()) return message;

        return prefix + message + suffix;
    }

    private static boolean chatStyleSplitsPerChar(IrcConfig config) {
        String style = config.chatstyle;
        if (style == null || style.isBlank()) return false;
        return ColorUtil.applyStyle("abc", style).getSiblings().size() > 1;
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

    private static String buildSentValue(String value, boolean allowStyleCodes) {
        if (value == null || value.isBlank()) return "";

        String[] parts = splitStyleAndText(value);
        String codes = allowStyleCodes ? toLegacyCodes(parts[0]) : "";
        if (codes.isEmpty()) return parts[1];
        return codes + parts[1] + STYLE_RESET;
    }

    private static String[] splitStyleAndText(String value) {
        if (value == null || value.isEmpty()) return new String[]{"", ""};

        int index = 0;
        StringBuilder style = new StringBuilder();

        while (index < value.length()) {
            char c = value.charAt(index);

            if ((c == '&' || c == '§') && index + 1 < value.length() && isLegacyCode(value.charAt(index + 1))) {
                StringBuilder codes = new StringBuilder();
                while (index + 1 < value.length()
                        && (value.charAt(index) == '&' || value.charAt(index) == '§')
                        && isLegacyCode(value.charAt(index + 1))) {
                    codes.append(value.charAt(index)).append(value.charAt(index + 1));
                    index += 2;
                }
                appendToken(style, codes.toString());
                continue;
            }

            String token = matchStyleToken(value, index);
            if (token != null) {
                appendToken(style, token);
                index += token.length();
                continue;
            }

            if (style.length() > 0 && c == ' ') {
                index++;
                continue;
            }
            break;
        }

        return new String[]{style.toString(), value.substring(index)};
    }

    private static String matchStyleToken(String value, int index) {
        int end = index;
        while (end < value.length() && isStyleChar(value.charAt(end))) {
            end++;
        }
        if (end == index) return null;

        String candidate = value.substring(index, end);
        if (ColorUtil.isKnownToken(candidate.toLowerCase(Locale.ROOT)) || isColorLiteral(candidate)) {
            return candidate;
        }
        return null;
    }

    private static void appendToken(StringBuilder style, String token) {
        if (style.length() > 0) {
            style.append(' ');
        }
        style.append(token);
    }

    private static boolean isStyleChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '#' || c == '(' || c == ')' || c == ',' || c == '.' || c == '%' || c == '-';
    }

    private static boolean isColorLiteral(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        return lower.matches("#([0-9a-f]{6}|[0-9a-f]{3})")
                || lower.matches("a?rgb\\([^()]*\\)")
                || lower.matches("a?hsl\\([^()]*\\)")
                || (lower.startsWith("gradient(") && lower.endsWith(")"))
                || (lower.startsWith("linear-gradient(") && lower.endsWith(")"));
    }

    private static boolean isLegacyCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }

    private static String toLegacyCodes(String style) {
        if (style == null || style.isEmpty()) return "";

        StringBuilder codes = new StringBuilder();
        for (int i = 0; i + 1 < style.length(); i++) {
            char c = style.charAt(i);
            if ((c == '&' || c == '§') && isLegacyCode(style.charAt(i + 1))) {
                codes.append('§').append(Character.toLowerCase(style.charAt(i + 1)));
                i++;
            }
        }
        return codes.toString();
    }
}
