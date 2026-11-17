# Specification Quality Checklist: 移动熔炉（右区工具，点一下打开原版熔炉界面，背包里也能"后台烧"）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-17
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs) — 本项目的 spec 沿用既有风格，"已查证的事实"与"尚未验证"两节记录了与原版接口的约束（读源码得来，plan 阶段逐项复核）；需求与验收本身以玩家可见的行为表述
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders — 背景、使用方式与用户故事用玩家语言；技术性内容集中在标明的两节
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details) — SC-004 引用了本项目的测试与版本号约定，属于项目内的发布口径
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification — 同第一项的说明

## Notes

- 没有 [NEEDS CLARIFICATION]：用户已确认的决策（做法 A、补算、只做普通熔炉、状态存在工具物品上、右区至多一个、配方草案、修订 5 → 6）都写进了 Clarifications。
- 默认值已写入 Assumptions：游戏时间口径（离线不算）、不做外部自动化、不画实时状态、经验发给打开并取走的玩家。
- "尚未验证"一节列出 plan 阶段必须先查证的事项（`ContainerData` 填充、数据组件与保存路径、写回频率、补算边界、经验、协议号），验证前不得声称已满足。
