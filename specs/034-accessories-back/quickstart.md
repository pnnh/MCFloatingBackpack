# Quickstart: Accessories 后背槽兼容验证

先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`：有客户端在运行就不要再启动。`./gradlew runClient` 默认带着 Curios、Accessories（和 owo-lib）、精妙背包、旅行者背包、Sodium + Iris、JEI。用 2026-11-09 之后新建的开发世界。

## A. 放得进去、背着、按 B 打开、背上没有默认外观（US1）

1. 打开 Accessories 界面（默认键 R）：有"后背"槽。
2. 把一只浮动背包拖进去：放得进去；背上（F5 看）画出背包，**只有我们的模型，没有一个"飘着的默认物品"叠在上面**。
3. 按 B：打开它；放入、拿出、Ctrl 快速转移（背包不会被转进它自己）、标签管理器、垃圾桶、自动拾取、升级都照常。
4. 在面板里改内容，退出世界重进：内容还在。
5. 在 Accessories 界面把背包取出：背上立刻没了；按 B 回到其它位置的查找。
6. （可选）在 Accessories 配置里打开 `forceNullRenderReplacement`：背上仍然只有我们的模型。

## B. 顺序（US2）

1. 胸甲槽与 Accessories 后背槽各放一只：背上画胸甲那只，按 B 打开胸甲那只。
2. 三处（胸甲、Curios 后背槽、Accessories 后背槽）各放一只：取胸甲的；摘掉它取 Curios 的；再摘掉取 Accessories 的。
3. 手里拿一只、Accessories 里一只：按 B 打开手里的；背上画 Accessories 里的。

## C. 隐藏渲染（US3）

1. 在 Accessories 界面把后背槽设为不渲染（槽旁的眼睛图标）：背上不画，按 B 仍打开；前面还有背包时仍是前面那只。
2. 打开渲染：背上立刻画出。

## D. `modLevels` 与没装 Accessories（US4）

1. 单人游戏，配置 `compat.modLevels` 加上 `accessories=GENERIC`（客户端配置，改动无需重启）：Accessories 里的背包不再算背着的、按 B 不打开它，但仍能放进槽位；此时背包在 Accessories 里按它自己的默认外观显示（和别的物品一样）；改回 FULL 立刻恢复。
2. **没装 Accessories**：`./gradlew runClient -PcleanRuntime`（只带 JEI）：模组正常加载，日志里没有任何与 Accessories 有关的错误。

## E. 回归

032、033 的场景、030/031 的快照功能不受影响；图集 2048×1024（`design/demo/tools/check_atlas_budget.sh`，默认干净环境）。

## 自动化

```bash
./gradlew compileJava test
```
