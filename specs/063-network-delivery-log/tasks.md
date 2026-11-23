# Tasks: 网络终端存入的误导 ERROR 日志

**Tests**: JUnit（`OpenContainerDeliveryTest` 新增分类用例）+ 接线检查；游戏内在容器 `mcfb-c1` 验证（宪法 V）。
不提交 Git；协议号不改（22）；`DataRevision` 仍 6；没有网络终端时行为与之前完全一致。

- [X] T001 `…/inventory/OpenContainerDelivery.java`：`Result` 增加 `absorbed`；新增 `absorbedOrInconsistent`；`deliver` 用它判定
- [X] T002 `…/network/BackpackQuickTransferPayload.java`：被吸收写 DEBUG，不一致写 ERROR
- [X] T003 `…test/inventory/OpenContainerDeliveryTest.java`：NETWORK / DUPLICATES / LOSES_OTHERS 三种假菜单；用例：普通容器、全吸收、部分吸收、收 0 个、复制、丢别的、边界；原有用例仍通过
- [X] T004 `…test/resources/NetworkDeliveryLogWiringTest.java`：DEBUG/ERROR 分级、通用路径无第三方类型
- [X] T005 `./gradlew test -PcleanRuntime -PnoJei`：246 个测试类 / 1881 项 0 失败
- [X] T006 游戏内（容器 AE2 ME 终端）：存入，日志无 "changed the item total" ERROR，物品数量对（见 quickstart）
- [X] T007 文档：060 的 DELIVERY 里 ERROR 一条改成"已由 063 处理"，并补待办 3 的核对结论；新建 063 的 DELIVERY.md
- [ ] T008 `./gradlew build -PcleanRuntime -PnoJei`
