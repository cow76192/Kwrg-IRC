package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class NameStyleModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public String namestyle = "";
    }

    public NameStyleModule() {
        super("nameStyle", Category.STYLE, PanelItemComponent.ItemType.TRIGGER, CONFIG);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        String initialStyle = CONFIG.get().namestyle != null ? CONFIG.get().namestyle : "";
        String[] currentEditingStyle = new String[]{ initialStyle };

        Minecraft mc = Minecraft.getInstance();
        String sampleName = mc.getUser() != null ? mc.getUser().getName() : "Player";

        TextInputPaneComponent inputPane = new TextInputPaneComponent("输入样式 (如 rainbow, #00F2FE)...", submitted -> {
            String clean = submitted.trim();

            if (!ColorUtil.isValidStyle(clean)) {
                ChatNotifier.send("§c[IRC] 样式不合法！存在未知参数: §f" + clean, NotificationType.WARN);
                return;
            }

            CONFIG.get().namestyle = clean;
            IrcConfig.save();
            ChatNotifier.send("§a[IRC] 已保存 NameStyle: §f" + clean, NotificationType.SUCCESS);
        });
        inputPane.setText(initialStyle);
        inputPane.onTextChanged(text -> currentEditingStyle[0] = text);

        panel.child(inputPane);
        panel.child(new TextInfoPaneComponent(
                "效果 (点击复制)",
                () -> ColorUtil.applyStyle(sampleName, currentEditingStyle[0]),
                () -> {
                    String styleToCopy = currentEditingStyle[0] != null ? currentEditingStyle[0].trim() : "";
                    if (!styleToCopy.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(styleToCopy);
                        ChatNotifier.send("§a[IRC] 已复制当前 NameStyle 样式配置: §f" + styleToCopy, NotificationType.SUCCESS);
                    } else {
                        ChatNotifier.send("§e[IRC] 当前样式配置为空！", NotificationType.WARN);
                    }
                }
        ));
        panel.child(TextInfoPaneComponent.header("预设样式"));

        for (String preset : ColorUtil.STYLE_PRESETS) {
            panel.child(new PanelItemComponent(
                    preset,
                    PanelItemComponent.ItemType.TRIGGER,
                    (item, click) -> {
                        // ★ 提取当前输入框中已启用的所有修饰词（如 bold, italic, underline 等） ★
                        // ★ 保留修饰词，仅替换主要样式（自动避免重复叠加） ★
                        String newStyle = ColorUtil.mergePreset(inputPane.getText().trim(), preset);
                        inputPane.setText(newStyle);
                        currentEditingStyle[0] = newStyle;
                        CONFIG.get().namestyle = newStyle;
                        IrcConfig.save();
                        ChatNotifier.send("§a[IRC] 已应用 NameStyle: §f" + newStyle, NotificationType.SUCCESS);
                    },
                    item -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(preset);
                        ChatNotifier.send("§a[IRC] 已复制预设: §f" + preset, NotificationType.SUCCESS);
                    }
            ));
        }
    }

    public static int setNameStyle(String style) {
        Settings config = CONFIG.get();
        config.namestyle = style;
        IrcConfig.save();

        Minecraft mc = Minecraft.getInstance();
        String sampleName = mc.getUser() != null ? mc.getUser().getName() : "Player";

        List<ChatNotifier.AnimatedSegment> segments = new ArrayList<>();
        segments.add(new ChatNotifier.AnimatedSegment("§a[IRC] NameStyle 已更新为: §f" + style + " §7[预览: ", "", true));
        segments.add(new ChatNotifier.AnimatedSegment(sampleName, style, false));
        segments.add(new ChatNotifier.AnimatedSegment("§7]", "", true));

        ChatNotifier.sendAnimatedInChatScreen(segments);
        return 1;
    }

    public static int showNameStyle() {
        Settings config = CONFIG.get();
        String currentStyle = config.namestyle != null ? config.namestyle : "";

        Minecraft mc = Minecraft.getInstance();
        String sampleName = mc.getUser() != null ? mc.getUser().getName() : "Player";

        List<ChatNotifier.AnimatedSegment> segments = new ArrayList<>();
        segments.add(new ChatNotifier.AnimatedSegment("§a[IRC] 当前 NameStyle: §f" + (currentStyle.isBlank() ? "无" : currentStyle) + " §7[预览: ", "", true));
        segments.add(new ChatNotifier.AnimatedSegment(sampleName, currentStyle, false));
        segments.add(new ChatNotifier.AnimatedSegment("§7] §e| 用法: /irc ns <rainbow | sunset | #HEX | gradient(...)>", "", true));

        ChatNotifier.sendAnimatedInChatScreen(segments);
        return 0;
    }
}