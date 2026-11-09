# Implementation Plan: 快照管理界面（玩家与管理员面板）

**Branch**: `031-snapshot-gui` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

030 命令的图形界面版本：一个模组自己的独立 `Screen`（不依赖背包），三个入口（命令 `/mcfb snapshot gui`、默认不绑定的快捷键、浮动面板标题栏上的代码绘制按钮），列表 + 版本 + **只读预览**，op 另有范围切换、过滤、恢复、删除、清理。**权限由服务端判断，与命令一致**。读 [research.md](./research.md) 得到的要点：

1. **不需要改 019 的宿主逻辑**：面板在任何非容器界面上只是被隐藏、状态保留（`onScreenOpening` 的既有规则），快照界面关闭后宿主自动回来（D1）。
2. **服务端逻辑抽成 `SnapshotOps`**（不含文字），命令与新 payload 共用，保证"界面与命令对同一玩家同一时刻结果相同"；命令的输出文字不变，030 已验证的行为不回归（D4）。
3. **网络**：4 个请求、5 个响应（含一个空的"打开"通知），协议 15 → 16；预览有服务端大小保护（D2、D3、D5，[contracts/network.md](./contracts/network.md)）。
4. **服务端元数据缓存**：快照文件写入后不再修改，"文件路径 → 元数据"缓存永远正确，翻页不再重复读每个文件（D6）。
5. **界面**：原版 `Button`/`EditBox` 控件 + 自绘列表与预览；布局、状态机、权限判断、请求校验、标题栏几何都是**纯逻辑**，可单测（D8、D9，[contracts/ui.md](./contracts/ui.md)）。

不新增贴图、配方、设置项；不改共用方块图集；快照格式与 030 的写出规则不变。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`Screen`、`Button`、`EditBox`、`KeyMapping`、`CustomPacketPayload`）；无新增依赖，不涉及第三方模组

**Storage**: 无新增（快照文件见 030）；仅内存里的元数据缓存

**Testing**: JUnit 5 纯逻辑：`SnapshotAccessTest`、`SnapshotRequestRulesTest`、`SnapshotScreenStateTest`（权限不变式、按钮可用性、待确认流程、选中/过滤/翻页）、`SnapshotScreenLayoutTest`（各种窗口尺寸不溢出、行数下限）、`TitleButtonsLayoutTest`（与关闭按钮、编号、等级图标不重叠，太窄时隐藏）、`SnapshotResultTextTest`（每个结果码都有语言键）、`SnapshotGuiResourcesTest`（中英文键一致、`gui` 命令消息的英文后备一致）；网络、界面、命令、权限只能在客户端/服务端会话里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 宪法 I（不替换/包装任何界面）、II（服务端权威）、V（客户端会话验证）、VI（无新贴图；服务端读文件有缓存；请求才读）、VII（无新依赖、无设置项）；030 命令的输出文字与行为不变

**Scale/Scope**: 新增 `snapshot/net/`（9 个 payload + 纯数据）、`snapshot/SnapshotOps`、`SnapshotAccess`、`SnapshotRequestRules`；`client/snapshot/`（`SnapshotScreen`、`SnapshotScreenState`、`SnapshotScreenLayout`、`SnapshotClient`）；`client/panel/TitleButtonsLayout`；改 `SnapshotCommands`（用 `SnapshotOps` + `gui` 子命令）、`SnapshotStore`（缓存）、`BackpackNetwork`（注册 + 版本）、`FloatingBackpackKeys`、`FloatingPanelEvents`（按键处理）、`FloatingPanelState`/`FloatingPanelRenderer`/`FloatingPanelInput`（标题栏按钮）、语言文件

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 快照界面是模组自己的 `Screen`，**不替换、不包装、不继承**任何其它界面，也不是宿主；面板在它上面按既有规则隐藏（D1） | PASS |
| II. Server Owns Items | 列表、版本、预览由服务端按权限读取；恢复/删除/清理由服务端校验后执行；客户端只显示；客户端发来的任何"权限"都不被使用 | PASS |
| III. One Feature At A Time | 只做 030 命令的 GUI 版本；不做面板子页面、不做编辑、不做搜索 | PASS |
| IV. Vanilla Path Before Compat | 只用原版控件与绘制；不引用第三方 | PASS |
| V. Verify In A Client Session | quickstart 要求三个入口、两种权限（需 op / 非 op）、预览、动作、窄窗口与大缩放、面板状态不变 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 无新贴图、不碰图集；标题栏按钮代码绘制；服务端只在被请求时读文件且有缓存；预览有大小保护 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无设置项；不涉及任何第三方模组的界面 | PASS |

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── snapshot/
│   ├── SnapshotAccess.java          # 新（纯）：谁能看什么
│   ├── SnapshotRequestRules.java    # 新（纯）：请求字段校验
│   ├── SnapshotOps.java             # 新（服务端）：列表/版本/预览/恢复/删除/清理，不含文字
│   ├── SnapshotStore.java           # 改：元数据缓存
│   ├── SnapshotCommands.java        # 改：用 SnapshotOps；新增 gui 子命令
│   └── net/                         # 新：Row、VersionRow、Scope、ActionKind、ResultCode + 9 个 payload
├── client/snapshot/
│   ├── SnapshotScreen.java          # 新：界面（控件 + 自绘）
│   ├── SnapshotScreenState.java     # 新（纯）：状态机与按钮可用性
│   ├── SnapshotScreenLayout.java    # 新（纯）：布局
│   ├── SnapshotResultText.java      # 新（纯）：结果码 → 语言键与参数
│   └── SnapshotClient.java          # 新：收响应、开界面
├── client/panel/
│   ├── TitleButtonsLayout.java      # 新（纯）：关闭按钮、快照按钮、编号右界
│   ├── FloatingPanelState.java      # 改：hitsSnapshotButton、编号右界
│   ├── FloatingPanelRenderer.java   # 改：画按钮与提示
│   └── FloatingPanelInput.java      # 改：点击打开
├── client/FloatingBackpackKeys.java # 改：SNAPSHOTS（默认不绑定）
├── client/FloatingPanelEvents.java  # 改：按键处理
└── network/BackpackNetwork.java     # 改：注册 9 个 payload，协议 "16"

src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 改：gui.*、key.*、panel.*、gui 命令消息

src/test/java/pnnh/mcfloatingbackpack/
├── snapshot/{SnapshotAccessTest,SnapshotRequestRulesTest}.java
├── client/snapshot/{SnapshotScreenStateTest,SnapshotScreenLayoutTest,SnapshotResultTextTest}.java
├── client/panel/TitleButtonsLayoutTest.java
└── resources/SnapshotGuiResourcesTest.java
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。

## Risks

- **快捷键与宿主界面**（D7）：面板可见时（宿主在场）按这个键能否打开，要在游戏里验证；备选是把处理挪到 `ClientTickEvent.Pre`。
- **抽取 `SnapshotOps` 不能让命令回归**：命令的输出文字不变；已有 `SnapshotCommandResourcesTest` 保护文字，行为由用户在游戏里再验证一遍（030 的 quickstart B）。
- **预览体积**：保护阈值 900,000 字节；超限回 `TOO_LARGE`，界面显示"太大，无法预览"。
- **标题栏很窄**：按钮隐藏（纯逻辑已覆盖），入口仍有命令与快捷键。
