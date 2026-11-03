package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.ExperimentsManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

public class ExperimentsModule extends Module {

    public ExperimentsModule() {
        super("experiments", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, ExperimentsManager::isEnabled);
    }

    @Override
    public void onExecute() {
        ExperimentsManager.toggle();
        saveConfig();
    }

    private boolean saveDelaySettings(String[] values) {
        if (values == null || values.length < 2) return false;
        try {
            int base = Integer.parseInt(values[0].trim());
            int rand = Integer.parseInt(values[1].trim());
            if (base < 50 || base > 1000 || rand < 0 || rand > 500) {
                return false;
            }
            IrcConfig.get().experimentsBaseDelay = base;
            IrcConfig.get().experimentsRandomDelay = rand;
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
        String[] delayInputs = new String[]{
                String.valueOf(ExperimentsManager.getBaseDelay()),
                String.valueOf(ExperimentsManager.getRandomDelay())
        };

        SlotInputPaneComponent delaySlot = new SlotInputPaneComponent(
                "基准 / 随机波动",
                delayInputs,
                new String[]{"ms", "ms"},
                values -> {
                    if (!saveDelaySettings(values)) {
                        ChatNotifier.send("§c[Experiments] 保存失败 | 基准(50~1000ms) 随机(0~500ms)", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[Experiments] 已更新延迟 | 基准:" + ExperimentsManager.getBaseDelay()
                            + "ms 随机:+" + ExperimentsManager.getRandomDelay() + "ms", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).bounds(new int[][]{{50, 1000}, {0, 500}}).dragStep(10.0).precision(0);
        panel.child(delaySlot);

        panel.child(TextInfoPaneComponent.header("状态控制"));
        PanelItemComponent resetBtn = new PanelItemComponent(
                "重置当前缓存序列",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    ExperimentsManager.resetState();
                    ChatNotifier.send("§e[Experiments] 已重置记忆序列与状态", NotificationType.INFO);
                },
                null
        );
        panel.child(resetBtn);
    }
}