# Specification Quality Checklist: 搜索前缀语法（`@模组`、`#标签`、`/hidden`）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)（本项目规格沿用领域词；`SearchQuery`、`HiddenHint` 等已有类名只用于说明"扩展哪里"，具体形式交给 plan）
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

- 没有 [NEEDS CLARIFICATION]：方案第 3 节已由用户确认；唯一的新问题（隐藏物品不在客户端）已由用户答复：**摘要模式**（见 spec 的 Clarifications）。
- 读代码时更正了方案里的一句错话："客户端已有隐藏行数据，不用新增同步"（实际只有摘要）；`docs/plan/backpack-search.md` 同步更正。
- 我替用户补的默认：`#` 的物品标签含所有命名空间；`@` 只看物品注册名的命名空间与模组显示名；`/hidden` 之后的词只过滤类别行，`@`、`#` 对类别行不成立；整条查询只有最开头的一个指令；`/`、`/h` 等不完整的指令算"未知指令"。
- plan 阶段先查证的事写在 spec 的"尚未验证"：背包标签在客户端的数据形态、物品标签读取与缓存、模组显示名来源、`/hidden` 在 3 列面板里的排布、前缀与 042 解析的结合。
