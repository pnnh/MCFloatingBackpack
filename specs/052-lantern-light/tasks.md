# Tasks: 灯笼光源（接 LambDynamicLights）

**Input**: `/specs/052-lantern-light/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

> 2026-11-13：方案从"服务端光源方块"改成"接 LambDynamicLights"。下面是改后的任务；原先做过、后来整个删除的服务端部分不再列出（见 research R1）。

## Format

- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；AI 自动验证的勾选规则同前（有明确证据才勾，主观项——平滑程度、光影包下的观感——只由用户确认）
- 不新增配置项、网络包、物品、世界数据、mixin；协议号仍 19，数据修订仍 3

## Phase 1: 规则（纯逻辑）与资源

- [X] T001 `…/light/ToolLights.java`（亮度表，数值等于原版）、`ToolLightRules.java`（下方区至多 1 个光源、第一个光源）、`LightTags.java`；单元测试 `ToolLightsTest`（等于原版、关系不反）、`ToolLightRulesTest`
- [X] T002 标签 `tool_lights.json`、语言键 `light.duplicate`；`BackpackClickPayload` 与 `FloatingPanelInput` 各接一处互斥检查与提示；`LightResourcesTest`

## Phase 2: 接 LambDynamicLights

- [X] T003 `build.gradle`：仓库 maven.gegy.dev（限 `dev.lambdaurora.lambdynamiclights`）、`compileOnly` API（mojmap，`transitive = false`）与 Yumi 事件库、`localRuntime` LDL（只客户端的开发运行）；`neoforge.mods.toml`：可选依赖（`optional`、`CLIENT`、`[4,)`）与 `yumi:entrypoints` 入口
- [X] T004 `…/light/PlayerLights.java`（读背包下方区、算亮度）；`…/compat/lambdynlights/BackpackDynamicLight.java`（入口）与 `BackpackEntityLuminance.java`（对玩家，受 `compat.modLevels` 控制）
- [X] T005 `FloatingPanelInput`：没装 LDL 时第一次放进光源提示一次（`light.needs_mod`，中英文）
- [X] T006 `LightWiringTest`（入口声明、可选依赖、只在 compat 包引用 LDL、没有 mixin 与网络包与世界方块操作……）；`./gradlew compileJava test` 与 `DependencyRulesTest` 通过

## Phase 3: 游戏内验证与文档

- [X] T007 游戏内验证（容器，quickstart B1～B6）：每项写实际验证了什么，做不了的写"未验证"；平滑程度写"待用户确认"
- [X] T008 [P] `README.md` 与 `README_CN.md`：灯笼光源一节（需要另外安装 LambDynamicLights、没装不发光、八类光源与亮度、互斥、只在下方区）
- [X] T009 `DELIVERY.md`（中文）：基线与完成后的类数与用例数、与最初方案的偏离（整个删除服务端方案）、每个验证项验证了什么/没验证什么、已知局限、发布说明要写的两条
- [X] T010 最终检查：`./gradlew compileJava test` 全部通过；`git status` 无临时修改残留；**不提交、不打 tag**
- [ ] T011 用户确认：走路时光的平滑程度、与 Iris 光影包的搭配、真机放置；**只有用户明确确认才勾**
