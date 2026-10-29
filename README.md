# Kwrg IRC

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBB98F?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-EA923E?style=flat-square)
[![License: MIT](https://img.shields.io/badge/License-MIT-4C9BD6?style=flat-square)](./LICENSE)
[![Java source lines: 28,648](https://img.shields.io/badge/Code%20lines-28%2C648-8B7EC8?style=flat-square)](./src "Total lines in src/**/*.java, including comments and blank lines")

面向 Hypixel SkyBlock 的 Fabric 客户端模组，提供内置 IRC 聊天、路径点与路线管理，以及可自定义的 GUI 和 HUD。

[English](./README.en.md) · [源码](https://github.com/cow76192/Kwrg-IRC)

## 安装

当前源码对应的运行环境：

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.1.2 |
| Java | 25 或更高 |
| Fabric Loader | 0.19.3 或更高 |
| Fabric API | 与 Minecraft 26.1.2 匹配 |
| owo-lib | 0.13.1+26.1 |

1. 为 Minecraft 26.1.2 安装 Fabric Loader。
2. 将 Kwrg 模组 JAR 和对应版本的 Fabric API 放入游戏目录的 `mods/` 文件夹。
3. 安装对应版本的 owo-lib。构建包也包含 owo-sentinel，可在缺少 owo-lib 时引导安装。
4. 启动游戏并进入世界，按 **右 Shift** 或输入 `/irc gui` 打开界面。
5. ENJOY your GAME

模组依赖skyblocker加载repo，兼容skyhanni。
Hypixel Mod API 已随模组打包，无需单独安装。配置、路线、路径点等文件保存在游戏目录下的 `kwrg/` 中。

## 主要功能

### IRC 聊天

- 公共大厅与自定义频道，可管理监听频道和发言目标。
- 自定义称号、名字样式、消息样式与消息提示音。
- 在线玩家列表、关注玩家提醒和打字状态。
- 远程发送图腾效果
- 支持 `:skull:`、`:joy:` 等表情短码。
- 分享配置、样式和私聊频道邀请。

IRC 使用项目自建服务和基于 TCP 的 JSON 协议；聊天与玩家外观同步需要连接该服务。

### 路径点与路线

- 独立的waypoint和route编辑屏幕
- 管理多组路径点，按 SkyBlock 岛屿设置显示范围。
- 创建、编辑路线，并在世界中显示节点和连线。
- 内置 Kuudra 珍珠路线。
- 识别聊天中的坐标，通过消息按钮显示临时路径点与自动寻路。
- 开启快速编辑后，使用 **Alt + 左键点击方块** 添加路线节点。

### 界面与 HUD

- 径向菜单、模块设置面板、自定义主题色、透明度和 GUI 缩放。
- 自定义gui主界面背景模糊
- 灵动岛 HUD，可显示 FPS、延迟、TPS、内存和硬件状态。
- 自定义 Tab 面板和记分板样式。
- HUD 布局编辑器，支持拖动、磁吸和位置重置。
- 加载本地 TTF / OTF 字体，无需重启游戏。

### 玩家显示

- 自定义静态或动画披风、模型缩放、按部位隐藏盔甲。
- IRC 玩家发光与颜色设置。
- 昵称与 Hypixel Rank 的本地显示修改。
- 玩家查找、队伍自动追踪与屏幕雷达。

披风、模型缩放等外观设置可通过 IRC 同步给使用该模组的玩家；昵称与 Rank 伪装仅影响本地显示。

### DEBUG
- 连接与断开 IRC 服务，测量当前连接延迟。
- 开关 GUI 背景模糊，并调整模糊质量与半径。
- 限制 GUI 帧率，降低打开界面时的资源占用。
- 调节寻路启发式权重，在搜索速度与路径质量之间取舍。
- 调节灵动岛的抗锯齿强度。
- 清理物品索引缓存、重载物品仓库、清空披风缓存。

## 常用操作

### 开始聊天

| 命令 | 说明 |
| --- | --- |
| `/chat irc` | 进入 IRC 聊天模式，后续普通聊天输入发送到 IRC |
| `/ic <消息>` | 向当前 IRC 发言目标发送一条消息 |
| `/icall <消息>` | 向公共大厅发送一条消息 |
| `/ci all` | 切换到公共大厅发言 |
| `/ci channel <名称>` | 加入并切换到指定频道发言 |
| `/irc online` | 查看 IRC 在线玩家 |
| `/irc coords` | 发送当前位置、岛屿与服务器信息 |

在 Hypixel 上可使用 `/chat all` 等其他 `/chat` 命令退出 IRC 聊天模式，切回游戏聊天。

### 界面与设置

| 命令 | 说明 |
| --- | --- |
| `/irc` | 查看命令帮助 |
| `/irc gui` | 打开主界面，默认快捷键为右 Shift |
| `/irc hud` | 打开 HUD 布局编辑器 |
| `/irc rank <称号>` | 设置 IRC 称号 |
| `/irc nameStyle <样式>` | 设置名字样式 |
| `/irc messageStyle <样式>` | 设置消息样式 |
| `/irc important [玩家名]` | 管理关注玩家 |
| `/irc config create <名称>` | 新建配置档案 |
| `/irc config load <名称>` | 加载配置档案 |
| `/irc config save` | 保存当前配置档案 |
| `/irc share open` | 打开分享界面 |
| `/irc debug connect` | 连接 IRC |
| `/irc debug disconnect` | 断开 IRC |
| `/irc debug ping` | 查看 IRC 延迟 |

### 编辑路线

以下命令操作当前选中的路线，节点索引从 **1** 开始。

| 命令 | 说明 |
| --- | --- |
| `/route show` | 列出当前路线的节点 |
| `/route add <名称> [x y z]` | 在末尾添加节点，省略坐标时使用当前位置 |
| `/route ins <名称> <前索引> <后索引>` | 在两个相邻节点之间插入当前位置 |
| `/route del <索引>` | 删除指定节点 |
| `/irc routeEdit [true\|false]` | 切换或指定快速编辑状态 |

## 本地文件

以下路径均相对于游戏目录。部分文件或目录会在首次使用相应功能时生成。

| 路径 | 内容 |
| --- | --- |
| `kwrg/config.json` | 当前配置档案的选择信息 |
| `kwrg/<配置名>.json` | 各配置档案的设置 |
| `kwrg/routes/` | 路线文件 |
| `kwrg/wayPoints/` | 路径点集 |
| `kwrg/font/` | 自定义 TTF / OTF 字体 |
| `kwrg/capes/cache/` | 披风缓存 |

路线和路径点文件保存前会检查磁盘内容；发现外部修改时会提示，避免直接覆盖。

## 从源码构建

需要 **JDK 25**。

Windows（PowerShell）：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```bash
./gradlew build
```

构建产物位于 `build/libs/`。安装时使用不带 `-sources` 后缀的模组 JAR。

## 作者与许可

- 项目作者：cow76192、2vnailong88。
- 模组元数据署名：Grwk、Kwrg。
- 本项目采用 [MIT License](./LICENSE)。
