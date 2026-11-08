# Specification Quality Checklist: 第三个扩展工具——自动拾取（标签黑名单与对其它背包模组的避让）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-08
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 与 018～021 的规格一致，"背景与已确认的决策"与"已查证的事实"记录了读本仓库源码得到的约束；功能需求与验收场景只描述玩家可见的行为。
- 用户在本次需求里提出的开放问题已由用户裁决并写入决策：背包满时剩余回落到玩家物品栏，命中黑名单的物品留在地上；**2026-11-08：取消主动避让**（最初的"检测其它背包并按 020 三档避让、档位由客户端同步给服务端"需要依赖精妙背包/旅行者背包的内部类，风险大），改为只做被动礼让（`LOW` 优先级 + 事件已处理就跳过），对应 spec 决策 9～11、US4、FR-020～FR-022。
- **"尚未验证"一节**登记了本 feature 的主要风险：与精妙背包/旅行者背包同时拾取时的表现是读字节码得出的，未在游戏里验证；"自己做完拾取"与原版的差异（不触发 `Post`、拾取声）。
- 我补的默认值都在 Assumptions：下拉候选 = 预设 + 本背包已用标签（plan 可简化）；标签页面与黑名单页面互斥、与垃圾桶面板共存；配方为建议值（`I E I / R H R / I D I`：4 铁锭、末影珍珠、2 红石、漏斗、钻石），plan 阶段可微调。
- 协议版本升级（`"12"`）、FR-024 的"可脱离 Minecraft 类单测"沿用 018/020/021 的写法，规定结果与约束，不规定类名与做法。
- 宪法 VII：取消主动避让后本 feature 不引用、不检测任何第三方模组，不新增依赖/`compat/` 代码/配置含义（FR-021），依赖规则自动检查保持通过。
- 登记为未验证、不得声称已满足：Windows/Linux；与精妙背包/旅行者背包同时拾取时的实际表现；拾取的统计/成就与原版是否一致；图集度量（实现完成时才度量）。
