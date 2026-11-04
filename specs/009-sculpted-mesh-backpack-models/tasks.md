# Tasks: 雕刻网格背包外观

**Input**: Design documents from `/specs/009-sculpted-mesh-backpack-models/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Status**: 补录。Phase 1~2 是**已经完成**的工作（按提交追溯，不是新做的）；Phase 3 是还没做的人工验证；Phase 4 是已知限制对应的后续候选，**不属于本 feature 的交付范围**。

**Tests**: 本功能是外观与资源，没有适合写自动化测试的纯逻辑。验证靠 `./gradlew compileJava test` + 日志扫描 + 客户端会话（宪法 V 条）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行
- **[Story]**: 对应 spec 里的用户故事（US1~US4）

---

## Phase 1: 已完成 — 外观实现（补录，按提交追溯）

**Purpose**: 把五档外观从立方体元素模型换成雕刻网格，并让背部、手持、图标一致。

- [X] T001 [US1] 五个等级各自一个独立的 `neoforge:obj` 物品模型：`src/main/resources/assets/mcfloatingbackpack/models/item/<tier>_floating_backpack.json`、`<tier>_backpack.obj`、`<tier>_backpack.mtl`（`map_Kd #base`）。皮革 `825007b`；铁、金 `d697aee`；钻石、下界合金 `5a9dc03`
- [X] T002 [US1] 修复贴图错位成黄色污渍：模型 JSON 加 `"flip_v": true`（research Decision 4）。`d46c212`
- [X] T003 [US1] 修正图标/手持/背上的朝向：`display` 沿用 006 之前最早的原始旋转值（research Decision 11）。`825007b`
- [X] T004 [US1] 每档一张 1024×1024 基础色贴图 `textures/item/<tier>_floating_backpack.png`（research Decision 6；**010 起改为 512×512**）。`7f28836`、`d697aee`、`5a9dc03`
- [X] T005 [US3] 关闭模型始终全亮：模型 JSON 加 `"emissive_ambient": false`；按平均亮度做 gamma 调亮（皮革 0.62、铁 0.89、下界合金 0.91、金与钻石不调）。`00e1dff`、`d697aee`、`5a9dc03`
- [X] T006 [P] [US3] 每档随附 LabPBR 法线贴图 `textures/item/<tier>_floating_backpack_n.png`（G 翻转，B/A 填 255）；不做高光贴图（research Decision 7）。`7f28836`
- [X] T007 [US2] 背部渲染改为 `ItemRenderer.renderStatic(FIXED)` 直接渲染物品烘焙模型，删除手搭立方体：`src/main/java/pnnh/mcfloatingbackpack/client/render/BackpackBackLayer.java`。`3beaef2`
- [X] T008 [US2] 背上缩放 0.68，并按近身面贴合躯干背面重新推导位移 z=-0.316、y=-0.42（含纠正第一次把方向搞反）。`87278da`、`c4798cd`
- [X] T009 [US2] 撤销自动减面，改用完整原始网格，避免背面剔除下的透光裂缝（research Decision 5）。`87278da`
- [X] T010 [US2] 各档背面统一对齐到 OBJ z=0.238，使背部位移常量对五档同时成立（research Decision 10）。`d697aee`、`5a9dc03`
- [X] T011 [US2] 钻石、下界合金导出前绕垂直轴旋转 −90°，使正面朝向一致（research Decision 12）。`5a9dc03`
- [X] T012 [US2] 下界合金下口袋补底板：`design/demo/tools/netherite_bottom_cap.py`、`src/main/resources/assets/mcfloatingbackpack/models/item/netherite_backpack.obj`（research Decision 13）。`66140a8`

**Checkpoint**: 五档在资源层面都已是雕刻网格模型，`./gradlew compileJava test` 通过，`runClient` 日志无异常。**外观本身尚未逐项验证**，见 Phase 3。

---

## Phase 2: 已完成 — 设计素材管线与文档对齐

- [X] T013 [US4] 设计素材放入 `design/` 并纳入 Git：`design/demo/`（原始 GLB、效果图、`obj_export/`、`tools/`、README）、`design/backpack-redesign/`（立方体时期的设计历史）。`dd277bb`、`4d4e12c`、`d697aee`、`5a9dc03`
- [X] T014 [US4] `design/**/*.obj` 走 Git LFS，随模组打包的 `.obj` 保持普通文件（research Decision 14）：`.gitattributes`。`ce51d7f`
- [X] T015 [P] [US4] 在 `design/demo/README.md` 记录各档处理步骤、踩过的坑、已知缺陷与未验证项
- [X] T016 补录 009 的 `spec.md`、`plan.md`、`research.md`、`quickstart.md`、`checklists/requirements.md`
- [X] T017 [P] 修订 006 中与现状冲突的外观条款（`spec.md` FR-010/FR-011/SC-004/Assumptions、`research.md` §5、`contracts/tiered-backpack.md`、`plan.md`、`tasks.md` T020~T022、`quickstart.md`、`DELIVERY.md`、`checklists/requirements.md`），原文保留并标注"【009 修订】"
- [X] T018 [P] 更新过期文档：`docs/MCFloatingBackpack.md` M5 外观段、`docs/plan/005-gameplay-item-tiers.md` 的 M7/M10 行、`design/backpack-redesign/README.md` 开头

---

## Phase 3: 待办 — 客户端会话验证（宪法 V 条，**本 feature 完成的前提**）

**Purpose**: 把 spec 里写明"尚未验证"的成功标准在游戏里逐项确认，结果记录到 `specs/009-sculpted-mesh-backpack-models/DELIVERY.md`。需要真实客户端会话，无法由自动化代替。

- [ ] T019 [US1] 按 `quickstart.md` 第一部分：五个等级的图标、手持、背上都是同一只包、朝向正面（SC-002）。目前只有皮革和下界合金被截图确认过
- [ ] T020 [US1] 组织 5 名观察者做五档区分测试（SC-001，沿用 006 SC-003 的口径），记录每人得分
- [ ] T021 [US2] 按 `quickstart.md` 第二部分：对每个等级检查背上贴身距离（SC-003）、是否有透光细缝（SC-004）、仰视底部是否封闭；重点看金、钻石、铁和下界合金的底板
- [ ] T022 [US3] 按 `quickstart.md` 第三部分：白天/夜晚/洞穴亮度变化（SC-005）、白天亮度对照效果图（SC-007）；如有条件在装了光影包的客户端确认法线效果（SC-006）
- [ ] T023 按 `quickstart.md` 第四部分：确认容量、互拖、升级等既有玩法没有被影响（FR-013）
- [ ] T024 按 `quickstart.md` 第五部分：粗测性能，记录背着下界合金背包与不背包的帧率、多人同屏帧率（为已知限制 3 提供第一组数据）
- [ ] T025 把 T019~T024 的结果和发现的缺陷写入 `specs/009-sculpted-mesh-backpack-models/DELIVERY.md`，并回头更新 spec"已知限制"里被验证或推翻的条目

---

## Phase 4: 后续候选（**不属于本 feature 的交付范围**，各自需要单独的 spec）

这些是 spec"已知限制"对应的后续工作，按建议优先级排列。本 feature 只负责如实记录，不在此实现（spec 范围边界已明确）。

- [X] F001 **共用方块图集膨胀**（已知限制 1，影响所有玩家，优先级最高）：评估把贴图降到 256/512、或单独图集，对比清晰度与显存；产出新的贴图预算。→ **已立项为 [010-backpack-texture-budget](../010-backpack-texture-budget/spec.md)，并已实现**：图集实测 2048×1024（上限 2048×2048），清晰度手持与背上已由用户确认（图标、五人盲测、光影下法线凹凸仍待确认，见 010 DELIVERY.md）
- [ ] F002 **性能预算**（已知限制 3）：由 T024 的数据出发，决定每档面数上限；必要时手工重拓扑（自动减面会撕网格）
- [ ] F003 **修补网格开放边**（已知限制 2）：金 325、钻石 169、铁 122、下界合金 114；先在游戏里确认哪些位置真的可见
- [ ] F004 **逐档校准显示参数**（已知限制 5、7）：铁/金/钻石/下界合金的图标、手持 `display` 与亮度；下界合金正面朝向确认（可能需要把旋转换成 +90°）
- [ ] F005 **钻石、金的背上位置偏差**（已知限制 4）：钻石翻盖导致的 0.05 方块间隔、金背包偏高
- [ ] F006 **评估高光/粗糙度贴图**（已知限制 8）：金的 GLB 渲染出的金属质感可能有真数据，值得逐个核对 ORM 通道
- [ ] F007 **`.glb` 与随包 `.obj` 的大文件策略**（已知限制 9）：`.glb` 是真二进制且未走 LFS；随包 `.obj` 每次重导出都让历史增长约 10MB
- [ ] F008 **清理遗留文件**：`floating_backpack.json`（已无引用）、`textures/entity/backpack_back.png`（未被引用）、`FloatingBackpackItem.texture()` 与 `BackpackTier.texturePath()`（已无调用方）
- [ ] F009 **可拆卸小包与升级组件玩法**：用户已明确延后（见 `docs/plan/005-gameplay-item-tiers.md` 的 M8/M9）；`docs/ideas/` 里有相关构想，该目录由用户手写维护，本 feature 不修改它，也不把其中内容当作已确定的需求

---

## Dependencies & Execution Order

- Phase 1~2 已完成，无依赖问题。
- Phase 3 的 T019~T024 可以并行进行（都在同一个客户端会话里），T025 依赖它们全部完成。
- **本 feature 在 T025 完成之前不得标记为 Done**：spec 的 Status 保持"Implemented（补录）"，成功标准里标注"尚未验证"的项在验证之前不算达成。
- Phase 4 各项互相独立，F001 与 F002 建议先做；每一项开工前先走 `/speckit-specify`。

## Notes

- 提交哈希来自 `git log`，可用 `git show <hash>` 追溯当时的改动与说明。
- 这是补录 tasks：Phase 1 的勾选表示**代码已存在**，不表示**外观已验收**。
