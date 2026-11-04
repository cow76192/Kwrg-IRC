package me.cookie.kwrg.module.modules.cheat;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.cheat.DanceRoomManager;
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

        // 1. 整数槽位：起跳Tick / 挥拳Tick (precision(0)，步长 1，范围 1~18)
        panel.child(TextInfoPaneComponent.header("动作时机 (Enter 保存 / 左右拖拽调节)"));
        String[] timingInputs = new String[]{
                String.valueOf(DanceRoomManager.getJumpCountdownTick()),
                String.valueOf(DanceRoomManager.getPunchCountdownTick())
        };

        SlotInputPaneComponent timingSlot = new SlotInputPaneComponent(
                "起跳 / 挥拳",
                timingInputs,
                new String[]{"t", "t"},
                values -> {
                    if (!DanceRoomManager.saveTimingSettings(values)) {
                        ChatNotifier.send("§c[DanceRoom] 保存失败 | 请输入有效的整数 (1 ~ 18)", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[DanceRoom] 已更新时机 | 起跳:" + DanceRoomManager.getJumpCountdownTick()
                            + "t 挥拳:" + DanceRoomManager.getPunchCountdownTick() + "t", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).bounds(new int[][]{{1, 18}, {1, 18}}).dragStep(1.0).precision(0);
        panel.child(timingSlot);

        // 2. 小数槽位：免修正死区 (precision(2)，步长 0.05，范围 0.25~0.90)
        panel.child(TextInfoPaneComponent.header("位置容差 (小于死区不修正，大于才修正)"));
        String[] deadzoneInputs = new String[]{
                String.format(Locale.ROOT, "%.2f", DanceRoomManager.getIgnoreDriftDeadzone())
        };

        SlotInputPaneComponent deadzoneSlot = new SlotInputPaneComponent(
                "免修正死区",
                deadzoneInputs,
                new String[]{"格"},
                values -> {
                    if (!DanceRoomManager.saveDeadzoneSetting(values)) {
                        ChatNotifier.send("§c[DanceRoom] 保存失败 | 请输入有效的死区数值 (0.25 ~ 0.90)", NotificationType.ERR);
                        return;
                    }
                    ChatNotifier.send("§a[DanceRoom] 已更新死区 | "
                            + String.format(Locale.ROOT, "%.2f", DanceRoomManager.getIgnoreDriftDeadzone()) + "格", NotificationType.SUCCESS);
                    renderUI(panel);
                },
                null
        ).bounds(new double[][]{{0.25, 0.90}}).dragStep(0.05).precision(2);
        panel.child(deadzoneSlot);

        panel.child(TextInfoPaneComponent.header("移动方向与状态控制"));
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