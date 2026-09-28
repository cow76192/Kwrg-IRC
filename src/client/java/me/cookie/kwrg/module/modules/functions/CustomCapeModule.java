package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CustomCapeModule extends Module {

    public CustomCapeModule() {
        super("customCape", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderCapeUI(panel);
    }

    private void renderCapeUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        String current = PlayerCustomizationManager.getMyCape();
        panel.child(new TextInfoPaneComponent("当前我的披风", () -> current == null ? "无 (原版)" : current, false, false));
        panel.child(TextInfoPaneComponent.header("披风文件 (Shift+左键打开目录)", (pane, click) -> {
            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
            if (isShiftDown) {
                openCapeDir();
            }
        }));
        List<String> available = PlayerCustomizationManager.getAvailableCapes();
        if (available.isEmpty()) {
            panel.child(new PanelItemComponent("capes 目录暂无 png/jpg 文件"));
        } else {
            for (String f : available) {
                panel.child(new PanelItemComponent(
                        f,
                        PanelItemComponent.ItemType.TOGGLE,
                        () -> {
                            PlayerCustomizationManager.setMyCape(f);
                            ChatNotifier.send("§a[Cape] 我的披风已设为: §e" + f, NotificationType.SUCCESS);
                            renderCapeUI(panel);
                        },
                        null,
                        () -> f.equalsIgnoreCase(PlayerCustomizationManager.getMyCape())
                ));
            }
        }
        panel.child(new PanelItemComponent(
                "循环切换披风",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    String next = PlayerCustomizationManager.cycleMyCape();
                    ChatNotifier.send("§a[Cape] 我的披风已设为: §e" + (next == null ? "(无/原版)" : next), NotificationType.SUCCESS);
                    renderCapeUI(panel);
                },
                null,
                () -> false
        ));
        panel.child(new PanelItemComponent(
                "移除自定义披风",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    PlayerCustomizationManager.setMyCape(null);
                    ChatNotifier.send("§e[Cape] 已恢复原版披风", NotificationType.INFO);
                    renderCapeUI(panel);
                },
                null,
                () -> false
        ));
    }

    private static void openCapeDir() {
        Path dir = PlayerCustomizationManager.getCapeDir();
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
            ChatNotifier.send("§a[Cape] 已打开披风文件夹: §f" + dir.toAbsolutePath(), NotificationType.SUCCESS);
        } catch (Exception e) {
            ChatNotifier.send("§c[Cape] 打开文件夹失败: " + e.getMessage(), NotificationType.ERR);
        }
    }
}