# Contract: 拦截说明与日志文字

拦截发生在服务器启动时，没有客户端语言环境；呈现形式是日志与崩溃报告（research D8）。所以用**一份固定的双语纯文本**，不走语言文件。`{…}` 是运行时填入的值；`WorldGuardTexts` 用纯函数生成，单元测试钉住"必须出现的信息"。

## 拒绝（REFUSE）

**崩溃报告标题（`CrashReport` 的描述，写在 `crash-reports/…-server.txt` 的 `Description:` 一行）**：

```
MCFloatingBackpack: this world was saved by a newer data revision ({stored} > {current}); refusing to load it / 此存档由更新的数据修订保存（{stored} > {current}），拒绝加载
```

**崩溃报告的说明（异常消息）与日志 ERROR**（多行，中文一段、English 一段，内容等价）：

```
[English]
This world was last saved by MCFloatingBackpack {markerVersion} (data revision {stored}); this is {modVersion} (data revision {current}).
Loading it with an older version can silently drop backpack data, and the loss cannot be undone. Nothing in the world was read or changed by this mod.
Update the mod, or back up the world first. To accept the risk anyway:
  1. one time: add the JVM argument -Dmcfloatingbackpack.acceptDowngrade=true; or
  2. for good: delete {markerPath} (or lower data_revision in it).

[中文]
此存档最后由 MCFloatingBackpack {markerVersion}（数据修订 {stored}）保存；当前是 {modVersion}（数据修订 {current}）。
用旧版本加载可能悄悄丢失背包数据，且无法找回。本模组没有读取或改动存档里的任何内容。
请升级模组，或先备份存档。若仍要接受风险：
  1. 一次性：加 JVM 参数 -Dmcfloatingbackpack.acceptDowngrade=true；或
  2. 永久：删除 {markerPath}（或把其中的 data_revision 改低）。
```

- `{markerVersion}` 缺失时写 `unknown / 未知`。
- 必须出现的信息（测试断言）：存档修订、当前修订、当前模组版本、参数 `-Dmcfloatingbackpack.acceptDowngrade=true`、标记文件路径、"备份"、"升级/Update"。

## 放行（ACCEPTED）——WARN

```
MCFloatingBackpack: world marker revision {stored} is higher than this version's {current}; accepted by -Dmcfloatingbackpack.acceptDowngrade=true, backpack data may be lost. / 存档标记修订 {stored} 高于当前版本的 {current}；已按启动参数放行，背包数据可能丢失。
```

## 标记损坏或读不了（CORRUPT）——WARN

```
MCFloatingBackpack: world marker {markerPath} is unreadable ({reason}); treating it as revision 0 and rewriting it. / 存档标记 {markerPath} 无法读取（{reason}）；按修订 0 处理并重写。
```

## 标记写不进去——WARN

```
MCFloatingBackpack: could not write world marker {markerPath} ({reason}); the downgrade guard has no effect for this run. / 无法写入存档标记 {markerPath}（{reason}）；本次降级拦截不生效。
```

## 首次写入 / 升级——INFO

```
MCFloatingBackpack: world marker set to data revision {revision} (was {previous|none}). / 存档标记已设为数据修订 {revision}（原为 {previous|无}）。
```
