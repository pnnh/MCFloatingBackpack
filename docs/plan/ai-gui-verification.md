# 让 Claude 自动验证游戏界面的方案（规划，2026-11-11）

状态：**方案 A（Linux 容器）已实测可行（见 4.1）**；虚拟机方案（B）留待以后，需要真机环境或更高保真时再试。本文记录选型分析、已查证的事实和未验证的假设，以及自动验证结果的记录规矩。

## 1. 为什么需要

- 本项目的验证（宪法 V）大量依赖"进游戏看一遍"：面板、点击、拖拽、按键、提示。目前全靠人工，积压了不少项（各 `specs/*/tasks.md` 里的未勾选任务）。
- Claude 能在命令行里启动 `./gradlew runClient`，但**看不见窗口、点不了键鼠**：这个会话没有屏幕截图和键鼠控制工具（不是沙盒限制）。
- 目标：给 Claude 一个**隔离的、可截图、可发键鼠**的环境，让能自动判定的项由它跑，把必须凭主观感受的项留给人。

## 2. 本项目对测试环境的要求

| 要求 | 说明 |
|------|------|
| JDK 21 + Gradle | `./gradlew runClient`（NeoForge 21.1.251 / Minecraft 1.21.1），首次要下载 Gradle 依赖与 Minecraft 资源 |
| OpenGL 3.2+ | Minecraft 1.21.1 的渲染要求；Mesa 软件渲染（llvmpipe）支持，但慢 |
| 鼠标捕获 | 游戏里按 B 打开面板后要点击、拖拽；需要键鼠输入能被 GLFW 收到 |
| 能截图 | Claude 用 Read 读 PNG，看画面判断 |
| 与真机隔离 | 不抢用户的鼠标键盘和屏幕；`run/` 独立，不与用户的世界冲突 |
| 开发联调模组 | 开发时 `runClient` 会带上 JEI 等（`-PcompatRuntime`）；容器里按需要取舍，先不带 |
| 不进入真实账号 | `runClient` 是开发离线账号，不需要登录 |

## 3. 方案对比

"已查证"指 2026-11-11 的网络搜索（第三方文章或官方文档摘要）；**没有一个在本机实测过**。

| 方案 | 形态 | Claude 怎么驱动 | 优点 | 对本项目的风险 | 状态 |
|------|------|------------------|------|------------------|------|
| **A. Linux 容器（Docker）+ Xvfb + xdotool** | arm64 Linux 容器，虚拟屏幕 | `docker exec` 里 `xdotool` 发键鼠、截图工具截图 | 已装 Docker；镜像小；不碰真机键鼠；脚本完全在 CLI | 无 GPU，软件渲染慢；Minecraft 在 Xvfb 里鼠标捕获是否可靠未验证；画面与 macOS 真机略有差异 | **先试** |
| **B1. Tart**（macOS 虚拟机） | Apple Virtualization.framework；OCI 镜像；APFS 写时复制克隆 | `tart clone / run / ip`，SSH 拷文件；有 Guest Agent 的镜像可 `tart exec`；VNC 截图与键鼠（用 vncdotool 等） | 最接近真机的 macOS 环境；克隆快；CLI 完整 | 镜像下载 20 GB 以上，本机可用磁盘约 50 GB 偏紧；macOS 来宾的 OpenGL 与半虚拟化 GPU 能否稳定跑 Minecraft 未验证；苹果许可限同时 2 台 macOS 虚拟机 | 以后 |
| **B2. Lume**（trycua） | 同样基于 Virtualization.framework，CLI 加本地 API 服务（搜索结果提到 7777 端口） | `lume run`；截图与键鼠在 Cua 框架一侧，**Lume 本身是否提供，搜索没有证实** | 与 Tart 同类，面向 agent | 同 B1；键鼠接口未证实 | 以后 |
| **B3. terminal-mac** | Swift 应用，包装 Virtualization.framework；`terminal-mac up` 下载镜像并启动 | 第三方文章称提供 **MCP 工具**让 agent 看屏幕、控鼠标键盘，宿主桌面不暴露 | 专为 agent 设计，开箱即用 | 2026-11 初刚发布；只见第三方文章，没读官方文档；同样要下载整套 macOS 镜像；MCP 工具要在 Claude Code 里配置 | 以后（最像"直接接管"） |
| **B4. UTM（utmctl）** | 全功能虚拟机管理器 | `utmctl start/stop`；**没有现成的截图、键鼠命令**（需另配 VNC 等） | 有 GUI，人能随时看 | 自动化弱 | 不优先 |
| 其他 | ClodPod、VM4A 等（搜索结果里出现） | 没有细看 | — | — | 未评估 |

### 3.1 哪个"近乎完整"支持本项目

- **环境真实度**：B 类（macOS 虚拟机）最高，因为玩家就在 macOS 上；A 类是 Linux，渲染路径和键位（Ctrl/Command、Alt/Option）不同。
- **能自动判定的东西**两者差不多：面板是否打开、格子里的物品、提示文字、能否放入/取出、按键交互。这些靠截图和日志就能判。
- **GPU 是关键不确定项**：Minecraft 要 OpenGL 3.2；macOS 来宾靠半虚拟化图形，Linux 容器靠 Mesa 软件渲染。两边都没实测，都可能慢。所以先用最便宜的 A 试，能跑再说，不行再换 B，不预先花 20 GB。
- **选 B 时倾向**：要"最像 agent 开箱即用"选 terminal-mac；要"脚本化、镜像生态"选 Tart；Lume 介于两者。到时再各用最小代价试一下，不在这里定。

## 4. 方案 A 的做法（计划）

- 容器：arm64 Linux、JDK 21、Xvfb、xdotool、截图工具（如 ImageMagick 的 `import` 或 `scrot`）、Mesa（llvmpipe）。Docker Desktop 现在分给 Linux 虚拟机约 8 GB 内存、10 核，够用。
- 代码放进容器：**拷贝**一份仓库（不挂载写回用户的工作区），`run/` 在容器里，用户的世界不受影响。
- Dockerfile 与脚本已收进 [scripts/ai-gui/](../../scripts/ai-gui/README.md)（用法、坐标与套路见那里的 README）。
- 第一个验收点：容器里 `runClient` 能出标题界面，我能截图并读到画面；第二个：能点进创造世界、按 B 打开面板；第三个：拖拽一格物品。任何一步卡住就记录原因，不硬撑。
- 每次动手前：`pgrep -f "fml.modFolders=mcfloatingbackpack"`，确认宿主上没有游戏在跑；用完 `docker rm -f`，不留后台容器。

### 4.1 方案 A 的实测结果（2026-11-11）

**结论：可行。** 在 arm64 Linux 容器里，Claude 能启动客户端、截图并读到画面、用 xdotool 点击和按键，并走完"标题界面 → 创建创造世界 → `/give` 背包 → 按 B 打开面板 → 放入物品 → 关闭再打开确认还在"。三个验收点全部通过。

| 验收点 | 结果 | 证据 |
|--------|------|------|
| 容器里 `runClient` 出标题界面，能截图读画面 | 通过 | OpenGL 4.5 Core、渲染器 llvmpipe；标题界面截图可读 |
| 点进创造世界，按 B 打开面板 | 通过 | 面板 ID 显示为 X26Y，五个功能区颜色正常 |
| 把物品放进背包格子 | 通过（点击法，见下） | 放入后关闭再按 B 重开，物品仍在（服务端往返） |

**遇到的坑与做法**（以后复用时照做）：

1. **LWJGL 原生库架构不对**：Gradle 在容器里解析出 `natives-linux`（x64），在 arm64 上报 `UnsatisfiedLinkError: liblwjgl.so`。做法：从 Maven Central 下载 `lwjgl`、`glfw`、`jemalloc`、`openal`、`opengl`、`stb`、`tinyfd` 的 3.3.3 `natives-linux-arm64` 包，解出 `.so` 放进容器的 `/natives`，用环境变量 `JAVA_TOOL_OPTIONS=-Dorg.lwjgl.librarypath=/natives` 启动。**没有改项目文件。**
2. **首次构建约 6 分钟**（下载依赖与 Minecraft 资源）；Gradle 缓存放在命名卷 `mcfb-gradle`，之后不用重下。
3. **画面小**：游戏窗口只有 854×480，位于 1280×720 虚拟屏幕中央（偏移约 213,120）。
4. **良性报错**：Narrator（缺 libflite）、OpenAL（没有声卡）、mixin 的一些 `Error loading class`（开发运行时带了 Sodium、Iris、JEI、精妙背包、旅行者背包、Curios、Accessories 等兼容模组）。不影响，不必追。
5. **"拖拽"用 xdotool 的按下/移动/松开没触发；改成"点一下拿起、再点一下放下"可靠**。
6. **从快捷栏放不进**：普通 HUD 的快捷栏不是可点击界面；创造物品栏界面里自带的快捷栏那一排被面板盖住，点到的是背后画面。可行做法：从创造物品栏的物品列表里直接点一件，再点背包格子（跨界面放入可用）。要用自己背包里的物品时，需要先用 `/give` 给自己，再从面板里取。
7. 原生库的下载在宿主机上做（宿主机有 `curl`），再用 `docker cp` 放进容器；没有检查容器内有没有 `curl`。

**还没验证**：软件渲染下的帧率与手感（容器里不测）；真机上的键位（Command/Option）；拖放长按手势的行为（本次用点击法）；多人联机。

**容器环境的组成**（脚本在 [scripts/ai-gui/](../../scripts/ai-gui/README.md)）：基础镜像 `eclipse-temurin:21-jdk-jammy`，加 `xvfb xdotool imagemagick mesa-utils libgl1-mesa-dri libopenal1` 等；Xvfb 分辨率 1280×720×24；环境变量 `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.5`；仓库用干净副本 `docker cp` 进容器（不挂载工作区）。镜像约 1.1 GB。

## 5. 验证结果的记录规矩（用户 2026-11-11 放宽）

原规矩：验证项一律等用户确认后才勾。现在：

1. **有明确证据的自动结果可以直接勾**：证据 = 截图文件、日志片段或指纹对比，写在任务下方或 DELIVERY 里，注明"AI 自动验证（环境：容器/虚拟机）"。
2. **证据不充分**（画面太模糊、软件渲染下表现不典型、只覆盖了一部分）：**不勾**，在任务下方备注"AI 自动验证"的证据，由用户手动跑一遍再勾。
3. **主观项始终由用户亲自确认**：图标、模型外观、手感、动画、帧率体验、颜色与排版是否好看等。
4. 自动验证环境与真机不同（Linux、软件渲染）这件事要写在备注里，不能把它当成真机结果。

## 5.1 可自动验证的候选清单（2026-11-11，对照当时 `manual-verification-checklist.md` 里**未勾选**的项；该文件已删除，见 5.2）

分档依据：能否靠"截图 + 日志 + 存档数据（NBT）"得出**客观**结论；容器是 Linux 软件渲染（Ctrl 对应真机的 Command），帧率不可信，不测。每项跑完照第 5 节写备注。

### 第一档：客观、步骤短、最值得先做（建议第一批）

| 项 | 判定证据 | 说明 |
|----|----------|------|
| 039-A～G | 聊天截图 + 日志 WARN | 用 `-Pmcfb.checkOwner=true -Pmcfb.serverVersion=…` 逐个版本启动；E 要退出重进、中英文各一遍（切语言多几步） |
| 040-A～D | 聊天截图 + 日志 + 存档里 `data_revision` | `-Pmcfb.stampRevision=99`；60 秒窗口靠等待；B 用存档 NBT 对比，不用 `/data get`（输出太长） |
| 040-F、G、H、I | 日志 + 截图 + 存档 NBT | 自动拾取、锻造台升级、丢弃/箱子/创造复制、正常情形不提示 |
| 041-H、041-J | 指纹对比 + 日志 | 纯服务端，**不用 GUI**：在容器或宿主 `runServer` 上做；J 的放行路径要手工给 JVM 加参数并用 `stop` 停服 |
| 041-G 其余回归 | 截图 + 日志 | 放行后 035～040 的功能；先放行再逐项点 |
| 035-B3、B4 | 存档 NBT + 日志 | B3 用存档 NBT 看 `extension_rows`（原先卡在 `/data get` 太长）；B4 装满带数据的潜影盒，查日志无解码错误 |
| 图集度量与日志检查（023-16/17、025-G、030-E 的图集部分） | 日志里 `blocks.png-atlas` 尺寸 | 第一次容器启动时日志里已出现 `2048x1024x4`（只是旁证）；正式结论要用 `check_atlas_budget.sh` 再跑一次 |
| 030-D | 文件复制 + 日志 + 命令 | 备份世界文件夹、使用新背包、换回备份、检查快照仍在并能 `restore`，容器里复制目录很方便 |
| 017-9～13 | 截图 + 配置文件 | 改 `visibleCols/visibleRows`、换档位、缩放；017-11 的窗口大小用 GUI 缩放代替 |
| 019-30、019-31 | 截图 | 用 `/execute in minecraft:the_nether run tp …` 换维度；019-31 用工作台这类不接管键盘的界面 |
| 022-Q11～Q14、保留选区 | 截图 + 日志 | 倾倒的保护规则、数据包去掉 `never_trash`、日志无 `bundle` 标签报错、删除受保护物品；数据包放进世界目录即可 |
| 024-L、024-P | 截图 + 聊天 + 存档 | 左区限放提示；黑名单退出重进、取走再放回、升级、换背包 |

### 第二档：可以做，但步骤多或部分靠观感，AI 先给证据、你补一次

| 项 | 难点 |
|----|------|
| 038-C、038-E | 顺序规则可用截图计数判；E 要升级换档，步骤长 |
| 038-D | "底色与 10 个分类行分得开"带主观成分；AI 给截图，你判断 |
| 012-5b、012-9 | 末影箱、潜影盒、漏斗、发射器；铁匠台、酿造台、村民（先 `/summon` 村民）；步骤多 |
| 013-21、014-32/33、016-31、018-46 | 精妙背包/旅行者背包界面里的拖动和 Shift/Ctrl 点击；容器里默认带这两个模组 |
| 024-C | 两个别的背包模组同时拾取，要核对总量守恒；步骤长 |
| 011-三.5/6、011-T032 | Command 在容器里是 Ctrl，只验证逻辑，不代表真机键位 |
| 020-1～18 | 配置要改文件再重启，容器里每次启动约 1 分钟；12 的十种坏写法要重复启动十次，建议拆成几批 |
| 025-A～F | 标签总览界面，Q1～Q31 步骤很多；列表、悬停、键盘规则可判，"大量分类的手感"归你 |
| 023-13 | 临时数据包里写旧格式升级配方，可做 |
| K3（撕裂修复） | 提示框叠放顺序要看细节，截图能给证据，最终建议你亲眼确认 |
| 026-E（搁置） | 重进世界编号不变、备份换回后能加载，可做；其余搁置项见第三档 |
| 040-J 的对照实验 | 可用 `git worktree` 在容器里构建旧版本，做"不点格子直接退出"的对照；你已做主实验，这只是补对照 |

### 第三档：需要第二个客户端或第二个账号（以后再说）

可以用多个容器（一个服务端加两个客户端）模拟局域网，但要先单独验证联机能否在容器间跑通：039-H/I、040-E（别的玩家）、021-Q49、022-Q36、023-14、026-C 的他人使用、031-B（非 op）、027-F、J2。

### 第四档：必须由你亲自确认

- 5 名观察者盲测、文档走查（010 T022）。
- 帧率与手感类：024-B、026-G、027-H、028 的 90+ fps 已由你验证，容器软件渲染不可比。
- 038 配方"成本观感"。
- 图标、模型、颜色是否好看、是否一眼分得出（AI 只能给截图）。
- 真机键位与手感（Command/Option）。

### 建议顺序

1. **先做图集和日志检查**：1 分钟，马上有结果。
2. **039 → 040 → 041**：都是最近实现、等你确认的积压项，全是第一档，一次性清掉最划算。
3. **035-B3/B4、030-D、017-9～13、019-30/31、022-Q11～14、024-L/P**。
4. **第二档**按功能块分批。

### 5.2 验证进度的唯一记录处（2026-11-11）

`docs/plan/manual-verification-checklist.md`（按功能块汇总的人工验证清单）已删除：它的条目与各 `specs/*/tasks.md`、`quickstart.md` 重复，而且编号不一致（如清单的 040-B 与 quickstart 的 B 不是一回事），经常要勾两遍。删除前核对过：清单里 94 个未勾选项，都能对上某个 feature 的未勾任务或 DELIVERY 里的"未验证"记录；只有 035 的 B3、B4 例外（任务当时被勾了，"未验证"只写在清单里），已在 035 的 T040、T041 补了备注。

- **以后只认两处**：`specs/<编号>/tasks.md`（勾选与备注）和 `specs/<编号>/DELIVERY.md`（结果与证据）。
- **看全部待验证项**：`grep -rn "^- \[ \]" specs/*/tasks.md`。
- 各 DELIVERY 里"来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选"这类话是**历史记录**（当时确有这份清单），没有改；文件在 git 历史里（`31a9a2f` 及以前的提交）。

## 6. 已查证 / 未验证

已查证（来自搜索结果，非实测）：

- Lume 是基于 Virtualization.framework 的 macOS/Linux 虚拟机 CLI，带本地 API 服务。
- terminal-mac 存在，是 2026-11 初发布的开源工具，给 agent 一个独立的 macOS 虚拟机（第三方文章，未读官方文档）。
- Tart 的非 vanilla 镜像带 Guest Agent，可 `tart exec`；cirruslabs 基础镜像的默认账号是 admin/admin；Tart 有 `--no-graphics`。
- Tart 项目的位置与 Homebrew 的安装渠道近来有变动（搜索结果说旧 tap 停在 2.32.1，建议用新的来源）；真装之前要再核对。

未验证：

- `tart run` 的 VNC 选项及 vncdotool 是否可用于截图与键鼠。
- Lume 本身是否带截图与键鼠接口。
- terminal-mac 的 MCP 工具的实际能力与稳定性。
- macOS 来宾里 Minecraft 的 OpenGL 表现；Linux 容器软件渲染下 Minecraft 的帧率与鼠标捕获。
- 本机清理过的旧工具（Tart、UTM、Lima、OrbStack）的残留是否会影响重装。

## 7. 来源

- Lume（trycua）：https://cua.ai/docs/lume 、https://cua.ai/docs/reference/lume/cli-reference
- terminal-mac：https://flaviocopes.com/terminal-mac 、https://flaviocopes.com/mac-test-vm/
- Tart 与 Guest Agent：https://flaviocopes.com/mac-test-vm/ 、https://crabbox.sh/providers/tart.html
