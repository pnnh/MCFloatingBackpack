# 交付记录：063 网络终端存入的误导 ERROR 日志

## 做了什么

- `OpenContainerDelivery.deliver` 的守恒检查从"总数必须相等"改成三分类（`absorbedOrInconsistent(before, after, taken)`）：
  - 相等 → 守恒，不写日志（同以前）；
  - 减少，且减少量 ≤ 取走的数量（投递数 − 剩下数）→ **被吸收**（物品进了菜单格子看不到的地方，如 ME 网络），`Result.absorbed` 记数量，只写一条 DEBUG；
  - 增加（复制），或减少量超过取走的数量（别的物品也没了）→ **不一致**，仍是 ERROR，措辞不变。
- `Result.conserved` 的含义放宽为"没有复制也没有多丢"（相等或被吸收）；唯一的调用方是 `BackpackQuickTransferPayload`。
- 不新增包（协议号仍 22）、`DataRevision` 仍 6、通用路径不引用任何第三方类型（接线测试断言）。
- 待办 3（集成终端背包重画的残留）重新核对后**不是缺陷，已关闭**，写在 060 的 DELIVERY 里；060 的"ERROR 仍在"一条改为"已由 063 处理"。

## 自动测试

`./gradlew test -PcleanRuntime -PnoJei`：**246 个测试类 / 1881 项，0 失败**（基线 245 / 1872；新增 `NetworkDeliveryLogWiringTest` 与 `OpenContainerDeliveryTest` 里 8 项：普通容器、全吸收、部分吸收、收 0 个、复制、丢别的、边界，原有用例不变）。

## 游戏内验证（容器 `mcfb-c1`，AE2 19.2.20：ME 箱子 + 1k 元件 + 能量元件 + 线缆 + ME 终端）

| # | 情形 | 结果 |
|---|---|---|
| 1 | 面板（铁背包：石头 5、泥土 7、苹果 3、圆石 32、铁锭 20）在 ME 终端上 Ctrl+点击圆石、铁锭、石头 | ✓ 终端网格出现圆石 32、铁锭 20、石头 5；面板里这三格清空；`latest.log` 没有 "changed the item total"，ERROR 只有启动期的 5 条无关项；`debug.log` 有三条 DEBUG："Quick transfer into open container: 32 of 32 minecraft:cobblestone went outside the menu slots (menu appeng.menu.me.common.MEStorageMenu)"（铁锭 20/20、石头 5/5 同） |

## 没验证

- 容器里没有再在**普通箱子**上存一遍确认"没有新日志"：这一路由单元测试覆盖（普通容器 `absorbed == 0`、守恒，且 `conserved && absorbed > 0` 才写 DEBUG），游戏内没复测。
- "复制"与"多丢"两类只在假菜单里构造过，没有真实模组会触发。
- 已知盲区（research R2）：吸收的同时又多丢了不超过取走数量的别的同种物品，通用路径看不出来。

## 宪法复查

II（服务端）、III（一次一件）、V（游戏内验证已做，没验证的如实写明）、VII（通用路径无第三方类型）：无违规。
