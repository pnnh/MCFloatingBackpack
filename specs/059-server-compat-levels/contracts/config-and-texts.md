# 契约：配置、文字、日志、README

对应 FR-001～FR-004、FR-012、FR-013、FR-015。

## 1. 服务端配置 `[compat].modLevels`

在 058 的 `[compat]` 节里（`ThirdPartyConfig.define`，同一次 `push("compat")` 里先定义 `modLevels` 再定义 `allowUntestedVersions`，或反之），`defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", element -> element instanceof String)`，与客户端同形。注释（英文，与客户端同风格）必须含：

- "How far the floating panel goes in working with another mod **on the server**. One entry per mod: "modid=LEVEL". A mod that is not listed is at FULL. Vanilla (minecraft) cannot be configured."
- 三档在服务端：`FULL`——the mod-specific adaptations on the server run; `GENERIC`——they do not run (today: the Integrated Terminals bridge, and the Curios and Accessories back slots as places a backpack can be worn); the generic Ctrl+click path stays; `OFF`——the server also refuses every item exchange between the panel and that mod's screens (the player gets a chat line once per opened screen), and the adaptations are off as well.
- "On a dedicated server this is the only place; in a single player world (or a LAN world) the client's `compat.modLevels` is read too and the stricter of the two counts."
- "Changes take effect without reopening the world."（以游戏内验证为准，见 research R5）
- 例子：`modLevels = ["sophisticatedstorage=OFF", "integratedterminals=GENERIC"]`。

错误项：与客户端同样的四种（MALFORMED、VANILLA、UNKNOWN_LEVEL、DUPLICATE）；WARN 日志 `Server config compat.modLevels: <Warning.describe()>`；同一个列表对象只写一次。配置未载入 → 空（全 FULL）。

## 2. 语言键（`en_us.json`、`zh_cn.json`）

| 键 | 参数 | 文字 |
|---|---|---|
| `message.mcfloatingbackpack.compat.server_off` | `%1$s` 模组 id | en: "This server does not allow the floating backpack to exchange items with %1$s screens (a server setting)." zh: "这台服务器不允许浮动背包与 %1$s 的界面交换物品（服务器设置）。" |

走 `message.mcfloatingbackpack.prefix` 前缀（同 058 的 `say`）。

## 3. 日志

- 被拒绝：DEBUG，`Server compat level OFF for <modId>: <action> refused for <playerName>`（每次，不限流；限流只管聊天）。
- 配置错误项：WARN（见 1）。
- 没有新的 INFO/ERROR。

## 4. README / README_CN（FR-015）

把 058 在 `## Compatibility` 里写的这一条

> The per-mod levels (`compat.modLevels`) are a **client** setting. On a dedicated server the parts that run on the server … cannot be switched off separately yet. …

替换为：

> **Per-mod levels.** `[compat].modLevels` in the server config (`serverconfig/mcfloatingbackpack-server.toml`, same `"modid=LEVEL"` list as the client's `compat.modLevels`) is what the server uses; where the server can also read the client's setting (single player, LAN host) the **stricter** of the two counts. `FULL`: normal. `GENERIC`: the parts of this mod that are written for that mod and run on the server are off (the Integrated Terminals insertion, the Curios and Accessories back slots), Ctrl+click on its screens still works the generic way. `OFF`: the server also refuses every item exchange between the panel and that mod's screens and tells the player once per opened screen. Vanilla cannot be configured. The client's `OFF` still hides the panel on that mod's screens; the server's does not (the client is not told), the player just gets the notice.

中文版同义。"Key conflict" 一条保留。README 对齐测试里 058 的 `theReadmesSayThePerModLevelsAreAClientSetting`（读 "are a **client** setting"）要同步改成新说明的关键词。
