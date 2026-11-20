# 契约：配置、文字、日志、调试开关、构建与加载声明

对应 FR-007、FR-008、FR-010～FR-013、FR-024、FR-026。

## 1. 服务端配置 `[compat].allowUntestedVersions`

`ThirdPartyConfig.define(builder)`（在 `DiagConfig` 的服务端配置里调用，与 057 的 `SnapshotConfig` 同法）。

```toml
[compat]
    # (注释：英文，与 057 的配置注释一致)
    allowUntestedVersions = false
```

注释必须包含（资源测试按关键词检查）：

- 它做什么：范围外版本的第三方模组，主动兼容默认不启用；设为 true 则照常启用；
- **WARNING**：第三方模组的 API 变了，我们的代码可能出错，严重时让服务器崩溃（`NoSuchMethodError` 之类不是普通异常）；
- 启用前先**备份**存档；出了问题先改回 `false`；
- 警告（日志与管理员聊天）照常出现；
- 启动时读取，改动需重开世界。

读取：`ServerStartingEvent` 里 `ThirdPartyConfig.allowUntested()`，配置未加载（`IllegalStateException`）取 `false`。

## 2. 语言键（`en_us.json` 与 `zh_cn.json` 两份；具体措辞在实现时定，下面是键与参数）

| 键 | 参数 | 用途 |
|---|---|---|
| `message.mcfloatingbackpack.compat.untested` | `feature`, `state`, `subject`, `installed`, `verdict`, `ranges` | 管理员登录的聊天；`state` 是下面两个键之一的文字 |
| `message.mcfloatingbackpack.compat.state.off` | — | "主动兼容未启用（面板在它的界面上仍可用，只是不会直接存入它的网络）" |
| `message.mcfloatingbackpack.compat.state.forced` | — | "已被配置强制启用，风险自负（请先备份存档；出问题先把 allowUntestedVersions 改回 false）" |
| `message.mcfloatingbackpack.compat.verdict.below` | — | "比我们验证过的所有版本都低" |
| `message.mcfloatingbackpack.compat.verdict.above` | — | "比我们验证过的所有版本都高" |
| `message.mcfloatingbackpack.compat.verdict.gap` | — | "落在我们验证过的两段版本之间" |
| `message.mcfloatingbackpack.compat.verdict.unknown` | — | "版本号无法判断" |
| `message.mcfloatingbackpack.compat.failed` | `feature`, `error` | 出错关闭时给在线管理员：已在本次运行里关闭、详情见日志、重启后重新判断 |

`feature` = 表里的显示名（"Integrated Terminals"）；`subject`/`installed` = 判不过的模组与它的已装版本原文（自己或某个要求的模组）；`ranges` = `subject` 的全部受支持版本段，用 `VersionRange.toString()` 以 `、` 连接。文字不得含服务器路径或玩家名。

## 3. 日志（服务端，logger = 本模组的 `MCFloatingBackpack.LOGGER`）

- 启动，每个范围外的已装行一条 **WARN**，英文，含：显示名、subject 与它的已装版本、全部受支持版本段、四种结论（below/above/gap/unknown）、状态（`compat disabled` / `compat FORCED by allowUntestedVersions`）。
  例：`Third-party mod Integrated Terminals: Integrated Terminals 1.13.0 is above every version this mod was verified with ([1.11.0, 1.12.0)); active compatibility is disabled (only the generic path of the panel remains). ...`
- 出错，第一次一条 **ERROR**，含异常；之后同模组不再写。
- 范围内、没装的模组：**不写**。

## 4. 调试开关

`-Pmcfb.fakeModVersion=<modid>=<版本>[,<modid>=<版本>…]`（`build.gradle` 现有转发 → `-Dmcfloatingbackpack.debug.fakeModVersion`）。`VersionDebug.fakeModVersions()` 返回 `Map<String, String>`；空/缺 `=`/空键的项忽略。`InstalledMods.versionOf(modId)`：先看没装则空（真的没装就是没装）；装了则优先用伪造值，否则 `ModInfo.getVersion().toString()`。默认不起作用（属性没给就是空映射）。

## 5. `neoforge.mods.toml`（新增四条，范围一律 `[0,)`）

```toml
# 058: Integrated Terminals + what it needs. Optional; the range is checked in our own code (compat/ThirdPartyTable), never here:
# FML aborts loading when an installed optional dependency is outside its versionRange.
[[dependencies.${mod_id}]]
    modId="integratedterminals"
    type="optional"
    versionRange="[0,)"
    ordering="NONE"
    side="BOTH"
# …同样三条：integrateddynamics、commoncapabilities、cyclopscore
```

## 6. `build.gradle`（新增四条 `compileOnly`，与已有 `localRuntime` 同版本）

```groovy
compileOnly("maven.modrinth:integrated-terminals:1.21.1-1.11.2") { transitive = false }
compileOnly("maven.modrinth:integrated-dynamics:1.21.1-1.39.1") { transitive = false }
compileOnly("maven.modrinth:common-capabilities:1.21.1-2.11.6") { transitive = false }
compileOnly("maven.modrinth:cyclops-core:mbABgx56") { transitive = false }
```

`DependencyRulesTest` 已有的规则覆盖：`maven.modrinth` 只允许 `compileOnly`/`localRuntime`。

## 7. README / README_CN 兼容表

在 `## Compatibility` 一节加一张表（中文版相同结构）：

| Mod | Panel → its container | Its container → panel | How | Supported versions |
|---|---|---|---|---|
| Sophisticated Storage | ✓ | ✓ | generic path (012), verified in game | any |
| Applied Energistics 2 | ✓ (to an ME terminal) | not verified, expected not to work | generic path (012), verified in game for panel → terminal | any |
| Integrated Terminals | ✓ (item storage tab only) | not supported yet | active compatibility (058) | `[1.11.0, 1.12.0)` with Integrated Dynamics `[1.39.0, 1.40.0)` and Common Capabilities `[2.11.0, 2.12.0)` |

表后一段文字：范围外的版本主动兼容默认不启用，管理员会在日志与聊天里收到警告；服务端配置 `[compat].allowUntestedVersions` 可强制启用（先备份存档）；客户端 `compat.modLevels` 对 `integratedterminals` 设为 `OFF` 仍会让面板不在其界面显示，设为 `GENERIC` 不会关闭服务端的存入（那是服务端的代码）。

表后再加一句（FR-027）：按模组的档位 `modLevels` 是**客户端**配置；专用服务器上，容器物品交换、Curios/Accessories 的背着来源与集成终端的存入这些服务端部分，暂时不能单独关闭（服务端档位计划在 059 做）。
