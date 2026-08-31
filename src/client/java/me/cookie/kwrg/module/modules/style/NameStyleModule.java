package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public class NameStyleModule extends Module {

    private static final List<String> MODIFIERS = List.of("bold", "underline", "italic", "strikethrough", "obfuscated", "flow");

    private static final String[] PRESETS = new String[]{
            "rainbow", "sunset", "ocean", "fire", "sakura", "neon", "gold", "purple",
            "bold", "underline", "italic", "strikethrough", "obfuscated", "flow",
            "rainbow bold", "#FF55AA", "rgb(255,100,50)", "gradient(#ff758c,#ff7eb3)", "gradient(#00f2fe,#4facfe,#00ff87)"
    };

    public NameStyleModule() {
        super("nameStyle", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String initialStyle = IrcConfig.get().namestyle != null ? IrcConfig.get().namestyle : "";
        String[] currentEditingStyle = new String[]{ initialStyle };

        Minecraft mc = Minecraft.getInstance();
        String sampleName = mc.getUser() != null ? mc.getUser().getName() : "Player";

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入样式 (如 rainbow, #00F2FE)...", submitted -> {
            String clean = submitted.trim();

            if (!ColorUtil.isValidStyle(clean)) {
                ChatNotifier.send("§c[IRC] 样式不合法！存在未知参数: §f" + clean);
                return;
            }

            IrcConfig.get().namestyle = clean;
            IrcConfig.save();
            ChatNotifier.send("§a[IRC] 已保存 NameStyle: §f" + clean);
        });
        inputPane.setText(initialStyle);
        inputPane.onTextChanged(text -> currentEditingStyle[0] = text);

        panel.child(inputPane);
        panel.child(new TextInfoPaneComponent(
                "预览",
                () -> ColorUtil.applyStyle(sampleName, currentEditingStyle[0]),
                false
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
                            // ★ 提取当前输入框中已启用的所有修饰词（如 bold, italic, underline 等） ★
                            java.util.List<String> currentMods = new java.util.ArrayList<>();
                            for (String mod : MODIFIERS) {
                                if (current.toLowerCase().matches(".*\\b" + mod + "\\b.*")) {
                                    currentMods.add(mod);
                                }
                            }

                            // ★ 保留修饰词，仅替换主要样式（自动避免重复叠加） ★
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
                        IrcConfig.get().namestyle = newStyle;
                        IrcConfig.save();
                        ChatNotifier.send("§a[IRC] 已应用 NameStyle: §f" + newStyle);
                    },
                    item -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(preset);
                        ChatNotifier.send("§a[IRC] 已复制预设: §f" + preset);
                    }
            ));
        }
    }

    public static int setNameStyle(String style) {
        IrcConfig config = IrcConfig.get();
        config.namestyle = style;
        IrcConfig.save();

        Component preview = ColorUtil.applyStyle("名字预览效果", style);
        Component message = Component.literal("§a[IRC] NameStyle 已更新为: " + style + " §7[预览: ")
                .append(preview)
                .append(Component.literal("§7]"));

        ChatNotifier.sendInChatScreen(message);
        return 1;
    }

    public static int showNameStyle() {
        IrcConfig config = IrcConfig.get();
        ChatNotifier.sendInChatScreen("§c[IRC] 当前 NameStyle: " + config.namestyle + ", 用法: /irc ns <rainbow | sunset | #HEX | gradient(...)>");
        return 0;
    }
}