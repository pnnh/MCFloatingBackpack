# Research: 063

## R1 为什么总数会变少

`deliver` 在停放格里放入整叠，调菜单的 `quickMoveStack`。ME 终端（`MEStorageMenu`）覆盖了 `transferStackToMenu`，把物品插入网络存储——不是留在菜单的任何格子里。`before`（含这叠）与 `after`（含剩下的）之差正好是网络收下的数量。060 的容器测试里见到 5 条 ERROR，物品都在网络里（ME 网格条目数量对）。

## R2 分类规则

令 `taken = 投递数 − 剩下数`，`shrank = before − after`。代入 `before = M0 + 投递数`、`after = M1 + 剩下数`（M0、M1 为投递前后菜单格子里同种物品总数）得 `shrank = 投递数 − 剩下数 − (M1 − M0) = taken − (M1 − M0)`。守恒时 `M1 − M0 = taken`，`shrank = 0`；全被网络吸收时 `M1 = M0`，`shrank = taken`。所以：

- `shrank == 0`：守恒；
- `0 < shrank ≤ taken`：被吸收（菜单格子里增加的比取走的少，差额进了格子看不到的地方）；
- `shrank < 0`：格子里增加的比取走的还多——复制；
- `shrank > taken`：格子里的总数反而比投递前少——别的同种物品也没了。

**取舍**：被吸收和"吸收的同时又丢了别的"在 `shrank ≤ taken` 内无法区分（例如取走 10、吸收 5、另丢 5）；这个盲区是通用路径看不到网络内部的代价，接受。复制与大量丢失（最危险的两类）仍然被抓到。

## R3 备选

- 在 `ContainerMenuAccess` 里识别"网络终端"菜单（按类名）→ 违反"通用路径不认第三方类型"；放弃。
- 对网络终端关掉检查 → 同上，且弱化防线；放弃。
- 把 ERROR 降为 WARN → 复制也不再显眼；放弃。
