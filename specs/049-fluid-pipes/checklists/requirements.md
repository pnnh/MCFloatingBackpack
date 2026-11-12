# Specification Quality Checklist: 水管、油管、岩浆管

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
- 我替用户补的默认：管子只在主手生效；提示冷却 1 秒；一次恰好一桶（不够就拒绝，不吸一半）；潜行右键本 feature 里什么也不做；管子的配方"比铁罐便宜"的具体形状留给 plan；PNC 只做开发运行时依赖。
- 协议号与数据修订是否变化留给 plan 决定（左键若需要新的客户端到服务端的包则协议号升 20）。
