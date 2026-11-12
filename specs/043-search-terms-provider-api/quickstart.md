# Quickstart: 搜索词提供者接口的验证

接口与数值以 [contracts/search-terms-api.md](./contracts/search-terms-api.md) 为准；类型与状态见 [data-model.md](./data-model.md)。

## A. 单元测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.search.*' --tests 'pnnh.mcfloatingbackpack.rules.*'
./gradlew compileJava test
```

预期：全部通过；042 的测试不改。覆盖点（用 `String` 当 `S`）：

1. **注册**：注册、同 id 替换（旧缓存丢弃）、非法 id、null 提供者 → `IllegalArgumentException`。
2. **归一**：null、空、空白、大小写、重复、超长词（整词丢弃）、超量（只留前 64）。
3. **容错**：提供者抛异常 → 无额外词；同一异常类只 WARN 一次；20 次后停用并再 WARN 一次；违规每类 WARN 一次且不计入停用；其它提供者不受影响。
4. **`modLevels`**：`enabled` 为假时提供者**调用次数为 0**；改回为真后立刻生效；缓存不因此清空。
5. **缓存**：同一物品在一代内只调用一次；LRU 淘汰；`clearCaches` 后重新调用；重新注册后重新调用。
6. **无提供者**：`hasActive()` 为假；`termsFor` 不调用任何外部代码（SC-002）。
7. **与 042 语法组合**：对假提供者词做并且、`|`、`-`、`"…"` 的命中判断（SC-001：10 种物品 × 四种写法 = 40 次）。
8. **契约对照**：`SearchTermsContractTest` 读 `contracts/search-terms-api.md` 末尾的数值表，与 `SearchTermsLimits` 常量逐一相等。
9. **依赖规则**：`DependencyRulesTest` 照常通过（无新依赖、`neoforge.mods.toml` 不变）。

## B. 游戏内（容器自动验证，临时假提供者）

**不在 `src/main` 留任何调试入口**：在 `scripts/ai-gui` 的容器里，对源码做**临时**修改（`ClientSetup` 里注册一个假提供者：对钻石镐返回 `zuanshigao`、`zsg`，对所有物品额外返回 `zzz`），验证后**还原**（`git status` 确认无残留）。

前提：容器里用 `/give` 或创造模式把钻石镐、橡木原木等放进背包。

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 提供者的词生效 | 点放大镜，输入 `zsg` | 钻石镐命中（过滤出来、有命中框）；当前语言名里的词（`diamond`）仍命中 |
| B2 全部物品共有的词 | 输入 `zzz` | 背包里每个非空格子都命中 |
| B3 工具区高亮 | 工具区放物品，输入 `zzz` | 工具区的物品也被框出；帧率无可见下降 |
| B4 语法 | `zs gao`、`-zsg`、`zsg\|zzzx` | 结果符合并且、排除、或的含义 |
| B5 `modLevels` | 把假提供者的 id 在配置里设成 `GENERIC`（改文件，不重启），再搜 `zsg` | 不再命中；改回 `FULL` 再搜又命中 |
| B6 没有提供者 | 去掉临时修改（还原后）重搜 `zsg` | 不命中，行为与 042 一致，日志里没有新增内容 |
| B7 日志 | 看 `latest.log` | 注册时有一条 INFO；无 WARN；（用返回异常的临时提供者另测一次）异常只 WARN 一次 |

记录：每个场景实际验证了什么；没验证的写"未验证"。**DELIVERY 必须写明：用的是临时假提供者，没有验证任何真实模组。**

## C. 文档核对

- `docs/api/search-terms.md` 与契约一致（包名、方法、数值、约定）。
- `README.md` / `README_CN.md` 搜索一节：写"默认按当前语言的名字与注册名搜索；其它模组可以通过公开接口增加可搜索的文字"，**不点名具体模组**。
- `modLevels` 的配置注释与两份语言文件的悬停提示都补了"对注册了搜索词的模组，`GENERIC`/`OFF` 表示忽略它提供的搜索词"。

## D. 度量（宪法 VI，DELIVERY 记录）

- 400 格背包、一个返回 5 个词的假提供者：已缓存与未缓存两种情况下，一次查询重算的耗时。
- 400 格全是装满物品的潜影盒（最坏的数据组件）时的重算耗时（D3 的风险）。
- 做不了的写"未度量"，不声称达标。
