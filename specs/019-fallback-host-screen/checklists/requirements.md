# Specification Quality Checklist: 取消"无界面 HUD 模式"，改为兜底的透明宿主界面

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

- 与 011～018 的规格一致，"已查证的事实"一节记录了读源码得到的约束（`setScreen(null)` 不触发 `Opening`、有界面时按键映射不生效等）；这些是用户要求规格尊重的事实，不是实现方案。功能需求与验收场景只描述玩家可见的行为。
- FR-011 与 FR-019 提到"驱动渲染/输入""网络协议"，是为了钉住本 feature 的范围（取消第二套驱动、不改协议），不规定做法。
- SC-006、SC-008 里的日志与 `grep` 检查沿用本项目既有规格的验收习惯。
- 没有 [NEEDS CLARIFICATION]：ESC 的行为取现状（面板可见时 ESC 先关面板）作为默认；"吞掉哪些按键"已明确留给 plan 阶段；"面板被非容器界面隐藏时会话是否保持"按用户已确认的决策 5 写入假设。
- 已登记为已知限制或未验证、不得声称已满足：F1/F3 等按键码直接处理的功能在宿主下不可用；不经 `consumeClick` 的其它模组按键可能无效；JEI/EMI 在宿主界面上的表现；"一帧闪动"；Windows/Linux。
