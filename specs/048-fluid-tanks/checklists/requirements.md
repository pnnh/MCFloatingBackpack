# Specification Quality Checklist: 液体罐

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)（已有类名只用于说明"沿用哪条规则"）
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic
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

- 没有 [NEEDS CLARIFICATION]：玩法与取舍已在方案阶段由用户逐条确认（见 spec 的 Clarifications 与 docs/plan/fluid-tanks.md）。
- 我替用户补的默认：钻石背包排在金与下界合金之间（没有钻石罐）；048 阶段玩家无法往罐里装液体，液量由内部接口与测试改变；超量液体不裁剪；左、右区放罐可放但不生效；协议号"按需要升到 19"（是否要进同步载荷由 plan 决定）。
- 2026-11-12 补充了 US6（隐藏液体：黄点与 `/hidden`，FR-020～023），属 048 的延伸（048 尚未提交）。
- 历史测试里断言"协议号是 18"的会随升级改为 19（FR-014）。
