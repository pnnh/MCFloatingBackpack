# Data Model: 版本一致性提示

没有存档数据、数据组件。全部在内存里。

## 版本比较（`ModVersions`，纯逻辑）

```text
Parsed   = (major: int, minor: int, patch: int, prerelease: List<String>)   // 构建元数据丢弃
parse(String) -> Optional<Parsed>      // 失败：null、空、非数字、不是 MAJOR.MINOR[.PATCH] 的形状
Result   = MATCH | CLIENT_OLDER | CLIENT_NEWER | UNCOMPARABLE
compare(clientVersion: String, serverVersion: String) -> Result
```

规则见 research D3。不变量：`compare(a, b)` 与 `compare(b, a)` 的结果互为镜像（`CLIENT_OLDER` ↔ `CLIENT_NEWER`，`MATCH`、`UNCOMPARABLE` 不变）；PATCH 与构建元数据不影响结果；解析失败的任一端给 `UNCOMPARABLE`。

## 提示类型（`VersionNotices`，纯逻辑）

```text
Notice = SILENT | OLDER | NEWER | UNKNOWN
noticeFor(Result) -> Notice            // MATCH -> SILENT；CLIENT_OLDER -> OLDER；CLIENT_NEWER -> NEWER；UNCOMPARABLE -> UNKNOWN
legacyClientText(serverVersion) -> String   // 老客户端的中英双语纯文本
```

## 握手包

| 包 | 方向 | 内容 | 注册 |
|---|---|---|---|
| `VersionHelloPayload` | 服务器 → 客户端 | `serverVersion: String`（≤ 64 字符） | 可选，版本 `"1"` |
| `VersionReportPayload` | 客户端 → 服务器 | `clientVersion: String`（≤ 64 字符） | 可选，版本 `"1"` |

## 服务器侧记录（`PlayerVersions`）

`UUID -> String`：玩家当前登录的客户端版本描述：`"尚未报告"`（发了 hello 还没回）、`"无版本检测（版本过旧）"`（老客户端）、或客户端报告的版本字符串。退出时清除。`describe(uuid)` 没有记录时返回 `"未知"`。

## 客户端待显示提示

一个单值（`Notice` + 两个版本字符串），hello 到达时设置，下一个客户端 tick 显示后清空；退出登录时清空。

## 调试开关（`VersionDebug`）

四个可空的系统属性（见 research D7）；全部缺省时行为与不存在这些开关完全相同。
