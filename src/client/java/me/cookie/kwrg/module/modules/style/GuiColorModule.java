package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.*;
import me.cookie.kwrg.gui.component.pane.ColorSettingComponent;
import me.cookie.kwrg.gui.component.pane.SlotInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.manager.GuiColorManager;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class GuiColorModule extends Module {

    public GuiColorModule() {
        super("guiColor", Category.STYLE, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {
        ChatNotifier.sendInChatScreen("§6[IRC] 当前 GUI 颜色: §f" + GuiColorManager.getGuiColorStr() + " §7(右键打开调色盘)");
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        String currentColor = GuiColorManager.getGuiColorStr();
        Color initialColor = ColorUtil.parseSingleColor(currentColor);
        if (initialColor == null) initialColor = new Color(0, 242, 254);

        int initR = initialColor.getRed();
        int initG = initialColor.getGreen();
        int initB = initialColor.getBlue();

        int[] initHsl = ColorUtil.rgbToHsl(initialColor);

        ColorSettingComponent[] settingHolder = new ColorSettingComponent[1];
        SlotInputPaneComponent[] rgbPaneHolder = new SlotInputPaneComponent[1];
        SlotInputPaneComponent[] hslPaneHolder = new SlotInputPaneComponent[1];

        boolean[] isUpdating = new boolean[]{false};

        SlotInputPaneComponent customRgbPane = new SlotInputPaneComponent(
                "#RRGGBB",
                new String[]{String.valueOf(initR), String.valueOf(initG), String.valueOf(initB)},
                new String[]{"", "", ""},
                values -> onRgbChanged(values, settingHolder[0], hslPaneHolder[0], isUpdating),
                values -> onRgbChanged(values, settingHolder[0], hslPaneHolder[0], isUpdating)
        ).bounds(new int[][]{{0, 255}, {0, 255}, {0, 255}});
        rgbPaneHolder[0] = customRgbPane;

        SlotInputPaneComponent customHslPane = new SlotInputPaneComponent(
                "HSL 调色",
                new String[]{String.valueOf(initHsl[0]), String.valueOf(initHsl[1]), String.valueOf(initHsl[2])},
                new String[]{"°", "%", "%"},
                values -> onHslChanged(values, settingHolder[0], rgbPaneHolder[0], isUpdating),
                values -> onHslChanged(values, settingHolder[0], rgbPaneHolder[0], isUpdating)
        ).bounds(new int[][]{{0, 360}, {0, 100}, {0, 100}});
        hslPaneHolder[0] = customHslPane;

        ColorSettingComponent colorSetting = new ColorSettingComponent(
                currentColor,
                GuiColorManager::setGuiColor,
                pickerHex -> {
                    if (isUpdating[0]) return;
                    isUpdating[0] = true;

                    Color c = ColorUtil.parseSingleColor(pickerHex);
                    if (c != null) {
                        int r = c.getRed();
                        int g = c.getGreen();
                        int b = c.getBlue();
                        int[] hsl = ColorUtil.rgbToHsl(c);

                        if (rgbPaneHolder[0] != null) {
                            rgbPaneHolder[0].setSlotValues(new String[]{String.valueOf(r), String.valueOf(g), String.valueOf(b)});
                        }
                        if (hslPaneHolder[0] != null) {
                            hslPaneHolder[0].setSlotValues(new String[]{String.valueOf(hsl[0]), String.valueOf(hsl[1]), String.valueOf(hsl[2])});
                        }
                    }

                    isUpdating[0] = false;
                },
                customRgbPane,
                customHslPane
        );
        settingHolder[0] = colorSetting;

        panel.child(colorSetting);

        TextInfoPaneComponent resetPane = new TextInfoPaneComponent(
                "重置默认",
                () -> {
                    String current = GuiColorManager.getGuiColorStr();
                    return ColorUtil.applyStyle(current, current);
                },
                (pane, click) -> {
                    boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();

                    if (isShiftDown) {
                        GuiColorManager.resetDefault();
                        panel.clearChildren();
                        this.buildTertiaryUI(panel);
                    } else {
                        ChatNotifier.send("§e[IRC] 提示: 请按住 §fShift + 左键 §e点击以重置为默认主题颜色！", NotificationType.INFO);
                    }
                }
        );

        panel.child(resetPane);
    }

    private static void onRgbChanged(String[] values, ColorSettingComponent setting,
                                     SlotInputPaneComponent hslPane, boolean[] isUpdating) {
        if (isUpdating[0] || setting == null || values == null || values.length < 3) return;
        try {
            int r = Mth.clamp(Integer.parseInt(values[0].trim()), 0, 255);
            int g = Mth.clamp(Integer.parseInt(values[1].trim()), 0, 255);
            int b = Mth.clamp(Integer.parseInt(values[2].trim()), 0, 255);

            String hex = String.format("#%02X%02X%02X", r, g, b);

            isUpdating[0] = true;

            setting.setColorFromExternal(hex, GuiColorManager::setGuiColor);

            if (hslPane != null) {
                int[] hsl = ColorUtil.rgbToHsl(new Color(r, g, b));
                hslPane.setSlotValues(new String[]{String.valueOf(hsl[0]), String.valueOf(hsl[1]), String.valueOf(hsl[2])});
            }

            isUpdating[0] = false;
        } catch (NumberFormatException ignored) {}
    }

    private static void onHslChanged(String[] values, ColorSettingComponent setting,
                                     SlotInputPaneComponent rgbPane, boolean[] isUpdating) {
        if (isUpdating[0] || setting == null || values == null || values.length < 3) return;
        try {
            int h = Mth.clamp(Integer.parseInt(values[0].trim()), 0, 360);
            int s = Mth.clamp(Integer.parseInt(values[1].trim()), 0, 100);
            int l = Mth.clamp(Integer.parseInt(values[2].trim()), 0, 100);

            Color color = ColorUtil.parseSingleColor(String.format("hsl(%d, %d%%, %d%%)", h, s, l));
            if (color == null) return;

            String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());

            isUpdating[0] = true;

            setting.setColorFromExternal(hex, GuiColorManager::setGuiColor);

            if (rgbPane != null) {
                rgbPane.setSlotValues(new String[]{
                        String.valueOf(color.getRed()),
                        String.valueOf(color.getGreen()),
                        String.valueOf(color.getBlue())
                });
            }

            isUpdating[0] = false;
        } catch (NumberFormatException ignored) {}
    }
}