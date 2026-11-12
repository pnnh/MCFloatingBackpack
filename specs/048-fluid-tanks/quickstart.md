# Quickstart: 液体罐的验证

规则见 [contracts/placement-rules.md](./contracts/placement-rules.md)，物品与配方见 [contracts/recipes-and-assets.md](./contracts/recipes-and-assets.md)，类型见 [data-model.md](./data-model.md)。

## A. 单元测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.tank.*' --tests 'pnnh.mcfloatingbackpack.resources.Tank*'
./gradlew compileJava test
```

覆盖点：

1. `TankCatalogTest`：19 种清单与顺序、没有皮岩浆、容量 8/16/64/256 桶、钻石抛异常、物品 id 规则、`allowedIn` 的 5 × 4 表。
2. `TankPlacementTest`：契约里的 7 组，含 1000 组随机不变式。
3. `FluidStoreTest`：`deposit`/`drain`/`canAccept` 的边界（0、满、容量±1）、原油锁定与解锁、其它液体不受影响、守恒；读入清理（负数、OIL 为 0 却有锁定）。
4. `FluidStoreCodecTest`：磁盘 Codec 往返、缺字段读作空、未知键忽略；网络 StreamCodec 往返、超长字符串与条数被拒。
5. `TankBarTest`：像素规则（0 → 0、极少量至少 1、满 = 14、超量夹到 14、容量 0 → 0）。
6. `TankResourcesTest`：契约"资源测试"一节。
7. `TankWiringTest`（源码文本）：`BackpackClickPayload` 里 `TankPlacement.check` 在 `ToolAreaRules.canPlace` 之后；`refusedByLimit` 用了同一个函数；`versioned("19")`；`DataRevision.CURRENT = 2`；`BackpackFluidsPayload` 已注册且会话打开时发送；渲染里量条只对底部区的 `TankItem` 画。
8. 既有测试：断言协议号 18 的（`grep 'versioned("18")' src/test`）改为 19，断言修订为 1 的改为 2；其它不动。

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器；先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`（用户的客户端在跑就不起容器）；改背包前备份世界。

造带液量的背包（命令，见 research R10；**第一步先验证这条命令能解析**）：

```text
/item replace entity @s weapon.mainhand with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],fluids:{water:8000,oil:4000,oil_fluid:"test:crude"}}]
```

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 创造栏 | 打开创造物品栏的本模组标签页 | 19 种罐都在，没有皮岩浆罐；名字与图标可分辨档次与液体 |
| B2 合成 | 工作台合成：皮水罐（皮革 + 玻璃 + 水桶）、铁油罐（铁锭 + 空桶 + 煤炭块）、金水罐、蜂蜜罐；锻造台升下界合金罐 | 结果正确；水桶退回空桶、蜂蜜瓶退回玻璃瓶（**未验证项，记录实际结果**） |
| B3 档次 | 每档背包逐一尝试放每档水罐进底部区 | 与契约的 5 × 4 表一致；被拒时动作栏有说明 |
| B4 互斥 | 放一个皮水罐；再放铁水罐到别的格；再放到同一格；再放铁油罐 | 第二个被拒（提示"已有这种液体的罐"）；同格替换成功；油罐成功 |
| B5 左右区 | 把水罐放进左区、右区 | 可放；没有量条；不影响底部区的互斥 |
| B6 量条与提示 | 用上面的命令得到铁背包，水罐放底部区 | 水罐格有蓝色量条约一半；悬停显示原版提示加"水：8000 / 16000 mB"；空罐显示"空"；中英文各看一次 |
| B7 液体留在背包 | 取下水罐、再放回 | 量条恢复到原来的量（背包里记着）；换成皮水罐（8 000）且现量为 10 000 时被拒 |
| B8 窄面板 | 缩到 3 列 | 量条与提示不溢出 |
| B9 重进与快照 | 重进世界；做一次快照再恢复 | 液量一致；旧背包（没有 `fluids`）照常打开 |
| B10 数据修订 | 新建的背包 | 磁盘上 `data_revision` 为 2（`/data get` 或存档标记） |

记录：每个场景实际验证了什么；没验证的写"未验证"（真实的原油模组联调、真机、Windows/Linux、创造模式的客户端整包写回）。

## C. 文档核对

- `README.md` / `README_CN.md`：液体罐一节（19 种、档次与容量、放入规则、048 阶段还不能装液体）。
- `docs/plan/fluid-tanks.md`：状态改为"048 已实现"；档次规则对三个工具区生效、主区域不限的说明与本 plan 一致。
- `DELIVERY.md`（中文）：数据修订 1→2 与理由、协议号 18→19、降级前备份的提醒、已知局限（创造模式整包写回、降级丢失）。

## D. 度量（宪法 VI，DELIVERY 记录）

- 加贴图之前与之后各运行一次 `design/demo/tools/check_atlas_budget.sh`，记录图集宽高，要求都 ≤ 2048；没量就写"未度量"。
- 面板每帧最多 11 条量条的 `fill`：容器里粗测帧率，或写"未度量"。
