# Specification Quality Checklist: 垃圾桶的删除手段与保护

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-05
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

- 与 018～021 的规格一致，"背景与已确认的决策"与"已查证的事实"记录了读本仓库源码得到的约束（021 的请求只有三个动作、018 的"标签已满"先例、019 的按键转发、测试类路径只有 JUnit）；这些是用户要求规格尊重的事实，不是实现方案。功能需求与验收场景只描述玩家可见的行为，"后置键盘事件"只出现在 Assumptions 里作为留给 plan 的提示。
- 用户已逐点确认的内容全部写入背景与 FR：倾倒、两个图标、两层保护、二次确认（阈值 > 9、整个面板蒙版）、Delete 键映射（默认键、条件、三种界面、服务端核对）。没有 [NEEDS CLARIFICATION]。
- **我补的默认值**（都在 Assumptions，不对就说）：有选区时 Delete 作用于选区；删除后选区清空；没有可倾倒物品时聊天区一句提示，有跳过但有倾倒时不另提示；确认框按钮文字"确认/取消"；销毁组数 = `max(0, 现有组数 + 放入组数 − 9)`；因此单次 Delete 悬停一格永远不弹确认；按键名 `key.mcfloatingbackpack.delete_to_trash`。
- 本 feature 改动 018 的标签面板行为（受保护物品拦截"垃圾"标签）和 021 的垃圾桶请求（批量放入、已确认标志，协议再升），已在 FR-008、FR-014、FR-017 明确。
- 登记为未验证、不得声称已满足：Windows/Linux（Delete 与 Backspace 的默认键只在 macOS 验证）；Delete 在原版箱子里的多玩家同步；JEI/配方书搜索框焦点下的实际表现；保护标签对 `bundle` 在特性未启用时的加载；图集度量（实现完成时才度量）。
- 倾倒、删除的"垃圾桶本体与手势"明确不在本 feature 范围内（021 已做）。
