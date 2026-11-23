# Quickstart: 063 验证

1. `./gradlew test -PcleanRuntime -PnoJei`：`OpenContainerDeliveryTest`（分类用例）、`NetworkDeliveryLogWiringTest` 通过。
2. 容器 `mcfb-c1`（AE2 试验台：ME 箱子 + 能量元件 + 线缆 + 终端）：面板里拿几叠物品，在 ME 终端上 Ctrl+点击存入；确认 ME 网格里条目增加、面板里减少；`latest.log` 里**没有** `changed the item total`，有 DEBUG 行时 `-Dforge.logging.console.level` 不一定打印，以日志文件为准（没有也不算失败）。
3. 在箱子上存入：行为与以前相同，没有新日志。
