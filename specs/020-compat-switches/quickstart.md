# Quickstart: 兼容开关与客户端配置验收

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/compat-config.md](./contracts/compat-config.md)

## 准备

- 自动检查：`./gradlew compileJava test`（`CompatPolicyTest`、`ServerCompatGateTest`、`DependencyRulesTest` 必须通过，此前全部测试仍通过）。
- 客户端：`./gradlew runClientCompat`（精妙背包、旅行者背包、JEI）；跑游戏前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，**不要杀用户的游戏**。
- 准备一只背包、一个箱子、一个工作台；穿上旅行者背包、带一只精妙背包。

> 记录规则（宪法 V）：每项写明"实际做了什么、看到什么"；没做的项保持"未验证"。

## 一、默认行为与首次挂载日志（US1 的前提、FR-018）

1. 配置里没有条目：面板挂在精妙背包界面、旅行者背包界面、箱子、工作台上，Ctrl 转移等一切照旧（回归 011～019）。
2. 第一次把面板挂在精妙背包界面上：`run/logs/latest.log` 里出现 1 行包含菜单所属模组 id 的提示（记下它**实际写的 id**，核对是不是 `sophisticatedbackpacks`；精妙背包的"存储"类界面如果写的是 `sophisticatedcore`，也记下）；反复开关同一界面，不再重复（SC-009）。旅行者背包同样看一次。
3. 打开原版箱子：日志**没有**这类提示（原版不提示）。

## 二、OFF（US1）

4. 打开游戏"模组"列表 → 浮动背包 → 配置，添加 `sophisticatedbackpacks=OFF`（或直接改配置文件），保存。**不重启**。
5. 面板可见时打开精妙背包界面：面板**隐藏**、不被绘制；在它的槽位上 Ctrl+点击：不触发面板转移；关闭界面：面板自动回来，位置/尺寸/选区不变。
6. 在精妙背包界面上按面板热键：面板不打开，**聊天区**出现一行 `[浮动背包]` 提示（带模组 id），按 T 打开聊天仍能看到；在原版箱子界面上按热键：照常打开。
7. 同一设置下：原版箱子、熔炉、工作台、物品栏，以及旅行者背包界面（没列出）：面板挂载与 Ctrl 转移与第 1 项一致。
8. 面板正挂在精妙背包界面上时（先 FULL），**打开配置界面改成 OFF**（或开着游戏改文件）：面板立即隐藏；若当时正在做 Ctrl 拖动/拖动均分，手势被取消，物品总数守恒。

## 二b、GENERIC 的含义（说明，不是新验证项）

GENERIC 只关闭我们为该模组写的专有代码（目前只有 jei 有）；对精妙背包、旅行者背包它与 FULL 表现相同，面板能否显示、Ctrl+点击能否转移取决于它们自己的实现。可顺手确认：把 `sophisticatedbackpacks` 设为 `GENERIC` 后，面板在它的界面上的表现与 `FULL` 时一致（SC-010）。

## 三、三档对 JEI（US2）

9. 默认（FULL）：把面板拖到 JEI 物品列表区域上方：JEI 避让，不被遮盖。
10. 配置 `jei=GENERIC`，保存，不重启：JEI 在下一次刷新布局后不再避让（记录**多久**之后生效，例如开关一下界面）；面板所有功能照常。
11. `jei=OFF`：与 GENERIC 相同；改回 FULL（或删掉条目）：JEI 又避让。来回切换 5 次均无需重启。

## 四、坏配置不崩溃（US3）

12. 逐个写入并启动游戏，**每种都要进得了世界**：`sophisticatedbackpacks=HALF`、`Bad Id=OFF`、`=OFF`、`abc`、`minecraft=OFF`、`jei=off`（小写）、同一 id 两条（取最后一条）、一条合法 + 一条非法（合法那条仍生效）、列了没装的模组 id、配置文件整体改成乱码。查看 `latest.log` 里的 WARN 行是否清楚。
12b. **聊天提示**：上面每种坏写法进入世界后，聊天区应出现对应的 `[浮动背包]` 提示（格式不对、档位无法识别、`minecraft`、重复；一次最多 5 条，多的合并成"还有 N 条"）；整个文件语法错误时出现"已备份为 …bak 并恢复默认"一条（先进入世界再看；游戏未进入世界时的提示会等进入世界后显示）。
12c. **游戏运行时改文件**：开着游戏用编辑器打开 `run/config/mcfloatingbackpack-client.toml`，故意存一个写了一半的内容（例如 `"abc` 少一个引号）：游戏里应在聊天区提示备份为 `mcfloatingbackpack-client-N.toml.bak`，配置变回默认；`.bak` 里能找到你写的内容。
13. 配置界面里：条目列表为空时能看到三档说明（中文与英文各看一遍）；能新增、修改、删除条目（若字符串列表项在界面里无法编辑，记为未验证/已知限制，改文件仍然可用）。

## 五、其它界面类模组（范围说明的验证）

14. 打开 JourneyMap 全屏地图（如果装了；默认开发环境没有，没装就记"未验证"）：面板自动隐藏，关闭后恢复，不受配置影响。
15. 小地图这类画在游戏画面上的 HUD 与面板的重叠：记录观察结果，不在本 feature 处理。

## 六、依赖规则与回归

16. `./gradlew compileJava test` 退出码 0；`DependencyRulesTest` 里三类反例都"被判为失败"，真实仓库通过。
17. 故意验证检查器：临时把 `neoforge.mods.toml` 的一个 optional 依赖改成 `required`，跑测试应当失败（验完改回）；临时在 `compat/` 之外加一条 `import mezz.jei...`，应当失败（验完删除）。
18. 服务端门禁：重跑 011/012/013 的转入、取出、合并、光标手势的手测，结果与改动前一致；`grep -rn "ServerCompatGate.allows" src/main/java` 应命中 5 个入口。
19. `run/logs/latest.log` 没有新增报错；协议版本仍为 `"8"`，能进世界。
20. 文档：`docs/MCFloatingBackpack.md` 等已说明配置项与三档含义。
