# Specification Quality Checklist: 版本一致性提示

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) —— 需求（FR）与成功标准里没有类名与框架；NeoForge 的 API 名只出现在"已查证的事实 / 尚未验证"两节（本项目惯例）
- [x] Focused on user value and business needs —— 以"玩家在出现怪问题之前就知道版本不对"为主线
- [x] Written for non-technical stakeholders —— 用户故事与验收场景用玩家的话
- [x] All mandatory sections completed —— 用户场景、需求、成功标准、假设都有

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain —— 0 个；无法比较时的处理按"当作不一致并提示"写进需求与假设
- [x] Requirements are testable and unambiguous —— FR-001～FR-014 都能在游戏里或单元测试里验证
- [x] Success criteria are measurable —— SC-001～SC-007 有比例、次数或输入种数
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined —— 5 个用户故事各有验收场景
- [x] Edge cases are identified —— 无法解析、开发版相同版本、聊天未就绪、提前退出、并发登录、重连、局域网、客户端回包丢失
- [x] Scope is clearly bounded —— Out of Scope 明确排除其它模组、降级保护（040）、自动升级与拒绝登录、配置命令、分级、规则修订号
- [x] Dependencies and assumptions identified —— 依赖 release-process 的版本规则；假设一节

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows —— 不一致提示、一致不提示、老客户端、日志、预测宽容
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 最大的不确定性是 NeoForge 的可选网络包行为（`optional()` 包放进单独注册器是否让没有通道的旧客户端仍能登录、`hasChannel` 何时可用、对无通道客户端发包会怎样），"尚未验证"一节列出，plan 阶段要先做实验；如果旧客户端因此进不了服务器，整个机制要重新设计（那就违反 FR-006 与 SC-002）。
- 已知局限：开发版之间（版本都是 0.1.0）的代码差异检测不出来。
