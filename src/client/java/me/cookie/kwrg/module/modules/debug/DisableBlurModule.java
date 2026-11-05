package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class DisableBlurModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class);

    public static final class Settings {
        public boolean disableBlur = false;
        public float blurQuality = 8.0f;
        public float blurSize = 16.0f;
    }

    public DisableBlurModule() {
        super("disableBlur", Category.DEBUG, PanelItemComponent.ItemType.TOGGLE, () -> CONFIG.get().disableBlur, CONFIG);
    }

    /**
     * 二级菜单点击：快速开关背景毛玻璃
     */
    @Override
    public void onExecute() {
        Settings config = CONFIG.get();
        config.disableBlur = !config.disableBlur;
        IrcConfig.save();

        if (config.disableBlur) {
            ChatNotifier.send("§aBlur 已: §c禁用", NotificationType.INFO);
        } else {
            ChatNotifier.send("§aBlur 已: §a启用", NotificationType.INFO);
        }

        refreshScreenSurface();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderTertiaryUI(panel);
    }

    private void renderTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        Settings config = CONFIG.get();

        panel.child(new TextInfoPaneComponent(
                "当前参数",
                () -> {
                    if (CONFIG.get().disableBlur) {
                        return Component.literal("§c已禁用 (透明)");
                    }
                    return Component.literal(String.format(Locale.ROOT, "§bQ: %.1f §7| §bS: %.1f", CONFIG.get().blurQuality, CONFIG.get().blurSize));
                },
                false
        ));

        panel.child(TextInfoPaneComponent.header("预设"));

        panel.child(new PanelItemComponent(
                "Balanced (3.0, 16.0)",
                PanelItemComponent.ItemType.TOGGLE,
                () -> applyPreset(3.0f, 16.0f, "Balanced", panel),
                null,
                () -> !CONFIG.get().disableBlur && Math.abs(CONFIG.get().blurQuality - 3.0f) < 0.05f && Math.abs(CONFIG.get().blurSize - 16.0f) < 0.05f
        ));

        panel.child(new PanelItemComponent(
                "Performance (2.0, 14.0)",
                PanelItemComponent.ItemType.TOGGLE,
                () -> applyPreset(2.0f, 14.0f, "Performance", panel),
                null,
                () -> !CONFIG.get().disableBlur && Math.abs(CONFIG.get().blurQuality - 2.0f) < 0.05f && Math.abs(CONFIG.get().blurSize - 14.0f) < 0.05f
        ));

        panel.child(new PanelItemComponent(
                "Ultra (8.0, 16.0)",
                PanelItemComponent.ItemType.TOGGLE,
                () -> applyPreset(8.0f, 16.0f, "Ultra", panel),
                null,
                () -> !CONFIG.get().disableBlur && Math.abs(CONFIG.get().blurQuality - 8.0f) < 0.05f && Math.abs(CONFIG.get().blurSize - 16.0f) < 0.05f
        ));

        panel.child(TextInfoPaneComponent.header("自定义数值 (回车保存)"));

        TextInputPaneComponent qualityInput = new TextInputPaneComponent("质量 Quality (当前: " + config.blurQuality + ")", input -> {
            try {
                float q = Float.parseFloat(input.trim());
                if (q < 0.5f || q > 32.0f) {
                    ChatNotifier.send("§c模糊质量建议在 0.5 ~ 32.0 之间", NotificationType.WARN);
                    return;
                }
                CONFIG.get().blurQuality = q;
                CONFIG.get().disableBlur = false;
                IrcConfig.save();
                ChatNotifier.send("§a模糊质量已更新为: §f" + q, NotificationType.SUCCESS);
                refreshScreenSurface();
                renderTertiaryUI(panel);
            } catch (Exception e) {
                ChatNotifier.send("§c请输入合法的数字格式", NotificationType.WARN);
            }
        });
        qualityInput.setText(String.valueOf(config.blurQuality));
        panel.child(qualityInput);

        TextInputPaneComponent sizeInput = new TextInputPaneComponent("半径 Size (当前: " + config.blurSize + ")", input -> {
            try {
                float s = Float.parseFloat(input.trim());
                if (s < 1.0f || s > 64.0f) {
                    ChatNotifier.send("§c半径数值建议在 1.0 ~ 64.0 之间", NotificationType.WARN);
                    return;
                }
                CONFIG.get().blurSize = s;
                CONFIG.get().disableBlur = false;
                IrcConfig.save();
                ChatNotifier.send("§a模糊半径已更新为: §f" + s, NotificationType.SUCCESS);
                refreshScreenSurface();
                renderTertiaryUI(panel);
            } catch (Exception e) {
                ChatNotifier.send("§c请输入合法的数字格式", NotificationType.WARN);
            }
        });
        sizeInput.setText(String.valueOf(config.blurSize));
        panel.child(sizeInput);
    }

    private static void applyPreset(float quality, float size, String name, TertiaryPanelComponent panel) {
        Settings config = CONFIG.get();
        config.disableBlur = false;
        config.blurQuality = quality;
        config.blurSize = size;
        IrcConfig.save();

        ChatNotifier.send("§a已应用 " + name + " 预设: §f" + quality + ", " + size, NotificationType.SUCCESS);
        refreshScreenSurface();
        panel.clearChildren();
        ((DisableBlurModule) me.cookie.kwrg.module.ModuleManager.getModule("disableBlur")).renderTertiaryUI(panel);
    }

    private static void refreshScreenSurface() {
        IrcScreen current = IrcScreen.getCurrentScreen();
        if (current != null) {
            current.updateSurface();
        }
    }
}