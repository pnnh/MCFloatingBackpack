# Delivery: 023-recipe-difficulty

**日期**: 2026-11-07

**状态**: 代码、数据、单元测试、编译、静态检查与文档完成（T001～T007、T009、T011、T013、T015、T017）。**客户端验证（2026-11-07 用户逐项勾选）：T008、T012、T014 通过并勾选；T010 部分通过（旧世界通过，旧格式数据包配方与局域网协议版本未验证），保持未勾选**；T016 的图集度量与日志检查**仍未做**（用户的游戏正在运行，见下）。详见文末"客户端验证结果"。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | `./gradlew compileJava test` 通过；61 个测试类、468 个用例、0 失败 |
| 先写的测试（T002、T005） | 新增 `BackpackRecipesResourcesTest`（5 个用例）；数据改之前运行，`everyUpgradeNeeds…` 与 `leatherBackpackNeedsTwoIronIngots` 如预期失败，其余通过 |
| 数据改完之后（T007） | `./gradlew compileJava test` 通过；62 个测试类、473 个用例、0 失败（新增 5 个全部通过） |
| 静态检查（T009） | `git diff --stat -- src/main/java`：本 feature 只改了 `BackpackUpgradeRecipe`、`FloatingBackpackJeiPlugin`、`BackpackNetwork`（只改版本号），没有改 `BackpackContents`、`FloatingBackpackItem`、`BackpackTier`、保存/编码类、面板或网络载荷类；`assemble` 一行未改；`optionalFieldOf("template", Ingredient.EMPTY)` 在位；`inventory/recipe` 包里没有第三方引用（宪法 IV） |

（同一工作区里还有 021/022 的未提交改动：`FloatingPanelRenderer`、`TrashActions`，它们不属于本 feature。）

## T003：`Ingredient.EMPTY` 的核实结论（读 NeoForge 21.1.251 的 `Ingredient.java` 与 `SmithingMenu.java`）

- `Ingredient.CODEC = makeIngredientCodec(true)`，**允许空成分**；`optionalFieldOf("template", Ingredient.EMPTY)` 缺省得到 `EMPTY`。
- `Ingredient.test(stack)`：成分为空时返回 `stack.isEmpty()`；所以**不需要**专门分"模板为空 / 非空"两个分支，统一写 `template.test(...)` 即可——空模板成分就等价于 006 的"模板格必须为空"。这比 plan 里设想的两分支写法更简单，是实现时的小偏离，行为与 plan 一致。
- `CONTENTS_STREAM_CODEC` 对空成分编码为长度 0 的物品列表，解码回 `EMPTY`，往返成立。
- `SmithingMenu`：模板格 `mayPlace` 用 `recipe.isTemplateIngredient(stack)`；取出结果时 `shrinkStackInSlot(0/1/2)` 对模板、基础、材料各消耗 1 个（`onTake`）。所以模板"会被消耗"不需要额外代码。
- **这些都是读源码的结论，没有在游戏里跑过**；空成分能否真的加载与同步要靠 quickstart 五 13 确认。

## 与 plan/tasks 的偏离

- 同上：`matches` 与 `isTemplateIngredient` 都直接用 `template.test(...)`，没有写两个分支。
- 无其它偏离；协议版本已升到 `"11"`。

## 没有做的

- **T016 的图集度量（宪法 VI）**：`pgrep` 发现**有一个客户端正在运行**（进程 80732，是你的游戏；2026-11-07 用户重启后的新进程 75345 同样在运行），所以我没有跑 `check_atlas_budget.sh`，也没有看 `latest.log`。**图集度量与"日志无新增配方报错"都未做**，本 feature 没有新增任何资源，不声称已度量。等你退出游戏后可以补跑。
- 另外：`./gradlew compileJava test` 在游戏运行期间执行过，会重写 `build/classes` 与 `build/resources`。已经在运行的游戏通常不受影响（已加载的类不变），但如果它之后才第一次加载某个类，可能读到新版本，所以**验证前请重启一次游戏**，别在这个旧会话里验证。

## 已知限制 / 不确定

- 模板的生存来源与概率读自原版 1.21.1 战利品表，**没有游戏内实测**：哨兵（前哨站箱子约 1/4）、沙丘（沙漠神殿约 1/7）、眼（要塞图书馆必出）、下界合金升级（堡垒宝藏箱必出）。
- 锻造台里模板格的空槽提示图标是盔甲/锭的轮廓，对背包没有意义，没有处理。
- JEI：升级配方在 JEI 里可见（006-JEI 修复）与模板格显示已由用户验证通过（见文末）。
- Windows/Linux 无法验证。

## 行为变化（需要你确认能否接受）

- 四档升级材料：铁块、金块、**钻石块（原 1 钻石）**、**下界合金块（原 1 下界合金锭）**。
- 每档升级要放入对应的原版锻造模板，模板与材料各消耗 1 个（原来模板格必须为空）。
- 皮革背包：5 皮革 + 2 铁锭 + 1 线 + 1 箱子（原 7 皮革 + 1 线 + 1 箱子）；旧摆法不再产出。
- 协议升到 `"11"`：版本不一致的客户端连不上。
- 成本对照见 [research.md](./research.md) D7。

## 客户端验证结果（2026-11-07，用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选）

| 任务 | 清单项 | 结果 |
|---|---|---|
| T008 | 023-1（四档升级 16 个正反用例：三样齐全才产出，缺模板、缺材料、材料换成单个钻石/下界合金锭、别档模板都不产出，模板与材料各消耗 1 个）、023-6（带内容的皮革背包一路升到下界合金，主区域、三个工具区、标签、垃圾桶内容都在） | **通过**，已勾选 |
| T010 | 023-7（改动前保存的世界：旧背包与内容完整，升级按新配方） | 通过 |
| T010 | 023-13（无 `template` 的旧格式数据包配方能加载、要求模板格为空） | **未验证**（可选项） |
| T010 | 023-14（局域网：旧版客户端被协议版本拒绝） | **未验证**（没有条件） |
| T012 | 023-10/11/12（JEI 里铁、金、钻石、下界合金各有锻造台配方、模板格有对应模板；`-PnoJei` 进游戏无报错）；006-JEI | **通过**，已勾选；同时确认上一轮 006-JEI 修复 |
| T014 | 023-8/9（工作台新摆法得皮革背包、旧摆法无产出）与 JEI 里的皮革新摆法 | **通过**，已勾选 |
| T016 | 023-16/17（图集度量、日志检查） | **未做**：我这一轮再次 `pgrep` 发现游戏仍在运行（进程 75345），所以没跑 `check_atlas_budget.sh`，也没看 `latest.log`；本 feature 没有新增资源，**不声称已度量**，退出游戏后补跑 |

T010 因 023-13、023-14 未验证保持未勾选。其余已验证项只证明"在用户的这台 macOS 机器、这一次会话里表现正常"，模板的生存来源与概率仍是读战利品表的结论，没有被实测（用户用创造栏取模板）。

## 2026-11-11 AI 自动验证：quickstart 五 14（局域网）

环境：局域网房主 = 容器里的单人世界（集成服务器，"对局域网开放"端口 25570），第二个客户端用 `mcfb-c1:25570` 直连。**集成服务器强制正版验证**，开发客户端没有正版账号，所以加了一个假的会话服务器 `scripts/ai-gui/MockSession.java`（两端的 `-Dminecraft.api.*.host` 指向它，`launch.sh` 用 `AIGUI_JAVA_OPTS` 传入，见脚本 README"联机"一节）。

- **新版本客户端连新版本房主，配方显示与升级一致**：**通过**。加入者 P2（生存）在锻造台里放"哨兵盔甲纹饰锻造模板 + 皮革背包 + 铁块"，输出格出现铁背包（悬停提示 `Iron …`，`Slot: Back`、`No ID yet (given on first use)`），取出后进入物品栏、解锁配方提示出现。
- **旧版本客户端被协议版本拒绝**：**通过**。构建一个除协议号外与当前源码相同的客户端（`versioned("11")`，模拟 023 当时的协议），连协议 17 的房主：被拒，界面 `Connection Lost`，`Channel of mod "MCFloatingBackpack" failed to connect: The client wants the payload to be version: 17, but the server wants it to be version: 11!`（28 个通道，界面折叠成 `mcfloatingbackpack:click [+27 more]`），房主日志 `lost connection: Incompatible client! Please use NeoForge 21.1.251`。另外用 023 的真实源码（`git archive 02daec6`，协议 11）也试了一次：它先因为 `owo:handshake`（开发环境后来才加了这个依赖，旧源码的运行环境里没有）被拒，不是协议号，所以以上面那次为准。
- 五 13（无 `template` 的旧格式数据包配方）没做。
