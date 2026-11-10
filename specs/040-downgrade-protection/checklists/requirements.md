# Specification Quality Checklist: 降级保护

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) —— 需求（FR）与成功标准里没有类名与框架；类名只出现在"背景 / 已查证的事实 / 尚未验证"（本项目惯例）
- [x] Focused on user value and business needs —— 以"降级后背包里的数据不被悄悄丢掉、玩家有出口"为主线
- [x] Written for non-technical stakeholders —— 用户故事与验收场景用玩家的话
- [x] All mandatory sections completed —— 用户场景、需求、成功标准、假设都有

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain —— 0 个；保护强度（两步确认、60 秒）、修订规则已由用户确认；认出"同一只没有身份的背包"的办法留给 plan（写在边缘情形与假设里）
- [x] Requirements are testable and unambiguous —— FR-001～FR-011 都能在游戏里或单元测试里验证
- [x] Success criteria are measurable —— SC-001～SC-006 有比例、次数或书面结论
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined —— 5 个用户故事各有验收场景
- [x] Edge cases are identified —— 无身份、修订比较、各种位置、多只被保护的背包、连续点击、旧版本保存的背包、创造复制、快照、单人与局域网
- [x] Scope is clearly bounded —— Out of Scope 明确排除解码层面的丢失（可能另开 041）、找回已丢数据、对无标记旧版本的保护、自动迁移、配置命令、协议变化
- [x] Dependencies and assumptions identified —— 依赖 `BackpackGate` 统一入口、036～039；假设一节

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows —— 被拒并提示、两步确认、其它路径、正常不受影响、日志与文档
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **降级实验必须在 plan 阶段先做**（"尚未验证"一节的 a～d）：它决定保护的有效范围，以及要不要另开 041 做"保留解码不了的物品"。这个 feature 的价值取决于实验结论：如果旧版本解码新背包整个失败而不是丢字段，本 feature 只能保护带标记的版本之间的降级。
- 已知局限：保护从第一个带标记的版本开始；早于它的版本不认识这个字段，也不会保留它。
