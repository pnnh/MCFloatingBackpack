# MCFloatingBackpack（浮动背包）

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.251+-orange.svg)](https://neoforged.net/)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3_or_later-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://openjdk.org/projects/jdk/21/)

[English](README.md) | **简体中文**

一个面向 **Minecraft 1.21.1 + NeoForge** 的浮动背包模组：像魔兽世界的背包窗口一样，一块可以拖动、缩放的面板始终浮在游戏画面之上，打开箱子、物品栏或其它模组的背包界面时它也不会消失，你可以在它们之间直接搬运物品。

> **状态**：早期开发（版本 0.1.0），还没有正式发布。功能已经能完整使用，但接口、存档格式和网络协议在发布前可能变化。**升级版本前请先备份存档。**

| 项 | 取值 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 及以上 |
| Java | 21 |
| 必须安装在 | **客户端和服务器两端**（背包内容由服务器保存） |

## 主要功能

- **浮动面板**：按 `B`（可在控制设置里改键）打开或关闭，也可以手持背包右键切换；面板可拖动、可缩放行列，位置和大小会保存。
- **与其它界面共存**：原版物品栏、箱子等容器界面打开时，面板保持可见、可点击；没有别的界面时，面板自己挂在一层透明的界面上。
- **五档背包**：皮革 36 格、铁 54 格、金 72 格、钻石 90 格、下界合金 108 格；皮革背包用皮革、线、箱子和铁锭合成，更高档在**锻造台**里用对应的材料块和纹饰模板升级，升级后内容原样保留。
- **三个功能区**：主区域的左、右两侧和下方各有一个功能区，用来放"功能件"：
  - 左区：**自动拾取**（捡到的物品自动进背包，带黑名单页）；
  - 右区：**垃圾桶**（带保护的垃圾桶面板，删除的物品可以取回）；
  - 下区：**标签管理器**（给物品打标签，用来整理和一键倾倒）。
- **分类扩展与通用扩展**：把扩展件放进功能区，主区域会多出一行 9 格。
  - 10 种**分类扩展**（食物、工具、武器、盔甲、矿物、农作物、木材、药水、红石、唱片），对应的行只放该类物品，行有各自的底色和分隔线；
  - 5 种**通用扩展**（皮革、铁、金、钻石、下界合金各一种，配方不同），行里任何物品都能放，只对同档背包生效，只在左区或右区生效，每个区只算一个，最多多出 2 行；
  - 拿走扩展件时，行里有东西的行会被隐藏（标题栏出现小黄点），放回去就回来，物品不会丢。
- **顺手的操作**：`Ctrl`（macOS 为 `Command`）加点击或拖动在背包和物品栏之间快速转移；`Shift` 选择格子；`Alt`（macOS 为 `Option`）加点击与主手物品交换；拖动分配和双击聚拢与原版一致。
- **可以背着**：潜行加右键把背包装到胸甲槽；装了 **Curios** 或 **Accessories** 时也可以背在它们的背部槽里，面板对背着的背包照常工作。
- **背包快照**：每只背包的内容会在世界之外保存快照，误操作后可以用界面（在控制设置里给"背包快照"绑一个键）或 `/snapshot` 命令恢复。
- **多个背包**：每只背包有自己的 4 位编号，显示在面板标题栏。

## 兼容性

- 精妙背包（Sophisticated Backpacks）、旅行者背包（Traveler's Backpack）的界面上面板也能显示并与它们互相转移物品；JEI 能避开面板；Curios、Accessories 为可选依赖。这些模组**都不是必需的**。
- 客户端配置 `config/mcfloatingbackpack-client.toml` 可以按模组降低或关闭兼容；服务器配置在世界的 `serverconfig/mcfloatingbackpack-server.toml`。
- 两个配置里都有诊断日志的级别设置（`OFF`/`ERROR`/`WARN`/`INFO`/`DEBUG`）。出了问题（比如背包不见了）时调高级别，`latest.log` 里会有更多线索。

## 多人游戏

- 客户端和服务器**必须安装同一个网络协议版本**，否则无法进入服务器。
- 即使网络协议相同，**客户端和服务器最好使用相同的模组版本**：部分规则只在服务器上生效，版本不同时界面显示可能与服务器的实际结果不一致。模组会在你登录时比较两端的版本：**MAJOR.MINOR** 不同（PATCH 不同没关系）时，聊天里会只给你自己一条警告，提醒你升级或降级（先备份存档），服务器也会记日志；太旧、没有这个检测功能的客户端，则由服务器发一条纯文本提示。
- 想检查客户端与服务器的模组版本是否一致，可以另外安装 [MCompatIssues](https://github.com/pnnh/MCompatIssues)（可选，不是本模组的依赖）。
- 背包内容由服务器保存和校验，客户端不能凭空产生物品。

## 升级与降级

升级模组之前，尤其是**降级**之前，请先备份存档。背包会记住最后一次保存它时的数据修订；如果你回到旧版本，被更新版本保存过的背包会受到保护：第一次打开不会成功，聊天里会告诉你原因，60 秒内再次打开表示你接受"旧版本不认识的数据可能丢失"的风险。（保护从第一个会盖章的版本开始生效，更早的版本无法保护。）

## 发布版本

版本由 GitHub Actions 根据语义化版本的 tag（`vMAJOR.MINOR.PATCH`）发布到 [GitHub Releases](https://github.com/pnnh/MCFloatingBackpack/releases)，每个附件都带 GitHub 构建溯源证明，可以验证：

```bash
gh attestation verify mcfloatingbackpack-<版本>.jar --repo pnnh/MCFloatingBackpack
```

（目前还没有发布过任何版本。版本号规则和发布步骤见 [`docs/plan/release-process.md`](docs/plan/release-process.md)。）

## 从源码构建

```bash
./gradlew build            # 产物在 build/libs/
./gradlew test             # 单元测试（不加载 Minecraft）
./gradlew runClient        # 启动开发客户端
```

开发客户端默认会带上 JEI、Curios、Accessories、精妙背包、旅行者背包等用于联调的模组（仅开发运行，不会进入发布的 jar）：

```bash
./gradlew runClient -PcleanRuntime            # 只留 JEI
./gradlew runClient -PcleanRuntime -PnoJei    # 连 JEI 也去掉
```

需要 Java 21。跑完 Gradle 后可以用 `./gradlew --stop` 关掉后台进程。

## 项目文档

- [`docs/MCFloatingBackpack.md`](docs/MCFloatingBackpack.md)：项目目标、功能说明和各个 feature 的概要。
- [`docs/plan/`](docs/plan)：规划与设计文档，以及[手动验证清单](docs/plan/manual-verification-checklist.md)。
- [`specs/`](specs)：用 [Spec Kit](https://github.com/github/spec-kit) 做的每个 feature 的规格、方案、任务与交付说明（编号从 001 起，如 035 起是"分类扩展"系列）。
- `docs/` 和 `specs/` 下的项目文档使用中文（作者的工作语言），约定见 [`AGENTS.md`](AGENTS.md)。

## 参与贡献

这是个人项目，可能不会接受外部贡献。

## 许可证

GNU Affero General Public License v3.0 或更高版本（AGPL-3.0-or-later），见 [LICENSE](LICENSE)。
