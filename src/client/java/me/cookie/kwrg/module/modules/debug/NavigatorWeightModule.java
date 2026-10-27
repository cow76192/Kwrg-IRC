package me.cookie.kwrg.module.modules.debug;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.Navigator;
import net.minecraft.util.Mth;

import java.util.Locale;

public class NavigatorWeightModule extends Module {

    private static final float MIN_WEIGHT = 0.8f;
    private static final float MAX_WEIGHT = 2.2f;

    public NavigatorWeightModule() {
        super("navigatorWeight", Category.DEBUG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        ChatNotifier.sendInChatScreen("§6[IRC] 寻路启发式权重: §f" + format(IrcConfig.get().navigatorWeight) + " §7(右键调节)");
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("寻路启发式权重 (1.0 = 最稳定解)"));
        panel.child(new SlotInputPaneComponent(
                "权重 w",
                new String[]{format(IrcConfig.get().navigatorWeight)},
                new String[]{""},
                values -> apply(values[0], true),
                values -> apply(values[0], false)
        ).bounds(new double[][]{{MIN_WEIGHT, MAX_WEIGHT}}).dragStep(0.05).precision(2).dragSensitivity(6.0));
    }

    private static String format(float weight) {
        return String.format(Locale.ROOT, "%.2f", weight);
    }

    private static void apply(String raw, boolean notify) {
        float weight;
        try {
            weight = Mth.clamp(Float.parseFloat(raw.trim()), MIN_WEIGHT, MAX_WEIGHT);
        } catch (NumberFormatException e) {
            if (notify) ChatNotifier.send("§c请输入 0.8 ~ 2.2 之间的数字", NotificationType.WARN);
            return;
        }
        IrcConfig config = IrcConfig.get();
        config.navigatorWeight = weight;
        IrcConfig.save();
        Navigator.setHeuristicWeight(weight);
        if (notify) ChatNotifier.send("§a寻路启发式权重: §e" + format(weight), NotificationType.SUCCESS);
    }
}
