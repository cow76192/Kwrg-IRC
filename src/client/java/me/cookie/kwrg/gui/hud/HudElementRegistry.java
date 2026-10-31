package me.cookie.kwrg.gui.hud;

import me.cookie.kwrg.gui.notification.NotificationManager;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import me.cookie.kwrg.render.InventoryHudRenderer;
import me.cookie.kwrg.render.PlayerDollHudRenderer;
import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudElementRegistry {

    public static final String ISLAND = "dynamicIsland";
    public static final String NOTIFICATIONS = "notifications";
    public static final String SCOREBOARD = "scoreboard";
    public static final String INVENTORY = "inventoryHud";
    public static final String PLAYER_DOLL = "playerDollHud";

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
        HudElement.State islandDefault = HudElement.State.topCenter();
        islandDefault.enabled = false;
        islandDefault.opacity = 0.2f;

        // The top status bar and expanded Tab panel share this element and do not support scaling.
        register(new HudElement(ISLAND, "灵动岛", islandDefault, false)
                .enabled(() -> state(ISLAND).enabled, value -> {
                    state(ISLAND).enabled = value;
                    IrcConfig.save();
                })
                .bounds(DynamicIslandRenderer::getIslandBounds)
                .preview(DynamicIslandRenderer::setEditorPreview));

        register(new HudElement(NOTIFICATIONS, "通知堆叠", HudElement.State.bottomRight(16.0f, 20.0f), false)
                .enabled(() -> state(NOTIFICATIONS).enabled, value -> {
                    state(NOTIFICATIONS).enabled = value;
                    IrcConfig.save();
                })
                .bounds(NotificationManager::getGroupBounds)
                .preview(NotificationManager::setEditorPreview));

        HudElement.State scoreboardDefault = HudElement.State.rightMiddle(3.0f);
        scoreboardDefault.enabled = false;
        scoreboardDefault.opacity = 1.0f;
        scoreboardDefault.padding = 6;

        register(new HudElement(SCOREBOARD, "DynamicScoreboard", scoreboardDefault)
                .enabled(() -> state(SCOREBOARD).enabled, value -> {
                    state(SCOREBOARD).enabled = value;
                    IrcConfig.save();
                })
                .bounds(ScoreboardIslandRenderer::getIslandBounds)
                .preview(ScoreboardIslandRenderer::setEditorPreview));

        HudElement.State inventoryDefault = new HudElement.State();
        inventoryDefault.anchorX = HudElement.Anchor.LEFT;
        inventoryDefault.anchorY = HudElement.Anchor.TOP;
        inventoryDefault.marginX = 10.0f;
        inventoryDefault.marginY = 10.0f;
        inventoryDefault.enabled = false;
        inventoryDefault.opacity = 0.75f;
        inventoryDefault.padding = 6;

        register(new HudElement(INVENTORY, "背包显示", inventoryDefault)
                .followGlobal(() -> IrcConfig.get().inventoryHudFollowGlobal,
                        value -> IrcConfig.get().inventoryHudFollowGlobal = value)
                .enabled(() -> state(INVENTORY).enabled, value -> {
                    state(INVENTORY).enabled = value;
                    IrcConfig.save();
                })
                .bounds(InventoryHudRenderer::getIslandBounds)
                .preview(InventoryHudRenderer::setEditorPreview));

        HudElement.State playerDollDefault = new HudElement.State();
        playerDollDefault.anchorX = HudElement.Anchor.LEFT;
        playerDollDefault.anchorY = HudElement.Anchor.BOTTOM;
        playerDollDefault.marginX = 18.0f;
        playerDollDefault.marginY = 22.0f;
        playerDollDefault.enabled = false;
        playerDollDefault.opacity = 1.0f;

        register(new HudElement(PLAYER_DOLL, "玩家模型", playerDollDefault)
                .followGlobal(() -> IrcConfig.get().playerDollFollowGlobal,
                        value -> IrcConfig.get().playerDollFollowGlobal = value)
                .enabled(() -> state(PLAYER_DOLL).enabled, value -> {
                    state(PLAYER_DOLL).enabled = value;
                    IrcConfig.save();
                })
                .bounds(PlayerDollHudRenderer::getDollBounds)
                .preview(PlayerDollHudRenderer::setEditorPreview));
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

    public static float resolveScale(String id) {
        HudElement element = get(id);
        if (element != null && !element.supportsScale()) return 1.0f;
        if (element != null && element.followsGlobal()) {
            float scale = IrcConfig.get().guiScale;
            return Mth.clamp(Float.isFinite(scale) ? scale : 1.0f,
                    HudElement.State.MIN_SCALE, HudElement.State.MAX_SCALE);
        }
        return state(id).scale;
    }

    public static boolean isEnabled(String id) {
        HudElement element = ELEMENTS.get(id);
        return element != null && element.isEnabled();
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

        float previousScale = resolveScale(id);
        applyDefaults(current, defaults);
        current.sanitize();

        // Bounds still describe the previous frame. Rebase the animation using the size
        // that the renderer will use after resetting, including any inherited global scale.
        float scaleRatio = resolveScale(id) / previousScale;
        float resetWidth = bounds[2] * scaleRatio;
        float resetHeight = bounds[3] * scaleRatio;
        float baseX = HudElement.Anchor.resolveX(current.anchorX, current.marginX, 0.0f, resetWidth, window.getGuiScaledWidth());
        float baseY = HudElement.Anchor.resolveY(current.anchorY, current.marginY, 0.0f, resetHeight, window.getGuiScaledHeight());

        float fromX = bounds[0] - baseX;
        float fromY = bounds[1] - baseY;

        RESET_ANIMATIONS.put(id, new ResetAnimation(fromX, fromY, System.currentTimeMillis()));
        IrcConfig.save();
    }

    private static void applyDefaults(HudElement.State current, HudElement.State defaults) {
        current.anchorX = defaults.anchorX;
        current.anchorY = defaults.anchorY;
        current.marginX = defaults.marginX;
        current.marginY = defaults.marginY;
        current.offsetX = 0.0f;
        current.offsetY = 0.0f;
        current.scale = defaults.scale;
    }

    public static void resetAll() {
        for (HudElement element : all()) {
            reset(element.id());
        }
    }

    public static void cancelResetAnimation(String id) {
        RESET_ANIMATIONS.remove(id);
    }

    public static float resolveOffsetX(String id) {
        ResetAnimation animation = RESET_ANIMATIONS.get(id);
        HudElement.State state = state(id);
        if (animation == null) return state.offsetX;

        return Mth.lerp(resetAnimEase(id, animation), animation.fromX, state.offsetX);
    }

    public static float resolveOffsetY(String id) {
        ResetAnimation animation = RESET_ANIMATIONS.get(id);
        HudElement.State state = state(id);
        if (animation == null) return state.offsetY;

        return Mth.lerp(resetAnimEase(id, animation), animation.fromY, state.offsetY);
    }

    private static float resetAnimEase(String id, ResetAnimation animation) {
        float raw = Mth.clamp((System.currentTimeMillis() - animation.startMs) / (float) RESET_ANIM_DURATION_MS, 0.0f, 1.0f);
        if (raw >= 1.0f) {
            RESET_ANIMATIONS.remove(id);
            return 1.0f;
        }
        return AnimationUtil.easeOutCubic(raw);
    }

    public static void setEditorPreview(boolean enabled) {
        editorPreview = enabled;
        for (HudElement element : all()) {
            element.setEditorPreview(enabled);
        }
    }
}
