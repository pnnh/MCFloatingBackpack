# Contract: 握手

## 注册

在 `BackpackNetwork` 的注册事件里，**另起一个注册器**：

```java
PayloadRegistrar versionCheck = event.registrar(MCFloatingBackpack.MOD_ID).versioned("1").optional();
versionCheck.playToClient(VersionHelloPayload.TYPE, VersionHelloPayload.STREAM_CODEC, VersionHelloPayload::handleClient);
versionCheck.playToServer(VersionReportPayload.TYPE, VersionReportPayload.STREAM_CODEC, VersionReportPayload::handleServer);
```

- 现有的 `versioned("17")` 注册器、包与协议号**不变**；
- 版本 `"1"` **永远不改**（两端都有而版本不同会让登录失败）；以后要演进就新增另一个包类型；
- ids：`mcfloatingbackpack:version_hello`、`mcfloatingbackpack:version_report`。

## 时序

```text
玩家登录 (PlayerLoggedInEvent, 服务器)
 ├─ server.isSingleplayerOwner(player) ........................ 什么都不做（单人游戏、局域网房主）
 ├─ connection.hasChannel(VersionHelloPayload.TYPE) 为真 ...... 发 hello(serverVersion)；PlayerVersions = "尚未报告"
 │      客户端：比较 → 不一致则下一个 tick 在本人聊天输出警告；总是回 report(clientVersion)
 │      服务器：收到 report → PlayerVersions = 版本；比较 → 不一致 WARN
 └─ 为假（老客户端） ........................................... player.sendSystemMessage(纯文本双语)；WARN；PlayerVersions = "无版本检测"
玩家退出 ........................................................ PlayerVersions 清除；客户端待显示提示清空
```

## 约束

- 对没有通道的客户端**绝不调用** `sendToPlayer`（NeoForge 会抛 `UnsupportedOperationException`）；
- 包体里的字符串限长 64，超长或解码失败按"无法比较"处理，**不断开连接**；
- report 丢失、延迟或不回：服务器的 `PlayerVersions` 停在"尚未报告"，不影响任何其它行为；
- 服务器**不据此拒绝登录、不踢人、不改变任何规则**；
- 每次登录最多一次 hello 与一次提示。
