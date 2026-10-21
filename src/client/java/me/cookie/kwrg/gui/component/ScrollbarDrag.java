package me.cookie.kwrg.gui.component;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

/**
 * Captures a left-button scrollbar gesture until release, including outside its track.
 */
public final class ScrollbarDrag {
    private boolean dragging;
    private double startY;
    private float startScroll, scroll;

    public static float thumbHeight(int trackHeight, int contentHeight, int minimum) {
        return Math.min(trackHeight, Math.max(minimum, (float) trackHeight * trackHeight / Math.max(1, contentHeight)));
    }

    public boolean begin(MouseButtonEvent click, int x, int y, int trackHeight, float thumbHeight, float offset, float maximum) {
        if (click.button() != 0 || maximum <= 0 || trackHeight <= thumbHeight
                || click.x() < x - 6 || click.x() > x + 8 || click.y() < y || click.y() > y + trackHeight) {
            return false;
        }
        float travel = trackHeight - thumbHeight;
        float thumbY = y + travel * offset / maximum;

        scroll = click.y() >= thumbY - 2 && click.y() <= thumbY + thumbHeight + 2 ? offset : Mth.clamp((float) (click.y() - y - thumbHeight / 2) / travel * maximum, 0, maximum);
        startY = click.y();
        startScroll = scroll;
        dragging = true;
        return true;
    }

    public boolean drag(MouseButtonEvent click, int trackHeight, float thumbHeight, float maximum) {
        if (!dragging || click.button() != 0) {
            return false;
        }
        if (maximum <= 0 || trackHeight <= thumbHeight) {
            cancel();
            return false;
        }
        scroll = Mth.clamp(startScroll + (float) ((click.y() - startY) / (trackHeight - thumbHeight) * maximum), 0, maximum);
        return true;
    }

    public float scroll() {
        return scroll;
    }

    public boolean dragging() {
        return dragging;
    }

    public void release(MouseButtonEvent click) {
        if (click.button() == 0) {
            cancel();
        }
    }

    public void cancel() {
        dragging = false;
    }
}
