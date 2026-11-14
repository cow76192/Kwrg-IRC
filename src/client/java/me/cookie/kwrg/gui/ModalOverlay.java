package me.cookie.kwrg.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

public interface ModalOverlay {

    float progress();

    boolean isFinished();

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int width, int height);

    void mouseClicked(MouseButtonEvent click, boolean doubled);

    void mouseReleased(MouseButtonEvent click);

    void mouseDragged(MouseButtonEvent click, double deltaX, double deltaY);

    void mouseScrolled(double x, double y, double amount);

    void keyPressed(KeyEvent input);

    void charTyped(CharacterEvent input);

    boolean preeditUpdated(PreeditEvent input);

    boolean flush();
}
