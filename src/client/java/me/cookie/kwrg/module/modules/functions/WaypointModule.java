package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

import java.util.ArrayList;
import java.util.List;

public class WaypointModule extends Module {

    private String currentName = "";
    private String[] currentValues = {"0", "64", "0"};

    public WaypointModule() {
        super("waypoint", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderUI(panel, null);
    }

    private void renderUI(TertiaryPanelComponent panel, String newlyAdded) {
        panel.clearChildren();

        SlotInputPaneComponent[] inputHolder = new SlotInputPaneComponent[1];
        inputHolder[0] = new SlotInputPaneComponent("名字", this.currentValues, null, values -> {
            String name = inputHolder[0].getLeftValue().trim();
            if (!WaypointManager.saveEntry(name, values)) {
                ChatNotifier.send("§c[Waypoint] 保存失败 | 名称或坐标无效，或已达上限", NotificationType.ERR);
                return;
            }

            this.currentName = "";
            this.currentValues = new String[]{"0", "64", "0"};
            int total = IrcConfig.get().waypoints == null ? 0 : IrcConfig.get().waypoints.size();
            ChatNotifier.send("§a[Waypoint] 已保存 | " + name + " (共 " + total + " 条)", NotificationType.SUCCESS);
            renderUI(panel, name);
        }, null).leftEditable("名字", this.currentName).precision(0);

        inputHolder[0].onValuesChanged(values -> this.currentValues = values.clone());
        inputHolder[0].onLeftChanged(name -> this.currentName = name);

        panel.child(TextInfoPaneComponent.header("名字 / X / Y / Z"));
        panel.child(inputHolder[0]);
        panel.child(new PanelItemComponent("＋ 新建坐标", () -> {
            this.currentName = "";
            this.currentValues = new String[]{"0", "64", "0"};
            renderUI(panel, null);
        }));

        List<IrcConfig.WaypointEntry> list = IrcConfig.get().waypoints;
        int count = list == null ? 0 : list.size();
        panel.child(TextInfoPaneComponent.header("已保存坐标 (" + count + ")\n同名更新 | 新名字新增 | 左键编辑 | 右击开关 | Shift删除"));

        if (list == null || list.isEmpty()) {
            panel.child(new PanelItemComponent("暂无坐标"));
            return;
        }

        for (IrcConfig.WaypointEntry entry : new ArrayList<>(list)) {
            if (entry == null || entry.name == null) continue;

            String label = (entry.enabled ? "§a[显示] " : "§7[隐藏] ")
                    + "§f" + entry.name
                    + " §7(" + WaypointManager.formatCoord(entry.x)
                    + ", " + WaypointManager.formatCoord(entry.y)
                    + ", " + WaypointManager.formatCoord(entry.z) + ")";

            PanelItemComponent item = new PanelItemComponent(
                    label,
                    PanelItemComponent.ItemType.TOGGLE,
                    (btn, click) -> {
                        if (click.hasShiftDown()) {
                            btn.dismiss(() -> {
                                WaypointManager.remove(entry.name);
                                if (entry.name.equalsIgnoreCase(this.currentName)) {
                                    this.currentName = "";
                                    this.currentValues = new String[]{"0", "64", "0"};
                                }
                                ChatNotifier.send("§e[Waypoint] 已删除 | " + entry.name, NotificationType.INFO);
                                renderUI(panel, null);
                            });
                        } else {
                            this.currentName = entry.name;
                            this.currentValues = new String[]{
                                    WaypointManager.formatCoord(entry.x),
                                    WaypointManager.formatCoord(entry.y),
                                    WaypointManager.formatCoord(entry.z)
                            };
                            renderUI(panel, null);
                        }
                    },
                    btn -> {
                        WaypointManager.toggle(entry.name);
                        renderUI(panel, null);
                    },
                    () -> entry.enabled
            );

            if (newlyAdded != null && entry.name.equalsIgnoreCase(newlyAdded)) {
                item.withSpawnAnimation();
            }

            panel.child(item);
        }
    }
}
