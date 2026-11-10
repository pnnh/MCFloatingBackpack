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

## 客户端与服务器的版本一致性（内置，只管本模组自己的版本）

作者的 [MCompatIssues](https://github.com/pnnh/MCompatIssues) 是一个完全独立的模组，能比较两端**全部**模组的版本；但我们不能假设玩家装了它、用对了它，所以**本模组自己内置一个只管自己版本的检测**（不管其它模组），MCompatIssues 只作为可选的推荐写在 README 与发布说明里，不是依赖。分两个 feature，先后顺序如下：

1. **版本一致性提示（039，已实现，游戏里的验证等用户确认：`specs/039-version-check/`）**：玩家登录服务器时，服务器把自己的模组版本告诉客户端，两端的版本不一致就在聊天里**只给他自己**输出警告；顺带让服务器记日志。
   - 比较规则：**MAJOR.MINOR 必须一致**（0.x 阶段 MINOR 就是"规则、数据、协议有变化"，见上面的版本号规则）；PATCH 不同只是修缺陷，不警告；预发布后缀不同按不一致处理。显示时带上完整版本号。
   - 提示的内容：客户端比服务器旧 → 提醒升级；比服务器新 → 提醒降级，或联系服主升级服务器；两种情况都提醒**版本差异不宜过大、改版本之前一定先备份存档**。每次登录最多提示一次；单人游戏与自己开的局域网主机不提示。
   - 机制：新增一个**可选**的网络包（`optional()`，单独一个注册器与版本号，不改现有协议号 17），服务器登录时发给客户端；老客户端没有这个包，服务器用 `hasChannel` 判断后改由**服务器给他发一条文字提示**并记日志——这恰好覆盖版本最落后的人。
   - 顺带的兜底：客户端预测对不认识的类别改为放行（`ItemCategoryMatcher` 现在对未知类别返回"不能放"）；服务器的诊断日志里带上玩家的模组版本。
2. **降级保护（040）**：把"最后一次保存这只背包的数据修订"写进背包数据，载入时发现背包是比当前版本更新的修订保存的，就警告并**保护**，不让旧版本悄悄覆盖。
   - 要保护的原因：降级模组后，新版本才有的东西会在载入或保存时丢失（见下面"尚未验证"）。
   - **必须先做一个验证实验**（降级后到底发生什么），再决定保护强度：只警告，还是拒绝打开面板直到玩家确认。
   - 老版本（早于这个功能的版本）读不到这个标记，也不会保留它，所以保护从第一个带这个标记的版本开始生效；这一点要写进说明。

开发里用调试开关验证（`./gradlew runClient -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.2.0`，可选 `-Pmcfb.clientVersion=…`、`-Pmcfb.legacyClient=true`）：一个客户端的单人游戏里就能看到提示与日志；这些开关默认关，只在开发运行里有意义。

**尚未验证**（都要在对应 feature 的 plan 阶段先查证，验证前不得声称已满足）：
- NeoForge 对"没有某个可选网络通道的客户端"调用 `sendToPlayer` 的行为（预期要先 `hasChannel`）；`optional()` 的包放在单独的注册器里是否不影响现有包的协议校验。
- 降级时的真实后果：`BackpackContents` 里 `extension_rows` 是可选字段，旧版本读到会忽略并在下次保存时丢掉；背包里嵌套的物品如果带有旧版本不存在的物品 id（如通用扩展）会怎样（整个背包的物品组件解码失败？只丢那一格？）。
- `displayTest="MATCH_VERSION"` 在服务器列表里对版本不同的客户端的实际表现（参考项目 MCompatIssues 就是靠它要求两端都安装）。
