package me.cookie.kwrg.gui.hud;

import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudElementRegistry {

    public static final String ISLAND = "dynamicIsland";
    public static final String NOTIFICATIONS = "notifications";

    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
    private static final Map<String, ResetAnimation> RESET_ANIMATIONS = new LinkedHashMap<>();
    private static final long RESET_ANIM_DURATION_MS = 320L;

    private static boolean editorPreview = false;

    private static final class ResetAnimation {
        final float fromX;
        final float fromY;
        final long startMs;

        ResetAnimation(float fromX, float fromY, long startMs) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.startMs = startMs;
        }
    }

    static {
        register(new HudElement(ISLAND, "灵动岛", HudElement.State.topCenter())
                .enabled(() -> IrcConfig.get().dynamicIsland, value -> {
                    IrcConfig.get().dynamicIsland = value;
                    IrcConfig.save();
                })
                .visibility(DynamicIslandRenderer::isIslandVisible)
                .bounds(DynamicIslandRenderer::getIslandBounds)
                .preview(DynamicIslandRenderer::setEditorPreview));

        register(new HudElement(NOTIFICATIONS, "通知堆叠", HudElement.State.bottomRight(16.0f, 20.0f))
                .enabled(() -> state(NOTIFICATIONS).enabled, value -> {
                    state(NOTIFICATIONS).enabled = value;
                    IrcConfig.save();
                })
                .visibility(NotificationManager::hasToasts)
                .bounds(NotificationManager::getGroupBounds)
                .preview(NotificationManager::setEditorPreview));
    }

    private HudElementRegistry() {
    }

    public static void register(HudElement element) {
        ELEMENTS.put(element.id(), element);
    }

    public static List<HudElement> all() {
        return new ArrayList<>(ELEMENTS.values());
    }

    public static HudElement get(String id) {
        return id == null ? null : ELEMENTS.get(id);
    }

    public static HudElement.State state(String id) {
        IrcConfig config = IrcConfig.get();
        if (config.hudElements == null) {
            config.hudElements = new LinkedHashMap<>();
        }

        HudElement.State state = config.hudElements.get(id);
        if (state == null) {
            HudElement element = ELEMENTS.get(id);
            state = element != null ? element.defaultState() : new HudElement.State();
            config.hudElements.put(id, state);
        }
        state.sanitize();
        return state;
    }

    public static boolean isEnabled(String id) {
        HudElement element = ELEMENTS.get(id);
        return element == null || element.isEnabled();
    }

    public static void reset(String id) {
        HudElement element = ELEMENTS.get(id);
        if (element == null) return;

        HudElement.State current = state(id);
        HudElement.State defaults = element.defaultState();

        var window = Minecraft.getInstance().getWindow();
        float[] bounds = element.bounds();
        if (window == null || bounds == null || bounds[2] <= 0.0f || bounds[3] <= 0.0f) {
            applyDefaults(current, defaults);
            IrcConfig.save();
            return;
        }

        float baseX = HudElement.Anchor.resolveX(defaults.anchorX, defaults.marginX, 0.0f, bounds[2], window.getGuiScaledWidth());
        float baseY = HudElement.Anchor.resolveY(defaults.anchorY, defaults.marginY, 0.0f, bounds[3], window.getGuiScaledHeight());

        float fromX = bounds[0] - baseX;
        float fromY = bounds[1] - baseY;

        applyDefaults(current, defaults);
        current.offsetX = fromX;
        current.offsetY = fromY;
        current.sanitize();

        RESET_ANIMATIONS.put(id, new ResetAnimation(current.offsetX, current.offsetY, System.currentTimeMillis()));
        IrcConfig.saveDelayed(200);
    }

    private static void applyDefaults(HudElement.State current, HudElement.State defaults) {
        current.anchorX = defaults.anchorX;
        current.anchorY = defaults.anchorY;
        current.marginX = defaults.marginX;
        current.marginY = defaults.marginY;
        current.offsetX = 0.0f;
        current.offsetY = 0.0f;
    }

    public static void resetAll() {
        for (HudElement element : all()) {
            reset(element.id());
        }
    }

    public static void cancelResetAnimation(String id) {
        RESET_ANIMATIONS.remove(id);
    }

    public static void tickResetAnimations() {
        if (RESET_ANIMATIONS.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ResetAnimation>> iterator = RESET_ANIMATIONS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, ResetAnimation> entry = iterator.next();
            ResetAnimation animation = entry.getValue();

            float raw = Mth.clamp((now - animation.startMs) / (float) RESET_ANIM_DURATION_MS, 0.0f, 1.0f);
            float eased = RenderUtil.easeOutCubic(raw);

            HudElement.State state = state(entry.getKey());
            state.offsetX = Mth.lerp(eased, animation.fromX, 0.0f);
            state.offsetY = Mth.lerp(eased, animation.fromY, 0.0f);
            state.sanitize();

            if (raw >= 1.0f) {
                state.offsetX = 0.0f;
                state.offsetY = 0.0f;
                IrcConfig.saveDelayed(200);
                iterator.remove();
            }
        }
    }

    public static void setEditorPreview(boolean enabled) {
        editorPreview = enabled;
        for (HudElement element : all()) {
            element.setEditorPreview(enabled);
        }
    }

    public static boolean isEditorPreview() {
        return editorPreview;
    }
}
