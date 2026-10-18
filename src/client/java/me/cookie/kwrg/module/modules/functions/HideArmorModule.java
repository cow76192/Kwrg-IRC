package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.Map;

public class HideArmorModule extends Module {

    public static final Map<String, EquipmentSlot> PARTS = Map.of(
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET
    );

    public HideArmorModule() {
        super("hideArmor", Category.FUNCTIONS, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().hideArmor);
    }

    @Override
    public void onExecute() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            setHideArmor(!IrcConfig.get().hideArmor);
        }
    }

    public static int setHideArmor(boolean enabled) {
        IrcConfig config = IrcConfig.get();
        config.hideArmor = enabled;

        if (enabled && !HideArmorManager.hasAnyPartHidden()) {
            PARTS.values().forEach(slot -> HideArmorManager.setPartHidden(slot, true));
        }

        IrcConfig.save();
        IrcClient.sendHideArmorSync();
        ChatNotifier.send("§a[IRC] hideArmor 已" + (enabled ? "开启" : "关闭"), NotificationType.INFO);
        return 1;
    }

    public static int setHideArmorPart(EquipmentSlot slot, boolean hidden) {
        IrcConfig config = IrcConfig.get();
        HideArmorManager.setPartHidden(slot, hidden);

        if (hidden) {
            config.hideArmor = true;
        } else if (!HideArmorManager.hasAnyPartHidden()) {
            config.hideArmor = false;
        }

        IrcConfig.save();
        IrcClient.sendHideArmorSync();
        ChatNotifier.send("§a[IRC] hideArmor " + slotName(slot) + (hidden ? " 已隐藏" : " 已显示"), NotificationType.INFO);
        return 1;
    }

    public static int showHideArmor() {
        IrcConfig config = IrcConfig.get();
        StringBuilder status = new StringBuilder("§6[IRC] hideArmor: ").append(config.hideArmor ? "§a开启" : "§c关闭");

        PARTS.values().forEach(slot -> status.append(" §7| ").append(slotName(slot))
                .append(HideArmorManager.isPartHidden(slot) ? " §c隐藏" : " §a显示"));

        ChatNotifier.sendInChatScreen(status.toString());
        ChatNotifier.sendInChatScreen("§7用法: /irc functions hideArmor [true|false | <head|chest|legs|feet> <true|false>]");
        return 1;
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();
        panel.child(TextInfoPaneComponent.header("隐藏部位"));

        List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                .forEach(slot -> panel.child(new PanelItemComponent(
                        slotName(slot),
                        PanelItemComponent.ItemType.TOGGLE,
                        () -> setHideArmorPart(slot, !HideArmorManager.isPartHidden(slot)),
                        null,
                        () -> HideArmorManager.isPartHidden(slot)
                )));
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "头盔";
            case CHEST -> "胸甲";
            case LEGS -> "护腿";
            case FEET -> "靴子";
            default -> "该部位";
        };
    }
}
