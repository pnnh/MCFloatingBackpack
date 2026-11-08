# DELIVERY: 005-item-backpack

**Date**: 2026-10-25  
**Status**: A/C/E source complete. `./gradlew compileJava test` **BUILD SUCCESSFUL**. Manual `runClient`: T025 done, T024 partial (see 人工验证 section at the end — one bug found and fixed, awaiting re-test).

> **[009 / 2026-11-04 revision]** The table below is the state at delivery (2026-10-25) and is partly outdated: chest equip is now `Equipable` (not an `ArmorItem`), the back layer renders the item's own sculpted-mesh model (no placeholder box; see specs/006 and 009), capacity is per tier (006), the panel is no longer a fixed 4×9 (006/007), and the dev JEI is `19.70.0.469` (see `build.gradle`).

## Implemented (A / C / E)

| Area | Notes |
| --- | --- |
| Item + component | `leather_floating_backpack`, `BackpackContents` data component, 36 slots |
| Attachment removed | `BackpackAttachments` deleted; inventory bound to ItemStack |
| Open gate | B / main-hand use → `BackpackOpenRequestPayload`; deny message if none |
| Resolve order | MAIN_HAND → OFF_HAND → CHEST → INV 0–35 |
| Chest equip | Extends `ArmorItem` leather chestplate; Shift-use keeps vanilla equip |
| Back render | `BackpackBackLayer` placeholder box when chest slot is backpack |
| Panel | 4×9 layout via `PanelSlots` |
| JEI | `localRuntime` JEI 19.25.0.325 unless `-PnoJei`; optional in mods.toml |
| Recipe / lang / textures | Shaped recipe + en/zh + placeholder PNGs |

## Verify

```bash
./gradlew test compileJava   # done — success
./gradlew runClient          # pending manual (quickstart.md)
```

## Environment notes (this machine)

- JVM PKIX failed against Maven/Gradle HTTPS (Avast/corporate intercept). Workaround:
  - Local Gradle zip: `gradle/wrapper/gradle-9.2.1-bin.zip` (gitignored); `distributionUrl=gradle-9.2.1-bin.zip`
  - Custom truststore: `.gradle-truststore.jks` + user `~/.gradle/gradle.properties` `javax.net.ssl.trustStore=...`
- Machines with a healthy Java trust store can restore the normal Gradle distribution URL.

## 人工验证（2026-11-04，runClient）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选（2026-11-04）。清单只记录了通过与否，**没有记录每项的细节数据**；没勾的项都是**未验证**，不是通过。

| quickstart 部分 | 结果 |
| --- | --- |
| 门禁 1（无背包按 B 有提示）、2（不开物品栏按 B 开关，ESC 不进暂停菜单）、4（暂停/设置菜单不显示，E 物品栏共存） | 通过 |
| 门禁 3（主手持背包、不瞄准方块右键切换面板） | 发现缺陷 BUG-005-1，已修复，**用户重测确认已修复**（见下） |
| 多背包顺序（主手优先、清空后开槽 12） | 通过 |
| 内容随物品（丢出再捡、放进箱子再取） | 通过 |
| 胸甲与背负（装备后 F5 可见、仅胸甲时 B 可开、卸下后无效）与预期失败 | 通过 |
| 回归：持有背包时面板与原版物品栏/箱子互拖（003 路径） | 通过 → T025 完成 |
| JEI / 依赖（JEI 能搜到、`mods.toml` 未把 JEI 标 required、无 JEI 实例可启动） | **未验证**（合并清单没有覆盖） |
| 回归：玩家不再拥有无物品的全局 Attachment 背包 | **未单独验证** |

### 缺陷 BUG-005-1：右键使用背包时面板反复开关、手臂反复挥动

- **现象**：不瞄准方块右键一次，面板打开后自动关闭、再自动打开，循环，右手反复播放挥动动画。**不是每次都出现**，没有确定的复现路径；出现时按 E 打开一次原版物品栏，闪烁消失，面板保持打开。之前没出现过。
- **原因（读源码得出，尚未在游戏里复现确认）**：
  1. 右键使用背包会让客户端向服务端请求打开，面板要等服务端回包才显示（有一个往返延迟）。
  2. 面板显示后，`FloatingPanelInput.onMouseButton` 对**松开**事件的处理是"只要光标在面板范围内就吞掉"（`RELEASE && state.contains(...)`，自 M1 起就有）。面板打开时会释放鼠标，光标被放到窗口中央，正好在面板里。
  3. 如果玩家在面板出现**之后**才松开右键（点击稍慢，或回包稍快），这个松开事件被吞，原版的 `KeyMapping` 没收到松开，"使用"键一直是按下状态。
  4. 原版在"使用"键按住时每 4 tick 重复执行一次使用物品；背包的使用是切换面板，于是面板每 4 tick 开/关一次，每次都带挥手动画。
  5. 按 E 打开原版物品栏会调用 `KeyMapping.releaseAll()`，把卡住的按键状态清掉，所以闪烁消失。
  - "不是每次都出现"符合这个机制：松开发生在面板出现之前就没事，之后就卡住，取决于点击时长与网络延迟。
- **修复**（`compileJava test` 通过，新增 `ConsumedPressTrackerTest` 4 个用例）：
  - `ConsumedPressTracker`：记录哪些按键的**按下**被面板吞了，只有对应的**松开**才吞；其余松开放行，让游戏正常释放按键。拖拽、滚动条、缩放的松开仍然被吞（它们都从被吞的按下开始）。面板隐藏时收到鼠标事件就清空记录，避免残留。
  - `ClientBackpackActions.toggleFromUse()` 里先 `keyUse.setDown(false)`：即使按键卡住或长按，一次按下也只切换一次，不再每 4 tick 重复。
- **验证**：2026-11-04 用户在游戏里重测，**确认右键闪烁已修复**。注意：该缺陷没有稳定复现路径，我也没有复现过；用户只报告了"已修复"，没有记录具体试了哪些条件（快速点击/按住稍久/高延迟）。因此这是"在日常使用中不再出现"，不是"在高延迟下被证明不会出现"。

## 2026-11-06：用户人工验证（清单 A–D 组）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选并备注；清单只记录通过与否和少量备注，没有逐项数据。没勾的项仍是**未验证**。

| 项 | 结果 |
| --- | --- |
| M1 `runClient` 中 JEI 能搜到浮动背包 | 通过 |
| M2 `mods.toml` 未把 JEI 标为 required；`-PnoJei` 的实例能启动、功能正常、日志无 JEI 相关错误/警告 | 通过 |
| M3 玩家不再拥有无物品的全局 Attachment 背包 | 通过 |

之前"未验证"的 JEI/依赖部分与 Attachment 回归至此补齐，**T024 勾选**。
