# Quickstart: 灯笼光源的验证

规则见 [contracts/rules.md](./contracts/rules.md)。

## A. 单元与资源测试

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.light.*' --tests 'pnnh.mcfloatingbackpack.resources.Light*' --tests '*DependencyRulesTest'
./gradlew compileJava test
```

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器（先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；`-PnoJei`）。造背包用数据包函数：`item replace entity @s hotbar.8 with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],tools:{bottom:[{id:"minecraft:lantern",count:1}]}}]`。

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 带 LDL 运行 | 默认 `runClient`（LDL 在 `localRuntime`）；日志里 LDL 与本模组都加载，没有错误 | 启动正常 |
| B2 发光 | 夜里（`time set 18000`）站在平地上，截图；带灯笼背包再截图 | 带灯笼时画面明显变亮，清掉背包后变暗 |
| B3 不带 LDL 运行 | `-PcleanRuntime -PnoJei`；同样的背包 | 游戏正常，没有来自本模组的错误，不变亮 |
| B4 互斥 | 下方区已有灯笼时放第二个光源；同格替换；放进左区 | 第二个被拒、聊天提示；同格替换成功；左区放得下 |
| B5 提醒 | 不带 LDL 时第一次把光源放进下方区 | 聊天出现一次"需要 LambDynamicLights"；再放不重复 |
| B6 数据 | 保存、读取 | 背包内容零变化、数据修订与协议号不变 |

**不能自动判断的**：走路时的平滑程度、与 Iris 光影包的搭配、别的玩家看到的光——记为需用户确认。
