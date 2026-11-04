# Quickstart: 010-backpack-texture-budget

## 自动化校验

```bash
./gradlew compileJava test
```

只能证明没破坏既有逻辑。预算与外观要靠下面的度量和客户端会话。

## 一、度量图集尺寸（SC-001、SC-002、SC-007）

```bash
design/demo/tools/check_atlas_budget.sh
```

期望输出的图集尺寸为 `2048x1024`（至少宽、高都 ≤ 2048），脚本退出码为 0。手动做法见 `contracts/texture-budget.md` 的"度量方法"。

对照记录（研究阶段实测，供回归对比）：

| 场景 | 图集尺寸 |
|---|---|
| 009 之前 / 纯原版 | 1024×1024 |
| 009 现状（10 张 1024²） | 4096×4096 |
| 本方案（512 基础色，`_n` 被过滤） | 2048×1024 |

## 二、清晰度与外观（US2、SC-003、SC-005）

```bash
./gradlew runClient
```

取出五个等级，逐个做：

1. **背上**：F5 第三人称，镜头拉近到能看清背包表面，和提交前版本（可用 `git stash` 或另一份工作区）对照，确认皮革方格纹理还能一格一格分辨，金色五金件没有糊成色块或出现明显光晕。
2. **手持**：第一人称近看。
3. **图标**：快捷栏图标，五档能区分、朝向是正面。
4. 请至少 5 名观察者做盲测：只看皮革背包，判断是否"近看略软但不糊"（≥80% 认为是）；再不看名称区分五档（5/5）。
5. 这是 512 贴图**第一次在 UV 正确的情况下进游戏**（之前那次 512 的"糊"发生在 `flip_v` 修复之前），不能拿旧反馈当结论。

## 三、亮度（SC-004）

对照 `design/demo/obj_export/<tier>_backpack_diffuse.png`（当前 1024 版）与新 512 版，按网格 UV 面积加权平均亮度，偏差应 ≤ ±10%（研究阶段实测 ≤ ±3%）。游戏内白天对比一次主观观感。

## 四、光影包（SC-006、US2 场景 4）

1. 安装 Iris / Oculus（NeOculus）和一个支持 PBR 的光影包。
2. 查看背包表面是否仍有法线带来的凹凸。
3. 如果凹凸消失，说明图集过滤影响了光影包读取 `_n`（研究阶段的推断不成立），需要回头改方案。
4. 没有光影包的环境确认外观与启动无异常。

## 五、既有玩法不受影响（FR-007）

容量、升级、开关面板、互拖物品各点一遍。

## 重新生成贴图（维护者）

```bash
# 1. 从 GLB 取出 2048 原图
Blender --background --factory-startup --python design/demo/tools/extract_textures.py
# 2. 生成 512 基础色（BOX + 轻锐化 + gamma）和 512 法线（LabPBR）
python3 design/demo/tools/build_textures.py
```

具体参数见 `research.md` Decision 3 和 Decision 7。
