package me.cookie.kwrg.module.modules.play;

import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TicTacToeOverlayComponent;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;

import java.util.function.Consumer;

public class TicTacToeModule extends Module {

    public TicTacToeModule() {
        super("tic tac toe", Category.PLAY, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        IrcScreen screen = IrcScreen.getCurrentScreen();
        if (screen != null) {
            screen.showOverlay(new TicTacToeOverlayComponent());
        }
    }

    @Override
    public PanelItemComponent createPanelItem(Consumer<PanelItemComponent> openTertiaryCallback) {
        return new PanelItemComponent(this.getName(), this.getType(),
                (item, click) -> this.onExecute(),
                item -> this.onExecute(),
                this::isActive);
    }
}