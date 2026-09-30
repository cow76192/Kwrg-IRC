package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
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
        panel.child(new TextInfoPaneComponent("当前我的披风",
                () -> current == null ? "无 (原版)" : current, false, false));

        panel.child(new TextInputPaneComponent(
                "画布尺寸",
                new String[]{String.valueOf(IrcConfig.get().capeCanvasSize)},
                new String[]{"px"},
                values -> {
                    try {
                        int v = Integer.parseInt(values[0].trim());
                        int closest;
                        if (v <= 64) {
                            closest = 64;
                        } else if (v >= 8192) {
                            closest = 8192;
                        } else {
                            int lower = Integer.highestOneBit(v);
                            int upper = lower << 1;
                            if (v - lower <= upper - v) {
                                closest = lower;
                            } else {
                                closest = upper;
                            }
                        }
                        closest = Math.max(64, Math.min(8192, closest));

                        IrcConfig.get().capeCanvasSize = closest;
                        IrcConfig.save();
                        PlayerCustomizationManager.setMyCape(PlayerCustomizationManager.getMyCape());
                        ChatNotifier.send("§e[Cape] 画布尺寸已修正并应用: §f" + closest, NotificationType.INFO);
                        renderCapeUI(panel);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c[Cape] 请输入有效的数字", NotificationType.ERR);
                    } catch (Exception ignored) {}
                },
                null
        ).bounds(new int[][]{{64, 8192}}));

        panel.child(new TextInputPaneComponent(
                "动画帧率",
                new String[]{String.valueOf(IrcConfig.get().capeAnimFps)},
                new String[]{"FPS"},
                values -> {
                    try {
                        int v = Integer.parseInt(values[0].trim());
                        v = Math.max(1, Math.min(60, v));
                        IrcConfig.get().capeAnimFps = v;
                        IrcConfig.save();
                        ChatNotifier.send("§e[Cape] 动画帧率已设为: §f" + v + " FPS", NotificationType.INFO);
                        renderCapeUI(panel);
                    } catch (NumberFormatException e) {
                        ChatNotifier.send("§c[Cape] 请输入有效的数字", NotificationType.ERR);
                    } catch (Exception ignored) {}
                },
                null
        ).bounds(new int[][]{{1, 60}}));

        panel.child(TextInfoPaneComponent.header("披风文件 (建议 PNG/JPG, 比例 5:8)\nShift+左键打开目录", (pane, click) -> {
            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
            if (isShiftDown) openCapeDir();
        }));

        List<String> available = PlayerCustomizationManager.getAvailableCapes();
        if (available.isEmpty()) {
            panel.child(new PanelItemComponent("capes 目录暂无文件"));
        } else {
            for (String f : available) {
                panel.child(new PanelItemComponent(
                        f,
                        PanelItemComponent.ItemType.TOGGLE,
                        () -> {
                            PlayerCustomizationManager.setMyCape(f);
                            ChatNotifier.send("§a[Cape] 披风已设为: §e" + f, NotificationType.SUCCESS);
                            renderCapeUI(panel);
                        },
                        null,
                        () -> f.equals(PlayerCustomizationManager.getMyCape())
                ));
            }
        }

        panel.child(new PanelItemComponent("✖ 移除披风", PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    PlayerCustomizationManager.setMyCape(null);
                    ChatNotifier.send("§e[Cape] 已恢复原版", NotificationType.INFO);
                    renderCapeUI(panel);
                }, null, () -> false));
    }

    private static void openCapeDir() {
        Path dir = PlayerCustomizationManager.getCapeDir();
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
            ChatNotifier.send("§a[Cape] 已打开目录: §f" + dir.toAbsolutePath(), NotificationType.SUCCESS);
        } catch (Exception e) {
            ChatNotifier.send("§c[Cape] 打开失败: " + e.getMessage(), NotificationType.ERR);
        }
    }
}