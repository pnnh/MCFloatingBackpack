# DELIVERY: 009-sculpted-mesh-backpack-models

**Date**: 2026-11-04
**Status**: 实现完成（补录）；人工验证**大部分完成**，缺五人观察者（T020）、升级配方回归（T023 一部分）、性能粗测（T024）。

## 人工验证

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选（2026-11-04）。清单只记录了通过与否，**没有记录每项的细节数据**；没勾的项都是**未验证**，不是通过。

| 任务 | 结果 |
| --- | --- |
| T019 五档的图标、手持、背上是同一只包、朝向正面 | 通过（用户确认五档渲染没有问题） |
| T021 背上贴身距离、细缝透光、仰视底部封闭（对每个等级） | 通过（G1 侧面缝隙、G2 身后/侧面透光、G3 仰视底部、G4 其他档手持透光，用户均已勾选；**没有记录各档的具体观察和数据**，包括钻石预期的 0.05 方块间隔与金背包是否超出躯干——这两项已知限制是否仍成立，按"用户未报告缺陷"处理，不是"已测得为 0"） |
| T020 五人观察者区分五档 | **未做** |
| T022 夜间/洞穴亮度随环境变化且夜里不发光（H1）、白天对照效果图亮度（H2）、光影下五档外观与法线（L1–L3） | 通过（用户逐项勾选；**没有记录各档的具体观察**，未报告亮度偏差） |
| T023 既有玩法回归 | **部分**：容量对应等级与面板互拖通过；升级配方保留内容未验证 |
| T024 性能粗测 | **未做**，仍没有任何帧率数据 |
| T025 汇总 | 待上面各项做完 |

## 已知限制的状态变化

- 已知限制 5（显示参数未逐档验证）：用户确认五档图标、手持、背上渲染没有问题，显示参数与朝向视为已验证；亮度仍是估算值。
- 已知限制 2、4（开放边、背上位置偏差）：G1/G2 用户未报告缺陷，但没有数据；铁背包手持透光缝已在 010 期间修复（见 `design/demo/README.md` 第十轮）。
- 已知限制 1（图集膨胀）：已由 010 解决。
- 已知限制 7（亮度是估算值）：H2、L3 用户未报告偏差，按"未发现问题"记，仍没有逐档的数据。
- 其余（性能预算、`_s`、LFS 策略）无变化。

## 009 之后对铁背包与开发环境的改动（记录在 010 期间，此处补全）

- **铁背包 OBJ 不再是"完整原始网格"**：`iron_backpack.obj` 末尾多了 10 块薄板（40 个三角形，正反各一层），补的是手持第一人称时能看到背后场景的缝。开放边数量没变（122），只是在缝里放了遮挡板。补丁脚本 `design/demo/tools/patch_iron_slit.py`，检查工具 `design/demo/tools/firstperson_holes.py`（按游戏真实的手持变换重建画面找洞），过程与踩坑见 `design/demo/README.md` 第十轮。重新导出铁背包时需要重新打补丁。
- **开发期光影环境**：`./gradlew runClientShader`（Iris 1.8.12 + Sodium 0.6.13，仅开发运行时）与 `design/demo/tools/fetch_shaderpack.sh`；见 010 的 DELIVERY.md。
- **清理遗留**（F008）：2026-11-04 删除了 `floating_backpack.json`、`textures/entity/backpack_back.png`、`FloatingBackpackItem.texture()`、`BackpackTier.texturePath()`；之后又删除了模板遗留的空 `mcfloatingbackpack.mixins.json`、空 `META-INF/accesstransformer.cfg`（及 `neoforge.mods.toml` 里对应的两段声明）和不再使用的语言键 `panel.mcfloatingbackpack.title`。`compileJava test` 通过，客户端启动无加载错误。
