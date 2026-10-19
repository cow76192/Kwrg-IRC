package me.cookie.kwrg.gui.component.route;

import me.cookie.kwrg.gui.util.AnimationUtil;

/** Starts on the first visible frame and survives list refreshes. */
public final class RouteNodeAnimation {
    private long startedAt = -1;
    private long removedAt = -1;
    private float removedFrom;

    public void remove(long now) {
        if (removedAt >= 0) return;
        removedFrom = progress(now);
        removedAt = now;
    }

    public boolean removing() { return removedAt >= 0; }
    public boolean finished(long now) { return removing() && now - removedAt >= 250; }

    public float remaining(long now) {
        return removing() ? 1 - AnimationUtil.easeOutCubic((now - removedAt) / 250.0f) : 1;
    }

    public float progress(long now) {
        if (startedAt < 0) startedAt = now;
        return removing() ? removedFrom * remaining(now) : AnimationUtil.easeOutCubic((now - startedAt) / 250.0f);
    }
}
