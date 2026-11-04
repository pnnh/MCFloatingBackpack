# Research: 008-panel-title-icon-and-jei-safe-zone

本阶段的两处 JEI 相关不确定性（见 spec.md Assumptions）没有停留在"查文档猜测"，而是直接解包了项目里已经缓存的 JEI 1.21.1 正式 jar（`~/.gradle/caches/.../mezz.jei/*/19.70.0.469/*.jar`）用 `javap` 读接口签名，并从 JEI 官方 GitHub 仓库（`mezz/JustEnoughItems`）拉取了对应的 Java 源码逐行确认实现，不是凭经验猜的。

## Decision 1: 标题栏图标 + 悬停提示

**Decision**: `FloatingPanelRenderer` 的标题栏改为：用 `ModItems.stackFor(BackpackTier)`（006/007 已有）取得当前背包等级的 `ItemStack`，通过 `graphics.renderItem(...)` 在标题栏左侧固定位置画一个小图标；新增一个命中区域判断（写法比照 `FloatingPanelState.hitsCloseButton`），命中时在 `render()` 末尾调用原版 `GuiGraphics.renderTooltip(font, Component, mouseX, mouseY)`（或等价重载）显示背包等级名称，不命中时不调用。原来固定的 `panel.mcfloatingbackpack.title` 翻译 key 和按等级取名的 `Component.translatable("item." + MOD_ID + "." + tier.id())` 逻辑整体从"标题文字"搬到"tooltip 内容"，逻辑复用，只是触发条件和绘制方式变了。

**Rationale**: 不需要新依赖、新资源；图标尺寸固定不随文字长度变化，天然解决溢出问题。

**Alternatives considered**: 缩小字体/省略号截断文字——治标不治本，背包名称本来就可能比最窄面板（3 列）还宽，省略号后基本认不出是哪个等级，排除。

## Decision 2: JEI 排除区域注册方式——用 `IGlobalGuiHandler`，不是按 Screen 类型注册

**Decision**: 新增一个只有 JEI 存在时才会被触碰的 `@JeiPlugin` 类（新包 `pnnh.mcfloatingbackpack.compat.jei`，与零编译期依赖的 `pnnh.mcfloatingbackpack.compat.ContainerScreenCompat` 分开，因为这是本项目第一次需要在源码里直接 `import mezz.jei.api.*`），在 `registerGuiHandlers` 里调用 `registration.addGlobalGuiHandler(...)`，实现 `IGlobalGuiHandler.getGuiExtraAreas()`：面板可见时返回一个覆盖 `FloatingPanelState` 当前 posX/posY/width/height 的 `Rect2i`，不可见时返回空集合。

**Rationale**（**推翻了 spec 阶段的"按 Screen 类型注册、需要枚举容器类型"的猜测**）：直接读了 JEI 官方源码 `IGuiHandlerRegistration.java`（[GitHub](https://github.com/mezz/JustEnoughItems/blob/main/Common/src/api/java/mezz/jei/api/registration/IGuiHandlerRegistration.java)）的 javadoc，`addGlobalGuiHandler(IGlobalGuiHandler)` 的官方说明原文就是："Add a handler to give JEI extra information about how to layout the ingredient list. **Used for guis that display next to GUIs and would normally intersect with JEI.**"——这正是我们的场景（一个可能和任意容器界面同屏出现的悬浮面板），`IGlobalGuiHandler.getGuiExtraAreas()` 本身不带 Screen 类型参数，不需要按容器类型单独注册，也不需要用 `addGenericGuiContainerHandler` 去猜测超类匹配规则。`GuiEventHandler.java`（JEI 内部，[GitHub](https://github.com/mezz/JustEnoughItems/blob/main/Gui/src/main/java/mezz/jei/gui/events/GuiEventHandler.java)）证实这些排除区域会被收集进 `guiExclusionAreas`，统一喂给 `ingredientListOverlay`/`bookmarkOverlay` 的布局更新，和 `addGuiContainerHandler` 注册的区域走的是同一条汇总路径——换句话说两种注册方式效果等价，`addGlobalGuiHandler` 只是不需要绑定具体 Screen 类型，更适合我们。

**Alternatives considered**:
- `addGuiContainerHandler(Class<? extends T>, ...)`：需要为每种可能叠加的容器 Screen 类型（原版物品栏、箱子、精妙/旅行者背包……）单独注册一次，枚举不全就会漏掉某些界面，排除。
- `addGenericGuiContainerHandler`：javadoc 原文说明它只是"same as addGuiContainerHandler but for handlers that use Java Generics"，解决的是泛型类型推断问题，不是"匹配任意子类"，依然需要绑定一个具体 `guiClass` 参数，排除。

## Decision 3: 排除区域对"物品格子布局"和"悬停提示触发"是否同时生效

**Decision**: 按官方机制实现（Decision 2），预期能同时解决 US2 场景 1（面板盖住物品格子导致提示误触发）；具体效果仍需在 `./gradlew runClientCompat` 真实会话里确认一次，这是宪法"Verify In A Client Session"原则本来就要求的步骤，不是因为这步设计存疑才加的额外验证。

**Rationale**: 反编译确认 JEI 自带一个 `GuiExclusionAreaShadow` 类（`gui/mezz/jei/gui/overlay/ingredients/`），且 `GuiEventHandler.updateOverlayProperties(...)` 把排除区域直接喂进 `ingredientListOverlay` 的属性更新——排除区域是物品网格布局阶段就参与计算的输入，不是事后再补一层"隐藏"。网格阶段如果压根没把任何物品放进这块区域，后续基于网格坐标做的悬停检测自然也不会在这块区域命中任何物品，不存在"网格已经避让、但悬停检测走另一套独立坐标"的分裂实现（JEI 源码里没有看到第二套独立的、不读取 `guiExclusionAreas` 的悬停检测路径）。

**Alternatives considered**: 无需考虑此前设想的"用 mixin 强制压掉 tooltip"方案——那是在"排除区域机制对 tooltip 不起作用"的前提下才需要的后备手段；现在从源码读到的结构看，这个前提大概率不成立，不必在本阶段引入 mixin 这种更重、更脆弱的技术。

## Decision 4: "提示框撕裂"（FR-010）的根因与修复方向

**Decision**: 这是和 Decision 2/3 **不同性质**的问题，和"排除区域"机制无关——它发生在浮动面板根本没有盖住任何 JEI 物品的情况下，所以不是"物品被挡住又被悬停到"的问题，而是**两个独立 UI 系统在同一帧内的绘制/缓冲刷新顺序问题**。反编译确认：JEI 的物品格子渲染和它自己的 tooltip 渲染是在同一次调用链里前后发生的（`GuiEventHandler.drawForScreenForeground` → `drawOverlayForegrounds`（画格子）→ `drawPostForeground` → `ingredientListOverlay.drawTooltips(...)`（画提示框）），这两步之间没有被我们的渲染打断的理由——除非 Minecraft 的文字渲染走的是缓冲/批量绘制（`MultiBufferSource`），在 JEI 调用完 `drawTooltips` 之后、真正刷新到屏幕之前，还停留在缓冲区里；而 `FloatingPanelRenderer.render()` 末尾会显式调用一次 `graphics.flush()`——如果这次 flush 把 JEI 尚未刷新的提示框文字缓冲一并刷出来，刷新时机落在"我们的背景色块已经画完、JEI 的提示框背景色块已经画完，但 JEI 的提示框文字还没画"这个时间点之后，就会出现背景被挡、文字穿透的现象。

**【2026-11-05 修订：真正的根因是深度，不是刷新顺序】** 用户在游戏里再次遇到"提示框背景在面板下、文字在面板上"（悬停被面板盖住的玩家物品栏格子、以及靠近 JEI 列表时）。读 1.21.1 源码得出：提示框背景在 z=400 用 `drawManaged` 立即画出，文字带阴影绘制，主体层比阴影层近 0.03（`Font.SHADOW_OFFSET`），即 z≈400.03；GUI 用正交投影，z 越大越近，深度测试是 LEQUAL。面板原来的基准 z 恰好也是 400：与背景等深（通过，盖住背景），比文字远（不通过，文字露在面板上）。所以下面"开头 flush"的假设并不能解决问题（它只决定文字在面板之前画出，不决定谁在深度上更近）。修复：面板基准 z 提到 800（`FloatingPanelRenderer.OVERLAY_Z`），高于提示框背景、文字和其中物品图。**这是读源码得出的结论，截图复现的场景需要在游戏里确认。**

**修复方向（本阶段先验证最小改动，不引入新依赖）**：在 `FloatingPanelRenderer.render()` 的**最开头**（绘制任何内容之前）也调用一次 `graphics.flush()`，把这一帧里此前任何尚未刷新的缓冲内容（包括 JEI 可能已经调用但未刷新的提示框）先行刷新到屏幕，确保我们的面板内容是在这些内容"已经定型"之后才画上去的，从而让面板稳定地处于提示框之上（而不是夹在提示框背景和文字中间）。这是最小、侵入性最低的改动——不需要知道 JEI 具体用了哪种 `RenderType`，只是确保"画我们自己的东西之前，先把别人已经画了一半的东西冲刷掉"。

**Rationale**: 没有找到独立的证据证明"物品格子 vs 提示框"之间真的存在跨 Z 层级的深度测试冲突（我们的渲染已经显式 `RenderSystem.disableDepthTest()`，意味着最终呈现顺序完全取决于绘制调用的发生顺序，不取决于 Z 坐标数值），所以最可能的解释就是"缓冲内容没来得及刷新就被我们的 flush 带出来"这种时序问题，而不是坐标/Z 值设置错误。

**Alternatives considered**: 调整 NeoForge 事件订阅优先级，让我们的 `ScreenEvent.Render.Post` 处理器比 JEI 的渲染钩子"更早"或"更晚"触发——JEI 的绘制调用（`drawForScreenForeground`/`drawForScreenBackground`）从方法命名和 javadoc（"before the screen contents are drawn"、"after the screen contents and before deferred tooltips are extracted"）来看，更像是直接挂在 vanilla `Screen`/`AbstractContainerScreen` 渲染流程的特定阶段（很可能通过 mixin），而不是走和我们完全对等的 NeoForge `ScreenEvent.Render` 总线事件；贸然调整我们自己的事件优先级大概率调不到真正起作用的那个时间点上，而且会是"猜着调"而不是"读懂了再改"，优先级低于直接处理缓冲刷新顺序这个有代码依据支撑的方案；如果开头加 `flush()` 验证后仍有残留问题，再回头考虑这个方向。

## Decision 5: 依赖与软兼容方式

**Decision**: 新增一个**无条件**生效的 `compileOnly "mezz.jei:jei-1.21.1-common-api:19.70.0.469"`（与现有 `-PcompatRuntime` 开关的 `localRuntime` JEI 开发期依赖是两回事，不受那个开关门控——这个 `compileOnly` 只是让新插件类能在编译期找到 `mezz.jei.api.*` 类型，不会被打进最终发布的 jar，也不会在玩家环境引入任何运行时依赖）。新插件类放在新包 `pnnh.mcfloatingbackpack.compat.jei`（和现有零编译依赖的 `pnnh.mcfloatingbackpack.compat.ContainerScreenCompat` 区分开，因为这是本项目第一次真正在源码里 `import mezz.jei.api.*`）。类上加 `@JeiPlugin` 注解并实现 `IModPlugin`，`getPluginUid()` 返回一个固定的 `ResourceLocation`；JEI 本身不存在时，这个类不会被扫描、不会被实例化，不产生任何类加载错误——这是 JEI 官方文档约定的插件发现机制（"This annotation lets JEI detect mod plugins"，发现逻辑是 JEI 自己的代码，JEI 不在场时这段代码根本不会运行）。

**Rationale**: 延续 004 建立的"可选集成不是硬依赖"约定，同时符合宪法第四条；用 `compileOnly` 而不是复用现有的 `-PcompatRuntime` 开关，是因为这个依赖是编译期需要、运行期不需要打包的，和现有开关"运行期才加载的本地 jar"的用途完全不同，混在一起反而让 `compatRequested` 这个开关的语义变得不清楚。

**Alternatives considered**: 用现有 `ContainerScreenCompat` 那种纯字符串/反射的零编译依赖方式去调用 JEI API——`IGlobalGuiHandler`/`IModPlugin` 是接口而不是简单的静态方法，用反射实现一整套接口代理的复杂度和脆弱性远高于一个受 `compileOnly` 保护、编译期类型安全的插件类，排除。

## Known limitations

- Decision 3（排除区域是否完全消除 tooltip 误触发）和 Decision 4（flush 时机修复是否真的解决撕裂）都需要在 `./gradlew runClientCompat` 真实会话里对照 spec.md 的验收场景逐条确认；源码阅读能确定"机制上说得通"，不能替代实际运行时的画面验证。
- 本阶段只处理 JEI；`IGlobalGuiHandler`/`@JeiPlugin` 是 JEI 专有 API，不适用于 EMI 等其它物品浏览模组（spec.md Assumptions 已经把 EMI 排除在本阶段范围外）。
