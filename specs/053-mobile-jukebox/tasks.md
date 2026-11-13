# Tasks: 移动唱片机

**Input**: `/specs/053-mobile-jukebox/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 列表读取、状态机、页几何、右区各一个用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键用资源测试；接线用源码文本检查。**页的实际外观与按钮、自动接歌的状态、声音实例的建立只能在客户端会话里验证**（宪法 V）；容器里 OpenAL 打不开，听觉由用户确认。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项——声音与手感——只由用户确认）
- 不新增配置项、网络包、依赖、mixin，不用反射与访问转换器；协议号仍 19；**数据修订 3 → 4**；011～052 的测试除写死 3 的修订断言外不改
- 用户故事：US1 物品、配方、右区各一个；US2 播放控制；US3 播放列表读全与单曲循环、随机；US4 数据与版本

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 191 个类、1476 个用例，0 失败）；记在 `specs/053-mobile-jukebox/DELIVERY.md`（新建，中文）；宪法 VI "之前"度量：`design/demo/tools/check_atlas_budget.sh`（预期 2048x1024）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 `…/inventory/ToolSingleRules.java`：`check(targetArea, area, incoming, slotIndex, areaSlots, isKind) → OK | DUPLICATE`（contracts/rules.md）；单元测试 `…test/inventory/ToolSingleRulesTest.java`
- [X] T003 [P] `…/jukebox/PlaylistReader.java`：`read(visibleRows, category, keyOf) → List<Track<T>>`；单元测试 `PlaylistReaderTest`（连续、被别的类别隔开、空格、非唱片、多行混合、重复歌曲、没有唱片行）
- [X] T004 `…/jukebox/JukeboxPlayer.java`：状态机（contracts/rules.md "序列规则"）；单元测试 `JukeboxPlayerTest`（顺序循环、单曲循环、随机固定种子 1000 轮"每轮每首恰好一次"与"轮间不相邻重复"、上一曲历史、列表中途变化、列表变空、倒带、停止后再播放）；变异检查：把随机的"避免轮间相邻重复"去掉，测试应失败，再还原
- [X] T005 [P] `…/client/panel/jukebox/JukeboxPanelLayout.java`（纯几何）；单元测试 `JukeboxPanelLayoutTest`
- [X] T006 运行 `./gradlew compileJava test`

**Checkpoint**: 规则就绪，不启动游戏即可测试

## Phase 3: User Story 1 - 物品、配方、右区各一个 (Priority: P1)

- [X] T007 [US1] `…/item/MobileJukeboxItem.java`；`…/item/ModItems.java` 注册 `MOBILE_JUKEBOX`、创造栏（自动拾取之后）；`never_trash.json` 加它
- [X] T008 [P] [US1] `design/demo/tools/make_mobile_tool_icons.py`（复用 036 的底板与图案函数）生成 `…res/textures/item/mobile_jukebox.png`；模型 `…res/models/item/mobile_jukebox.json`；目视检查放大预览
- [X] T009 [P] [US1] 配方 `…data/recipe/mobile_jukebox.json`；语言键（contracts/resources.md，含页上的按钮、开关、提示、限制消息）中英文
- [X] T010 [US1] 右区各一个：`…/network/BackpackClickPayload.java`（服务端，被拒时动作栏提示 `limit.mobile_jukebox`）与 `…/client/panel/FloatingPanelInput.java`（客户端预览，聊天提示）各接一处 `ToolSingleRules`
- [X] T011 [US1] `…/inventory/revision/DataRevision.java`：`CURRENT = 4`（类注释加一句）；改 `DataRevisionTest`、`TankWiringTest`、`PipeWiringTest`、`LightWiringTest` 里写死 3 的断言
- [X] T012 [P] [US1] 资源测试 `…test/resources/MobileJukeboxResourcesTest.java`
- [X] T013 运行 `./gradlew compileJava test`

**Checkpoint**: 物品可合成、可放进右区、每区一个（页还没有）

## Phase 4: User Story 2 + 3 - 播放控制页与播放器 (Priority: P1) 🎯 MVP

- [X] T014 [US2] `…/client/jukebox/MobileJukeboxSound.java`（`AbstractSoundInstance`：`RECORDS`、`relative`、`Attenuation.NONE`）；`…/client/jukebox/JukeboxPlayback.java`（读背包栈与行、计时、开始 / 停止、停止条件、状态快照）
- [X] T015 [US2] `…/client/panel/jukebox/JukeboxPanelState.java`、`JukeboxPanelInput.java`、`JukeboxPanelRenderer.java`（填充与文字，不用贴图；按钮悬停提示）
- [X] T016 [US2] 挂接：`FloatingPanelInput`（点右区唱片机展开、与垃圾桶页互斥、`onPress` 先于面板命中测试）、`FloatingPanelRenderer`（画页）、`ClientBackpackView.hasMobileJukebox()`、`FloatingPanelEvents`（收起条件、关闭面板 / 登录 / 登出的重置与停止、客户端 tick 驱动、滚轮被吞）
- [X] T017 [P] [US2] 接线测试 `…test/resources/JukeboxWiringTest.java`（contracts/resources.md "测试"）
- [X] T018 运行 `./gradlew compileJava test`

**Checkpoint**: 全部代码完成，等游戏内验证

## Phase 5: 游戏内验证与文档

- [X] T019 游戏内验证（AI 自动验证，容器，quickstart B1～B10；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"；听觉与手感写"待用户确认"
- [X] T020 宪法 VI "之后"度量：`check_atlas_budget.sh`，与 T001 对比，都 ≤ 2048；没量写"未度量"
- [X] T021 [P] `README.md` 与 `README_CN.md`：移动唱片机一节；`docs/plan/mobile-tools.md` 状态改为"053 已实现"
- [X] T022 `specs/053-mobile-jukebox/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、**数据修订 3 → 4 的理由与协议号不变**、已知局限（别的玩家听不到、没有暂停、别的模组唱片）、图集前后尺寸、宪法复查
- [X] T023 最终检查：`./gradlew compileJava test` 全部通过；`git status` 无临时修改残留；**不提交、不打 tag**
- [ ] T024 用户确认：声音是否在耳边、音量跟随滑条、衔接是否自然、背景音乐是否停掉、真机点页与按钮；**只有用户明确确认才勾**

## Dependencies

- T002～T005 大体并行（T004 最大）；T006 在其后。
- T007 → T008 / T009 / T012 并行；T010 依赖 T002 与 T009；T011 独立；T013 在 Phase 3 之后。
- T014 依赖 T003、T004；T015 依赖 T005、T014；T016 依赖 T015；T017 依赖 T014～T016。
- T019 依赖全部代码；T020～T022 在 T019 之后。

## Implementation Strategy

- **MVP**：Phase 1～4。一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 状态机最容易出错：先把 `JukeboxPlayer` 与它的测试做扎实再写客户端。
- 不提交；做完、用户确认后由用户决定何时提交。
