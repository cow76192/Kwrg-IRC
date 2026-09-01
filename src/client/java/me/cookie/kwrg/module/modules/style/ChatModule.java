package me.cookie.kwrg.module.modules.style;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.TextInfoPaneComponent;
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

    // 声音选项列表 (包含 6 款小猫声音与 1 款弓箭命中清脆叮声)
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
        ChatNotifier.send("§a[IRC] 聊天提示音 (Chat) 已: " + (config.chat ? "§a开启" : "§c关闭"));
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

        // ① 顶部只读选择框 (点击展开/收起声音列表)
        String displayText = "提示音: " + currentSound;
        TextInputPaneComponent pickerPane = new TextInputPaneComponent(displayText, null)
                .asButton(displayText, () -> {
                    this.isDropdownOpen = !this.isDropdownOpen;
                    renderChatUI(panel);
                });

        panel.child(pickerPane);

        // ② 下拉展示声音选项列表
        if (this.isDropdownOpen) {
            for (String soundKey : SOUND_OPTIONS) {
                panel.child(new PanelItemComponent(
                        soundKey,
                        PanelItemComponent.ItemType.TRIGGER,
                        () -> {
                            // 1. 存入配置
                            IrcConfig.get().chatSound = soundKey;
                            IrcConfig.save();

                            // 2. 播放声音预览并提示
                            playSound(soundKey);
                            ChatNotifier.send("§a[IRC] 提示音已应用: §f" + soundKey);

                            // 3. 回填名称并收起列表
                            this.isDropdownOpen = false;
                            renderChatUI(panel);
                        },
                        null,
                        () -> soundKey.equalsIgnoreCase(IrcConfig.get().chatSound)
                ).withSpawnAnimation());
            }
        }

        // ③ 居中说明标题
        panel.child(TextInfoPaneComponent.header("提示音设置 (左键试听并应用)"));
    }

    // =========================================================
    // 【公共调用 API】：供外部在收到聊天/消息时调用
    // =========================================================

    /**
     * 播放当前配置文件中设置的聊天提示音 (会自动检查 config.chat 开关是否开启)
     */
    public static void playConfiguredSound() {
        IrcConfig config = IrcConfig.get();
        if (!config.chat) return; // 如果未开启提示音则静默不播放

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

            // 针对不同声音进行音量增益 (Volume Boost) 与音调 (Pitch) 校准
            float volume = 1.8F; // 默认放大音量
            float pitch = 1.0F;

            String upper = soundKey != null ? soundKey.toUpperCase(Locale.ROOT) : "";
            if (upper.contains("BABY") || upper.contains("CAT")) {
                // 【核心优化】：将小猫声音增益拉到 2.0F，音调提升到 1.35F，声音立刻清脆响亮可爱！
                volume = 2.0F;
                pitch = 1.35F;
            } else if (upper.contains("ARROW")) {
                volume = 1.3F;
                pitch = 1.0F;
            }

            // 使用 forUI 播放无距离衰减的清晰立体声音效
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * 纯净名称映射到原版 SoundEvent
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