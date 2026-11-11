# 自动验证脚本（Claude 在 Linux 容器里操作游戏）

让 Claude 在隔离的 Linux 容器里启动 `runClient`、截图、用 xdotool 点击和按键，做"截图 + 日志 + 存档数据"能客观判定的验证。设计、选型和实测结论见 [docs/plan/ai-gui-verification.md](../../docs/plan/ai-gui-verification.md)；结果的记录规矩见该文档第 5 节。

**限制**：容器是 Linux、软件渲染（llvmpipe），帧率与手感不可信，键位是 Ctrl 而不是 Command；只验证"逻辑与提示"，不代替真机。

## 前提

- Docker 在运行（Docker Desktop 即可）。宿主机上没有游戏进程（`pgrep -f "fml.modFolders=mcfloatingbackpack"`，一次只开一个客户端）。
- 脚本会用到命名卷 `mcfb-gradle`（Gradle 缓存，省下首次约 6 分钟的下载）和 `mcfb-run`（容器里的 `run/`：世界、`options.txt`）。

## 用法

```bash
scripts/ai-gui/setup.sh                      # 建镜像、容器、虚拟屏幕，把工作区复制进容器（第一次要几分钟）
scripts/ai-gui/launch.sh A -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.2.0   # 启动客户端到标题界面
scripts/ai-gui/enter.sh                      # 进入第一个世界
scripts/ai-gui/gui.sh key b                  # 按键 / click / type / shot …，每次都会截 last.png
scripts/ai-gui/try.sh B -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.0.9      # 启动 + 进世界 + 打印聊天行
scripts/ai-gui/server.sh P1                  # 专用服务器（/work/run/world）；停服用 stop-server.sh
scripts/ai-gui/sync.sh                       # 改了代码后重新同步进容器
python3 -I scripts/ai-gui/bp_dump.py 目录/*.dat   # 转储玩家数据里的背包，对比"零变化"
docker rm -f mcfb-gui                        # 用完删容器（卷和镜像保留）
```

截图在 `$AIGUI_SHOTS`（默认 `$TMPDIR/mcfb-ai-gui/shots`），不进仓库。要看画面就读 `last.png`。

## 坐标与套路（1280×720 虚拟屏幕）

- 游戏窗口 854×480，左上角约 (213,120)。标题界面：Singleplayer (638,323)；选世界列表第一项 (600,255)，Play Selected World (480,515)；创建世界界面 Game Mode 按钮 (638,291)，Create New World (480,567)；暂停菜单 Save and Quit to Title (638,503)。
- 第一次没有世界：点 Singleplayer → Game Mode 点两次（Survival → Hardcore → Creative，要生存模式就只点一次不用）→ Create New World，约 40 秒。第一次启动会先出现"欢迎/无障碍"界面，点 Continue (638,567)。
- **点击**用"点一下拿起、再点一下放下"；xdotool 的拖拽手势在这些界面里不灵。
- **命令**：`gui.sh key slash`、`gui.sh type "give @s …"`、`gui.sh key Return`。聊天行会写进 `latest.log` 的 `[CHAT]`，**判定优先看日志，不靠 OCR 截图**。
- **面板**：先在物品栏界面（E）里拿起物品，再按 B 打开面板，再点面板格子（面板格子约 36 像素一格，第一行 y≈172，左侧工具区 x≈245，主区域第二行第三格 (315,190)）。快捷栏不是可点击的界面。
- **创造物品栏界面**会让背包的 `data_revision` 被客户端写回覆盖（040 已知局限）；造"受保护的背包"要用生存模式。
- **退出**一定走图形界面 Save and Quit（可能要按两次 Escape），再转储存档；**不要 pkill**：SIGTERM 触发的关停钩子可能在你刚还原存档之后又把它覆盖掉。
- `runServer` 里 Gradle 不转发标准输入，`stop` 打不进去，用 `stop-server.sh`。
- 带 `-Pmcfb.stampRevision=99` 的会话里，每只背包第一次打开就被盖章 99，同一会话第二次打开会被拒（预期）；每只背包只开一次面板。

## 其它经验（第 3 步补充）

- **聊天命令最长 256 个字符**。要给长的物品组件（整只背包内容、几十个潜影盒），写成数据包函数：在世界的 `datapacks/<名>/data/<名>/function/x.mcfunction` 里放一行 `give …`，加 `pack.mcmeta`（`pack_format: 48`），进世界后 `/reload`、`/function <名>:x`。背包内容的字段格式（`items`、`tools.left/bottom/right`、`extension_rows`）用 `bp_dump.py` 看存档里的真实结构；`tools` 的列表可以写短，会自动补齐。
- **面板开关会错位**：宿主界面在聊天关闭后会自己重新打开，所以按 B 是"开"还是"关"取决于当时状态。看容器里 `latest.log` 最后一条 `019 host:` 是 `open` 还是 `close`，或者直接点面板右上角的 X 关掉。
- **文本框有焦点时按键会被吃掉**（标签面板、黑名单页的输入框）：先点面板 X 关掉面板，再继续输命令。
- **快捷栏在物品栏界面里的位置**：y≈494（第 n 格 x≈494+36×(n−1)）；面板打开时面板底边约 490，所以点快捷栏要用 y≈504。
- **聊天命令前先确认没有界面开着**；生存模式下别站在岩壁里（会窒息死亡）。
- **工具区坐标**（面板 9 列时）：左区 x≈245，右区 x≈604；下区在面板最底下一行，面板是 4 行主区域时 y≈315，是 8 行时 y≈460。
- **右键**：`docker exec -e DISPLAY=:99 <容器> sh -c 'xdotool mousemove X Y; sleep 0.3; xdotool click 3'`；`gui.sh rclick` 不移动鼠标，只点当前位置。
- **缩小窗口**：`docker exec -e DISPLAY=:99 <容器> sh -c 'xdotool windowsize $(xdotool search --name Minecraft | tail -1) 200 150'`，用来测面板自动收缩。

## 联机：一个服务端加两个客户端（多容器）

`multi-setup.sh` 建三个容器 `mcfb-srv`（专用服务器）、`mcfb-c1`、`mcfb-c2`（客户端，各有自己的虚拟屏幕），同一个 Docker 网络 `mcfb-net`，客户端用主机名 `mcfb-srv:25565` 直连。用环境变量选容器：`AIGUI_CONTAINER=mcfb-c1 ./gui.sh shot a`、`AIGUI_CONTAINER=mcfb-srv ./server.sh M1`。

2026-11-11 实测：三个容器同时运行，服务端约 1.4 GB、每个客户端约 2 GB，Docker 总内存 8 GB 够用（合计约 5.5 GB）；每个客户端在游戏时吃 3～4 个核。要点：

- **Gradle 缓存卷不能共用**：三个容器挂同一个卷会报 `Timeout waiting to lock journal cache`（缓存锁靠本机进程互相通信，跨容器无效）。`multi-setup.sh` 让服务端用原来的 `mcfb-gradle`，两个客户端各用一份复制出来的卷（`mcfb-gradle-c1`、`-c2`，各约 1.5 GB，只复制一次）。
- **服务端要 `online-mode=false`**：往 `mcfb-srv:/work/run/server.properties` 写 `online-mode=false`，否则开发环境的客户端（没有正版登录）进不来。想让某人是 op，先停服，往 `ops.json` 写条目（离线 UUID 看 `usercache.json`）再启动；`/deop` 可以在游戏里由 op 执行。
- **两个客户端的玩家名不能相同**，否则服务端按离线 UUID 当成同一个人。用 `game-args.init.gradle` 给客户端追加游戏参数（JavaExec 的 `--args` 在 ModDevGradle 里会顶掉主类，不能用）：`AIGUI_GAME_ARGS="--username P1 --quickPlayMultiplayer mcfb-srv:25565" AIGUI_CONTAINER=mcfb-c1 ./launch.sh C1`。`--quickPlayMultiplayer` 启动后直接连服务器，不用在界面里点；全新的 `options.txt` 会先出现"欢迎/无障碍"界面，要点一次 Continue (638,567) 才开始连。
- **启动要从 `scripts/ai-gui/` 目录里调 `./launch.sh`**（或用绝对路径），在别的目录里相对路径找不到脚本会静默失败。
- 服务端世界默认出生在平地上，夜里会有史莱姆把玩家打死：用 op 执行 `difficulty peaceful`、`gamerule keepInventory true`。

### 局域网房主（对局域网开放）

房主是 `mcfb-c1` 里进入的单人世界，暂停菜单点 "Open to LAN" → 端口框输入 `25570` → Start LAN World；加入者 `mcfb-c2` 用 `AIGUI_GAME_ARGS="--username P2 --quickPlayMultiplayer mcfb-c1:25570"` 启动（或在多人界面的 Direct Connection 里输入 `mcfb-c1:25570`，输入框里原有文字要先清掉）。

- **集成服务器强制正版验证**：开发客户端没有正版账号，加入会报 `Invalid session` 或 `That name is already taken`（两个客户端默认都叫 Dev）。解决：`scripts/ai-gui/MockSession.java` 是个假的会话服务器（只实现 `hasJoined`/`join`），在服务端容器里 `java /tmp/MockSession.java 8080`（先 `docker cp`），然后**房主和加入者都**用 `AIGUI_JAVA_OPTS="-Dminecraft.api.env=custom -Dminecraft.api.auth.host=http://mcfb-srv:8080 -Dminecraft.api.account.host=… -Dminecraft.api.session.host=… -Dminecraft.api.services.host=…"`（四个 host 都写同一个地址）启动。
- **旧版本客户端**：用 `git archive <提交> | tar -x -C 某目录` 导出旧源码，`docker cp` 进容器，在里面 `./gradlew -I game-args.init.gradle runClient`。老源码的开发运行环境可能缺后来加的依赖（如 owo），会先因为别的通道被拒；要单独验证本模组的协议号，用当前源码把 `BackpackNetwork` 的 `versioned("17")` 临时改成别的值（用完用 `sync.sh` 还原）。
- 面板热键 B 与精妙背包、旅行者背包的默认键冲突，测它们的界面时在 `options.txt` 里把对方的键解绑。
- 向别的玩家发命令：房主打 `give P2 …`；`tp` 要带显式的偏航与俯仰（`tp P2 x y z 0 30`），`facing` 对这个客户端不起作用。

## arm64 的原生库

Apple Silicon 上 Docker 跑的是 arm64 容器，Gradle 却会解析出 x64 的 LWJGL 原生库，`runClient` 会报 `UnsatisfiedLinkError: liblwjgl.so`。`setup.sh` 会从 Maven Central 下载 `natives-linux-arm64` 的七个包（缓存在 `$AIGUI_CACHE`，默认 `~/.cache/mcfb-ai-gui`），放进容器的 `/natives`，并用环境变量 `JAVA_TOOL_OPTIONS=-Dorg.lwjgl.librarypath=/natives` 指过去。**不改项目文件。** x86_64 机器上不需要，脚本会自动跳过。
