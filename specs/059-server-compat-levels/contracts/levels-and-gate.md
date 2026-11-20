# 契约：有效档位、门禁、专用适配

对应 FR-005～FR-011。

## 1. 有效档位

| 服务端 \ 客户端 | FULL | GENERIC | OFF |
|---|---|---|---|
| **FULL** | FULL | GENERIC | OFF |
| **GENERIC** | GENERIC | GENERIC | OFF |
| **OFF** | OFF | OFF | OFF |

专用服务器上客户端档位恒为 FULL（读不到配置），有效档位 = 服务端档位。原版（`minecraft`）与无法判断所属的菜单恒 FULL。测试覆盖全部 9 格。

## 2. 门禁允许表（服务端，`ServerCompatGate.allows(Action, 有效档位)`）

| 动作 | FULL | GENERIC | OFF |
|---|---|---|---|
| `DELIVER_TO_CONTAINER` | 是 | 是 | **否** |
| `TAKE_FROM_CONTAINER` | 是 | 是 | **否** |
| `MERGE_CURSOR` | 是 | 是 | **否** |
| `USE_MENU_CURSOR` | 是 | 是 | **否** |

被拒绝的入口 `return`，不改背包与容器；七处调用（6 个入口，`BackpackTrashPayload` 两处）都换成 `ServerCompatDenials.allows(player, menu, action)`。`BackpackTrashSendPayload` 不改。

## 3. 专用适配（GENERIC 或 OFF 时关闭，即有效档位必须是 FULL）

| 适配 | 谁问 | 条件 |
|---|---|---|
| 058 集成终端桥 | `BridgeDispatch.tryInsert` | `state.isActive(id)`（版本门/强制/出错）**且** `ServerCompatLevels.specificAdaptation(id)` |
| Curios 背着来源 | `CuriosWornSource.active(entity)` | 服务端侧：`ServerCompatLevels.specificAdaptation("curios")`；客户端侧：`CompatConfig.policy().specificAdaptation("curios")`（原样） |
| Accessories 背着来源 | `AccessoriesWornSource.active(entity)` | 同上，id `accessories` |

桥因档位不允许而被跳过时**不是失败**：不调用失败回调、不改 `CompatRuntimeState`、不写 ERROR。来源关闭时背包不删不动，只是不再被当作背着的（`firstWorn` 跳过它，`activeSource` 返回 null）。

## 4. 限流（`DenialThrottle`）

| 情形 | `shouldNotify` |
|---|---|
| 玩家第一次被拒（界面 5，模组 a） | true |
| 同一玩家再次被拒（界面 5，模组 a） | false |
| 同一玩家被拒（界面 6，模组 a） | true（界面编号不同） |
| 同一玩家被拒（界面 6，模组 b） | true |
| `forget(玩家)` 之后（界面 6，模组 b） | true |
| 另一个玩家（界面 5，模组 a） | true（互不影响） |

清除点：`PlayerContainerEvent.Close`（服务端玩家）与 `PlayerLoggedOutEvent`。
