# Specification Quality Checklist: 分类扩展：界面

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) —— 需求（FR）与成功标准里没有类名、框架名；类名只出现在"已查证的事实 / 尚未验证"两节（本项目的惯例，035/036 同）
- [x] Focused on user value and business needs —— 以玩家看得出行、放得对、知道隐藏了什么为主线
- [x] Written for non-technical stakeholders —— 用户故事与验收场景用玩家的话
- [x] All mandatory sections completed —— 用户场景、需求、成功标准、假设都有

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain —— 0 个；有歧义处按现有信息取默认并写进"假设"（隐藏内容按格数、客户端保守放行、色值留给 plan）
- [x] Requirements are testable and unambiguous —— FR-001～FR-018 都能在游戏里或单元测试里验证
- [x] Success criteria are measurable —— SC-001～SC-008 有次数、比例或时间
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined —— 5 个用户故事各有验收场景
- [x] Edge cases are identified —— 折行、被覆盖、窄面板、光标上的扩展件、标签未同步、色觉、多类隐藏、升级、创造模式光标
- [x] Scope is clearly bounded —— Out of Scope 明确排除搜索与整理、新物品、标签面板的只读类别显示、快照预览、新配置/命令/包
- [x] Dependencies and assumptions identified —— 依赖 035 的规则与行信息、036 的物品；假设一节

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria —— FR 与用户故事的验收场景一一可对应
- [x] User scenarios cover primary flows —— 看得出行、形状不变、预览知道规则、隐藏提示、共存回归
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 与用户请求的一处差异：用户写"各多少件物品被隐藏"，而 035 给客户端的信息只有"有东西的格数"；规格按格数写（假设一节），要精确到件数需升协议，留给 plan 论证。
- "尚未验证"一节列出的各项（客户端能否拿到判定输入、布局怎样提供行归属、`slotCount()` 各处该用哪个、绘制开销）必须在 plan 阶段查证，验证前不得声称已满足。
