package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 自动寻路的移动方式配置：AOTV 传送、游泳、梯子、跳跃。
 * 修改立即生效，正在显示的引导路径会在下一次刷新时按新设置重新计算。
 */
public class NavigatorModule extends Module {

    public NavigatorModule() {
        super("navigator", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        ChatNotifier.sendInChatScreen("§6[IRC] 寻路移动方式: §fAOTV " + state(config.navigatorAotv)
                + " §7| §f游泳 " + state(config.navigatorSwim)
                + " §7| §f梯子 " + state(config.navigatorLadder)
                + " §7| §f跳跃 " + state(config.navigatorJump) + " §7(右键配置)");
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        panel.child(TextInfoPaneComponent.header("自动寻路 · 移动方式"));

        panel.child(toggle("AOTV 传送", () -> IrcConfig.get().navigatorAotv,
                value -> IrcConfig.get().navigatorAotv = value));
        panel.child(toggle("游泳 / 涉水", () -> IrcConfig.get().navigatorSwim,
                value -> IrcConfig.get().navigatorSwim = value));
        panel.child(toggle("使用梯子", () -> IrcConfig.get().navigatorLadder,
                value -> IrcConfig.get().navigatorLadder = value));
        panel.child(toggle("跳跃", () -> IrcConfig.get().navigatorJump,
                value -> IrcConfig.get().navigatorJump = value));
    }

    private static PanelItemComponent toggle(String title, BooleanSupplier state, Consumer<Boolean> setter) {
        return new PanelItemComponent(
                title,
                PanelItemComponent.ItemType.TOGGLE,
                () -> {
                    boolean next = !state.getAsBoolean();
                    setter.accept(next);
                    IrcConfig.save();
                    IrcConfig.applyNavigatorSettings();
                    ChatNotifier.send("§6[IRC] 寻路 · " + title + " " + (next ? "§a已启用" : "§c已禁用"),
                            NotificationType.SUCCESS);
                },
                null,
                state
        );
    }

    private static String state(boolean enabled) {
        return enabled ? "§a开" : "§c关";
    }
}
