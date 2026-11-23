# Specification Quality Checklist: 被 Accessories 丢掉的背包（槽位消失时归还）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-23
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)（沿用 030～061 的写法，会点到 NBT 的路径与已确认的决定；具体类与时机留给 plan）
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders（用户故事一节是玩家/服主视角）
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded（只还浮动背包、只处理整个槽位类型不存在、不处理 Curios 与格数变少）
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

- 没有需要再问用户的点：范围来自 061 的实测与用户的选择；归还位置（物品栏、放不下掉脚边）、提示、档位门都用合理默认并写入 Assumptions。
- plan 阶段已查完三件事（见 research R1、R4、R5）：Accessories 对缺失槽位类型是直接丢弃、对格数变少是自己归还（不在范围）；现存槽位类型读 `AccessoriesCapability.getContainers().keySet()`；核对放在登录后 20 刻与下线事件。原要查的三件事：怎样在服务端读出"Accessories 现在给这个玩家的槽位类型"而不引用它的类型（或只放在 `compat/accessories/`）；登录后延迟的合适长度；Accessories 的"槽位还在、格数变少"是否也丢物品（决定是否另开）。
