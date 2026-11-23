# 验证指南：饰品模组里的专用槽位与通用槽位

容器环境见 [scripts/ai-gui/README.md](../../scripts/ai-gui/README.md)（`mcfb-c1`，必要时 `mcfb-srv`）。开发运行里 Curios 与 Accessories 默认都在（`localRuntime`）。判定优先看日志与存档，不靠 OCR；主观项（图标好不好看、槽位在界面里的位置）由用户确认。

## 一、自动测试（`./gradlew test`）

- `SlotLimitTest`：`allows` 的真值表（已有一只/没有、enforced/不 enforced）。
- 来源表：`Source` 前 6 项序号不变，新 4 项顺序；各来源的顺序数值 0/10/11/12/20/21/22 递增。
- 资源：9 个数据文件、2 张贴图、4 个 tag、语言键齐全；条件 JSON 的 `type`、`mod` 与注册名、配置键对得上；槽位 id 与 tag 文件名一致；两个 `order` 不与原版冲突。
- 接线：`STARTUP` 配置在构造函数里注册；条件类型挂在 `CONDITION_SERIALIZERS`；`registerCurio` 只在 Curios 已加载的路径里；`BackpackAccessory` 的 `canEquipFromUse` 仍为否；`CuriosCompat`/`AccessoriesCompat` 注册三个来源。
- 漂移与依赖规则（058 的）仍通过；jar 里没有 `top/theillusivec4`、`io/wispforest` 的类。
- 宪法修订后的版本号与 Sync Impact Report。

## 二、游戏内（容器，第一步先做 1）

1. **槽位与图标出现**：Curios 的饰品界面里有"浮动背包"槽、图标显示（不是缺失贴图的紫黑块）；Accessories 的界面里同样，并且在"胸部"组里（否则在"未分类"组里，记录下来）。若图标不对，按 research R3 的备选路径改。
2. **专用槽可用**：把浮动背包拖进专用槽 → 放得进；别的模组的背包、普通物品放不进；`B` 键打开的是它；背部渲染画出它（Curios 与 Accessories 各做一遍）。
3. **`back` 不变**：背包放 `back` 槽，行为与 033/034 一致；专用槽与 `back` 各放一只时，`B` 键打开顺序里第一只（Curios：`back` 先），另一只原样。
4. **每类一只**：用数据包或 Curios/Accessories 的配置把 `charm` 调成 3 格，放一只后再放第二只（任意等级）→ 放不进；不同类型各放一只 → 放得进。非 FULL（`curios=GENERIC`）时放得进。命令（`/curios replace` 或 `/give`+`/item replace`）绕过后多出的一只**不被动**。
5. **`charm`**：Accessories 里把浮动背包放进 `charm`，`B` 键与渲染工作；Curios 在有 `charm` 槽的世界里同样（Curios 默认没有，要用数据包给玩家分配 `charm`，只用于验证）。
6. **开关**：把 `mcfloatingbackpack-startup.toml` 的 `[slots] curios = false`，重启：Curios 里没有专用槽，`back`、`charm` 照常，Accessories 的专用槽还在；改回 true 重启，槽位回来。日志里有条件被拒的行（"Skipping loading slot …"）。
7. **槽位消失时的物品**（决定 README 措辞）：专用槽里放一只装了东西的背包，把开关改成 false 重启进世界，看背包去了哪里（物品栏/脚下/丢失）、里面的东西是否完整；Curios 与 Accessories 各一次。**若有任何丢失，立刻停下告诉用户，README 与配置注释改成"先取下"的警告。**
8. **档位**：`curios=GENERIC` → 专用槽里的背包不被当作背着的（`B` 键找不到、背部不画），物品不动；改回 FULL 恢复。专用服务器（`mcfb-srv`）上同样检查一次，并确认开关在专用服务器上启动时被读取（日志）。
9. **联机客户端**：客户端把开关设 false、服务端 true：槽位仍由服务端同步出现（STARTUP 警告的实际情形）；记入 README。
10. **老存档**：背包放在 `back` 槽的旧世界，升级后背包仍在、`B` 键仍打开它。

## 三、不做的验证

- 与别的背包模型的重复渲染；`curio`、`accessories:any`；Accessories 的装饰格行为（只记录观察到的）。
