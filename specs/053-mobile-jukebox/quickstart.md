# Quickstart: 移动唱片机的验证

规则见 [contracts/rules.md](./contracts/rules.md)，资源见 [contracts/resources.md](./contracts/resources.md)。

## A. 单元与资源测试

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.jukebox.*' --tests 'pnnh.mcfloatingbackpack.client.panel.jukebox.*' \
  --tests '*ToolSingleRulesTest' --tests '*MobileJukeboxResourcesTest' --tests '*JukeboxWiringTest'
./gradlew compileJava test
```

覆盖点：`PlaylistReaderTest`（连续行、被别的类别隔开的行、隐藏行不在输入里、空格、非唱片、多行混合、重复歌曲）、`JukeboxPlayerTest`（顺序循环、单曲循环、随机固定种子 1000 轮"每轮每首恰好一次"与"轮间不相邻重复"、上一曲历史、列表中途变化、列表变空、倒带、停止后再播放）、`JukeboxPanelLayoutTest`（几何、按钮与开关的命中、页外不命中）、`ToolSingleRulesTest`（空区、重复、同格替换、别的区不计、旧背包保留）、`MobileJukeboxResourcesTest`、`JukeboxWiringTest`。既有测试只改写死 3 的修订断言。

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器（先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；`-PnoJei`）。**容器里 OpenAL 打不开，声音是空操作，但计时与状态照常走**，所以验证"页、按钮、曲目序号与曲名、自动接歌、单曲循环、随机的序列"；听觉由用户确认。造背包用数据包函数（聊天命令最长 256 字符），右区放移动唱片机，唱片行与别的行交错：

```text
item replace entity @s hotbar.8 with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],tools:{right:[{id:"mcfloatingbackpack:mobile_jukebox",count:1}]},extension_rows:{visible:[{category:"disc",cells:[{id:"minecraft:music_disc_13",count:1},{id:"minecraft:music_disc_cat",count:1},{},{},{},{},{},{},{}]},{category:"food",cells:[{},{},{},{},{},{},{},{},{}]},{category:"disc",cells:[{id:"minecraft:music_disc_blocks",count:1},{},{},{},{},{},{},{},{}]}],hidden:[]}}]
```

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 页 | 面板里左键点右区的移动唱片机 | 页展开，曲名行空、显示"第 0 / 3 首"或提示；再点收起；右键拿起 |
| B2 读全 | 点播放 | 第 1 首（13）开始，页显示"第 1 / 3 首"和曲名；下一曲 → 第 2 首（cat）→ 第 3 首（blocks，来自被食物行隔开的第二个唱片行）→ 回到第 1 首 |
| B3 倒带与停止 | 播放中点倒带；点停止；再点播放 | 倒带后计时从 0（读日志）；停止后状态为停止；再播放从当前曲目开头 |
| B4 自动接歌 | 用 `/gamerule`、`/time` 不影响；等一首放完（13 约 178 秒，可换更短的唱片或用 `/data` 造短曲） | 放完自动接下一首；最后一首接第一首 |
| B5 单曲循环与随机 | 开单曲循环等放完；关，开随机，连点下一曲 | 单曲循环重放同一首；随机每轮三首各一次，上一曲回到上一首放过的 |
| B6 列表变化 | 播放中把当前唱片拿出行；拿走一整行的扩展件（行隐藏） | 当前曲放完，之后的列表不含被拿走的；隐藏行的唱片不在列表里；全拿走则停止 |
| B7 互斥与限制 | 展开垃圾桶页再点唱片机；右区再放第二个唱片机；放在左区、下区 | 两页互斥；第二个被拒并有提示；左区与下区点它不展开页 |
| B8 停止条件 | 把唱片机拿出右区；退出世界再进入 | 播放停止、页收起；重进是停止状态、开关为关 |
| B9 面板关着 | 播放中关闭面板（B） | 音乐（计时）继续；放完自动接下一首（读背包栈，不读面板） |
| B10 数据与图集 | 保存读取；`check_atlas_budget.sh` 前后各一次 | 背包内容零变化、修订 4、协议 19；图集 ≤ 2048 |

**不能自动判断的**：声音是否在耳边、音量跟随滑条、衔接是否自然、背景音乐是否被正确停掉——记为需用户确认。
