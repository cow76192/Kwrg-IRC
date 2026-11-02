package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.DanceRoomManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

import java.util.Locale;

public class DanceRoomModule extends Module {

    public DanceRoomModule() {
        super("danceroom", Category.CHEAT, PanelItemComponent.ItemType.TOGGLE, DanceRoomManager::isEnabled);
    }

    @Override
    public void onExecute() {
        DanceRoomManager.toggle();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderUI(panel);
    }

    private void renderUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("起跳Tick / 挥拳Tick / 免修正死区(格)"));
        String[] currentInputs = new String[]{
                String.valueOf(DanceRoomManager.getJumpCountdownTick()),
                String.valueOf(DanceRoomManager.getPunchCountdownTick()),
                String.format(Locale.ROOT, "%.2f", DanceRoomManager.getIgnoreDriftDeadzone())
        };

        SlotInputPaneComponent timingInput = new SlotInputPaneComponent(
                "参数设置",
                currentInputs,
                null,
                values -> {
                    if (!DanceRoomManager.saveSettings(values)) {
                        ChatNotifier.send("§c[DanceRoom] 保存失败 | 请输入有效的数值", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[DanceRoom] 已更新 | 起跳:" + DanceRoomManager.getJumpCountdownTick()
                            + "t 挥拳:" + DanceRoomManager.getPunchCountdownTick()
                            + "t 死区:" + DanceRoomManager.getIgnoreDriftDeadzone() + "格", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).precision(2);
        panel.child(timingInput);

        panel.child(TextInfoPaneComponent.header("移动方向设置"));
        PanelItemComponent dirToggle = new PanelItemComponent(
                DanceRoomManager.isClockwise() ? "方向: 顺时针 (W->D->S->A)" : "方向: 逆时针 (W->A->S->D)",
                PanelItemComponent.ItemType.TOGGLE,
                (btn, click) -> {
                    DanceRoomManager.toggleClockwise();
                    renderUI(panel);
                },
                null,
                DanceRoomManager::isClockwise
        );
        panel.child(dirToggle);

        PanelItemComponent resetBtn = new PanelItemComponent(
                "重置进度与起点",
                PanelItemComponent.ItemType.TRIGGER,
                (btn, click) -> {
                    DanceRoomManager.resetStateAndRelease();
                    ChatNotifier.send("§e[DanceRoom] 已重置进度与起点", NotificationType.INFO);
                },
                null
        );
        panel.child(resetBtn);
    }
}