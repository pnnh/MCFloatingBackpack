# Research: 背包在 GUI 里用平面图标

## 已读到的事实

| # | 事实 | 出处 |
|---|---|---|
| F1 | `SeparateTransformsModel`：`base` 与 `perspectives`（按 `ItemDisplayContext` 的序列化名）；`Baked.applyTransform` 对命中的场景返回子模型，渲染用的是子模型的面；顶层 `getTransforms()` 返回空 | NeoForge 21.1.251 `SeparateTransformsModel` |
| F2 | `perspectives` 里的键是 `ItemDisplayContext.getSerializedName()`：`gui`、`ground`、`fixed`、`firstperson_righthand`、`thirdperson_righthand` 等 | 同上 |
| F3 | 背在背上的渲染 `BackpackBackLayer` 调 `renderStatic(..., ItemDisplayContext.FIXED, ...)`；注释里明确依赖 3D 模型的 `fixed` 变换（缩放 1.1、旋转 180°） | `BackpackBackLayer` |
| F4 | 现有 3D 模型 JSON：`loader: neoforge:obj`、`model`、`flip_v: true`、`textures: {particle, base}`、`display`（gui、手持、ground、fixed） | `models/item/*_floating_backpack.json` |
| F5 | OBJ：顶点坐标按方块空间 0～1 使用，渲染前会自动平移 (-0.5, -0.5, -0.5)；`flip_v` 为真时纹理 v 取 1−v；`.mtl` 的 `map_Kd #base` 指向 JSON 的 `textures.base` | `design/demo/README.md`、`ObjLoader` |
| F6 | 现有 GUI 视角：`display.gui` 的 `rotation [30, 225, 0]`、`translation [0, 1, 0]`、`scale 0.95` | `leather_floating_backpack.json` |
| F7 | 垃圾桶等平面物品用 `parent: minecraft:item/generated` + `textures.layer0`；图标由 `design/demo/tools/` 的脚本生成 | `trash_can.json` |
| F8 | 本仓库的 Python 环境只有 PIL 13.0.0，没有 numpy | 试运行 |

## Decisions

### D1. 只换 GUI，不换 FIXED 与 GROUND

**决定**：`perspectives` 只有 `gui`。**理由**：F3：背在背上用 `FIXED`；展示框也用 `FIXED`，数量少。掉落物（`GROUND`）原先也设想换成图标，**2026-11-09 用户验证帧率达标后要求保持 3D**（同时掉落很多会掉帧，但正常游玩不太可能）。手持场景不放进 `perspectives`，自然用 `base`（3D）。**弃**：把 `fixed` 也换成图标——背在背上会变成一张纸片。**弃**：给背在背上改用别的场景——要动 `BackpackBackLayer` 与它的位置推导，风险与本 feature 的目的不成比例。

### D2. 平面图标的做法：从 3D 模型渲出来

**决定**：脚本读 OBJ 与皮肤贴图，按 F6 的视角渲染，输出 32×32。**理由**：外形、配色与 3D 模型一致（US2）；与现有垃圾桶等图标的"脚本生成、开发期工具"做法一致；可重复运行。**弃**：用 AI 工具生成像素画（垃圾桶的做法）——五档要保持与 3D 一致，AI 出图不可控。**弃**：用 Blender MCP 渲染——要求 Blender 在运行，不可重复。**弃**：减面网格代替图标——工作量大、仍有每帧提交成本，且需要美术确认（spec 已列为备选，不做）。

### D3. 渲染方法（纯 Python）

- 读 OBJ：`v`、`vt`、`f`（三角形或多边形，扇形三角化），纹理 v 取 1−v（F5）。
- 变换：先平移到以 (0.5, 0.5, 0.5) 为原点，再 `v' = Rx(30°)·Ry(225°)·v`（对应 `Quaternionf.rotationXYZ`：先绕 Y 再绕 X）；正交投影，屏幕 x 向右、y 向上，深度大者靠近观察者。
- 4× 超采样（128×128）、Z 缓冲、逐三角形重心坐标采样皮肤贴图、柔和方向光（环境 0.62 + 漫反射 0.38，光从左上前方）；再用 LANCZOS 缩到 32×32；透明背景。
- **五档共用同一个缩放比例**（取能让五档都装下的最小值），各自按剪影包围盒居中，留 1 像素透明边（图集接缝）。
- 渲出之后**用眼睛看**（读 PNG），与 2026-11-08 截图里快捷栏的外观对照，朝向不对就调旋转符号。

### D4. 模型文件重组

**决定**：入口 `<tier>_floating_backpack.json`：`loader: neoforge:separate_transforms`、`gui_light: front`（平面图标用正面光）、`base: {parent: 3D}`、`perspectives: {gui: {parent: 图标}, ground: {parent: 图标}}`。3D：原来的 JSON 原样搬到 `_3d`（内容不改）。图标：`parent: minecraft:item/generated`、`textures.layer0`。**未验证风险**：`base` 用 `parent` 引用带 `neoforge:obj` 的模型是否能烘焙——读 `SeparateTransformsModel.bake` 倾向可以；**用启动客户端看资源重载日志来先验，再由用户在游戏里确认**。退化方案：把 3D 模型的全部内容（loader、model、textures、display）直接内联进 `base`。

### D5. 度量与验证

- 图集：改动前 2048×1024（2026-11-08，027 的度量）；改动后再测一次。
- 资源重载错误：跑 `check_atlas_budget.sh`（会启动客户端），然后在 `run/logs/latest.log` 里找模型相关的 ERROR/WARN。
- 帧率：用户按 quickstart 做对照；我不在没有人的会话里估计帧率。

## 待验证

| # | 内容 |
|---|---|
| U1 | `base` 经 `parent` 引用 OBJ 模型能烘焙（日志无错，手持仍是 3D） |
| U2 | 帧率回升到什么程度（SC-001、SC-002） |
| U3 | 平面图标的观感、五档可区分、亮暗背景 |
| U4 | 手持、背在背上、展示框外观与改动前一致 |
| U5 | JEI、箱子里的显示；掉落物仍是 3D |
| U6 | ESC 菜单掉帧是否还在 |
