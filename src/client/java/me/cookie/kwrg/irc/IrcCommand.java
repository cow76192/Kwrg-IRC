package me.cookie.kwrg.irc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.module.modules.debug.ClearIndexCacheModule;
import me.cookie.kwrg.module.modules.functions.GlowColorModule;
import me.cookie.kwrg.module.modules.functions.HideArmorModule;
import me.cookie.kwrg.module.modules.functions.ImportantPlayerModule;
import me.cookie.kwrg.module.modules.play.PlayModule;
import me.cookie.kwrg.module.modules.style.MessageStyleModule;
import me.cookie.kwrg.module.modules.style.NameStyleModule;
import me.cookie.kwrg.module.modules.style.RankModule;
import me.cookie.kwrg.util.SkyblockItemHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

import static me.cookie.kwrg.module.modules.config.LoadModule.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class IrcCommand {

    private static final SuggestionProvider<FabricClientCommandSource> STYLE_SUGGESTIONS = (context, builder) -> {
        String[] presets = new String[]{
                "rainbow", "sunset", "ocean", "fire", "sakura", "neon", "gold", "purple", "bold", "underline", "italic", "strikethrough", "obfuscated",
                "rainbow bold", "#FF55AA", "rgb(255,100,50)", "gradient(#ff758c,#ff7eb3)", "gradient(#00f2fe,#4facfe,#00ff87)"
        };
        for (String p : presets) {
            builder.suggest(p);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();

        for (String sbItem : me.cookie.kwrg.util.SkyblockItemHelper.getSuggestedIds()) {
            if (sbItem.toLowerCase().startsWith(remaining)) {
                builder.suggest(sbItem);
            }
        }

        for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            String full = id.toString();
            String path = id.getPath();
            if (full.startsWith(remaining)) {
                builder.suggest(full);
            } else if (path.startsWith(remaining)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> CONFIG_SUGGESTIONS = (context, builder) -> {
        for (String configName : IrcConfig.listExistingProfiles()) {
            builder.suggest(configName);
        }
        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(IrcCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(literal("ci")
                .then(literal("all")
                        .executes(context -> {
                            IrcClient.isIrcChatActive = true;
                            IrcConfig config = IrcConfig.get();
                            config.channelEnabled = false;
                            IrcConfig.save();
                            IrcClient.sendChannelSync(config.currentChannel, false);

                            IrcMessageHandler.printMessage("§a[IRC] 已切换至 §e公共大厅发言 §a模式");
                            if (!IrcClient.isIrcConnect) {
                                IrcClient.connect();
                            }
                            return 1;
                        }))
                .then(literal("channel")
                        .executes(context -> {
                            IrcClient.isIrcChatActive = true;
                            IrcConfig config = IrcConfig.get();

                            if (config.currentChannel == null || config.currentChannel.isBlank()) {
                                IrcMessageHandler.printMessage("§c[IRC] 你尚未加入任何频道！请先在 GUI 中加入频道。");
                                return 0;
                            }

                            config.channelEnabled = true;
                            IrcConfig.save();
                            IrcClient.sendChannelSync(config.currentChannel, true);

                            IrcMessageHandler.printMessage("§a[IRC] 已切换至 §b[#" + config.currentChannel + "] §a频道发言模式！");
                            if (!IrcClient.isIrcConnect) {
                                IrcClient.connect();
                            }
                            return 1;
                        }))
                .executes(context -> {
                    IrcClient.isIrcChatActive = true;
                    IrcConfig config = IrcConfig.get();
                    if (config.channelEnabled && config.currentChannel != null && !config.currentChannel.isBlank()) {
                        IrcMessageHandler.printMessage("§a[IRC] 已切换 IRC 聊天频道！当前发言频道: §b[#" + config.currentChannel + "]");
                    } else {
                        IrcMessageHandler.printMessage("§a[IRC] 已切换 IRC 聊天频道！当前处于: §e公共大厅");
                    }

                    if (!IrcClient.isIrcConnect) {
                        IrcClient.connect();
                    }
                    return 1;
                })
        );

        dispatcher.register(literal("ic")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            IrcClient.sendMessage(message);
                            return 1;
                        })
                )
                .executes(context -> {
                    IrcMessageHandler.printMessage("§c[IRC] 用法: /ic <消息内容>");
                    return 0;
                })
        );

        dispatcher.register(literal("icall")
                .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            IrcClient.sendMessageToPublic(message);
                            return 1;
                        })
                )
                .executes(context -> {
                    IrcMessageHandler.printMessage("§c[IRC] 用法: /icall <消息内容> (强制发送至公共大厅)");
                    return 0;
                })
        );

        var ircRoot = literal("irc")
                .executes(context -> {
                    sendHelp();
                    return 1;
                })
                .then(literal("rank")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(context -> RankModule.setRank(StringArgumentType.getString(context, "name"))))
                        .executes(context -> RankModule.showRank()))
                .then(literal("r")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(context -> RankModule.setRank(StringArgumentType.getString(context, "name"))))
                        .executes(context -> RankModule.showRank()))
                .then(literal("nameStyle")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> NameStyleModule.setNameStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> NameStyleModule.showNameStyle()))
                .then(literal("ns")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> NameStyleModule.setNameStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> NameStyleModule.showNameStyle()))
                .then(literal("messageStyle")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> MessageStyleModule.setChatStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> MessageStyleModule.showChatStyle()))
                .then(literal("ms")
                        .then(argument("style", StringArgumentType.greedyString())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(context -> MessageStyleModule.setChatStyle(StringArgumentType.getString(context, "style"))))
                        .executes(context -> MessageStyleModule.showChatStyle()))
                .then(literal("play")
                        .then(argument("player", StringArgumentType.string())
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .executes(context -> PlayModule.handlePlayCommand(
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "item")
                                        )))))
                .then(literal("p")
                        .then(argument("player", StringArgumentType.string())
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests(ITEM_SUGGESTIONS)
                                        .executes(context -> PlayModule.handlePlayCommand(
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "item")
                                        )))))
                .then(literal("important")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> ImportantPlayerModule.toggleImportant(StringArgumentType.getString(context, "player"))))
                        .executes(context -> ImportantPlayerModule.listImportant()))
                .then(literal("imp")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> ImportantPlayerModule.toggleImportant(StringArgumentType.getString(context, "player"))))
                        .executes(context -> ImportantPlayerModule.listImportant()))
                .then(literal("config")
                        .then(literal("load")
                                .then(argument("name", StringArgumentType.string())
                                        .suggests(CONFIG_SUGGESTIONS)
                                        .executes(context -> loadConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.string())
                                        .executes(context -> createConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("save")
                                .executes(context -> saveConfig()))
                        .executes(context -> showCurrentConfig()))
                .then(literal("cfg")
                        .then(literal("load")
                                .then(argument("name", StringArgumentType.string())
                                        .suggests(CONFIG_SUGGESTIONS)
                                        .executes(context -> loadConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.string())
                                        .executes(context -> createConfig(StringArgumentType.getString(context, "name")))))
                        .then(literal("save")
                                .executes(context -> saveConfig()))
                        .executes(context -> showCurrentConfig()))
                .then(literal("online")
                        .executes(context -> {
                            IrcClient.queryOnline();
                            return 1;
                        }))
                .then(literal("debug")
                        .then(literal("clearImgCache")
                                .executes(context -> ClearIndexCacheModule.handleClearImgCache()))
                        .then(literal("connect")
                                .executes(context -> {
                                    IrcClient.connect();
                                    return 1;
                                }))
                        .then(literal("disconnect")
                                .executes(context -> {
                                    IrcClient.disconnect();
                                    IrcMessageHandler.printMessage("§c[IRC] 已断开 IRC 连接");
                                    return 1;
                                }))
                        .then(literal("clearIndexCache")
                                .executes(context -> {
                                    SkyblockItemHelper.invalidateCache();
                                    return 1;
                                }))
                        .then(literal("reloadRepo")
                                .executes(context -> {
                                    SkyblockItemHelper.reloadRepo();
                                    return 1;
                                })))
                .then(literal("functions")
                        .then(literal("hideArmor")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> HideArmorModule.setHideArmor(BoolArgumentType.getBool(context, "enabled"))))
                                .executes(context -> HideArmorModule.showHideArmor()))
                        .then(literal("glowColor")
                                .then(argument("color", StringArgumentType.greedyString())
                                        .suggests(STYLE_SUGGESTIONS)
                                        .executes(context -> GlowColorModule.setGlowColor(StringArgumentType.getString(context, "color"))))
                                .executes(context -> GlowColorModule.showGlowColor())))
                .then(literal("func")
                        .then(literal("hideArmor")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> HideArmorModule.setHideArmor(BoolArgumentType.getBool(context, "enabled"))))
                                .executes(context -> HideArmorModule.showHideArmor()))
                        .then(literal("glowColor")
                                .then(argument("color", StringArgumentType.greedyString())
                                        .suggests(STYLE_SUGGESTIONS)
                                        .executes(context -> GlowColorModule.setGlowColor(StringArgumentType.getString(context, "color"))))
                                .executes(context -> GlowColorModule.showGlowColor())))
                .then(literal("gui").executes(ctx -> {
                    IrcScreen.showGui();
                    return 1;
                }));

        dispatcher.register(ircRoot);
    }

    private static void sendHelp() {
        IrcMessageHandler.printMessage("§6===== [Kwrg IRC 帮助] =====");
        IrcMessageHandler.printMessage("§e/ci §7或 §e/chat irc §7- 切换 IRC 聊天频道");
        IrcMessageHandler.printMessage("§e/ic <消息> §7- 快捷发送一条 IRC 消息");
        IrcMessageHandler.printMessage("§e/irc r <称号> §7- 设置称号 (如 /irc r &6VIP&b+)");
        IrcMessageHandler.printMessage("§e/irc ns <样式> §7- 设置名字样式 (支持彩虹、渐变、CSS)");
        IrcMessageHandler.printMessage("§e/irc ms <样式> §7- 设置消息样式 (支持彩虹、渐变、CSS)");
        IrcMessageHandler.printMessage("§e/irc important/imp [玩家名] §7- 添加/移除关注玩家 (不区分大小写)");
        IrcMessageHandler.printMessage("§e/irc config/cfg load/create <配置名> §7- 加载/新建配置");
        IrcMessageHandler.printMessage("§e/irc config/cfg save §7- 保存当前配置至文件(只有新建的配置文件才需要保存)");
        IrcMessageHandler.printMessage("§e/irc play/p <玩家名> <物品ID> §7- 向指定玩家发送 Totem 动画效果");
        IrcMessageHandler.printMessage("§e/irc online §7- 查看当前 IRC 在线玩家列表");
        IrcMessageHandler.printMessage("§e/irc functions §7- 查看功能列表");
        IrcMessageHandler.printMessage("§e/irc debug  §7- 查看调试选项");
    }
}