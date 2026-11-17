package me.cookie.kwrg.window.input;

/** Shares press edges between callbacks and physical-state polling. */
public final class WindowControlInput {
    private boolean gDown, rightDown;

    public void synchronize(boolean g, boolean right) {
        gDown = g;
        rightDown = right;
    }

    public boolean shortcut(boolean pressed, boolean alt) {
        boolean edge = pressed && !gDown && alt;
        gDown = pressed;
        return edge;
    }

    public boolean rightButton(boolean pressed) {
        boolean edge = pressed && !rightDown;
        rightDown = pressed;
        return edge;
    }
}
