# Implementation Plan: 网络终端存入的误导 ERROR 日志

**Branch**: `063-network-delivery-log` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

## Summary

`OpenContainerDelivery.deliver` 的守恒检查数的是"菜单所有格子里同种物品的总数"。AE2 ME 终端等网络终端的 `quickMoveStack` 把物品放进网络，离开了所有格子，总数就少了，于是每次存入都被当成"总数变了"写一条 ERROR。本 feature 把检查从"必须相等"改成三分类（详见 [research.md](./research.md)）：

1. **守恒**：投递前后总数相等 → 不写日志（同现在）。
2. **被吸收**：总数减少，减少量 `0 < 减少 ≤ 取走的数量`（取走 = 投递数 − 剩下数）→ 物品进了格子看不到的地方，只写一条 DEBUG。
3. **不一致**：总数增加，或减少量超过取走的数量 → 仍写 ERROR（措辞不变）。

实现：`Result` 增加 `absorbed`（int）字段；`conserved` 的含义改为"没有复制也没有多丢"（相等或被吸收）；新增纯函数 `absorbedOrInconsistent(before, after, taken)`；`BackpackQuickTransferPayload` 对被吸收写 DEBUG、对不一致写 ERROR。

不做：任何第三方类型引用（通用路径）、新网络包、改协议号（22）、改 `DataRevision`（6）、网络终端专用桥（058/060 已有）。

## Technical Context

**Language/Version**: Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1 · 无新依赖
**Testing**: JUnit 5（假菜单加 NETWORK / DUPLICATES / LOSES_OTHERS 三种模式）+ 源码接线检查；游戏内：容器 `mcfb-c1` 的 AE2 ME 终端
**Constraints**: 宪法 II（服务端）、III（一次一件）、V（游戏内验证）、VII（通用路径不引用第三方类型）

## Constitution Check

| 原则 | 评估 |
|---|---|
| II. Server Owns Items | 只改服务端投递结果的判定与日志 — PASS |
| III. One Feature At A Time | 只改日志判定 — PASS |
| V. Verify In A Client Session | 容器里 ME 终端存入、看日志 — PASS（已规划） |
| VII. Minimal Dependencies | 通用路径，无第三方类型，接线测试断言 — PASS |

设计后复核：无违规项。

## Project Structure

```text
specs/063-network-delivery-log/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md · tasks.md
src/main/java/pnnh/mcfloatingbackpack/inventory/OpenContainerDelivery.java        # Result + 分类
src/main/java/pnnh/mcfloatingbackpack/network/BackpackQuickTransferPayload.java   # 日志分级
src/test/java/…/inventory/OpenContainerDeliveryTest.java                         # 分类用例
src/test/java/…/resources/NetworkDeliveryLogWiringTest.java                      # 日志接线
```
