# Implementation Plan: 第一个扩展工具——标签管理器

**Branch**: `018-tag-manager` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-tag-manager/spec.md`

## Summary

新增物品"标签管理器"：放进功能区后左键展开一个覆盖主区域的标签面板，管理主区域**选中物品的分类**的标签（查看、增加、删除，每个分类最多 10 个，多选只显示共同标签）；标签保存在背包的数据里、按物品注册 id 为键。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **普通物品 + "在功能区里"才有功能**：`TagManagerItem`（堆叠 1）进创意栏，**不改任何放入规则**；展开/收起是纯客户端的状态切换（左键、光标空），右键与其他情况走 016 的点击。
2. **标签数据**：`BackpackContents` 增加可选字段 `tags`（`ItemTags`：`ResourceLocation → 标签列表`），随背包保存与升级；每个分类 ≤ 10 个标签、每个标签 1～16 个字符，**小写**可见 ASCII（拒绝大写与空格，不自动转换）；服务端另把分类数封顶 1024。
3. **服务端权威的增删**：`BackpackTagPayload(op, 选区的主格子下标, 标签)`；服务端核对背包与"功能区里有标签管理器"、校验请求、把下标解析成物品 id 并去重，整个请求要么全成要么零变化；之后用 `BackpackTagsPayload` 把整份标签发回客户端。协议 `"7"` → `"8"`。
4. **纯逻辑为主**：`TagRules`、`PresetTags`（10 个稳定英文 id）、`TagMatcher`（前缀 > 子串 > 子序列的模糊匹配）、`TagBook`、`TagRequest`、`TagPanelModel`、`TagPanelLayout`、`TagTextInput` 都不依赖 Minecraft，可单元测试。
5. **标签面板是覆盖主区域的叠层**：展开时命中主区域的点击与滚轮由面板消费（不进入选区/Ctrl 转移/拖动手势，选区保持不变）；用 `fill` 和字体绘制，不新增贴图。
6. **键盘接管看焦点（关键设计）**：没有焦点时**完全不拦截**；文本框有焦点时，容器界面里用可取消的 `ScreenEvent.Key*/CharacterTyped` 拦截；**HUD 下**因为 `InputEvent.Key` 不可取消，编辑期间临时打开一个**透明、不暂停、不绘制的 `TagInputScreen`** 来承载键盘（原版因此释放按键状态，WASD 不再移动角色），编辑结束立即关闭——这是对宪法 I 的一个窄例外，已在 Complexity Tracking 里说明。
7. **配方**：3×3 `线 纸 线 / 皮革 书 皮革 / 纸 墨 纸`（参考精妙背包/旅行者背包"线 + 皮革 + 主题材料"的升级组件，一段式，不用第三方物品）。
8. **图标**：一张 16×16 平面贴图（`item/generated`），先用 ludo.ai 生成再整理成 16×16，失败则自绘；记录来源。

范围：只做标签管理器本身；垃圾桶、标签显示/筛选/重命名、跨背包共享都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（数据组件与编码、`ScreenEvent`、`GuiGraphics`、`PacketDistributor`、数据驱动配方）；无新增依赖，不涉及第三方模组（只读过两个模组的配方 JSON 作设计参考）

**Storage**: 物品数据组件 `BACKPACK_CONTENTS` 新增可选字段 `tags`；界面状态（展开、输入框内容、滚动）只在客户端会话里，不持久化

**Testing**: JUnit 5 纯逻辑测试：`TagRules`、`PresetTags`、`TagMatcher`、`TagBook`、`TagRequest`、`TagTextInput`、`TagPanelModel`、`TagPanelLayout`；资源测试：解析配方 JSON 与两份语言文件的键；加客户端会话手测（quickstart，宪法 V），HUD 与打开界面都要测，重点是键盘接管

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（物品 + 存储 + 网络 + 客户端界面与输入）

**Performance Goals**: 面板每帧只重算选中物品的分类与标签（≤ 108 格、每分类 ≤ 10 个标签）；一次增删一个请求、一次整份同步

**Constraints**:
- 标签的增删由服务端校验并执行，服务端不信任客户端的物品 id；整个请求要么全成要么零变化（宪法 II）
- 没有焦点时不得拦截任何键盘事件；HUD 下 WASD 照常
- 011～017 的行为、放入规则、选区、手势、合法尺寸全部不变
- 只新增一张 16×16 贴图与语言条目（宪法 VI）；协议版本升到 `"8"`

**Scale/Scope**: 约 14 个新类（物品、`TagRules`、`PresetTags`、`TagMatcher`、`TagBook`、`ItemTags`、`TagRequest`、两个网络包、`TagPanelState`、`TagPanelModel`、`TagPanelLayout`、`TagTextInput`、`TagInputScreen`、渲染与输入的接入类）；改动 `BackpackContents`、`BackpackInventory`、`BackpackUpgradeRecipe`、`BackpackNetwork`、`ClientBackpackView`、`FloatingPanelInput`、`FloatingPanelEvents`、`FloatingPanelRenderer`、`ModItems`；约 8 个新测试类；资源：配方、模型、贴图、语言

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 浮动面板与标签面板仍是叠层，打开路径不变；**HUD 编辑文本时**临时打开一个透明输入屏幕作为键盘承载体，仅此一处，编辑结束即关闭（见 Complexity Tracking） | PASS（有一处已说明的窄例外） |
| II. Server Owns Items | 标签的增删与上限在服务端校验、保存；客户端只发选区下标与标签，不发物品 id；服务端核对工具存在；整个请求原子 | PASS |
| III. One Feature At A Time | 只做标签管理器（物品、配方、图标、标签数据、面板、文本输入）；垃圾桶、标签显示/筛选/重命名明确不做；键盘承载屏幕是它的实现需要，不另开功能 | PASS |
| IV. Vanilla Path Before Compat | 配方只用原版材料与 NeoForge 通用标签；不引用精妙背包、旅行者背包的任何物品或类型 | PASS |
| V. Verify In A Client Session | quickstart 46 项逐场景要求客户端验证（HUD/容器界面、键盘接管、ESC、持久化、升级、旧存档）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 只新增一张 16×16 物品贴图（物品图集，不进方块图集的大图），面板全是 `fill`；实现完成时度量一次图集作回归，生成来源记录在 DELIVERY | PASS（已规划） |

**Re-check after Phase 1 design**：除上面 I 的一个窄例外外，没有新增违反项。实现阶段要盯住的风险：
1. **透明输入屏幕**：打开/关闭时的闪烁、鼠标抓取、`ScreenEvent.Opening` 误关面板、系统热键、焦点丢失后移动键恢复——必须在 HUD 与容器界面两种情况下实测（research Decision 11）；
2. **`InputEvent.Key` 是否在被 `ScreenEvent` 取消的按键上跳过**：源码看起来是先 `return`，需要实测，否则 `onKey` 里要靠"编辑中直接返回"兜底（已设计）；
3. **存储与同步**：`BackpackContents` 编码改变、会话包字段已满所以另发标签包、协议升到 8；旧背包读成空（research Decision 2/6）；
4. **展开期间主区域被面板消费**：不能漏出任何点击到选区/Ctrl 转移/拖动手势。

## Project Structure

### Documentation (this feature)

```text
specs/018-tag-manager/
├── plan.md              # 本文件
├── research.md          # Phase 0：14 条有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：规则、预设、TagBook、ItemTags、面板状态/模型/布局、资源清单
├── quickstart.md        # Phase 1：46 项客户端验收步骤
├── contracts/
│   ├── tags-network-storage.md   # Phase 1：数据组件、协议 8、请求校验、同步
│   └── tag-panel-ui.md           # Phase 1：展开/收起、输入分派、键盘焦点模型、输入屏幕衔接
├── checklists/requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/
│   ├── TagManagerItem.java            # 新增：普通 Item（堆叠 1）
│   └── ModItems.java                  # 修改：注册 tag_manager、进创意栏
├── inventory/
│   ├── tags/
│   │   ├── TagRules.java              # 新增：纯逻辑，合法字符/长度/过滤
│   │   ├── PresetTags.java            # 新增：纯逻辑，10 个预设 id 与语言键
│   │   ├── TagMatcher.java            # 新增：纯逻辑，前缀 > 子串 > 子序列
│   │   ├── TagBook.java               # 新增：纯逻辑泛型，增删/交集/上限/原子性
│   │   ├── TagRequest.java            # 新增：纯逻辑，请求校验
│   │   └── ItemTags.java              # 新增：MC 记录 + CODEC/STREAM_CODEC
│   ├── BackpackContents.java          # 修改：增加 tags 字段
│   ├── BackpackInventory.java         # 修改：tags()、读写
│   └── recipe/BackpackUpgradeRecipe.java  # 修改：升级时保留 tags
├── network/
│   ├── BackpackTagPayload.java        # 新增：客户端→服务端，增删请求与服务端处理
│   ├── BackpackTagsPayload.java       # 新增：服务端→客户端，整份标签同步
│   └── BackpackNetwork.java           # 修改："7" → "8"，注册两个包，sendSession(open) 后发标签
└── client/
    ├── FloatingPanelEvents.java       # 修改：hostsPanel、ScreenEvent 键盘钩子、onKey 编辑中返回、每 tick 同步
    └── panel/
        ├── tags/
        │   ├── TagPanelState.java     # 新增：界面状态单例
        │   ├── TagPanelModel.java     # 新增：纯逻辑，显示内容
        │   ├── TagPanelLayout.java    # 新增：纯逻辑，排版与命中
        │   ├── TagTextInput.java      # 新增：纯逻辑，文本编辑
        │   ├── TagPanelInput.java     # 新增：与游戏相连，点击/键盘/发请求
        │   ├── TagPanelRenderer.java  # 新增：用 fill 与字体绘制
        │   └── TagInputScreen.java    # 新增：透明、不暂停的键盘承载屏幕（仅 HUD 编辑时存在）
        ├── ClientBackpackView.java    # 修改：保存 ItemTags
        ├── FloatingPanelInput.java    # 修改：功能区里标签管理器的左键切换；展开时主区域输入交给面板
        └── FloatingPanelRenderer.java # 修改：展开时绘制标签面板并隐藏主区域格子/选区/箭头

src/main/resources/
├── data/mcfloatingbackpack/recipe/tag_manager.json
└── assets/mcfloatingbackpack/
    ├── models/item/tag_manager.json
    ├── textures/item/tag_manager.png
    └── lang/en_us.json、zh_cn.json    # 修改

design/tools/make_tag_manager_icon.py   # 新增：把生成的大图整理成 16×16（或手绘网格），不随模组发布

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/tags/{TagRulesTest, PresetTagsTest, TagMatcherTest, TagBookTest, TagRequestTest}.java
├── client/panel/tags/{TagTextInputTest, TagPanelModelTest, TagPanelLayoutTest}.java
└── resources/TagManagerResourcesTest.java   # 配方 JSON 与语言键

specs/016、017 与 docs/MCFloatingBackpack.md   # 修改：加 018 说明
```

**Structure Decision**: 沿用单模块。规则、排版、编辑、模型都是不依赖 Minecraft 的纯类；与游戏相连的部分（输入、渲染、输入屏幕、网络）单独成类；标签子系统放在 `inventory/tags` 与 `client/panel/tags` 两个子包里，避免挤进已有的大类。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 宪法 I：HUD 下编辑标签文本时临时打开一个透明输入屏幕（`TagInputScreen`），把 `Minecraft.screen` 用作键盘承载体 | 没有界面时按键直接进 `KeyMapping`，`InputEvent.Key` 不可取消、也没有字符事件，无法既把字符送进文本框、又让 WASD 不再移动角色；用户明确要求"焦点在文本框时才接管键盘、HUD 下没有焦点时 WASD 照常" | ① 在 `InputEvent.Key` 里自己把按键转成字符并每 tick 清 `KeyMapping`：依赖键盘布局、按键状态会闪烁，不可靠；② mixin `KeyboardHandler`：侵入大、维护成本高；③ 始终打开一个屏幕：违背宪法 I 且没有焦点时也会拦截。该屏幕只在编辑时存在、不绘制、不暂停，浮动面板与标签面板仍是叠层，面板的打开路径没有变化 |
