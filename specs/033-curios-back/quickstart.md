# Quickstart: Curios 后背槽兼容验证

先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`：有客户端在运行就不要再启动。`./gradlew runClient` 现在默认带着 Curios、Accessories（和 owo-lib）、精妙背包、旅行者背包、Sodium + Iris、JEI。用 2026-11-09 之后新建的开发世界。

## A. 放得进去、背着、按 B 打开（US1）

1. 打开 Curios 界面（默认键 G）：有一个"后背"槽。
2. 把一只浮动背包拖进去：放得进去；背上（F5 看）画出背包。
3. 按 B：打开它；放入、拿出、Ctrl 快速转移（**背包不会被转进它自己**）、标签管理器、垃圾桶、自动拾取、升级都照常。
4. 在面板里改内容，退出世界重进：内容还在。
5. 在 Curios 界面把背包取出：背上立刻没了；按 B 回到其它位置的查找。
6. 往 Curios 后背槽放别的有标签的饰品（若有）：不受影响。

## B. 顺序（US2）

1. 胸甲槽与 Curios 后背槽各放一只：背上画胸甲那只，按 B 打开胸甲那只。
2. 摘掉胸甲那只：背上改画 Curios 里那只，按 B 打开它。
3. 手里拿一只、Curios 里一只：按 B 打开手里的；背上画 Curios 里的。
4. 把背包放进别的 Curios 槽（若数据包给了）：不算背着的。

## C. 隐藏渲染与未激活（US3）

1. 在 Curios 界面把后背槽设为不渲染（槽旁的眼睛图标）：背上不画，按 B 仍打开；胸甲里也有一只时仍是胸甲那只。
2. 打开渲染：背上立刻画出。

## D. `modLevels` 与没装 Curios（US4）

1. 单人游戏，把配置 `compat.modLevels` 加上 `curios=GENERIC`（客户端配置，改动无需重启）：Curios 里的背包不画，按 B 不打开它，但仍能放进槽位；改回 FULL 立刻恢复。
2. **没装 Curios**：`./gradlew runClient -PcleanRuntime`（只带 JEI）：模组正常加载，日志里没有任何与 Curios 有关的错误；浮动背包照常用。
3. （专用服务器上 `modLevels` 不生效是已知限制，没有条件就不测。）

## E. 回归

032 的 8 个场景、030/031 的快照功能不受影响；图集 2048×1024（`design/demo/tools/check_atlas_budget.sh`，默认干净环境）。

## 自动化

```bash
./gradlew compileJava test
```
