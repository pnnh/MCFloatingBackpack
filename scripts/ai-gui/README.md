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

## arm64 的原生库

Apple Silicon 上 Docker 跑的是 arm64 容器，Gradle 却会解析出 x64 的 LWJGL 原生库，`runClient` 会报 `UnsatisfiedLinkError: liblwjgl.so`。`setup.sh` 会从 Maven Central 下载 `natives-linux-arm64` 的七个包（缓存在 `$AIGUI_CACHE`，默认 `~/.cache/mcfb-ai-gui`），放进容器的 `/natives`，并用环境变量 `JAVA_TOOL_OPTIONS=-Dorg.lwjgl.librarypath=/natives` 指过去。**不改项目文件。** x86_64 机器上不需要，脚本会自动跳过。
