package me.cookie.kwrg.gui.title;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import me.cookie.kwrg.render.LoadingOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class TitleMenuUi {
    private static final long CLOCK_START = Util.getMillis();
    private static final Component TITLE = Component.translatable("kwrg.title.name");
    private static final Component SUBTITLE = Component.translatable("kwrg.title.subtitle");
    private static TitleMenuUi drawing;

    private final Map<AbstractButton, Slot> slots = new IdentityHashMap<>();
    private final List<AbstractButton> actions = new ArrayList<>();
    private final List<AbstractButton> shortcuts = new ArrayList<>();
    private long lastFrame;
    private float pointerX;
    private float pointerY;
    private float orbit;
    private float solo;
    private float network;
    private float settings;
    private float quit;
    private float reveal;
    private float typing;
    private float time;

    public void begin(TitleScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, TitleBrowser browser,
                      TitleModeTransition mode, boolean customUi) {
        Minecraft game = Minecraft.getInstance();

        long now = Util.getMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.clamp((now - lastFrame) * 0.001f, 0.0f, 0.05f);
        lastFrame = now;
        time = (now - CLOCK_START) * 0.001f;

        float startupReveal = LoadingOverlayRenderer.titleReveal();
        reveal = startupReveal * browser.menuVisibility() * mode.customUi();

        if (startupReveal > 0.02f) {
            typing += dt;
        } else {
            typing = 0.0f;
        }

        Theme selected = customUi ? layout(screen, graphics, mouseX, mouseY, dt) : Theme.IDLE;
        float blend = 1.0f - (float) Math.exp(-6.5f * dt);
        solo = Mth.lerp(blend, solo, selected == Theme.SOLO ? 1.0f : 0.0f);
        network = Mth.lerp(blend, network, selected == Theme.NETWORK ? 1.0f : 0.0f);
        settings = Mth.lerp(blend, settings, selected == Theme.SETTINGS ? 1.0f : 0.0f);
        quit = Mth.lerp(blend, quit, selected == Theme.QUIT ? 1.0f : 0.0f);

        int width = screen.width;
        int height = screen.height;
        if (!mode.switching() || mode.classic()) {
            orbit += network * dt * 0.27f;
            var window = game.getWindow();
            double x = game.mouseHandler.getScaledXPos(window);
            double y = game.mouseHandler.getScaledYPos(window);
            pointerX = Mth.lerp(blend, pointerX, (float) Math.clamp(2.0 * x / width - 1.0, -1.0, 1.0));
            pointerY = Mth.lerp(blend, pointerY, (float) Math.clamp(1.0 - 2.0 * y / height, -1.0, 1.0));
        }

        float compact = Math.clamp((1.48f - (float) width / height) * 1.8f, 0.0f, 1.0f);
        LoadingOverlayRenderer.submitTitle(new LoadingOverlayRenderer.TitleFrame(time, pointerX, pointerY, orbit,
                compact, solo, network, settings, quit, browser.scene(), mode.scene()));
    }

    private Theme layout(TitleScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
        actions.clear();
        shortcuts.clear();

        var children = screen.children();
        slots.keySet().removeIf(button -> !children.contains(button));

        for (var child : children) {
            if (child instanceof AbstractButton button && !(button instanceof TitleModeButton)
                    && button.visible && !isCopyright(button, screen.height)) {
                if (button instanceof SpriteIconButton) {
                    shortcuts.add(button);
                } else {
                    actions.add(button);
                }
                slots.computeIfAbsent(button, Slot::new);
            }
        }

        int width = screen.width;
        int height = screen.height;
        int left = TitleBrowser.left(width);
        int panelWidth = Math.max(62, Math.min(208, Math.round(width * 0.39f)));
        int header = Math.max(11, Math.round(height * 0.13f));
        int top = header + 57;
        int foot = shortcuts.isEmpty() ? 19 : 44;

        if (top + actions.size() * 25 + foot > height) {
            header = Math.max(8, Math.min(header, height / 10));
            top = header + 47;
        }

        int rowStep = Math.clamp((height - top - foot) / Math.max(1, actions.size()), 13, 28);
        int rowHeight = Math.min(22, rowStep - 3);

        if (top + rowStep * actions.size() + foot > height) {
            top = Math.max(20, height - foot - rowStep * actions.size());
        }

        float ease = 1.0f - (float) Math.exp(-12.0f * dt);

        Theme selected = Theme.IDLE;
        Theme focused = Theme.IDLE;
        for (int i = 0; i < actions.size(); i++) {
            AbstractButton button = actions.get(i);
            Slot slot = slots.get(button);
            int y = top + i * rowStep;

            Theme theme = theme(button.getMessage());

            boolean hover = reveal > 0.05f && button.isActive() && contains(mouseX, mouseY, left, y, panelWidth + Math.round(6.0f * slot.hover), rowHeight);
            boolean focus = reveal > 0.05f && button.isActive() && button.isFocused();

            if (hover) selected = theme;
            if (focus) focused = theme;

            slot.update(button, left, y, panelWidth, rowHeight, hover || focus, ease, theme, false);
        }

        int shortcutY = Math.min(height - 36, top + actions.size() * rowStep + 6);
        for (int i = 0; i < shortcuts.size(); i++) {
            AbstractButton button = shortcuts.get(i);
            int x = left + i * 29;

            boolean hover = reveal > 0.05f && button.isActive() && contains(mouseX, mouseY, x, shortcutY, 25, 18);
            boolean focus = reveal > 0.05f && button.isActive() && button.isFocused();

            if (hover) selected = Theme.SETTINGS;
            if (focus) focused = Theme.SETTINGS;

            slots.get(button).update(button, x, shortcutY, 25, 18, hover || focus, ease, Theme.SETTINGS, true);
        }

        if (reveal > 0.001f) {
            Minecraft game = Minecraft.getInstance();
            drawHeader(graphics, game.font, left, header, top, panelWidth, game.options.hideSplashTexts().get());
        }
        return selected == Theme.IDLE ? focused : selected;
    }

    public TitleMenuUi activate() {
        TitleMenuUi previous = drawing;
        drawing = this;
        return previous;
    }

    public static void restore(TitleMenuUi previous) {
        drawing = previous;
    }

    public void restoreBounds() {
        slots.forEach((button, slot) -> button.setRectangle(slot.originalWidth, slot.originalHeight, slot.originalX, slot.originalY));
        slots.clear();
    }

    public static boolean drawButton(AbstractButton button, GuiGraphicsExtractor graphics) {
        TitleMenuUi menu = drawing;
        if (menu == null) return false;
        Slot slot = menu.slots.get(button);
        if (slot == null) return false;
        if (menu.reveal > 0.001f) menu.drawButton(button, graphics, slot);
        return true;
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Font font, int x, int y, int buttonTop, int width, boolean hideSubtitle) {
        int accent = 0x82E8E7;
        graphics.fill(x, y, x + 15, y + 1, color(accent, reveal * 0.9f));
        graphics.fill(x + 18, y, x + 22, y + 1, color(accent, reveal * 0.35f));
        graphics.text(font, "KWRG  /  MINECRAFT", x, y + 5, color(0x89B4C8, reveal * 0.92f), false);

        float scale = Math.min(2.55f, (float) width / Math.max(1, font.width(TITLE)));
        var pose = graphics.pose();
        pose.pushMatrix().translate(x, y + 17).scale(scale);

        graphics.text(font, TITLE, 0, 1, color(0x2C8498, reveal * 0.48f), false);
        graphics.text(font, TITLE, 0, 0, color(0xEAFBFF, reveal), false);
        pose.popMatrix();

        int subtitleY = y + 19 + Math.round(9.0f * scale);
        if (!hideSubtitle && subtitleY + 10 < buttonTop) {
            String caption = SUBTITLE.getString();
            int count = Math.min(caption.codePointCount(0, caption.length()), (int) (typing * 30));
            String text = caption.substring(0, caption.offsetByCodePoints(0, count));

            graphics.text(font, text, x, subtitleY, color(accent, reveal * 0.78f), false);
            if (font.width(text) + 4 <= width && ((int) (time * 2.4f) & 1) == 0) {
                int cursor = x + font.width(text) + 2;
                graphics.fill(cursor, subtitleY + 1, cursor + 1, subtitleY + 8, color(accent, reveal * 0.55f));
            }
        }
    }

    private void drawButton(AbstractButton button, GuiGraphicsExtractor graphics, Slot slot) {
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

        float hover = slot.hover;
        float alpha = reveal * (button.isActive() ? 1.0f : 0.45f);
        int accent = slot.theme.color;

        graphics.fillGradient(x, y, x + w, y + h, color(0x183048, alpha * 0.25f), color(0x081626, alpha * 0.20f));
        if (hover > 0.01f) {
            var pose = graphics.pose();
            pose.pushMatrix().translate(x + w, y).rotate((float) (Math.PI * 0.5));
            graphics.fillGradient(0, 0, h, w, color(accent, alpha * hover * 0.025f), color(accent, alpha * hover * 0.34f));
            pose.popMatrix();
            graphics.fill(x + 2, y, x + 12 + Math.round(15.0f * hover), y + 1, color(accent, alpha * hover * 0.74f));

            for (int spark = 0; spark < 4; spark++) {
                float age = (time * (0.67f + spark * 0.11f) + spark * 0.27f) % 1.0f;
                int px = x + w - 12 + spark * 3 + Math.round(age * 10);
                int py = y + h / 2 - 2 + spark * 2 - Math.round(age * 5);

                graphics.fill(px, py, px + 1, py + 1, color(accent, alpha * hover * (1.0f - age) * 0.8f));
            }
        }
        int inset = Math.max(1, Math.round(h * (0.27f - 0.18f * hover)));
        graphics.fill(x, y + inset, x + 2, y + h - inset, color(accent, alpha * (0.42f + 0.58f * hover)));

        Font font = Minecraft.getInstance().font;
        Component text = slot.shortcut ? shortcutLabel(button.getMessage()) : button.getMessage();

        int textX = x + (slot.shortcut ? 5 : 11);
        int textY = y + (h - 8) / 2;

        float scale = Math.min(1.0f, (float) (w - (slot.shortcut ? 8 : 20)) / Math.max(1, font.width(text)));
        var pose = graphics.pose();
        pose.pushMatrix().translate(textX, textY).scale(scale);
        graphics.text(font, text, 0, 0, color(0xF1FCFF, alpha * (0.70f + 0.30f * hover)), false);
        pose.popMatrix();

        if (button.isHovered()) {
            graphics.requestCursor(button.isActive() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
        }
    }

    private boolean isCopyright(AbstractButton button, int height) {
        Slot slot = slots.get(button);
        int y = slot == null ? button.getY() : slot.originalY;
        return button instanceof PlainTextButton && y >= height - 13;
    }

    private static Component shortcutLabel(Component message) {
        String key = key(message);

        if (key.contains("language")) return Component.literal("LANG");
        if (key.contains("accessibility")) return Component.literal("A11Y");

        return message.getString().isBlank() ? Component.literal("···") : message;
    }

    private static Theme theme(Component message) {
        String key = key(message);
        if (key.equals("menu.singleplayer") || key.contains("demo")) return Theme.SOLO;
        if (key.equals("menu.multiplayer") || key.equals("menu.online") || key.contains("realms")) return Theme.NETWORK;
        if (key.equals("menu.quit")) return Theme.QUIT;
        if (key.contains("options") || key.contains("modmenu") || key.contains("mods")) return Theme.SETTINGS;

        return Theme.IDLE;
    }

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static int color(int rgb, float alpha) {
        return (Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f) << 24) | rgb;
    }

    private static boolean contains(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    private enum Theme {
        IDLE(0x70D8EA), SOLO(0x7AF2AD), NETWORK(0xB58DFF), SETTINGS(0xFFC27B), QUIT(0xA1ABBC);

        private final int color;

        Theme(int color) {
            this.color = color;
        }
    }

    private static final class Slot {
        private final int originalX;
        private final int originalY;
        private final int originalWidth;
        private final int originalHeight;
        private float hover;
        private Theme theme;
        private boolean shortcut;

        private Slot(AbstractButton button) {
            originalX = button.getX();
            originalY = button.getY();
            originalWidth = button.getWidth();
            originalHeight = button.getHeight();
        }

        private void update(AbstractButton button, int x, int y, int w, int h, boolean selected, float ease,
                            Theme theme, boolean shortcut) {
            hover = Mth.lerp(ease, hover, selected ? 1.0f : 0.0f);
            this.theme = theme;
            this.shortcut = shortcut;
            button.setRectangle(w, h, x + Math.round((shortcut ? 2.0f : 6.0f) * hover), y);
        }
    }
}
