# Research: 009-sculpted-mesh-backpack-models

本文件是**补录**：下面每条决定都已经落在代码里，这里记录当时为什么这样做，以及证据来自哪里。凡"依据"一栏写的是读 NeoForge 21.1.251 源码（`build/moddev/artifacts/neoforge-21.1.251-sources.jar`）或脚本实测，不是凭经验猜的；凡写"推断"的是没有直接验证的判断。

## Decision 1: 用 NeoForge 自带的 `neoforge:obj` 加载器，而不是原版立方体 `elements` 模型

**Decision**: 每个等级一个物品模型 JSON（`models/item/<tier>_floating_backpack.json`），写 `"loader": "neoforge:obj"`，`"model"` 指向同目录 `<tier>_backpack.obj`，OBJ 里的 `mtllib` 指向同名 `.mtl`。

**Rationale**: 源 GLB 是真实雕刻网格（皮革 15,535 顶点 / 16,178 面，带倒角），原版物品模型只支持轴对齐长方体，无法表达。`ObjLoader` 是 NeoForge 本体的一部分（`ClientNeoForgeMod` 里以 `neoforge:obj` 注册），不是第三方模组，所以不违反宪法 IV。

**依据**: `ObjLoader.java`（读取 `model` / `automatic_culling` / `shade_quads` / `flip_v` / `emissive_ambient` / `mtl_override`）。

**Alternatives considered**: 把雕刻网格手工近似成立方体——会丢掉几乎全部细节，等于放弃这批素材，排除。

## Decision 2: 贴图通过 `.mtl` 的 `map_Kd #base` 引用模型 JSON 的 `textures.base`

**Decision**: `.mtl` 里写 `map_Kd #base`，模型 JSON 的 `textures` 里提供 `base`（和 `particle`）指向 `mcfloatingbackpack:item/<tier>_floating_backpack`。

**Rationale**: `ObjModel` 对 `diffuseColorMap` 调 `UnbakedGeometryHelper.resolveDirtyMaterial`，因此 `#变量` 会在模型 JSON 的 `textures` 里解析，和原版 `"texture": "#xxx"` 同一套机制，不需要为 OBJ 另发明贴图系统。

## Decision 3: OBJ 坐标用 0..1 范围（不是以原点居中）

**Decision**: 导出时用 Blender `Forward: -Z, Up: Y`（得到 OBJ=(x, z, −y)），再把所有顶点整体 +0.5（x、y），使模型落在 0..1 范围。

**Rationale**: `ItemRenderer.render()` 对任何模型都无条件执行 `poseStack.translate(-0.5, -0.5, -0.5)`，所以 OBJ 也必须按"0 到 1"来写，自动居中才会正确。OBJ 顶点是直接当方块空间坐标用的（1 单位 = 1 方块），不像 `elements` 是 0-16 网格。

## Decision 4: 必须 `"flip_v": true`

**Decision**: 每个模型 JSON 带 `"flip_v": true`。

**Rationale**: `ObjModel` 取样是 `texture.getV(flipV ? 1 - texCoord.y : texCoord.y)`，`flip_v` 默认 `false`。标准 OBJ（含 Blender 导出）V=0 在图片底部，Minecraft 的 V=0 在顶部。不翻转会让每个面都在贴图里上下颠倒取色，表现为一片片黄色污渍（皮革第一次进游戏时的实际现象，已确认是该原因）。

**教训**: 之前把这个现象先归因于贴图缩小的模糊，只对了一小部分；真正原因要靠读加载器源码才能发现。

## Decision 5: 不做自动减面，使用完整原始网格

**Decision**: 导出 OBJ 前不做任何减面，只做三角化。

**Rationale**: Minecraft 渲染物品时做背面剔除，而 Blender 预览不做，所以用 Blender 预览检查"减面没损失"会漏掉缺陷。按顶点位置统计每条边被几个面共用，"只被 1 个面使用"的是开放边（裂缝）：皮革原始网格只有 16 条；Dissolve 20° 减面后变成 **1,506 条**；Dissolve 5° 699 条；Collapse（按 UV 边界保护）0.6 比例 4,648 条、0.4 比例 6,022 条。这个 AI 网格由大量互相穿插的部件叠成，边被 3~6 个面共用的非流形边有 900 多条，任何自动减面都会撕开洞。

**代价**: 面数保持在 1.6 万 ~ 2.0 万三角面，没有性能预算（已知限制）。

**Alternatives considered**: 手工重拓扑——工作量大，未做，留作后续选项。

## Decision 6: 基础色贴图 1024×1024，按平均亮度做 gamma 校正

**Decision**: 贴图统一缩到 1024×1024（Lanczos），再按各档情况做 gamma 提亮：皮革 0.62、铁 0.89、下界合金 0.91、金与钻石不调。

**Rationale**:
- 分辨率：对照 64/128/256/512/1024（缩小后用最近邻放大到同尺寸）。低于 256 时方格纹理直接糊成噪点而不是变成清晰像素风；512 会把原本清晰的方格纹理抹糊；1024 基本保持 2048 原图的清晰度。金色五金件的边缘在 2048 原图里就是软边（3D 烘焙自带抗锯齿），任何分辨率都改不掉。
- 亮度：按网格实际用到的 UV 区域面积加权，皮革贴图平均亮度 32.7，参考效果图背包部分约 61；铁、金、钻石、下界合金与各自效果图的比较见 `design/demo/README.md`。选 gamma 而不是线性放大，是因为线性放大会把金色高光截断成纯色。
- 因为关闭了全亮（Decision 8），实际游戏光照在白天约再乘 0.8~1.0，所以取了略偏亮的目标值。

**推断/风险**: 这些亮度比较是用效果图中间区域或前景像素粗略对比的，误差较大；**没有在游戏里逐档验证**。

**已知副作用**: 5 张 1024² 贴图被拼进共用方块图集，游戏日志显示图集从 1024×1024 变成 4096×4096（见 spec 已知限制 1）。

## Decision 7: 随附 LabPBR 法线贴图 `_n`，不做高光贴图 `_s`

**Decision**: 每个等级在基础色贴图旁放 `<tier>_floating_backpack_n.png`：R 保持，**G 翻转**（glTF/Blender 是 OpenGL 的 Y 向上，LabPBR 要 DirectX 的 Y 向下），B 填 255（AO，源数据本来为空，255 表示无遮蔽），A 填 255（高度/视差，无数据）。不做 `_s`。

**Rationale**: 格式依据 ShaderLabs Wiki 的 LabPBR Materials 页。没装光影包时这张图不被使用，装了的玩家（Iris/Oculus/NeOculus）才会按同目录 `_n` 后缀自动识别，不需要任何代码。`_s` 不做的原因：用 Blender 读 GLB 自带的粗糙度/金属度通道实测，数值几乎没有区分度（皮革的粗糙度全在 250~255、金属度全在 80~98，金色扣具与皮革主体分不开），强行套用只会得到全图一个效果或靠臆测遮罩，做错比不做更难看。

**推断**: 铁、金等其它三个 GLB 的 ORM 数据没有逐一重新核对；金的 Blender 渲染看起来有金属质感，可能有真数据，留待后续评估。

## Decision 8: 关闭 `emissive_ambient`

**Decision**: 每个模型 JSON 带 `"emissive_ambient": false`。

**Rationale**: `.mtl` 里 `Ka 1 1 1` 会让 `ObjModel` 在 `emissiveAmbient`（默认 `true`）下执行 `fakeLight = (Ka.r+Ka.g+Ka.b)*15/3` → 光照 15，并且 `setShade(fakeLight == 0 && shadeQuads)` 为假，结果模型始终全亮、不受昼夜/洞穴/阴影影响（夜里像自己发光）。在 JSON 里关掉比改每个 `.mtl` 更集中。

## Decision 9: 背部渲染直接渲染物品自己的烘焙模型

**Decision**: `BackpackBackLayer` 在 `getParentModel().body.translateAndRotate` + 绕 X 轴 180° 之后，平移 `(0, -0.42, -0.316)`、缩放 `0.68`，调用 `ItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, ...)`。不再手搭立方体，也不读取任何贴图路径。

**Rationale**: 之前手搭 4 个长方体，形状永远不可能等于真实模型；改为渲染烘焙模型后，背部、手持、图标自然是同一套外形，以后换模型也无需改这段代码。图层注册在所有玩家皮肤模型上（`BackpackRenderEvents.onAddLayers`），所以他人的背包走同一路径。

**位置推导（依据 `ItemTransform.apply` 与 `ItemRenderer.render` 读源码）**:
- 净缩放 `S = FIXED 显示缩放(1.1) × 本层缩放(0.68) = 0.748`。
- 绕 X 轴 180° 之后，Z 越负离身体越远（映射到身体骨骼的 +Z，即背后方向）。**第一次推导把方向搞反了**：曾把 z 从 -0.32 改成 -0.40 想"贴近"，实际拉开约 2 像素的缝。
- 导出时各档模型的**背面统一落在 OBJ z=0.238**，其半深为 0.262，所以近身面在身体坐标里约 `-(z + 0.262·S)`；躯干半深 0.125，取 0.12 使近身面基本齐平，得 `z = -(0.12 + 0.262·S) = -0.316`。缩放变化时必须重算 z。
- Y 从 -0.45 微调到 -0.42，避免放大后掉到腰以下。

## Decision 10: 各档统一"背面落点"，不统一尺寸

**Decision**: 导出时对每个模型沿 OBJ 的 z 方向平移，使其最靠后的面（背面）都落在皮革的 z=0.238；x/y 以包围盒中心对齐 0.5。不把铁、金、钻石、下界合金缩放成和皮革一样大。

**Rationale**: 背部图层的位移常量只对一个背面位置推导，统一背面落点后对五档都成立。档位越高包越大（宽度一致，皮革高 0.81/厚 0.54，铁 0.88/0.63，金 0.97/0.72）符合"高等级更大"的直觉。

**副作用**: 更厚的模型整体中心稍偏离方块中心（铁约 0.04、金约 0.09 方块），对手持/图标影响很小。钻石翻盖向后包，背面最外缘是翻盖，主体背面因此多约 0.05 方块间隔。

## Decision 11: `display` 沿用"重建模之前的原始值"，不沿用立方体模型改过的值

**Decision**: 五个模型的 `display`（`gui` / `thirdperson_*` / `firstperson_*` / `fixed` / `ground`）使用 006 之前最早的 `floating_backpack.json` 那套数值（`gui` 旋转 `[30,225,0]`，`fixed` `[0,180,0]` 等）。

**Rationale**: `ItemTransform.apply` 对旋转是 `Quaternionf().rotationXYZ(...)`、`translation` 乘 0.0625，这是和几何形状无关的固定变换。立方体模型的"正面"（扣具一侧）朝 north，当时需要把 Y 旋转加 180°；而雕刻网格的正面朝 south，正好与原始值的朝向一致，所以应该用未改过的原始值。已被游戏截图确认：图标、手持、背上显示的都是正面。

**推断/风险**: 位移与缩放同样沿用，**只有皮革和下界合金被游戏截图确认过**，铁/金/钻石的手持与图标没有逐档验证。

## Decision 12: 钻石与下界合金需要先旋转 90°

**Decision**: 这两个 GLB 的包宽 1.0 在 Y 轴（X 轴只有 0.65~0.70），正面朝 +X。导出前对网格数据做世界坐标下的 −90° 绕 Z 旋转（+X → −Y），使正面朝向与前三档一致。

**Rationale**: 用 −Y 方向相机渲染看到的是侧面口袋；用射线/高度剖面确认钻石的口袋与翻盖扣具在 +X 一侧。

**踩的坑**: glTF 导入的物体默认是四元数旋转，直接改 `rotation_euler` 不生效（包围盒没变）；改为在世界坐标下直接对网格数据做矩阵变换，并用包围盒和渲染图确认。

**不确定**: 下界合金几乎左右镜像对称（各高度 X 范围几乎一样），无法判断哪面是正面，按钻石的做法取 +X。

## Decision 13: 下界合金补底板

**Decision**: 在下口袋开口下加一块向下的盖板（比开口四边大约 0.03，埋进边框；高度比边框底面高 0.03），UV 取贴图里口袋正面同款的金色斑点区域。Blender 脚本保存为 `design/demo/tools/netherite_bottom_cap.py`。

**Rationale**: 从下往上发射射线并按首个命中面的高度画图：下界合金底部是一圈宽约 0.058 的贴地边框，框内最先打到离底部 0.22~0.26 的"天花板"，即下口袋是没有底板的盒子；其它四档底部都是平的。该网格的开放边散落在整个高度上，不是干净的洞边，不适合自动填洞。

**验证**: 对最终 `.obj` 在开口范围发射 1,681 条射线，全部命中盖板，没有命中旧空腔或背面。盖板法线在 Blender 里没算过恒为 0，所以直接读 `.obj` 三角形绕序确认朝下（法线 y=-0.27）。

## Decision 14: Git LFS 只管设计目录的 `.obj`

**Decision**: `.gitattributes` 加 `design/**/*.obj filter=lfs diff=lfs merge=lfs -text`；`src/main/resources` 里的 `.obj` 保持普通文件。

**Rationale**: `.obj` 是文本格式但近 2MB、几万行顶点，没法人工 review。随模组打包的资源如果走 LFS，没装 git-lfs 的构建环境会把指针文本打进 jar，模型就坏了。设计目录用 LFS 让历史不再膨胀。已有的旧版本仍是普通对象，没有改写历史。

**代价**: 每次重新导出 `src/main/resources` 的五个 `.obj` 仍会让历史增长约 10MB。`.glb`（真二进制）当前也没走 LFS。
