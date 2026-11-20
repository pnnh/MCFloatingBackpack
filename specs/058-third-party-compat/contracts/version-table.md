# 契约：版本范围表、版本解析与判断

对应 FR-001～FR-006、FR-022、FR-025。纯逻辑，放 `compat/`，没有 Minecraft 类型。

## 1. 版本解析 `ThirdPartyVersion.parse(String)`

1. 输入为 null、空串、全空白 → 无法判断。
2. 从左到右找**第一个数字字符**；之前的字符（如 `v`）忽略。
3. 从那里起读"数字+"，其后若紧跟 `.` 且再后面是数字，继续读下一段；否则停。
4. 其余的一切（预发布后缀 `-beta.54`、构建元数据 `+1.21.1`、`..`、末尾的 `.`、字母）忽略。
5. 找不到数字 → 无法判断。段值超过 `long` 范围 → 无法判断（不抛异常）。
6. 比较：逐段，缺的补 0；`1.11` = `1.11.0`；`1.12.0-beta` = `1.12.0`。

| 输入 | 结果 |
|---|---|
| `1.11.2` | [1,11,2] |
| `9.5.1+1.21.1` | [9,5,1] |
| `1.1.0-beta.54+1.21.1` | [1,1,0] |
| `21.1.22` | [21,1,22] |
| `v2.0` | [2,0] |
| `1.12.0-beta` | [1,12,0] |
| `1..2` | [1] |
| `snapshot` / `""` / null | 无法判断 |

## 2. 版本段与判断 `VersionChecker.check(List<VersionRange> ranges, String installed)`

`ranges` 已按下限升序、互不重叠（表的完整性规则）。

| 条件 | 结论 |
|---|---|
| 解析不出版本 | `UNKNOWN` |
| 落在某一段内（`min ≤ v < max`） | `IN_RANGE` |
| `v < ranges[0].min` | `BELOW_ALL` |
| `v ≥ ranges[last].max` | `ABOVE_ALL` |
| 其余（落在两段之间） | `IN_GAP` |

边界：下限含、上限不含。`[1.11.0, 1.12.0)`：`1.11.0`、`1.11.99` 内；`1.10.99` 过低；`1.12.0`、`1.12.0-beta` 过高。

**一行的判断 `CompatFinding check(ThirdPartyCompat, Function<String, Optional<String>> installedVersion)`**：

1. 自己没装 → 不适用（返回空）。
2. `also` 里有任何一个没装 → 不适用（返回空；见 research R6）。
3. 先判自己，不是 `IN_RANGE` 就以自己为"判不过的模组"返回；
4. 再按声明顺序判每个 `also`，第一个不是 `IN_RANGE` 的返回；
5. 全通过 → `IN_RANGE`（subject 为自己）。

## 3. 表 `ThirdPartyTable`

初版一行：

| modId | 显示名 | 受支持版本段 | 同时要求 |
|---|---|---|---|
| `integratedterminals` | Integrated Terminals | `[1.11.0, 1.12.0)` | `integrateddynamics`（Integrated Dynamics）`[1.39.0, 1.40.0)`；`commoncapabilities`（Common Capabilities）`[2.11.0, 2.12.0)` |

完整性规则（测试强制）：每行 `supported` 非空且升序不重叠、每段 `min < max`；`also` 同理；`modId` 唯一；`variant == null`。以后验证了新版本就**追加一段**，不改旧段。

## 4. 与别处的对齐（FR-025，测试读文件核对）

- **`neoforge.mods.toml`**：表里每个 `modId` 和每个 `also` 的 `modId`、再加 `cyclopscore`，都必须有 `[[dependencies.${mod_id}]]` 块，`type="optional"`、`versionRange="[0,)"`。`DependencyRulesTest` 已有的"只有可选"检查继续有效；新增测试额外要求这几条的范围恰为 `[0,)`。
- **README.md / README_CN.md**：兼容说明里表的每一行：该行的显示名和每一个受支持版本段的 `VersionRange.toString()` 原文（如 `[1.11.0, 1.12.0)`）必须出现在文件里；缺一个就失败，信息写明缺的是哪个。
- **`build.gradle`**：读每条 `localRuntime "maven.modrinth:<slug>:<version>"`，slug 与 modId 的对应在测试里写死（`integrated-terminals`↔`integratedterminals`、`integrated-dynamics`↔`integrateddynamics`、`common-capabilities`↔`commoncapabilities`）；版本去掉 `1.21.1-` 前缀后解析，必须落在表里对应模组的某一段内。`cyclops-core` 因为用的是 Modrinth 版本 id（非数字）不进这个检查。`compileOnly` 的同三个模组版本必须与对应 `localRuntime` 的版本**相同**（防止编译用的 API 与开发运行的不一致）。
