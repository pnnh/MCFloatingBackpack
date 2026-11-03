# 皮革背包 OBJ 模型实验（已应用到正式代码）

你放进这个目录的文件：

| 文件 | 内容 |
|---|---|
| `皮革背包.glb` | AI 生成的皮革背包完整模型（真实雕刻网格，带 PBR 贴图），本轮实验的原始素材 |
| `皮革背包.jpg` / `铁背包.jpg` / `金背包.jpg` / `钻石背包.jpg` / `下界合金背包.jpg` | 五档的效果参考图，目前只有皮革档生成了对应的 `.glb` 完整模型，其余四档暂时只是风格/配色参考，还没建模 |
| `obj_export/` | 我处理后、实际应用到游戏里的产物（见下）|

## 分析结论：能不能直接用

**不能直接套用 Minecraft 原版物品模型 JSON**（就是 `leather_floating_backpack.json` 之前用的那种
`"elements"` 格式）——那个格式只支持轴对齐长方体。用 Blender MCP 读了 `皮革背包.glb` 的真实结构：

- 15,535 顶点 / 16,178 面，是一个真正意义上的雕刻网格（看得出每条边都加了倒角），不是立方体拼接。
- 材质是标准 glTF PBR 三件套：Base Color（漫反射）+ Normal（法线）+ ORM（金属度/粗糙度打包）各一张
  2048×2048。Minecraft 物品渲染是不带实时 PBR 的简单纹理贴图，Normal/ORM 这两张贴图游戏里完全用不上，
  只有 Base Color 这张能实际派上用场。
- 没有背带（肩带）几何体，只有主体+翻盖+前袋+两个侧袋+铆钉装饰——你确认了这是有意的设计方向（偏"工具
  腰包/斜挎包"风格），不用补背带。

## 用的方案：NeoForge 内置的 OBJ 模型加载器

这是 NeoForge 自带的能力（不是第三方 mod，`net.neoforged.neoforge.client.model.obj.ObjLoader`，loader id
是 `neoforge:obj`），读了它的源码确认了几个关键点：

1. `.mtl` 材质文件里的 `map_Kd` 可以写成 `#贴图变量名` 的写法（和我们现有模型里 `"texture": "#backpack"`
   是同一套机制），实际贴图由物品模型 JSON 自己的 `"textures"` 字典决定——所以可以完全照搬现有的贴图资源
   管理方式，不需要给 OBJ 模型单独发明一套贴图系统。
2. OBJ 模型的顶点坐标是**直接当成方块空间坐标使用**的（1 个 OBJ 单位 = 1 个 Minecraft 方块），跟原来
   `"elements"` 格式的 0-16 网格（0-16 对应 0-1 方块）不是一回事。
3. `ItemRenderer.render()`（读的是 `neoforge-21.1.251-sources.jar` 里的源码）不管模型是 `"elements"`
   格式还是 OBJ 格式，都会无条件执行 `poseStack.translate(-0.5, -0.5, -0.5)`——这意味着 OBJ 模型的坐标
   也得按"0 到 1，不是 -0.5 到 0.5"的约定来写，和原来的 `"elements"` 格式一致，这样这次自动居中才能把模型
   摆到正确位置。
4. `display`（`gui`/`thirdperson_*`/`firstperson_*`/`fixed` 等视角变换）这块完全通用，和模型是哪种格式
   无关——`ItemTransform.apply()` 对旋转数值的处理是 `new Quaternionf().rotationXYZ(...)`，这是一个纯粹
   作用在"摄像机 vs 物体"关系上的固定变换，跟挂在物体上的具体几何形状完全没关系。这点很关键：第二轮我们
   把立方体模型的 `gui`/`thirdperson_*`/`firstperson_*`/`fixed` 旋转都加了 180°，是因为立方体模型的"正
   面"（扣具那一侧）朝着 north；而这次读取这个 GLB 导出的 OBJ 坐标发现它的"正面"（扣具/前袋那一侧）朝
   着 **south**（和立方体模型方向相反）。所以这次没有套用立方体模型改过的旋转值，而是直接照搬了**最原
   始、第一次重新建模之前的** `floating_backpack.json` 那套旋转数值——因为那套原始数值本来就是"south 朝
   镜头"的效果，正好和这个新网格的正面朝向对上。

## `obj_export/` 里的产物 & 处理流程

1. **减面**：原始 16,178 三角面对一个要频繁渲染的物品（图标、手持、GUI 悬停、其他玩家背上同时看到好几个）
   来说偏重，用 Blender 的 Decimate（Dissolve 模式，20° 共面合并阈值）把面数砍到 7,277（降了 55%），渲染
   图对比过，这个幅度下视觉细节几乎没有损失（见下面"减面前后对比"）。
2. **只保留 Base Color 贴图**，从 2048×2048 缩小到 512×512（像素风格，缩小不影响清晰度，游戏里也用不到
   原始分辨率），丢弃了 Normal/ORM 两张贴图。
3. **坐标偏移 +0.5**：导出时用 Blender 默认的 `Forward: -Z, Up: Y` 轴向转换成 Minecraft 的 Y-up 坐标系，
   导出后整体顶点坐标又加了 (0.5, 0.5, 0.5)，把原来以原点为中心的 -0.5~0.5 范围挪到 0~1（配合上面第 3 点
   的自动居中逻辑）。
4. **`.mtl` 改成用贴图变量**：`map_Kd` 从 Blender 导出的字面文件名改成了 `map_Kd #base`，对应物品模型 JSON
   `"textures"` 里的 `"base"` 这一项。

产物三件：`leather_backpack.obj`（7,277 三角面）、`leather_backpack.mtl`、`leather_backpack_diffuse.png`
（512×512）。

## 已应用到正式代码

- `src/main/resources/assets/mcfloatingbackpack/models/item/leather_floating_backpack.json` 整个换成了
  `"loader": "neoforge:obj"` 格式，不再是 `"elements"` 长方体格式；`display` 沿用了上面说的"原始未修改"
  那套旋转数值，位移/缩放数值也暂时沿用原始值（没有重新摸索，详见下面"还没验证的部分"）。
- `leather_backpack.obj` / `leather_backpack.mtl` 放进了同一个 `models/item/` 目录。
- `textures/item/leather_floating_backpack.png` 换成了上面说的 512×512 贴图。
- `BackpackBackLayer.java`（背到身上的渲染，上一轮已经改成直接渲染物品真实烘焙模型）不需要任何结构性改
  动——它本来就是不关心模型具体格式的通用代码。只是因为这个模型没有背带，没有"东西固定住"的视觉支撑，
  按你的要求把贴近身体的位移从 `-0.32` 调近到了 `-0.40`，让背包尽量贴紧人物、减少空隙。
- 铁/金/钻石/下界合金四档**没有改动**，仍然是立方体 `"elements"` 格式——这四档目前只有参考图，还没有对
  应的 `.glb` 完整模型。

## 已验证 / 还没验证的部分

**已验证**：
- `./gradlew compileJava test` 通过。
- `./gradlew runClient` 完整日志扫了一遍，没有任何 Exception/Error（包括 OBJ 解析、贴图变量 `#base` 解
  析、贴图图集拼接）——说明模型和贴图本身是有效的，能被游戏正确加载。
- 用 Blender 重新导入了最终的 `leather_backpack.obj` + 实际游戏里用的 `leather_floating_backpack.png`，
  渲染确认减面和贴图替换没有破坏模型外观（见 `final_front_check` 这类渲染，和原始 GLB 相比看不出肉眼差
  异）。

**还没验证**（没法自己截图确认，需要你在游戏里实测）：
- `display` 里的位移/缩放数值是直接沿用"第一次重新建模之前"的原始值，没有针对这个新网格的实际尺寸重新
  调过——这个网格比原来的立方体模型宽（原始 bbox 宽度约 1.0 方块 vs 立方体模型约 0.73 方块），图标/手持
  大小可能偏大，需要你看一眼再决定要不要调小。
- `BackpackBackLayer` 里贴近身体的 `-0.40` 调整是估计值，没有实测"贴紧、无空隙"的效果是否达到预期。
- GUI 图标朝向的"south=正面"判断是从读源码 + 已验证过的立方体模型反向推算出来的，逻辑链条比较绕，建议
  重点确认一下图标和手持视角显示的确实是正面（扣具+前袋那一侧），不是背面。
