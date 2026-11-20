# Implementation Plan: 第三方模组版本管理与集成终端主动兼容（面板 → 终端）

**Branch**: `058-third-party-compat` | **Date**: 2026-11-20 | **Spec**: [spec.md](./spec.md)

## Summary

在 012 的"把物品送进打开的容器"这一步前面加一道可选分派，并给所有以后的第三方主动兼容配一套版本管理（设计依据与对集成终端 1.11.2 jar 的查证见 [research.md](./research.md)，全文方案见 [docs/plan/third-party-compat.md](../../docs/plan/third-party-compat.md)）：

1. **版本范围表**（`compat/`，纯逻辑）：`ThirdPartyCompat(modId, displayName, supported[], also[], variant)`，`VersionRange` 下限含、上限不含；宽松版本解析（取第一个数字起、用单个点连接的数字，忽略其后的一切）；判断结果五种：在范围内 / 过低 / 过高 / 空隙 / 无法判断；"同时要求的模组"逐个判，第一个不合格的写进结论。表里只有一行（集成终端），要求集成动力学与公共能力。
2. **运行时状态**（`CompatRuntimeState`，纯逻辑）：服务器启动时对每行算一次，状态是 `ACTIVE` / `OUT_OF_RANGE`（未启用）/ `FORCED`（范围外但被配置强制启用）/ `FAILED`（本次运行里出错被关闭）/ 没装（不进状态表）。`[compat].allowUntestedVersions`（服务端配置，默认 false）在启动时读一次。
3. **警告**：启动时每个范围外的已装模组一条 WARN；op（`hasPermissions(2)`）每次登录对每个范围外模组一条聊天；出错时一条 ERROR + 在线 op 各一条聊天（每次运行每个模组至多一次）。文字走语言文件。
4. **网络存储桥**：`NetworkStorageBridge`（`modId()`、`handles(menu)`、`insert(player, menu, stack)` 返回剩余；取出留给 060）。`BackpackQuickTransferPayload.backpackOut` 在调用 `OpenContainerDelivery.deliver` 之前先问 `NetworkStorageBridges.tryInsert`：桥已注册、状态为启用、认领这个界面时由桥完成，否则走原来的通用路径。分派逻辑写成泛型纯类（`BridgeDispatch<P,M,S>`），与 `OpenContainerDelivery` 一样用假菜单测；出错捕获（`LinkageError`、`RuntimeException`）、返回值合法性检查（剩余数不超过原数、同一种物品）与"首次出错即关闭"都在这个纯类里。
5. **集成终端的桥**（`compat/integratedterminals/`，第三方类型只在这里）：`handles` = `ContainerTerminalStorageBase` + 当前页是 `TerminalStorageTabIngredientComponentServer` 但**不是**它的合成页子类 + 成分是 `IngredientComponent.ITEMSTACK`（末影箱页本来就不是这个类）；`insert` = `tab.getIngredientNetwork().getChannel(container.getSelectedChannel()).insert(stack, false)`。
6. **加载声明与依赖**：`neoforge.mods.toml` 加 `integratedterminals`、`integrateddynamics`、`commoncapabilities`、`cyclopscore` 四条可选依赖，`[0,)`；`build.gradle` 加四条 `compileOnly`（`transitive = false`，与已有的 `localRuntime` 同版本，cyclops-core 用 Modrinth 版本 id）。
7. **调试开关** `-Pmcfb.fakeModVersion=<modid>=<版本>[,<modid>=<版本>…]`（`VersionDebug`，只改**已安装**模组的"已装版本"）。
8. **测试**：纯逻辑（版本解析、五种结论、多段与要求、状态机与配置、桥分派与出错、通知的选择）、资源与接线（`mods.toml` 宽松、README 与范围表一致、`build.gradle` 开发依赖落在段内、配置注释、语言键、桥在 `backpackOut` 里被问到、第三方类型只在 `compat/integratedterminals/`）。**游戏内**：容器里搭集成动力学网络验证存入、部分存入、离线、合成页、末影箱页、范围外伪造（见 [quickstart.md](./quickstart.md)）。
9. **文档**：README / README_CN 兼容表（精妙存储、AE2、集成终端三行，集成终端行列出受支持的版本段）；DELIVERY 写已验证与没验证的。

不做：集成终端合成页/末影箱页/别的成分类型、任何终端 → 面板、AE2 代码、别的 Cyclops 模组、反射特性探测、精确版本白名单、按模组的服务端档位、客户端侧第三方（LambDynamicLights 等）进范围表（它们沿用 033/034/052 的 `mods.toml` 下限，058 不动）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；新增**仅编译**依赖（不进发布的 jar）：`integrated-terminals 1.11.2`、`integrated-dynamics 1.39.1`、`common-capabilities 2.11.6`、`cyclops-core`（Modrinth 版本 id `mbABgx56`，即 1.30.2 的 NeoForge 构建），全部 `transitive = false`，`maven.modrinth` 组（`DependencyRules` 已列入）

**Storage**: 无新增存储（`DataRevision` 仍 6）；无新增快照字段；服务端配置多一节 `[compat]`

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查（单元测试类路径里没有 Minecraft 类，桥的真实调用只能在游戏里验证）；游戏内验证用容器 `mcfb-c1`（见 [docs/plan/ai-gui-verification.md](../../docs/plan/ai-gui-verification.md)），`-PnoJei`

**Target Platform**: 服务端为主（专用服务器与单人游戏同一套）；客户端不改

**Constraints**: 宪法 II（存入由服务端完成，客户端不改）、III（一个 feature）、V（游戏内验证：在容器里搭网络）、VII（`compat/integratedterminals/` 之外不得出现第三方类型；可选依赖 `[0,)`；无反射、无 mixin）；协议号仍 **21**（不新增包）；`DataRevision` 仍 6；**`neoforge.mods.toml` 不得写会排除任何已装版本的范围**（research R1）

**Scale/Scope**: 新增约 15 个类（纯逻辑 9 个：`ThirdPartyVersion`、`VersionRange`、`Requirement`、`ThirdPartyCompat`、`VersionVerdict`/`VersionChecker`、`CompatFinding`、`ThirdPartyTable`、`CompatRuntimeState`、`BridgeDispatch`；MC 层 6 个：`InstalledMods`、`ThirdPartyConfig`、`ThirdPartyCompatService`、`ThirdPartyNotices`、`NetworkStorageBridge`、`NetworkStorageBridges`；第三方层 2 个：`IntegratedTerminalsBridge`、`IntegratedTerminalsCompat`），改 6 个（`BackpackQuickTransferPayload`、`DiagConfig`、`VersionDebug`、`MCFloatingBackpack`、`neoforge.mods.toml`、`build.gradle`），语言键约 10 个（中英）、README 两份

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面；面板照常叠在集成终端界面上 | PASS |
| II. Server Owns Items | 存入、版本门、出错关闭、通知都在服务端；客户端不改、不发新包；守恒由桥的返回值与分派层的合法性检查保证 | PASS |
| III. One Feature At A Time | 版本管理 + 集成终端正向；反向（060）、AE2 代码、别的页都不做 | PASS |
| IV. Vanilla Path Before Compat | 通用路径（012）原样保留；桥只是它前面的可选分派，没桥或不认领时与之前完全一致 | PASS |
| V. Verify In A Client Session | 容器里搭网络验证六种情形（SC-007）；"集成终端存储页服务端行为"目前只有源码证据，验证结果如实记入 DELIVERY | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图、模型 | PASS（不适用） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 四个依赖都是可选、仅编译、`[0,)`；第三方类型只在 `compat/integratedterminals/`；无反射；**档位**：见下 | PASS（带说明） |

**宪法 VII 的档位说明**（FR-030）：集成终端属于第 2 档。客户端 OFF 沿用 020（不在该界面显示面板）；服务端"存入终端"的开关是服务端的事（客户端设置不是安全边界），由版本门、`allowUntestedVersions`、出错关闭三者决定；没有按模组的服务端档位，`ServerCompatGate` 仍恒为允许。**含义**：客户端把 `integratedterminals` 设为 GENERIC 不会关掉桥（桥是服务端代码），文档里写明；要彻底关闭只能不装该模组或不放入范围表里的版本。按模组的服务端档位已确认单开 **059**（取客户端与服务端档位中更严的一个，改 `ServerCompatGate.allows` 与 Curios/Accessories 来源，桥的调用点已经先问门禁，058 不用返工）；反向顺延为 060。058 只在 README 里如实写明这一限制（FR-027）。

设计后复核：无违规项。两个需要写进交付说明的事实：(1) 集成终端存储页"服务端不处理 Shift+点击物品栏"目前只有读 1.11.2 jar 的证据，游戏内验证要坐实；(2) 桥在 `insert` 中途抛异常时，已经进入网络的部分无法追回，所以出错路径保证"不丢"但不保证"不重复"（research R7）。

## Project Structure

### Documentation (this feature)

```text
specs/058-third-party-compat/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── version-table.md        # 范围表、版本解析与判断、README 对齐格式
│   ├── bridge.md               # 桥接口、分派算法、出错处理、集成终端的 handles/insert
│   └── config-and-texts.md     # 配置项、语言键、日志、调试开关、mods.toml 与 build.gradle 的行
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/ThirdPartyVersion.java        # 新（纯）：解析成数字数组，比较
├── compat/VersionRange.java             # 新（纯）：[min, max)、contains、toString "[1.11.0, 1.12.0)"
├── compat/Requirement.java              # 新（纯）：同时要求的模组与它的版本段
├── compat/ThirdPartyCompat.java         # 新（纯）：表的一行
├── compat/VersionChecker.java           # 新（纯）：VersionVerdict 与 check(段列表, 已装版本)
├── compat/CompatFinding.java            # 新（纯）：一个模组一次检查的结论（谁、什么版本、哪种、哪几段）
├── compat/ThirdPartyTable.java          # 新（纯）：表本身（单一事实来源）与完整性规则
├── compat/CompatRuntimeState.java       # 新（纯）：每行的状态、启用判断、首次出错关闭
├── compat/BridgeDispatch.java           # 新（纯，泛型）：按顺序问桥、版本门、出错与返回值检查
├── compat/InstalledMods.java            # 新（MC）：ModList 取已装版本 + fakeModVersion 覆盖
├── compat/ThirdPartyConfig.java         # 新（MC）：[compat].allowUntestedVersions
├── compat/ThirdPartyCompatService.java  # 新（MC）：服务器启动时算状态、写 WARN；停止时清空
├── compat/ThirdPartyNotices.java        # 新（MC）：登录聊天、出错通知
├── compat/NetworkStorageBridge.java     # 新（MC）：接口
├── compat/NetworkStorageBridges.java    # 新（MC）：注册表 + tryInsert（把 MC 类型接到 BridgeDispatch）
├── compat/integratedterminals/IntegratedTerminalsBridge.java  # 新（第三方）：handles / insert
├── compat/integratedterminals/IntegratedTerminalsCompat.java  # 新（第三方）：register()，只在模组已加载时被调用
├── network/BackpackQuickTransferPayload.java  # 改：backpackOut 先问桥
├── diag/DiagConfig.java                 # 改：服务端配置里调用 ThirdPartyConfig.define
├── version/VersionDebug.java            # 改：fakeModVersions()
├── MCFloatingBackpack.java              # 改：加载时注册桥、ServerStarting/Stopping、登录通知
src/main/templates/META-INF/neoforge.mods.toml  # 改：四条可选依赖
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json  # 加键（contracts/config-and-texts.md）
src/test/java/pnnh/mcfloatingbackpack/compat/…  # 见 tasks
build.gradle                                  # 改：四条 compileOnly
README.md, README_CN.md                       # 改：兼容表
```

**Structure Decision**: 与 012、057 一致：判断与调度写成不含 Minecraft 类型的纯类，用假数据测；Minecraft 层只做"取已装版本、读配置、发消息、把真菜单接到泛型分派"；第三方类型只在 `compat/integratedterminals/` 的两个类里。`VersionOrder`（057，快照提示用）**不复用**：它取整个字符串里的所有数字段，`1.1.0-beta.54+1.21.1` 会变成 7 段，不适合范围判断（research R2）。

## Complexity Tracking

无违规项。
