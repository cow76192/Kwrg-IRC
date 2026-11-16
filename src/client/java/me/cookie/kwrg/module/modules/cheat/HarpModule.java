package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.irc.manager.HarpManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class HarpModule extends Module {

    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public boolean harpEnabled = false;
        public int harpBaseDelay = 150;
        public int harpJitterDelay = 10;

        private void sanitize() {
            if (harpBaseDelay < 10) harpBaseDelay = 150;
            harpBaseDelay = Math.clamp(harpBaseDelay, 10, 500);
            if (harpJitterDelay < 0) harpJitterDelay = 10;
            harpJitterDelay = Math.clamp(harpJitterDelay, 0, 100);
        }
    }

    public HarpModule() {
        super("AutoHarp", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, HarpManager::isEnabled, CONFIG);
    }

    @Override
    public void onExecute() {
        HarpManager.toggle();
        saveConfig();
    }

    private boolean saveBaseDelaySetting(String[] values) {
        if (values == null || values.length < 1) return false;
        try {
            int base = Integer.parseInt(values[0].trim());
            if (base < 10 || base > 500) {
                return false;
            }
            CONFIG.get().harpBaseDelay = base;
            saveConfig();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean saveJitterDelaySetting(String[] values) {
        if (values == null || values.length < 1) return false;
        try {
            int jitter = Integer.parseInt(values[0].trim());
            if (jitter < 0 || jitter > 100) {
                return false;
            }
            CONFIG.get().harpJitterDelay = jitter;
            saveConfig();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void saveConfig() {
        IrcConfig.saveDelayed(500);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderUI(panel);
    }

    private void renderUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("点击延迟配置 (Enter 保存 / 左右拖拽调节)"));
        String[] baseInputs = new String[]{
                String.valueOf(HarpManager.getBaseDelay())
        };

        SlotInputPaneComponent baseDelaySlot = new SlotInputPaneComponent(
                "基准延迟",
                baseInputs,
                new String[]{"ms"},
                values -> {
                    if (!saveBaseDelaySetting(values)) {
                        ChatNotifier.send("§c[Harp] 保存失败 | 基准延迟范围 (10 ~ 500ms)", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[Harp] 已更新基准延迟 | " + HarpManager.getBaseDelay() + "ms", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).bounds(new int[][]{{10, 500}}).dragStep(10.0).precision(0);
        panel.child(baseDelaySlot);

        String[] jitterInputs = new String[]{
                String.valueOf(HarpManager.getJitterDelay())
        };

        SlotInputPaneComponent jitterDelaySlot = new SlotInputPaneComponent(
                "抖动延迟",
                jitterInputs,
                new String[]{"ms"},
                values -> {
                    if (!saveJitterDelaySetting(values)) {
                        ChatNotifier.send("§c[Harp] 保存失败 | 抖动延迟范围 (0 ~ 100ms)", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[Harp] 已更新抖动延迟 | ±" + HarpManager.getJitterDelay() + "ms", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).bounds(new int[][]{{0, 100}}).dragStep(2.0).precision(0);
        panel.child(jitterDelaySlot);

        panel.child(TextInfoPaneComponent.header("状态控制"));
        PanelItemComponent resetBtn = new PanelItemComponent(
                "重置当前缓存队列",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    HarpManager.resetState();
                    ChatNotifier.send("§e[Harp] 已重置点击队列与音轨状态", NotificationType.INFO);
                },
                null
        );
        panel.child(resetBtn);
    }
}