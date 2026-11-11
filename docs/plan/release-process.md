# 版本号与发布流程（2026-11-10）

**状态**：已落地（流水线与文档），**还没有打过任何 tag，也没有发布过 Release**。参考了作者的另一个模组项目 [MCompatIssues](https://github.com/pnnh/MCompatIssues)（同样是 NeoForge 1.21.1 的模组，已经在用同一套流水线发布 v0.1.0），本项目沿用它的做法，只改了名字和一处构建参数（见下）。

## 版本号规则

- 使用**语义化版本** `MAJOR.MINOR.PATCH`（可带预发布后缀，如 `0.2.0-rc1`）；真正的版本只写在一个地方：`gradle.properties` 的 `mod_version`，jar 文件名、`neoforge.mods.toml` 里的版本都从它来。
- 1.0 之前（0.x）的约定：
  - **MINOR（0.x.0）**：新功能、规则变化、存档数据格式变化、网络协议版本（`BackpackNetwork` 里的 `versioned("N")`）变化——任何会让两端或旧存档表现不同的改动；
  - **PATCH（0.x.y）**：只修缺陷，不改规则、不改数据格式、不改协议。
- **网络协议版本和模组版本是两回事**：协议号只管"能不能连上"（两端必须相同，否则无法进服）；模组版本管"这是哪一版"。协议变了，模组版本至少升 MINOR。
- 开发期间 `mod_version` 保持上一个发布版本的号，**发布前才改**；不需要为每个 feature 都升版本。

## 发布流程

1. 在 `main` 上确认测试通过（`./gradlew test`），游戏里的验证已做完（见 [手动验证清单](manual-verification-checklist.md)）。
2. 改 `gradle.properties` 的 `mod_version`（如 `0.1.0`）。
3. 写发布说明 `.github/release-notes/v<版本>.md`（中英文，参考 `v0.1.0.md`）；通用的"安装与使用、构建溯源"段落在 `.github/release-notes/_footer.md`，会自动附在每次发布说明后面，其中的 `{{version}}`、`{{repository}}` 会被替换。
4. 提交，然后打 tag 并推送：`git tag v0.1.0 && git push origin v0.1.0`（**tag 必须是 `v` 加语义化版本，并且与 `mod_version` 完全一致**，否则流水线失败）。
5. GitHub Actions（`.github/workflows/release.yml`）自动：校验 tag → 跑测试 → 构建 jar → 生成构建溯源证明（Artifact Attestation）→ 创建 GitHub Release 并上传 jar。
6. 验证产物：`gh attestation verify mcfloatingbackpack-0.1.0.jar --repo pnnh/MCFloatingBackpack`。

推送到 `main` 的提交和拉取请求只会跑测试与构建，不会发布。

## 与参考项目的差别

流水线是从 MCompatIssues 复制来的，只有两处不同：

1. **名字**：模组名、jar 名（`mcfloatingbackpack-<版本>.jar`）。
2. **构建参数 `-PcleanRuntime -PnoJei`**：本项目的 `build.gradle` 把联调用的模组（精妙背包、旅行者背包、Curios、Accessories、owo-lib、Sodium、Iris、JEI）放在 `localRuntime`，而它被并进了 `runtimeClasspath`，所以不加参数时 CI 里的 `test` 也要从 CurseMaven 与 Modrinth 下载它们。这些只用于本地 `runClient`，不进发布 jar；CI 里去掉。已在本机验证：`./gradlew test build -PcleanRuntime -PnoJei` 通过，产物 `build/libs/mcfloatingbackpack-0.1.0.jar` 里只有本模组。

**尚未验证**：流水线本身没有在 GitHub 上跑过（需要推送后才知道），第一次推送到 `main` 会触发一次"只测试与构建"的运行，可以先用它检查；发布路径要到第一次打 tag 才会跑。

## 许可证

`LICENSE` 是 GNU AGPL v3，项目的许可证是 **AGPL-3.0-or-later**（用户 2026-11-10 确认，不是 only）。`gradle.properties` 的 `mod_license` 原来写的是 `All Rights Reserved`（会写进 jar 里的 mods.toml），已改成 `AGPL-3.0-or-later`，README 里的说明与徽章一致。

## 背包数据修订（`DATA_REVISION`，040）

背包数据上有一个可选的整数标记 `data_revision`（磁盘上，`backpack_contents` 记录里的一个可选字段；不进网络编码）。每次保存背包都盖上当前版本的 `DataRevision.CURRENT`；使用时发现背包上的修订**大于**当前版本的 `CURRENT`，说明它是更新的版本保存的，当前版本就**保护**它（面板、自动拾取、锻造台升级都不改写它，先提示玩家，两步确认后才放行）。

- **`CURRENT` 现在是 1**（第一个盖章的版本）；没有标记的旧背包读作 0。
- **何时递增**：只有当背包的存储数据发生**旧版本无法完整往返**的变化时才递增——新增存储字段、已有字段的含义改变、新增只能存在背包里的物品种类。纯规则或界面变化、修缺陷**不**递增。
- **每个改变背包存储数据的 feature**：必须在 `DELIVERY.md` 里写明"数据修订是否递增，为什么"；递增时同时改 `DataRevision.CURRENT`、更新 `DataRevisionTest`，并在发布说明里提醒"降级前先备份存档"。
- **保护只对带标记的版本有效**：早于 040 的版本不认识这个字段，保存时会把它丢掉，所以那些版本之间的降级无法保护。
- 修订与模组版本号是两回事：模组版本号管"这是哪一版"，修订管"存储数据的格式级别"；修订变了，模组版本至少升 MINOR。
- 已知局限：创造模式下客户端写回背包栈时修订会丢；客户端界面上看不出背包是被保护的（只有被拒绝时的聊天提示）。
- **这个保护不防"读入时的丢失"，也不保证降级后数据完整**：040 的降级实验证实，不带保护的旧版本在载入玩家数据时就会丢掉不认识的字段（`extension_rows`）与不认识的嵌套物品（工具区里的扩展件）；带保护的旧版本是否同样会丢是推断（解码早于 `BackpackGate`），未实测。所以 040 的作用是"在背包被用面板改写之前警告并让玩家有机会先备份"，**不是防丢数据**。
- **总的口径：本模组不支持降级，降级前请备份存档。** 降级保证数据不丢在软件开发里没有先例；我们只做"检测、警告、拒绝"，不做数据保留或迁移回退。

### 存档级降级拦截（041，已实现，游戏里的验证等用户确认：`specs/041-world-downgrade-guard/`）

040 是"细网"（背包级，只拦使用路径）；**041 是"粗网"**：存档里有一个本模组自己的小文件 `<世界>/mcfloatingbackpack/world_marker.properties`（`data_revision=…`、`mod_version=…`，手工好改），记录"这个存档被至多哪个数据修订的版本碰过"。服务器启动存档时，在 `ServerAboutToStartEvent` 里（**早于 `loadLevel()`，也早于任何玩家数据被读取**，单人与专用服务器都一样）读它并与 `DataRevision.CURRENT` 比较：

- **没有标记、损坏、或修订低于当前** → 照常启动，把标记写成当前修订（只升不降；损坏时记 WARN 并重写）；写不进去只记 WARN，不阻止启动。
- **修订等于当前** → 照常启动；只有 `mod_version` 变了才更新它。
- **修订高于当前** → **拒绝启动**：抛带说明的 `ReportedException`（专用服务器：控制台 ERROR 与 `crash-reports/…-server.txt`，进程退出；单人游戏：游戏窗口关闭、退出码 -1，启动器提示崩溃，说明在 `crash-reports/…-server.txt` 里；原版 1.21.1 没有游戏内的崩溃界面，也没有可行的更友好办法，见 research D2；拒绝前要先让客户端的等待循环出得来，否则游戏会卡死在加载界面）。拒绝路径**不写任何文件**。
- **接受风险的两种办法**：`-Dmcfloatingbackpack.acceptDowngrade=true`（一次性，放行时记 WARN，**不改标记**，所以下次不带参数还会被拦）；或删除标记文件、把 `data_revision` 改低（永久）。
- 比较的是**数据修订**，不是模组版本号（0.1.1 → 0.1.0 不拦）；标记只升不降，所以放行后不会被悄悄写低。
- 开发里：`-Pmcfb.worldMarkerRevision=99` 让标记写成 99（先写下，再不带开关启动就会被拦）；`-Pmcfb.acceptDowngrade=true` 在 runClient 里放行。
- **局限**：本机制出现之前的版本不认识标记也不检查；标记被删或改低即失效；单人游戏里原版在我们之前写了 level.dat（原始 NBT 透传，不丢东西）；验证见清单 041-A～J（专用服务器的通过与拒绝路径已实测，见 DELIVERY）。
- 总口径不变：**不支持降级，降级前请备份存档**；041 让"低版本拒绝读高版本数据"在存档级成立，不保证、也不承诺数据找回。

## 客户端与服务器的版本一致性（内置，只管本模组自己的版本）

作者的 [MCompatIssues](https://github.com/pnnh/MCompatIssues) 是一个完全独立的模组，能比较两端**全部**模组的版本；但我们不能假设玩家装了它、用对了它，所以**本模组自己内置一个只管自己版本的检测**（不管其它模组），MCompatIssues 只作为可选的推荐写在 README 与发布说明里，不是依赖。分两个 feature，先后顺序如下：

1. **版本一致性提示（039，已实现，游戏里的验证等用户确认：`specs/039-version-check/`）**：玩家登录服务器时，服务器把自己的模组版本告诉客户端，两端的版本不一致就在聊天里**只给他自己**输出警告；顺带让服务器记日志。
   - 比较规则：**MAJOR.MINOR 必须一致**（0.x 阶段 MINOR 就是"规则、数据、协议有变化"，见上面的版本号规则）；PATCH 不同只是修缺陷，不警告；预发布后缀不同按不一致处理。显示时带上完整版本号。
   - 提示的内容：客户端比服务器旧 → 提醒升级；比服务器新 → 提醒降级，或联系服主升级服务器；两种情况都提醒**版本差异不宜过大、改版本之前一定先备份存档**。每次登录最多提示一次；单人游戏与自己开的局域网主机不提示。
   - 机制：新增一个**可选**的网络包（`optional()`，单独一个注册器与版本号，不改现有协议号 17），服务器登录时发给客户端；老客户端没有这个包，服务器用 `hasChannel` 判断后改由**服务器给他发一条文字提示**并记日志——这恰好覆盖版本最落后的人。
   - 顺带的兜底：客户端预测对不认识的类别改为放行（`ItemCategoryMatcher` 现在对未知类别返回"不能放"）；服务器的诊断日志里带上玩家的模组版本。
2. **降级保护（040，已实现，游戏里的验证与降级实验等用户确认：`specs/040-downgrade-protection/`）**：把"最后一次保存这只背包的数据修订"写进背包数据，载入时发现背包是比当前版本更新的修订保存的，就警告并**保护**，不让旧版本悄悄覆盖。
   - 要保护的原因：降级模组后，新版本才有的东西会在载入或保存时丢失。
   - 降级实验已做（2026-11-10，`specs/040-downgrade-protection/research.md`）：不带保护的旧版本载入时就丢不认识的字段与物品，整只背包不会消失；因此 040 只能警告并拦住面板层的改写，**拦不住读入时的丢失**，由 041（存档级）补上。
   - 老版本（早于这个功能的版本）读不到这个标记，也不会保留它，所以保护从第一个带这个标记的版本开始生效；这一点要写进说明。

开发里用调试开关验证（`./gradlew runClient -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.2.0`，可选 `-Pmcfb.clientVersion=…`、`-Pmcfb.legacyClient=true`）：一个客户端的单人游戏里就能看到提示与日志；这些开关默认关，只在开发运行里有意义。

**尚未验证**（都要在对应 feature 的 plan 阶段先查证，验证前不得声称已满足）：
- NeoForge 对"没有某个可选网络通道的客户端"调用 `sendToPlayer` 的行为（预期要先 `hasChannel`）；`optional()` 的包放在单独的注册器里是否不影响现有包的协议校验。
- ~~降级时的真实后果~~（已由 040 的实验回答，2026-11-10）：`extension_rows` 被丢，隐藏行里的物品随之消失；旧版本不存在的物品 id 只丢那一格，其余保留；整只背包不消失。尚未实测的是：带保护的旧版本在"不碰面板、只登录再退出"时是否也会丢（推断会丢；041 在存档级拦截就是为了这个）。
- `displayTest="MATCH_VERSION"` 在服务器列表里对版本不同的客户端的实际表现（参考项目 MCompatIssues 就是靠它要求两端都安装）。
