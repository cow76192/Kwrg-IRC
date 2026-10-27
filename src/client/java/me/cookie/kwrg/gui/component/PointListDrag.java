package me.cookie.kwrg.gui.component;

/**
 * Long-press capture shared by point editors; coordinates are in screen pixels.
 */
public final class PointListDrag<T> {
    public static final long HOLD_MS = 50;
    private T item;
    private int originalIndex;
    private long pressedAt;
    private double pressedX, pressedY, mouseX, mouseY, offsetX, offsetY;
    private boolean holding;

    public void press(T item, int index, double mouseX, double mouseY, double rowX, double rowY, long now) {
        this.item = item;
        originalIndex = index;
        pressedAt = now;
        pressedX = this.mouseX = mouseX;
        pressedY = this.mouseY = mouseY;
        offsetX = mouseX - rowX;
        offsetY = mouseY - rowY;
        holding = false;
    }

    /**
     * Movement before the hold threshold leaves the original click gesture alone.
     */
    public boolean update(double x, double y, long now) {
        mouseX = x;
        mouseY = y;

        if (item == null) return false;

        if (!holding && now - pressedAt < HOLD_MS && Math.hypot(x - pressedX, y - pressedY) > 4) {
            clear();
            return false;
        }

        if (!holding && now - pressedAt >= HOLD_MS) {
            holding = true;
            return true;
        }

        return false;
    }

    public T item() {
        return item;
    }

    public boolean holding() {
        return holding;
    }

    public int originalIndex() {
        return originalIndex;
    }

    public boolean moved() {
        return Math.hypot(mouseX - pressedX, mouseY - pressedY) > 4;
    }

    public float x() {
        return (float) (mouseX - offsetX);
    }

    public float y() {
        return (float) (mouseY - offsetY);
    }

    public long heldFor(long now) {
        return Math.max(0, now - pressedAt - HOLD_MS);
    }

    public int insertionIndex(int contentY, int scroll, int rowHeight, int size) {
        // Compare the held row's center with the fixed slots, not the animated neighbours.
        return Math.clamp((int) Math.floor((y() + rowHeight / 2f - contentY + scroll) / rowHeight), 0, size - 1);
    }

    public void clear() {
        item = null;
        holding = false;
    }
}
