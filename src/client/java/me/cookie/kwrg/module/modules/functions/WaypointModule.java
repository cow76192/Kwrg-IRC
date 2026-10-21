package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.WaypointScreen;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class WaypointModule extends Module {

    private String currentName = "";
    private String[] currentValues = {"", "", ""};
    private int editingIndex = -1;

    public WaypointModule() {
        super("waypoint", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        WaypointScreen.open();
    }

    @Override
    public PanelItemComponent createPanelItem(Consumer<PanelItemComponent> openTertiaryCallback) {
        return new PanelItemComponent(getName(), getType(), (item, click) -> WaypointScreen.openFrom(item), openTertiaryCallback, this::isActive);
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        this.editingIndex = -1;
        this.currentName = "";
        this.currentValues = new String[]{"", "", ""};
        renderUI(panel, null);
    }

    private void renderUI(TertiaryPanelComponent panel, String newlyAdded) {
        panel.clearChildren();

        SlotInputPaneComponent[] inputHolder = new SlotInputPaneComponent[1];
        inputHolder[0] = new SlotInputPaneComponent("名字", this.currentValues, null, values -> {
            String name = inputHolder[0].getLeftValue().trim();
            if (values[0].isBlank() && values[1].isBlank() && values[2].isBlank()) return;
            if (!WaypointManager.saveEntry(this.editingIndex, name, values)) {
                ChatNotifier.send("§c[Waypoint] 保存失败 | 坐标无效，或已达上限", NotificationType.ERR);
                return;
            }

            this.editingIndex = -1;
            this.currentName = "";
            this.currentValues = new String[]{"", "", ""};
            int total = WaypointManager.savedEntries().size();
            String displayName = name.isBlank() ? "未命名" : name;
            ChatNotifier.send("§a[Waypoint] 已保存 | " + displayName + " (共 " + total + " 条)", NotificationType.SUCCESS);
            renderUI(panel, name);
        }, null).leftEditable("名字", this.currentName).precision(0);

        inputHolder[0].onValuesChanged(values -> this.currentValues = values.clone());
        inputHolder[0].onLeftChanged(name -> {
            this.currentName = name;
            if (this.editingIndex < 0) return;
            List<IrcConfig.WaypointEntry> current = WaypointManager.savedEntries();
            if (this.editingIndex >= current.size()) {
                this.editingIndex = -1;
                return;
            }
            IrcConfig.WaypointEntry editing = current.get(this.editingIndex);
            if (editing == null) {
                this.editingIndex = -1;
            }
        });

        panel.child(TextInfoPaneComponent.header("名字 / X / Y / Z (Enter 保存)"));
        panel.child(inputHolder[0]);

        List<IrcConfig.WaypointEntry> list = WaypointManager.savedEntries();
        int count = list.size();
        panel.child(TextInfoPaneComponent.header("已保存坐标 (" + count + ")"));

        if (list.isEmpty()) {
            panel.child(new PanelItemComponent("暂无坐标"));
            return;
        }

        for (int index = 0; index < list.size(); index++) {
            final int entryIndex = index;
            IrcConfig.WaypointEntry entry = list.get(index);
            if (entry == null || entry.name == null) continue;

            String coords = WaypointManager.formatCoord(entry.x)
                    + ", " + WaypointManager.formatCoord(entry.y)
                    + ", " + WaypointManager.formatCoord(entry.z);
            String label = (entry.enabled ? "[显示] " : "[隐藏] ")
                    + "§f" + entry.name
                    + " §7(" + coords + ")";
            BooleanSupplier shown = () -> {
                List<IrcConfig.WaypointEntry> current = WaypointManager.savedEntries();
                return entryIndex < current.size() && current.get(entryIndex).enabled;
            };

            PanelItemComponent item = new PanelItemComponent(
                    label,
                    PanelItemComponent.ItemType.TOGGLE,
                    (btn, click) -> {
                        if (click.hasShiftDown()) {
                            btn.dismiss(() -> {
                                WaypointManager.removeAt(entryIndex);
                                this.editingIndex = -1;
                                this.currentName = "";
                                this.currentValues = new String[]{"", "", ""};
                                ChatNotifier.send("§e[Waypoint] 已删除 | " + entry.name, NotificationType.INFO);
                                renderUI(panel, null);
                            });
                        } else {
                            WaypointManager.toggleAt(entryIndex);
                        }
                    },
                    btn -> {
                        this.editingIndex = entryIndex;
                        this.currentName = entry.name;
                        this.currentValues = new String[]{
                                WaypointManager.formatCoord(entry.x),
                                WaypointManager.formatCoord(entry.y),
                                WaypointManager.formatCoord(entry.z)
                        };
                        renderUI(panel, null);
                    },
                    shown
            );
            item.withStyledTitle(() -> {
                boolean visible = shown.getAsBoolean();
                MutableComponent title = Component.literal(visible ? "[显示] " : "[隐藏] ")
                        .withStyle(style -> style.withColor(visible ? 0xFF55FF55 : 0xFFAAAAAA));
                title.append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE));
                title.append(Component.literal(" (" + coords + ")").withStyle(ChatFormatting.GRAY));
                return FontManagerHelper.apply(title);
            });

            if (newlyAdded != null && entry.name.equalsIgnoreCase(newlyAdded)) {
                item.withSpawnAnimation();
            }

            panel.child(item);
        }
    }
}
