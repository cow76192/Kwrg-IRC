package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Locale;

public class ChatModule extends Module {

    private static final List<String> SOUND_OPTIONS = List.of(
            "CAT_AMBIENT_BABY",
            "CAT_PURREOW_BABY",
            "CAT_BEG_FOR_FOOD_BABY",
            "CAT_PURR_BABY",
            "CAT_STRAY_AMBIENT_BABY",
            "CAT_HISS_BABY",
            "ARROW_HIT_PLAYER"
    );

    private boolean isDropdownOpen = false;

    public ChatModule() {
        super("chat", Category.STYLE, PanelItemComponent.ItemType.TOGGLE, () -> IrcConfig.get().chat);
    }

    @Override
    public void onExecute() {
        IrcConfig config = IrcConfig.get();
        config.chat = !config.chat;
        IrcConfig.save();
        ChatNotifier.send("§a[IRC] 聊天提示音 (Chat) 已: " + (config.chat ? "§a开启" : "§c关闭"),(config.chat ? NotificationType.SUCCESS : NotificationType.ERR));
        if (config.chat) {
            playSound(config.chatSound);
        }
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        renderChatUI(panel);
    }

    private void renderChatUI(TertiaryPanelComponent panel) {
        panel.clearChildren();

        IrcConfig config = IrcConfig.get();
        String currentSound = (config.chatSound != null && !config.chatSound.isBlank()) ? config.chatSound : "CAT_AMBIENT_BABY";

        String displayText = "提示音: " + currentSound;
        TextInputPaneComponent pickerPane = new TextInputPaneComponent(displayText, null)
                .asButton(displayText, () -> {
                    this.isDropdownOpen = !this.isDropdownOpen;
                    renderChatUI(panel);
                });

        panel.child(pickerPane);

        if (this.isDropdownOpen) {
            for (String soundKey : SOUND_OPTIONS) {
                panel.child(new PanelItemComponent(
                        soundKey,
                        PanelItemComponent.ItemType.TRIGGER,
                        () -> {
                            IrcConfig.get().chatSound = soundKey;
                            IrcConfig.save();

                            playSound(soundKey);
                            ChatNotifier.send("§a[IRC] 提示音已应用: §f" + soundKey, NotificationType.SUCCESS);

                            this.isDropdownOpen = false;
                            renderChatUI(panel);
                        },
                        null,
                        () -> soundKey.equalsIgnoreCase(IrcConfig.get().chatSound)
                ).withSpawnAnimation());
            }
        }

        panel.child(TextInfoPaneComponent.header("提示音设置 (左键试听并应用)"));
    }

    public static void playConfiguredSound() {
        IrcConfig config = IrcConfig.get();
        if (!config.chat) return;

        String soundKey = config.chatSound;
        playSound(soundKey);
    }

    /**
     * 播放指定名称的声音
     */
    public static void playSound(String soundKey) {
        try {
            Minecraft mc = Minecraft.getInstance();
            SoundEvent event = getSoundEvent(soundKey);
            if (event == null) return;

            float volume = 1.8F; // 放大音量
            float pitch = 1.0F;

            String upper = soundKey != null ? soundKey.toUpperCase(Locale.ROOT) : "";
            if (upper.contains("BABY") || upper.contains("CAT")) {
                volume = 2.0F;
                pitch = 1.35F;
            } else if (upper.contains("ARROW")) {
                volume = 1.3F;
                pitch = 1.0F;
            }

            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * 名称映射 SoundEvent
     */
    public static SoundEvent getSoundEvent(String key) {
        if (key == null) return SoundEvents.CAT_AMBIENT_BABY.value();
        return switch (key.toUpperCase(Locale.ROOT)) {
            case "CAT_PURREOW_BABY" -> SoundEvents.CAT_PURREOW_BABY.value();
            case "CAT_BEG_FOR_FOOD_BABY" -> SoundEvents.CAT_BEG_FOR_FOOD_BABY.value();
            case "CAT_PURR_BABY" -> SoundEvents.CAT_PURR_BABY.value();
            case "CAT_STRAY_AMBIENT_BABY" -> SoundEvents.CAT_STRAY_AMBIENT_BABY.value();
            case "CAT_HISS_BABY" -> SoundEvents.CAT_HISS_BABY.value();
            case "ARROW_HIT_PLAYER", "ARROW_HIT" -> SoundEvents.ARROW_HIT_PLAYER;
            default -> SoundEvents.CAT_AMBIENT_BABY.value();
        };
    }
}