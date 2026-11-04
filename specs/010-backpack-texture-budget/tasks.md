# Tasks: 背包贴图预算与共用图集占用

**Input**: Design documents from `/specs/010-backpack-texture-budget/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/texture-budget.md, quickstart.md

**Tests**: 本功能是资源与素材改动，没有适合写成 JUnit 的纯逻辑；规格也没有要求测试任务。验证靠度量脚本、数值检查和客户端会话（宪法 V 条），已作为任务列出。

**Organization**: 按用户故事分组。US1（图集降下来）是 MVP；US2（外观质量）需要 US1 产出的新贴图才能验证；US3、US4 依赖 US1 的结果。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1~US4）
- 路径都相对于仓库根目录

---

## Phase 1: Setup（素材处理脚本）

**Purpose**: 把研究阶段用过的一次性命令固化成可重复的脚本。

- [ ] T001 [P] 在 `.gitignore` 增加 `design/demo/work/`（2048 原图与中间产物的工作目录，约 5 张×4MB 基础色加 5 张法线，不入库），并确认 `git status` 不会列出它
- [ ] T002 [P] 新建 `design/demo/tools/extract_textures.py`：Blender 无头脚本，对五个 GLB（`design/demo/<等级>背包.glb`）导出 2048 基础色与法线原图到 `design/demo/work/orig2048/<tier>_base.png`、`<tier>_normal.png`；通过 Material 节点连接找到图像，**不要按图像名取**（同一 Blender 会话里图像名会变成 `Image_0.001`）。参考 research.md 的实验脚本
- [ ] T003 新建 `design/demo/tools/build_textures.py`（依赖 T002 的输出）：Pillow 脚本，接受尺寸参数（默认 512）。基础色：`BOX` 缩放 → `UnsharpMask(radius=1.0, percent=90, threshold=2)` → 按档位 gamma（皮革 0.62、铁 0.89、下界合金 0.91、金与钻石不调）；法线：先 `BOX`/Lanczos 缩放，再做 LabPBR 转换（R 保持、G 翻转为 255−G、B=255、A=255，输出 RGBA）。输出到 `design/demo/work/out/` 与 `design/demo/obj_export/<tier>_backpack_diffuse_<size>.png`、`<tier>_backpack_normal_n_<size>.png`。尺寸做成参数，是为了 US2 不达标时可以退到 640

---

## Phase 2: Foundational（度量脚本，阻塞所有故事的验证）

**Purpose**: 没有度量手段，就无法判断任何故事是否达标，也是这次问题被发现得太晚的原因。

- [ ] T004 新建 `design/demo/tools/check_atlas_budget.sh`：删除旧 `run/logs/latest.log`，起 `./gradlew runClient`（超时约 45 秒后结束），从日志读 `Created: <W>x<H>x<N> minecraft:textures/atlas/blocks.png-atlas`，打印 W、H、估算显存（`W×H×4×1.332`），宽或高大于 2048 时非零退出；日志里找不到该行时也非零退出并给出明确提示（避免"没测到"被当成"通过"）。结束时确认没有遗留的客户端进程

**Checkpoint**: 在**尚未改动资源**的当前工作区跑一次 T004，应当得到 `4096x4096` 并以非零退出——证明脚本能抓到现状问题。

---

## Phase 3: User Story 1 - 不用背包的玩家也不为它多付显存代价 (Priority: P1) 🎯 MVP

**Goal**: 共用方块图集宽、高都 ≤ 2048，且与玩家是否使用背包无关。

**Independent Test**: 干净客户端启动，度量脚本输出 `2048x1024`，退出码 0。

- [ ] T005 [US1] 新建 `src/main/resources/assets/minecraft/atlases/blocks.json`，内容为 `contracts/texture-budget.md` 里那一个 `minecraft:filter` 源（`namespace: mcfloatingbackpack`，`path: item/.*_n`）。注意路径是 `assets/minecraft/`，不是 `assets/mcfloatingbackpack/`
- [ ] T006 [US1] 运行 T002、T003 生成 512 贴图，并把 5 张基础色与 5 张 `_n` 覆盖到 `src/main/resources/assets/mcfloatingbackpack/textures/item/<tier>_floating_backpack.png` 与 `<tier>_floating_backpack_n.png`；保留旧的 1024 版 `design/demo/obj_export/<tier>_backpack_diffuse.png` 不动，作为对照基准
- [ ] T007 [US1] 运行 `./gradlew compileJava test`，确认没有破坏既有逻辑；运行 `rm -f run/logs/latest.log && ./gradlew runClient` 并扫日志，确认没有关于 `mcfloatingbackpack` 模型、OBJ、贴图的异常（图集过滤文件解析错误会出现在日志里：`Failed to parse atlas definition`）
- [ ] T008 [US1] 运行 T004 的度量脚本，期望 `2048x1024`、退出码 0；把数字记录到 `specs/010-backpack-texture-budget/DELIVERY.md`（新建）。如果得到的不是 2048×1024，回头对照 research.md 的矩阵排查（先怀疑 `_n` 是否真的被过滤、贴图是否真的是 512）

**Checkpoint**: US1 达成：SC-001、SC-002（尺寸部分）。此时外观质量还没验证，不得宣称完成。

---

## Phase 4: User Story 2 - 用背包的玩家看到的背包仍然清晰 (Priority: P1)

**Goal**: 五档背包在图标、手持、背上三种场景下达到"近看略软但不糊"，五档可区分，朝向与亮度不变，法线仍可用。

**Independent Test**: 把新 512 版与当前 1024 版逐档对比并做盲测。

- [ ] T009 [P] [US2] 新建 `design/demo/tools/check_brightness.py`：按网格 `.obj` 的实际使用 UV 区域面积加权，计算新贴图与旧 1024 版（`design/demo/obj_export/<tier>_backpack_diffuse.png`）的平均亮度偏差，超过 ±10% 时非零退出；输出五档偏差表。研究阶段的实测数字（≤ ±3%）作为预期值
- [ ] T010 [US2] 运行 T009，把结果记录到 DELIVERY.md；如果某档超过 ±10%，先检查 T003 的 gamma 是否按档位应用，不要直接放宽阈值
- [ ] T011 [US2] 按 `quickstart.md` 第二部分，在 `./gradlew runClient` 里逐档对比 512 新版与 1024 旧版：背上（F5 拉近）、手持、图标。重点看皮革方格纹理是否一格一格分辨得出，金色五金件边缘是否有明显光晕。这是 512 **第一次在 UV 正确的情况下进游戏**，结果记录到 DELIVERY.md（附截图路径）
- [ ] T012 [US2] 组织 5 名观察者做盲测：只看皮革背包判断是否"近看略软但不糊"（要求 ≥80% 认为是，SC-003）；不看名称区分五档（每人 5/5，SC-005）；记录每人结果
- [ ] T013 [US2] 在装有 Iris/Oculus（NeOculus）和一个支持 PBR 的光影包的客户端里，查看背包表面是否仍有法线凹凸；同时在没有光影包的环境确认外观无异常（quickstart 第四部分）。**这是对 research.md 里"图集过滤不影响光影包读取 `_n`"这个推断的验证**
- [ ] T014 [US2] **条件任务**：如果 T011/T012 判定 512 达不到"近看略软但不糊"，用 `build_textures.py 640` 重新生成并替换，再重做 T008（预期 2048×2048，没有余量）、T010、T011；如果连 640 也不行，停下来回到 spec，与用户确认是否放宽上限，**不要**自行改回 1024
- [ ] T015 [US2] **条件任务**：如果 T013 发现光影包下法线消失，说明图集过滤影响了光影读取，回到 research.md 重新评估（候选：只过滤图集里的精灵而保留文件在原位置的其它做法）；在这个问题解决之前不得合入

**Checkpoint**: US2 达成：SC-003、SC-004、SC-005，以及 FR-006。

---

## Phase 5: User Story 3 - 低端硬件也能正常启动和渲染 (Priority: P2)

**Goal**: 在最大纹理尺寸为 2048 的环境下，图集创建成功，背包显示正常。

**Independent Test**: 在限制了最大纹理尺寸的环境里启动并查看背包。

- [ ] T016 [US3] 查明如何验证最大纹理尺寸 2048 的环境：先看本机游戏日志里有没有 GPU 最大纹理尺寸的输出行；评估是否能用已有手段限制（真实旧硬件、或其它能限制纹理尺寸的测试环境）。把结论（能验证 / 只能按构造论证）写进 DELIVERY.md，**不能验证时如实写，不要写成已验证**
- [ ] T017 [US3] 如果有可用环境：按 quickstart 在该环境启动，确认图集创建成功、方块与物品贴图完整、取出五档背包都正常显示（SC-006）；没有可用环境时，在 DELIVERY.md 注明 SC-006 仅按"图集尺寸 ≤ 2048"构造论证，未实测
- [ ] T018 [P] [US3] 按 `quickstart.md` 第五部分，确认容量、升级、开关面板、互拖物品等既有玩法没有被影响（FR-007）

**Checkpoint**: US3 达成（或如实记录为未能实测）：SC-006。

---

## Phase 6: User Story 4 - 维护者能度量并守住预算 (Priority: P3)

**Goal**: 维护者凭仓库里的说明能度量占用，并在新增大贴图时发现超预算。

**Independent Test**: 按说明 10 分钟内测出图集尺寸；故意放一张大贴图能被脚本抓到。

- [ ] T019 [US4] 验证 T004 脚本能抓到超预算：临时把一张背包基础色换成 1024×1024，运行脚本，确认非零退出并给出明确信息；然后用 `git checkout` 还原该文件，**并确认 `git status` 干净**
- [ ] T020 [P] [US4] 更新 `design/demo/README.md`：新增一节说明新的贴图处理流程（2048 原图 → 512，BOX + 轻锐化 + gamma；`_n` 被图集过滤排除）、预算数值与度量方法入口；修订其中"1024×1024 是最终选择"和"512 糊"的旧说法，指向 010 research.md
- [ ] T021 [P] [US4] 在 `design/demo/README.md` 或 `contracts/texture-budget.md` 里补一条"新增/替换背包贴图前后必须跑度量脚本"的维护规则，并确认 `contracts/texture-budget.md` 中的路径、命令与实际脚本一致（脚本名、参数、退出码约定）
- [ ] T022 [US4] 请另一个人（或以"只看文档"的方式自己）按 `design/demo/README.md` 与 `contracts/texture-budget.md` 走一遍：能否在 10 分钟内测出图集尺寸并判断是否超预算（SC-007）；记录卡住的步骤并修文档

**Checkpoint**: US4 达成：FR-008、SC-007。

---

## Phase 7: Polish & 文档收尾

- [ ] T023 [P] 更新 `specs/009-sculpted-mesh-backpack-models/spec.md` 的"已知限制 1"：写明已由 010 解决（图集尺寸与方案），或在 010 的 T008 未达标时如实保留；同步更新 009 `tasks.md` 的 F001
- [ ] T024 [P] 更新 `docs/MCFloatingBackpack.md` M5 的外观段落：把"贴图每档 1024×1024"和"图集撑到 4096×4096"的说法改成实际结果
- [ ] T025 [P] 在 010 `spec.md` 的 Status 里写明实现状态；把 plan.md 的"仍然未验证"清单逐条更新为已验证/仍未验证
- [ ] T026 完成 `specs/010-backpack-texture-budget/DELIVERY.md`：记录 T008、T010、T011、T012、T013、T017 的结果，**包括没做到的和结论不确定的**
- [ ] T027 运行 `./gradlew compileJava test` 做最后回归，并再跑一次度量脚本确认仍是 2048×1024

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001~T003）：T001、T002 可并行；T003 依赖 T002 的输出（2048 原图）
- Phase 2（T004）：独立，可与 Phase 1 并行；**在改动资源之前先跑一次**，作为对照
- Phase 3 US1（T005~T008）：依赖 Phase 1 与 Phase 2
- Phase 4 US2（T009~T015）：依赖 US1 产出的新贴图；T009 可以提前并行写
- Phase 5 US3（T016~T018）：依赖 US1；T018 可与 US2 并行
- Phase 6 US4（T019~T022）：T019 依赖 T004；T020、T021 可与 US2/US3 并行写；T022 最后做
- Phase 7：依赖前面的结果

### User Story Dependencies

- **US1**：无依赖，是 MVP
- **US2**：依赖 US1 的资源（要有 512 贴图才能对比）；T014、T015 是条件任务，可能触发回到 research
- **US3**：依赖 US1
- **US4**：T019 依赖 T004；文档类任务相互独立

### Parallel Opportunities

- T001 与 T002 并行；T004 与 T001~T003 并行
- T009 可在 US1 实现期间提前写
- T018、T020、T021 与 US2 的验证并行
- T023、T024、T025 并行

## Parallel Example

```text
T001 .gitignore 增加 work 目录
T002 extract_textures.py（Blender 无头脚本）
T004 check_atlas_budget.sh（度量脚本）
```

## Implementation Strategy

### MVP 优先（只做 US1）

1. 完成 Phase 1~2：脚本到位，先在未改动的工作区跑一次度量，确认得到 4096×4096（对照）
2. 完成 Phase 3：加图集过滤、换 512 贴图、再度量得到 2048×1024
3. **停下来验证**：US1 只保证了尺寸，外观质量必须由 US2 验证，**US1 完成不等于功能可交付**

### 增量交付

1. US1 → 图集降下来（可度量）
2. US2 → 外观与光影验证（决定能否合入；不达标则走 T014/T015 条件任务）
3. US3 → 低端硬件与既有玩法
4. US4 → 度量与文档守住预算

## Notes

- **不要用 1024 作为退路**：T014 明确写了退到 640，再不行就回 spec；1024 会让图集回到 4096×2048，超出用户确认的 2048×2048 上限
- 实验用的贴图替换必须在实验后还原并确认 `git status` 干净（研究阶段用 `cmp` 对比备份确认过，实现阶段同样要做）
- 图集尺寸是实测，显存是推算，DELIVERY.md 里要如实标注
- `docs/ideas/` 不要修改；规划/设计文档放 `docs/plan/`
