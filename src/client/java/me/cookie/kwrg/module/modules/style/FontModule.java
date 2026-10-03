package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FontModule extends Module {

    public FontModule() {
        super("font", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderFontUI(panel);
    }

    private void renderFontUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("字体列表 (Shift+左键打开字体目录)", (pane, click) -> {
            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
            if (isShiftDown) {
                Path fontDir = FontManagerHelper.ensureFontDir();
                try {
                    if (!Files.exists(fontDir)) {
                        Files.createDirectories(fontDir);
                    }
                    Util.getPlatform().openPath(fontDir);
                    ChatNotifier.send("§a[IRC] 已打开字体文件夹: §f" + fontDir.toAbsolutePath(), NotificationType.SUCCESS);
                } catch (Exception e) {
                    ChatNotifier.send("§c[IRC] 打开文件夹失败: " + e.getMessage(), NotificationType.ERR);
                }
            }
        }));

        String currentFont = IrcConfig.get().guiFont != null ? IrcConfig.get().guiFont : FontManagerHelper.DEFAULT_FONT;
        panel.child(new TextInfoPaneComponent(
                "当前生效",
                () -> Component.literal("§b" + (FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(currentFont) ? "原版默认 (Default)" : currentFont)),
                false
        ));

        panel.child(new PanelItemComponent(
                "重新加载当前字体",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    boolean success = FontManagerHelper.reloadCurrentFont();
                    if (success) {
                        ChatNotifier.send("§a[IRC] 已重新载入字体资源", NotificationType.SUCCESS);
                        renderFontUI(panel);
                    } else {
                        ChatNotifier.send("§c[IRC] 重新载入字体失败", NotificationType.ERR);
                    }
                },
                null,
                () -> false
        ));

        panel.child(TextInfoPaneComponent.header("可用字体"));

        List<String> fontList = FontManagerHelper.getAvailableFonts();

        for (String fontName : fontList) {
            boolean isDefault = FontManagerHelper.DEFAULT_FONT.equalsIgnoreCase(fontName);
            String displayName = isDefault ? "Default" : fontName;

            PanelItemComponent item = new PanelItemComponent(
                    displayName,
                    PanelItemComponent.ItemType.TOGGLE,
                    (btn, click) -> {
                        boolean success = FontManagerHelper.applyFont(fontName);
                        if (success) {
                            IrcConfig.get().guiFont = fontName;
                            IrcConfig.save();
                            ChatNotifier.send("§a[IRC] 已应用并切换字体: §f" + fontName, NotificationType.SUCCESS);
                            renderFontUI(panel);
                        } else {
                            ChatNotifier.send("§c[IRC] 字体加载失败，已保持当前字体", NotificationType.WARN);
                        }
                    },
                    null,
                    () -> fontName.equalsIgnoreCase(IrcConfig.get().guiFont)
            );

            panel.child(item);
        }
    }
}