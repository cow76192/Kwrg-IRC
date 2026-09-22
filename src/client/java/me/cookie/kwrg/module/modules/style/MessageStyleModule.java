package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class MessageStyleModule extends Module {

    private static final List<String> MODIFIERS = List.of("bold", "underline", "italic", "strikethrough", "obfuscated", "flow");

    private static final String[] PRESETS = new String[]{
            "rainbow", "sunset", "ocean", "fire", "sakura", "neon", "gold", "purple",
            "bold", "underline", "italic", "strikethrough", "obfuscated", "flow",
            "rainbow bold", "#FF55AA", "rgb(255,100,50)", "gradient(#ff758c,#ff7eb3)", "gradient(#00f2fe,#4facfe,#00ff87)"
    };

    public MessageStyleModule() {
        super("messageStyle", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String initialStyle = IrcConfig.get().chatstyle != null ? IrcConfig.get().chatstyle : "";
        String[] currentEditingStyle = new String[]{ initialStyle };
        String sampleMessage = "Hello, Kwrg IRC!";

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入消息样式 (如 gradient, sunset)...", submitted -> {
            String clean = submitted.trim();

            if (!ColorUtil.isValidStyle(clean)) {
                ChatNotifier.send("§c[IRC] 样式不合法！存在未知参数: §f" + clean, NotificationType.ERR);
                return;
            }

            IrcConfig.get().chatstyle = clean;
            IrcConfig.save();
            ChatNotifier.send("§a[IRC] 已保存 MessageStyle: §f" + clean, NotificationType.SUCCESS);
        });
        inputPane.setText(initialStyle);
        inputPane.onTextChanged(text -> currentEditingStyle[0] = text);

        panel.child(inputPane);
        panel.child(new TextInfoPaneComponent(
                "效果 (点击复制)",
                () -> ColorUtil.applyStyle(sampleMessage, currentEditingStyle[0]),
                () -> {
                    String styleToCopy = currentEditingStyle[0] != null ? currentEditingStyle[0].trim() : "";
                    if (!styleToCopy.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(styleToCopy);
                        ChatNotifier.send("§a[IRC] 已复制当前 MessageStyle 样式配置: §f" + styleToCopy, NotificationType.SUCCESS);
                    } else {
                        ChatNotifier.send("§e[IRC] 当前消息样式配置为空！", NotificationType.WARN);
                    }
                }
        ));
        panel.child(TextInfoPaneComponent.header("预设样式"));

        for (String preset : PRESETS) {
            panel.child(new PanelItemComponent(
                    preset,
                    PanelItemComponent.ItemType.TRIGGER,
                    (item, click) -> {
                        String current = inputPane.getText().trim();
                        String newStyle;

                        if (MODIFIERS.contains(preset.toLowerCase())) {
                            if (current.toLowerCase().contains(preset.toLowerCase())) {
                                newStyle = current.replaceAll("(?i)\\b" + preset + "\\b", "").trim().replaceAll(" +", " ");
                            } else {
                                newStyle = current.isEmpty() ? preset : current + " " + preset;
                            }
                        } else {
                            java.util.List<String> currentMods = new java.util.ArrayList<>();
                            for (String mod : MODIFIERS) {
                                if (current.toLowerCase().matches(".*\\b" + mod + "\\b.*")) {
                                    currentMods.add(mod);
                                }
                            }

                            StringBuilder sb = new StringBuilder(preset);
                            for (String mod : currentMods) {
                                if (!preset.toLowerCase().matches(".*\\b" + mod + "\\b.*")) {
                                    sb.append(" ").append(mod);
                                }
                            }
                            newStyle = sb.toString().trim();
                        }
                        inputPane.setText(newStyle);
                        currentEditingStyle[0] = newStyle;
                        IrcConfig.get().chatstyle = newStyle;
                        IrcConfig.save();
                        ChatNotifier.send("§a[IRC] 已应用 MessageStyle: §f" + newStyle, NotificationType.SUCCESS);
                    },
                    item -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(preset);
                        ChatNotifier.send("§a[IRC] 已复制预设: §f" + preset, NotificationType.SUCCESS);
                    }
            ));
        }
    }

    public static int setChatStyle(String style) {
        IrcConfig config = IrcConfig.get();
        config.chatstyle = style;
        IrcConfig.save();

        List<ChatNotifier.AnimatedSegment> segments = new ArrayList<>();
        segments.add(new ChatNotifier.AnimatedSegment("§a[IRC] MessageStyle 已更新为: §f" + style + " §7[预览: ", "", true));
        segments.add(new ChatNotifier.AnimatedSegment("Hello Kwrg IRC!", style, false));
        segments.add(new ChatNotifier.AnimatedSegment("§7]", "", true));

        ChatNotifier.sendAnimatedInChatScreen(segments);
        return 1;
    }

    public static int showChatStyle() {
        IrcConfig config = IrcConfig.get();
        String currentStyle = config.chatstyle != null ? config.chatstyle : "";

        List<ChatNotifier.AnimatedSegment> segments = new ArrayList<>();
        segments.add(new ChatNotifier.AnimatedSegment("§a[IRC] 当前 MessageStyle: §f" + (currentStyle.isBlank() ? "无" : currentStyle) + " §7[预览: ", "", true));
        segments.add(new ChatNotifier.AnimatedSegment("Hello Kwrg IRC!", currentStyle, false));
        segments.add(new ChatNotifier.AnimatedSegment("§7] §e| 用法: /irc ms <rainbow | sunset | #HEX | gradient(...)>", "", true));

        ChatNotifier.sendAnimatedInChatScreen(segments);
        return 0;
    }
}