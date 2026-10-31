package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryHudRenderer {

    private static final int COLS = 9;
    private static final int MAIN_ROWS = 3;
    private static final float SLOT_SIZE = 18.0f;
    private static final float HOTBAR_GAP = 4.0f;
    private static final float SLOT_RADIUS = 4.5f;
    private static final float FADE_SPEED = 6.0f;

    private static final float[] ISLAND_BOUNDS = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

    private static boolean editorPreview = false;
    private static boolean renderedThisFrame = false;
    private static float visibilityProgress = 0.0f;
    private static long lastFrameMs = -1L;

    private InventoryHudRenderer() {
    }

    public static void resetFrameState() {
        renderedThisFrame = false;
    }

    public static void setEditorPreview(boolean preview) {
        editorPreview = preview;
    }

    public static float[] getIslandBounds() {
        return ISLAND_BOUNDS;
    }

    public static void renderPreviewIfEmpty(GuiGraphicsExtractor graphics) {
        if (!renderedThisFrame && editorPreview) {
            render(graphics, 0.016f);
        }
    }

    public static void render(GuiGraphicsExtractor graphics, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof me.cookie.kwrg.gui.IrcScreen) return;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.INVENTORY);
        boolean enabled = state.enabled;
        if (!enabled && !editorPreview) {
            visibilityProgress = 0.0f;
            lastFrameMs = -1L;
            return;
        }

        IrcConfig config = IrcConfig.get();
        boolean followGlobal = config.inventoryHudFollowGlobal;

        // 解析缩放比例与内边距
        float scale = HudElementRegistry.resolveScale(HudElementRegistry.INVENTORY);
        int padding = followGlobal
                ? Mth.clamp(HudElementRegistry.state(HudElementRegistry.ISLAND).padding, 2, 20)
                : Mth.clamp(state.padding, 2, 20);

        boolean showHotbar = config.inventoryHudShowHotbar;
        int totalRows = showHotbar ? (MAIN_ROWS + 1) : MAIN_ROWS;
        int spacing = Mth.clamp(config.inventoryHudSlotSpacing, 0, 6);

        float unscaledW = COLS * SLOT_SIZE + (COLS - 1) * spacing + padding * 2.0f;
        float unscaledH = totalRows * SLOT_SIZE + (totalRows - 1) * spacing + (showHotbar ? HOTBAR_GAP : 0.0f) + padding * 2.0f;
        float scaledW = unscaledW * scale;
        float scaledH = unscaledH * scale;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float offsetX = HudElementRegistry.resolveOffsetX(HudElementRegistry.INVENTORY);
        float offsetY = HudElementRegistry.resolveOffsetY(HudElementRegistry.INVENTORY);

        float panelX = HudElement.Anchor.resolveX(state.anchorX, state.marginX, offsetX, scaledW, screenW);
        float panelY = HudElement.Anchor.resolveY(state.anchorY, state.marginY, offsetY, scaledH, screenH);

        ISLAND_BOUNDS[0] = panelX;
        ISLAND_BOUNDS[1] = panelY;
        ISLAND_BOUNDS[2] = scaledW;
        ISLAND_BOUNDS[3] = scaledH;

        Inventory inventory = mc.player != null ? mc.player.getInventory() : null;
        boolean hasAnyItem = hasDisplayedItems(inventory, showHotbar);

        boolean shouldShow = editorPreview || (enabled && (!config.inventoryHudHideEmpty || hasAnyItem));
        updateVisibility(shouldShow);

        if (visibilityProgress <= 0.005f && !editorPreview) {
            return;
        }

        renderedThisFrame = true;
        float alphaFactor = editorPreview ? 1.0f : AnimationUtil.easeOutCubic(visibilityProgress);

        // 2. 解析面板背景透明度与槽位格独立透明度
        float basePanelOpacity = followGlobal
                ? Mth.clamp(GuiThemeService.opacity(), 0.0f, 1.0f)
                : Mth.clamp(Float.isFinite(config.inventoryHudOpacity) ? config.inventoryHudOpacity : state.opacity, 0.0f, 1.0f);

        float baseSlotOpacity = followGlobal
                ? Mth.clamp(GuiThemeService.opacity() * 0.75f, 0.0f, 1.0f)
                : Mth.clamp(Float.isFinite(config.inventoryHudSlotOpacity) ? config.inventoryHudSlotOpacity : 0.65f, 0.0f, 1.0f);

        float panelOpacity = basePanelOpacity * alphaFactor;
        float slotOpacity = baseSlotOpacity * alphaFactor;

        OwoUIGraphics owo = OwoUIGraphics.of(graphics);
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX, panelY);
        graphics.pose().scale(scale, scale);

        drawBackdropAndSlots(
                owo, graphics, mc, inventory, config,
                0.0f, 0.0f, unscaledW, unscaledH,
                padding, spacing, showHotbar, followGlobal,
                hasAnyItem, panelOpacity, slotOpacity, alphaFactor
        );

        graphics.pose().popMatrix();
    }

    private static void updateVisibility(boolean shouldShow) {
        long now = System.currentTimeMillis();
        float dt = lastFrameMs < 0L ? 0.016f : Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;

        float target = shouldShow ? 1.0f : 0.0f;
        if (visibilityProgress < target) {
            visibilityProgress = Math.min(target, visibilityProgress + dt * FADE_SPEED);
        } else if (visibilityProgress > target) {
            visibilityProgress = Math.max(target, visibilityProgress - dt * FADE_SPEED);
        }
    }

    private static boolean hasDisplayedItems(Inventory inventory, boolean includeHotbar) {
        if (inventory == null) return false;
        for (int slot = 9; slot < 36; slot++) {
            if (!inventory.getItem(slot).isEmpty()) return true;
        }
        if (includeHotbar) {
            for (int slot = 0; slot < 9; slot++) {
                if (!inventory.getItem(slot).isEmpty()) return true;
            }
        }
        return false;
    }

    private static void drawBackdropAndSlots(
            OwoUIGraphics owo,
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            Inventory inventory,
            IrcConfig config,
            float panelX, float panelY, float totalW, float totalH,
            int padding, int spacing, boolean showHotbar, boolean followGlobal,
            boolean hasAnyItem, float panelOpacity, float slotOpacity, float contentAlpha
    ) {
        boolean followTheme = followGlobal || config.inventoryHudFollowTheme;
        float cornerRadius = Mth.clamp(config.inventoryHudCornerRadius, 2.0f, 18.0f);

        int bgRgb = followTheme ? GuiThemeService.panelBg() : 0x101620;
        int borderRgb = followTheme ? GuiThemeService.panelOutline() : 0x4FC3F7;
        int panelAlpha = Math.round(panelOpacity * 225.0f);
        int borderAlpha = Math.round(panelOpacity * (followTheme ? 140.0f : 96.0f));

        if (panelAlpha > 2) {
            Color fill = AnimationUtil.withAlpha(bgRgb, panelAlpha);
            Color outline = borderAlpha > 2 ? AnimationUtil.withAlpha(borderRgb, borderAlpha) : null;
            RenderUtil.drawSubpixelRoundedPanel(owo, panelX, panelY, totalW, totalH, cornerRadius, 1.0f, fill, outline);
        }

        // 快捷栏分隔线
        if (showHotbar && contentAlpha > 0.05f) {
            float sepY = panelY + padding + MAIN_ROWS * SLOT_SIZE + (MAIN_ROWS - 1) * spacing + (HOTBAR_GAP * 0.5f) - 0.5f;
            int sepRgb = followTheme ? GuiThemeService.cardBorder() : 0x4FC3F7;
            int sepAlpha = Math.round(Math.max(panelOpacity, slotOpacity) * 95.0f);
            if (sepAlpha > 2) {
                owo.fill(
                        Math.round(panelX + padding + 2.0f),
                        Math.round(sepY),
                        Math.round(panelX + totalW - padding - 2.0f),
                        Math.round(sepY + 1.0f),
                        AnimationUtil.argb(sepRgb, sepAlpha)
                );
            }
        }

        boolean usePreviewItems = editorPreview && !hasAnyItem;
        int selectedHotbarSlot = inventory != null ? inventory.getSelectedSlot() : 0;

        int slotBgRgb = followTheme ? GuiThemeService.cardBg() : 0x182230;
        int slotOccupiedRgb = followTheme ? GuiThemeService.listRowBg() : 0x223246;
        int slotBorderRgb = followTheme ? GuiThemeService.cardBorder() : 0x394A5A;
        int accentRgb = GuiThemeService.accent();

        int totalRows = showHotbar ? (MAIN_ROWS + 1) : MAIN_ROWS;
        for (int row = 0; row < totalRows; row++) {
            boolean isHotbarRow = (row == MAIN_ROWS);
            float rowY = panelY + padding + row * (SLOT_SIZE + spacing) + (isHotbarRow ? HOTBAR_GAP : 0.0f);

            for (int col = 0; col < COLS; col++) {
                float slotX = panelX + padding + col * (SLOT_SIZE + spacing);
                int slotIndex = isHotbarRow ? col : (9 + row * COLS + col);

                ItemStack stack = resolveSlotStack(mc, inventory, slotIndex, usePreviewItems);
                boolean occupied = !stack.isEmpty();
                boolean isSelectedHotbar = isHotbarRow && col == selectedHotbarSlot;

                if (config.inventoryHudShowSlotBg && slotOpacity > 0.005f) {
                    int cellRgb = occupied ? slotOccupiedRgb : slotBgRgb;
                    int cellAlpha = Math.round(slotOpacity * (occupied ? 210.0f : 155.0f));
                    Color cellFill = AnimationUtil.withAlpha(cellRgb, cellAlpha);

                    Color cellOutline = null;
                    if (isSelectedHotbar) {
                        cellOutline = AnimationUtil.withAlpha(accentRgb, Math.round(contentAlpha * 225.0f));
                    } else if (occupied) {
                        cellOutline = AnimationUtil.withAlpha(slotBorderRgb, Math.round(slotOpacity * 140.0f));
                    }

                    RenderUtil.drawSubpixelRoundedPanel(
                            owo, slotX, rowY, SLOT_SIZE, SLOT_SIZE,
                            SLOT_RADIUS, isSelectedHotbar ? 1.3f : 0.8f,
                            cellFill, cellOutline
                    );
                } else if (isSelectedHotbar) {
                    Color cellOutline = AnimationUtil.withAlpha(accentRgb, Math.round(contentAlpha * 210.0f));
                    RenderUtil.drawSubpixelRoundedPanel(
                            owo, slotX, rowY, SLOT_SIZE, SLOT_SIZE,
                            SLOT_RADIUS, 1.2f,
                            AnimationUtil.withAlpha(accentRgb, Math.round(contentAlpha * 36.0f)),
                            cellOutline
                    );
                }

                if (occupied) {
                    int itemX = Math.round(slotX + (SLOT_SIZE - 16.0f) * 0.5f);
                    int itemY = Math.round(rowY + (SLOT_SIZE - 16.0f) * 0.5f);
                    graphics.item(stack, itemX, itemY);
                    graphics.itemDecorations(mc.font, stack, itemX, itemY);
                } else if (usePreviewItems && isPreviewSlot(slotIndex)) {
                    drawFallbackPreviewBadge(owo, slotX, rowY, slotIndex, accentRgb, contentAlpha);
                }
            }
        }
    }

    private static ItemStack resolveSlotStack(Minecraft mc, Inventory inventory, int slotIndex, boolean usePreviewItems) {
        if (inventory != null && !usePreviewItems) {
            return inventory.getItem(slotIndex);
        }
        if (usePreviewItems && mc.level != null) {
            try {
                return switch (slotIndex) {
                    case 9 -> new ItemStack(Items.DIAMOND_SWORD);
                    case 10 -> new ItemStack(Items.BOW);
                    case 11 -> new ItemStack(Items.ARROW, 64);
                    case 13 -> new ItemStack(Items.GOLDEN_APPLE, 12);
                    case 17 -> new ItemStack(Items.TOTEM_OF_UNDYING);
                    case 18 -> new ItemStack(Items.ENDER_PEARL, 16);
                    case 22 -> new ItemStack(Items.FIREWORK_ROCKET, 32);
                    case 26 -> new ItemStack(Items.WIND_CHARGE, 24);
                    case 27 -> new ItemStack(Items.COBBLESTONE, 64);
                    case 28 -> new ItemStack(Items.OAK_PLANKS, 48);
                    case 35 -> new ItemStack(Items.WATER_BUCKET);
                    case 0 -> new ItemStack(Items.NETHERITE_SWORD);
                    case 1 -> new ItemStack(Items.ENDER_PEARL, 16);
                    case 8 -> new ItemStack(Items.COOKED_BEEF, 64);
                    default -> ItemStack.EMPTY;
                };
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPreviewSlot(int slotIndex) {
        return slotIndex == 9 || slotIndex == 10 || slotIndex == 11 || slotIndex == 13
                || slotIndex == 17 || slotIndex == 18 || slotIndex == 22 || slotIndex == 27 || slotIndex == 0;
    }

    private static void drawFallbackPreviewBadge(OwoUIGraphics owo, float slotX, float slotY, int slotIndex, int accentRgb, float alpha) {
        Color dotFill = AnimationUtil.withAlpha(accentRgb, Math.round(alpha * 70.0f));
        Color dotBorder = AnimationUtil.withAlpha(accentRgb, Math.round(alpha * 150.0f));
        RenderUtil.drawSubpixelRoundedPanel(owo, slotX + 3.0f, slotY + 3.0f, SLOT_SIZE - 6.0f, SLOT_SIZE - 6.0f, 3.0f, 0.8f, dotFill, dotBorder);
        if (slotIndex == 11 || slotIndex == 13 || slotIndex == 18) {
            RenderUtil.drawFluidSubpixelText(owo, Component.literal("64"), slotX + 5.5f, slotY + 8.5f, AnimationUtil.argb(0xFFFFFF, Math.round(alpha * 220.0f)));
        }
    }
}
