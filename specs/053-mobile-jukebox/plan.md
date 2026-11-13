# Implementation Plan: 移动唱片机

**Branch**: `053-mobile-jukebox` | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

## Summary

新物品 `mobile_jukebox` 放进右区、左键展开"播放控制"页；页里的按钮驱动一个**纯客户端**的播放器，播放按 B 的背包里可见唱片扩展行的唱片（设计依据见 [research.md](./research.md)）：

1. **物品与规则**：`MobileJukeboxItem`（仿 `TrashCanItem`，什么也不做）、模型、手绘贴图、配方、语言键、`never_trash`、创造栏；右区"各至多一个"用一个新的纯逻辑 `ToolSingleRules`（泛化 `ToolLightRules` 的模式：指定生效的区、判定某种工具），服务端（`BackpackClickPayload`）与客户端预览（`FloatingPanelInput`）各接一处，不改 `ToolAreaRules` 的签名。
2. **读列表（纯逻辑）**：`PlaylistReader` 在"可见行列表 + 判定是不是唱片"上取出类别 `disc` 的行里的唱片（行序、格子序，跳过空格、非唱片、其它类别的行、隐藏行）；MC 层从**客户端上这只背包的栈**读（`BackpackRef.resolve(player)` → `BACKPACK_CONTENTS` → `rows().visible()`），所以**面板关着也能读**（面板的 `ClientBackpackView` 在面板关闭时会清空，不能用它）。
3. **播放状态机（纯逻辑）**：`JukeboxPlayer`：播放 / 停止、倒带、下一曲 / 上一曲、单曲循环、列表随机（洗牌 + 历史），每次操作拿"当前列表"作参数，返回要执行的命令（开始某一首 / 停止 / 无）；随机用注入的 `RandomSource`/`Random`，可测。
4. **声音与计时（MC 层，客户端）**：`JukeboxPlayback`：持有状态机与当前声音实例；每个客户端 tick 检查（不在暂停界面时计时）：这首放完（用 `JukeboxSong.lengthInSeconds` 的计时，**不靠 `isActive`**，因为音量为 0 时引擎根本不创建通道）→ 状态机的"放完了"；右区没有移动唱片机 / 背包换了 / 列表空了 / 退出世界 → 停止并清状态。声音实例是 `AbstractSoundInstance` 的小子类：`SoundSource.RECORDS`、`relative = true`、`Attenuation.NONE`（在耳边），播放前 `MusicManager.stopPlaying()`，`Gui.setNowPlaying(曲名)`。**不用反射或访问转换器**，所以没有暂停。
5. **页（客户端，仿垃圾桶页）**：`JukeboxPanelLayout`（纯几何）、`JukeboxPanelState`（展开与否）、`JukeboxPanelInput`（按下、滚轮）、`JukeboxPanelRenderer`（填充与文字，不用贴图）；与垃圾桶页同位置，**互斥**（展开一个收起另一个）；页挂接点与垃圾桶页一一对应（输入分发、渲染、滚轮、关闭面板 / 登录 / 登出 / 客户端 tick 的收起条件）。
6. **数据修订 3 → 4**（新的只能存在背包里的物品种类，同 051），协议号不变（19）；同改 `DataRevisionTest` 与写死 3 的历史测试；无新网络包、存储字段、配置项、依赖、mixin。
7. **测试**：纯逻辑（列表读取、状态机含固定种子的 1000 轮随机、页几何、右区各一个）、资源（物品、模型、贴图、配方、语言键、`never_trash`）、源码文本检查（接线、没有反射与访问转换器、没有新包、修订 4）；游戏里（容器）验证页、按钮与状态变化（听觉由用户确认）。

## Technical Context

**Language/Version**: Java 21；Python 3（开发期手绘脚本，Pillow）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无新增；播放状态只在客户端内存

**Testing**: JUnit 5；纯逻辑 + 资源 + 源码文本检查；容器里 OpenAL 打不开（日志有 `Failed to open OpenAL device`），所以声音只能验证"状态与计时"，听觉由用户确认

**Target Platform**: 客户端（播放与页）；服务端只多一条右区放置规则

**Constraints**: 宪法 I（页是面板的一部分，不新增界面）、II（放置规则服务端校验；播放是客户端体验，不是物品状态）、III（一个 feature）、V（客户端会话验证）、VI（1 张 16×16 进共享图集，前后各量一次）、VII（无新依赖、不碰内部接口）；协议 19 不变、修订 3 → 4

**Scale/Scope**: 新增约 9 个类（4 个纯逻辑：`PlaylistReader`、`JukeboxPlayer`、`ToolSingleRules`、`JukeboxPanelLayout`；5 个 MC 客户端：`JukeboxPlayback`、`MobileJukeboxSound`、`JukeboxPanelState`/`Input`/`Renderer`）、1 个物品类、1 个模型、1 张贴图、1 个配方、语言键约 14 条 × 2 语言、1 个手绘脚本、约 6 个测试类；改 `ModItems`、`BackpackClickPayload`、`FloatingPanelInput`、`FloatingPanelRenderer`、`FloatingPanelEvents`、`DataRevision`、`never_trash.json`

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 播放控制页是浮动面板的一部分（同垃圾桶页），不替换也不包装任何界面 | PASS |
| II. Server Owns Items | 右区"各一个"服务端校验；播放是客户端的声音，不改任何物品状态 | PASS |
| III. One Feature At A Time | 只做移动唱片机；工作台、熔炉是 054、055 | PASS |
| IV. Vanilla Path Before Compat | 纯原版（`JukeboxSong` 的数据组件，别的模组的唱片只要走它也行） | PASS |
| V. Verify In A Client Session | 容器里验证页与状态；听觉待用户 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 新增 1 张 16×16 进共享图集：前后各量一次 | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；刻意不用反射与访问转换器 | PASS |

设计后复核：无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/053-mobile-jukebox/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md        # 列表读取、状态机、右区各一个、页的行为与几何
│   └── resources.md    # 物品、配方、贴图、语言键
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/MobileJukeboxItem.java                 # 新：什么也不做的工具物品
├── item/ModItems.java                          # 改：注册、创造栏
├── inventory/ToolSingleRules.java              # 新（纯）：某区里某种工具至多一个
├── jukebox/PlaylistReader.java                 # 新（纯）：从可见行读出唱片
├── jukebox/JukeboxPlayer.java                  # 新（纯）：播放状态机
├── client/jukebox/JukeboxPlayback.java         # 新（MC，客户端）：读背包、计时、声音、停止条件
├── client/jukebox/MobileJukeboxSound.java      # 新（MC，客户端）：耳边的声音实例
├── client/panel/jukebox/JukeboxPanelLayout.java   # 新（纯）：几何
├── client/panel/jukebox/JukeboxPanelState.java    # 新：展开与否
├── client/panel/jukebox/JukeboxPanelInput.java    # 新：按下与滚轮
├── client/panel/jukebox/JukeboxPanelRenderer.java # 新：绘制
├── client/panel/FloatingPanelInput.java        # 改：点工具展开、互斥、分发、客户端预览的限制与提示
├── client/panel/FloatingPanelRenderer.java     # 改：画页
├── client/panel/ClientBackpackView.java        # 改：hasMobileJukebox()
├── client/FloatingPanelEvents.java             # 改：收起条件、重置、滚轮、客户端 tick 调 playback
├── network/BackpackClickPayload.java           # 改：服务端右区各一个的检查与提示
└── inventory/revision/DataRevision.java        # 改：CURRENT = 4

src/main/resources/
├── assets/mcfloatingbackpack/models/item/mobile_jukebox.json                 # 新
├── assets/mcfloatingbackpack/textures/item/mobile_jukebox.png                # 新（手绘）
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json                         # 改
├── data/mcfloatingbackpack/recipe/mobile_jukebox.json                        # 新
└── data/mcfloatingbackpack/tags/item/never_trash.json                        # 改

design/demo/tools/make_mobile_tool_icons.py     # 新（开发期，不进发行包）；以后工作台、熔炉的图标也加在这里
src/test/java/…                                  # 新约 6 个；改 DataRevisionTest 与写死 3 的历史测试
```

**Structure Decision**: 纯逻辑放 `jukebox/`、`inventory/`（无 Minecraft 类型，可测）；客户端类放 `client/jukebox/` 与 `client/panel/jukebox/`；页的结构与垃圾桶页一一对应，便于以后第三个页也用同一套挂接。

## Complexity Tracking

无宪法违规项。
