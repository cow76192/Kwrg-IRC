package me.cookie.kwrg.module.modules.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class LoadModule extends Module {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ILLEGAL_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private String pendingImportJson = null;

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    public LoadModule() {
        super("load", Category.CONFIG, PanelItemComponent.ItemType.TRIGGER);
    }

    @Override
    public void onExecute() {}

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        this.pendingImportJson = null; // 打开时重置导入暂存
        renderConfigManagerUI(panel, null);
    }

    public static String validateProfileName(String name) {
        if (name == null || name.isBlank()) {
            return "配置名称不能为空！";
        }

        if (name.length() > 64) {
            return "配置名称过长（最多支持 64 个字符）！";
        }

        if (name.equals(".") || name.equals("..") || name.contains("..")) {
            return "配置名称包含非法的路径字符！";
        }

        if (ILLEGAL_CHARS_PATTERN.matcher(name).find()) {
            return "配置名称包含系统非法字符：\\ / : * ? \" < > |";
        }

        if (name.endsWith(" ") || name.endsWith(".")) {
            return "配置名称不能以空格或句号（.）结尾！";
        }

        if (WINDOWS_RESERVED_NAMES.contains(name.toUpperCase(java.util.Locale.ROOT))) {
            return "该名称为系统保留设备名 (" + name + ")，无法使用！";
        }

        return null;
    }

    private void renderConfigManagerUI(TertiaryPanelComponent panel, String newlyAddedProfile) {
        panel.clearChildren();

        String placeholder = this.pendingImportJson != null
                ? "已读取剪贴板！输入名称按回车保存..."
                : "输入配置名称 (回车新建 / 右键导入)...";

        TextInputPaneComponent inputPane = new TextInputPaneComponent(placeholder, name -> {
            String cleanName = name.trim();
            if (cleanName.isEmpty()) return;

            String error = validateProfileName(cleanName);
            if (error != null) {
                ChatNotifier.send("§c[IRC] 创建失败: " + error, NotificationType.ERR);
                return;
            }

            if (this.pendingImportJson != null) {
                try {
                    Path dir = IrcConfig.getKwrgDir();
                    Files.createDirectories(dir);
                    Path filePath = dir.resolve(cleanName + ".json");

                    Files.writeString(filePath, this.pendingImportJson);
                    this.pendingImportJson = null;

                    IrcConfig.loadProfile(cleanName, true);
                    ChatNotifier.send("§a[IRC] 成功从剪贴板导入并创建配置: §f" + cleanName + ".json", NotificationType.SUCCESS);
                    renderConfigManagerUI(panel, cleanName);
                } catch (Exception e) {
                    ChatNotifier.send("§c[IRC] 保存导入配置失败: " + e.getMessage(), NotificationType.ERR);
                }
            } else {
                IrcConfig.createProfile(cleanName);
                IrcConfig.save();
                ChatNotifier.send("§a[IRC] 已新建并切换至配置: §f" + cleanName + ".json", NotificationType.SUCCESS);
                renderConfigManagerUI(panel, cleanName);
            }
        });

        inputPane.onRightClick(() -> {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard == null || clipboard.isBlank()) {
                ChatNotifier.send("§c[IRC] 剪贴板为空！请先复制配置的 JSON 文本。", NotificationType.WARN);
                return;
            }

            try {
                IrcConfig parsed = GSON.fromJson(clipboard.trim(), IrcConfig.class);
                if (parsed == null || parsed.glowColor == null) {
                    throw new IllegalArgumentException("配置缺少关键字段");
                }

                if (parsed.savedChannels == null) {
                    parsed.savedChannels = new ArrayList<>();
                }
                if (parsed.currentChannel == null) {
                    parsed.currentChannel = "";
                }

                this.pendingImportJson = GSON.toJson(parsed);

                ChatNotifier.send("§a[IRC] 剪贴板 JSON 解析成功！", NotificationType.SUCCESS);
                ChatNotifier.send("§e[IRC] 请在输入框输入该配置文件的名称，按回车键完成保存。", NotificationType.INFO);

                renderConfigManagerUI(panel, null);
            } catch (Exception e) {
                this.pendingImportJson = null;
                ChatNotifier.send("§c[IRC] 剪贴板内容不是有效的配置 JSON！解析失败。", NotificationType.ERR);
            }
        });

        panel.child(inputPane);
        panel.child(TextInfoPaneComponent.header("配置文件列表 (Shift+左键删除 )", (pane, click) -> {
            boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();
            if (isShiftDown) {
                Path configDir = IrcConfig.getKwrgDir();
                try {
                    if (!Files.exists(configDir)) {
                        Files.createDirectories(configDir);
                    }
                    net.minecraft.util.Util.getPlatform().openPath(configDir);
                    ChatNotifier.send("§a[IRC] 已打开配置文件夹: §f" + configDir.toAbsolutePath(), NotificationType.SUCCESS);
                } catch (Exception e) {
                    ChatNotifier.send("§c[IRC] 打开文件夹失败: " + e.getMessage(), NotificationType.ERR);
                }
            }
        }));

        List<String> profiles = new ArrayList<>(IrcConfig.listExistingProfiles());
        if (!profiles.contains("default")) {
            profiles.add(0, "default");
        }

        for (String profileName : profiles) {
            PanelItemComponent item = new PanelItemComponent(
                    profileName,
                    PanelItemComponent.ItemType.TOGGLE,
                    (btn, click) -> {
                        boolean isShiftDown = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || click.hasShiftDown();

                        if (isShiftDown) {
                            if ("default".equalsIgnoreCase(profileName)) {
                                ChatNotifier.send("§c[IRC] 默认配置文件 default.json 受保护不可删除！", NotificationType.WARN);
                                return;
                            }

                            btn.dismiss(() -> {
                                try {
                                    Path filePath = IrcConfig.getKwrgDir().resolve(profileName + ".json");
                                    Files.deleteIfExists(filePath);
                                    ChatNotifier.send("§e[IRC] 已删除配置文件: §f" + profileName + ".json", NotificationType.SUCCESS);

                                    if (profileName.equalsIgnoreCase(IrcConfig.getCurrentProfileName())) {
                                        IrcConfig.loadProfile("default", true);
                                        ChatNotifier.send("§a[IRC] 当前配置已删除，已自动切换回: §fdefault.json", NotificationType.SUCCESS);
                                    }

                                    renderConfigManagerUI(panel, null);
                                } catch (Exception e) {
                                    ChatNotifier.send("§c[IRC] 删除文件失败: " + e.getMessage(), NotificationType.ERR);
                                }
                            });
                        } else {
                            try {
                                Path filePath = IrcConfig.getKwrgDir().resolve(profileName + ".json");
                                String jsonContent = Files.exists(filePath) ? Files.readString(filePath) : "";
                                if (!jsonContent.isEmpty()) {
                                    com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(jsonContent).getAsJsonObject();
                                    jsonObject.remove("currentChannel");
                                    jsonObject.remove("savedChannels");

                                    String cleanedJson = GSON.toJson(jsonObject);

                                    Minecraft.getInstance().keyboardHandler.setClipboard(cleanedJson);
                                    ChatNotifier.send("§a[IRC] 已复制 §f" + profileName + ".json §a的 JSON 内容到剪贴板！(已自动剔除频道数据)", NotificationType.SUCCESS);
                                } else {
                                    ChatNotifier.send("§e[IRC] 配置文件 §f" + profileName + ".json §e内容为空", NotificationType.WARN);
                                }
                            } catch (Exception e) {
                                ChatNotifier.send("§c[IRC] 读取文件失败: " + e.getMessage(), NotificationType.ERR);
                            }
                        }
                    },
                    btn -> {
                        boolean success = IrcConfig.loadProfile(profileName, false);
                        if (success) {
                            ChatNotifier.send("§a[IRC] 已成功切换并加载配置: §f" + profileName + ".json", NotificationType.SUCCESS);
                        }
                    },
                    () -> profileName.equalsIgnoreCase(IrcConfig.getCurrentProfileName())
            );

            if (newlyAddedProfile != null && profileName.equalsIgnoreCase(newlyAddedProfile)) {
                item.withSpawnAnimation();
            }

            panel.child(item);
        }
    }

    public static int loadConfig(String name) {
        boolean success = IrcConfig.loadProfile(name, false);
        if (success) {
            IrcMessageHandler.printMessage("§a[IRC] 已成功加载配置文件: §f" + IrcConfig.getCurrentProfileName() + ".json");
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 配置文件 §f" + name + ".json §c不存在！可使用 /irc cfg create " + name + " 创建");
        }
        return success ? 1 : 0;
    }
    public static int createConfig(String name) {
        String cleanName = name.trim();
        String error = validateProfileName(cleanName);
        if (error != null) {
            IrcMessageHandler.printMessage("§c[IRC] 创建失败: " + error);
            return 0;
        }

        IrcConfig.createProfile(cleanName);
        IrcMessageHandler.printMessage("§a[IRC] 已新建配置: §f" + IrcConfig.getCurrentProfileName() + " §7(注意: 调整好设置后输入 §e/irc cfg save §7保存)");
        return 1;
    }

    public static int saveConfig() {
        boolean success = IrcConfig.save();
        if (success) {
            IrcMessageHandler.printMessage("§a[IRC] 当前配置已成功保存至 §f" + IrcConfig.getCurrentProfileName() + ".json");
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 配置文件保存失败");
        }
        return success ? 1 : 0;
    }

    public static int showCurrentConfig() {
        IrcMessageHandler.printMessage("§6[IRC] 当前生效的配置: §f" + IrcConfig.getCurrentProfileName() + ".json");
        List<String> all = IrcConfig.listExistingProfiles();
        IrcMessageHandler.printMessage("§7已有配置文件列表: " + (all.isEmpty() ? "无" : String.join(", ", all)));
        IrcMessageHandler.printMessage("§7用法: /irc cfg load <配置名> | /irc cfg create <配置名> | /irc cfg save");
        return 1;
    }
}