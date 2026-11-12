# DELIVERY: 液体罐（048）

状态：已完成，用户已在真机确认（2026-11-12，T037）。

## 基线（T001、T002）

- `./gradlew test`（实现前）：**173 个类、1298 个用例，0 失败**。
- 共享方块图集（`design/demo/tools/check_atlas_budget.sh`，干净客户端，加贴图**之前**）：**2048x1024**（mip 级别 4），估算显存约 11.2MB，通过预算（宽、高都 ≤ 2048）。

## 完成情况（2026-11-12）

- 实现完成：T003～T031 全部做完并勾选；Phase 7 的游戏内验证（T032）做了一部分，没做的项如实列在下面；T037 等用户确认。
- `./gradlew test`（实现后）：**179 个类、1354 个用例，0 失败**（基线 173/1298；新增 6 个测试类与 56 个用例；`FluidStoreCodecTest` 做不了：测试类路径里没有 Minecraft、netty、DFU，已在 tasks 里说明并以源码断言与游戏内验证代替）。

## 与 spec/plan 的偏离

1. **档次限制对三个工具区都生效**（spec 原文只写底部区；plan 阶段更正，已改 spec）。主区域不限制，**需要用户确认**。
2. **被拒时给提示**（spec 补了一句）：服务端动作栏、客户端预测用聊天栏。
3. **配方书解锁撤销**：现有配方都没有进度文件，沿用。
4. **油的量条颜色**从 `0xFF2B2B2B` 改为 `0xFF6A5A7A`：原色与量条的深色底几乎分不开（游戏里看出来的）。
5. 协议号 19、数据修订 2、液量独立 S→C 包（`BackpackFluidsPayload`）。

## 数据修订与协议（release-process.md 要求写明）

- **数据修订 1→2**：背包数据新增可选字段 `fluids`，并新增只能存在背包里的罐物品；旧版本读不到它们，会在载入玩家数据时丢掉（040 的降级实验已证）。**口径不变：不支持降级，降级前先备份存档**；发布说明要提醒。
- **协议号 18→19**：背包数据组件的网络编码末尾追加了 `fluids`，另加一个服务端到客户端的包。9 个历史测试里断言 18 的改为 19，`SearchTermsContractTest` 与 `BackpackContentsRevisionTest` 的源码断言同步，`DataRevisionTest` 改为 2。
- 配置项、依赖：无新增。

## 游戏内验证（容器 mcfb-c1，创造模式的世界，AI 自动验证）

**验证了**：
- B1（部分）：创造物品栏搜索 "tank" 得到 19 个罐（3 行 6 个 + 1，无皮岩浆罐）；JEI 里看到皮水罐、铁油罐、铁蜂蜜罐、下界合金水罐（锻造台）的配方，形状与契约一致；物品提示有"容量"与"放入某档及以上的背包"（英文）。
- 命令 `item replace … [mcfloatingbackpack:backpack_contents={items:[],tools:{…},fluids:{water:8000,oil:4000,milk:5000,oil_fluid:"test:crude"}}]`（通过数据包函数，聊天命令最长 256 字符）能被解析，`fluids` 的对象写法有效（research R10 的未验证项已验证）。
- B5：左区放牛奶罐、放铁水罐都被接受，**没有量条**，也不占底部区的"同液体一个"。
- B6：底部区的水罐、油罐有量条（水 8000/16000 约一半，油 4000/16000 约四分之一，颜色分别是蓝与灰紫）；悬停显示原版提示加"Water: 8000 / 16000 mB"、"Crude Oil: 4000 / 16000 mB"；左区的罐悬停没有液量行。
- B4：已有铁水罐时，把皮水罐放进别的格，聊天提示"This backpack already has a tank for that liquid"，没有发请求；放到同一格替换成功，量条变为满（8000/8000）。
- B3（部分）：皮背包放金水罐，聊天提示"This tank needs a Gold backpack or better"；铁背包放皮罐、铁罐被接受。
- B9（部分）：Save and Quit 后重新启动再进世界，同一只背包的罐与 `fluids`（`/data get` 看到 `fluids: {oil: 4000, milk: 5000, oil_fluid: "test:crude", water: 8000}`）原样在，量条与提示照常。
- 宪法 VI：共享方块图集加贴图**之前 2048x1024，之后 2048x1024**（`check_atlas_budget.sh`，干净客户端），通过预算。

**没验证（如实记录）**：
- B2：真实合成（工作台、锻造台）没做；**蜂蜜瓶作配方材料时是否退回玻璃瓶未验证**（只在 JEI 里看了配方形状）。
- B3：钻石背包放下界合金罐、下界合金背包的各种组合只由单元测试穷举（95 项）覆盖，游戏里没试。
- B4/B7：`STORE_EXCEEDS_CAPACITY`（现量 10000 放皮罐）的游戏内提示没试出来（JEI 与创造栏的界面循环干扰了操作）；取下罐、再放回、液体仍在，只有 `FluidStoreOps`/规则的单元测试，游戏里没做。
- 服务端那一侧的拒绝（客户端预测被绕过时）没有单独触发过，只有源码断言与纯逻辑测试；客户端预测都先挡住了。
- B8：3 列窄面板下量条与提示没测。
- B9：快照与恢复、旧存档（没有 `fluids` 字段）的背包照常打开：没在游戏里做（旧背包路径由 `optionalFieldOf` 保证，是代码层面的推断）。
- B10：磁盘上 `data_revision` 为 2：没在游戏里核对（命令造的背包没被服务端保存过，数据里没有这个字段）。
- 真实原油模组联调（只用了假 id `test:crude`）；Windows/Linux 真机；创造模式的客户端整包写回（既有的绕过，见 research R3）。
- 中文界面下的罐名与提示：只在英文界面里看了。

## 已知局限

- 048 阶段没有任何玩家操作能改变液量（只能用命令/测试造出带液量的背包）。
- 创造模式下物品栏的客户端写回会整只覆盖背包栈（原版创造模式由客户端授权），可以绕过放入规则；`ExtensionPackRules` 等既有规则同样。
- 降级会丢液量与罐子（不支持降级）。
- 占位贴图是程序生成的，等用户替换。

## 宪法复查

I 不新增 Screen；II 放入判定与液量都在服务端，客户端只预测与显示；III 只做罐子；IV 无第三方依赖（原油只定义规则）；V 游戏内验证已做一部分并如实记录；VI 图集前后相同（2048x1024）；VII 无新依赖。

## 补充 US6：隐藏液体（黄点与 /hidden，2026-11-12）

实现（T038～T043）：`tank/HiddenLiquids`（纯逻辑）、`HiddenSummaryRows.Line` 加 `liquidMb`（3 参数构造保留）、`HiddenTextKeys`（按"只物品/只液体/两者"选语言键，纯逻辑）、`FloatingPanelRenderer`（黄点条件、悬停液体段）、`SearchContext.hiddenLines()` 追加液体行、`SearchRenderer`（液体行与标题、提示），5 个语言键中英文。无新网络包、配置项、依赖。

- `./gradlew test`：**181 个类、1371 个用例，0 失败**（之前 179/1354）。
- **改了一个历史测试**：`SearchPrefixResourcesTest.theHiddenSummaryReusesThe037KeysAndTheyAreStillThere` 原来断言 `SearchRenderer` 源码里直接有 `.hidden.title`、`.hidden.hint`，现在这两个键由 `HiddenTextKeys` 给出，断言改为"渲染器用 `HiddenTextKeys`，`HiddenTextKeys` 里有原来的两个键"（语言文件里的键与文字不变）。

**游戏内验证了**（容器，`-PnoJei`，数据包函数造的背包：底部区水罐、左区油罐，液量水 8000、油 4000、牛奶 5000、蜂蜜 750）：
- 标题栏图标角上出现黄点；悬停黄点，提示列出 "Crude Oil: 4000 mB / Milk: 5000 mB / Honey: 750 mB / Put the matching tank into the bottom area to use it again"（水有罐，不列；油罐在左区不生效，所以油算隐藏）。
- `/hidden`：标题 "Some liquids have no tank"，三行 "Crude Oil (liquid): 4000 mB" 等，提示行，计数 3；`/hidden milk` → 1 行、计数 1；`/hidden liquid -oil` → 牛奶与蜂蜜、计数 2；`/hidden zzz` → "No hidden category matches"、计数 0。
- 把左区的油罐拿到底部区第 2 格：油那一行消失，牛奶、蜂蜜仍在，计数 2（这也顺带验证了"放回罐，液体恢复"）。

**没验证的**：
- 物品和液体同时隐藏（合并标题与两段提示）只有 `HiddenTextKeysTest` 与源码断言，游戏里没造出隐藏行。
- 中文界面下的文字。
- 悬停黄点时提示的第一行标题在窗口顶部被截掉（鼠标贴着窗口上沿，原版的提示定位只检查右、下边界）：这是 037 的黄点提示就有的既有行为，不是本次引入；标题文字在 `/hidden` 汇总里可见。
- 窄面板（3 列）下的换行。
