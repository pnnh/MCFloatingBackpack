# Specification Quality Checklist: 通用扩展

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) —— 需求（FR）与成功标准里没有类名与框架；类名只出现在"已查证的事实 / 尚未验证"两节（本项目惯例，035/036/037 同）
- [x] Focused on user value and business needs —— 以"过渡升级的选择、不比升级更划算、通用行是溢出区"为主线
- [x] Written for non-technical stakeholders —— 用户故事与验收场景用玩家的话
- [x] All mandatory sections completed —— 用户场景、需求、成功标准、假设都有

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain —— 0 个；配方、色值、是否画淡图标按默认写进"假设"，由 plan 与用户在游戏里看过后调整
- [x] Requirements are testable and unambiguous —— FR-001～FR-019 都能在游戏里或单元测试里验证
- [x] Success criteria are measurable —— SC-001～SC-007 有次数、数量或时间
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined —— 6 个用户故事各有验收场景
- [x] Edge cases are identified —— 溢出区常空、皮革功能区机会成本、背包与扩展件放进通用行、与垃圾桶同区、创造模式超限、旧版本读 general、隐藏后放回受限
- [x] Scope is clearly bounded —— Out of Scope 明确排除新类别、3 个以上、搜索整理、手动标签、配置命令与新包
- [x] Dependencies and assumptions identified —— 依赖 035/036/037 与工具区规则；假设一节

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows —— 物品、放置与隐藏、数量限制、目标顺序、界面、共存回归
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 与分类扩展的**唯一规则变更**是目标顺序三段化（FR-010、FR-018）：它改的是 035 的规则，035 的相关测试要显式更新并说明原因。
- 2026-11-10 用户补充后规格已重写：放哪里都允许、只有同档背包左/右区才生效（每区算一个、最多 2 行）；改成 5 个档位各一个通用扩展、各有配方；同区第二个与别档都是"允许放但不生效"，没有拒绝与弹窗。
- 最大的新工作量：核对行数要看"物品在哪个区、背包的档位"（现在 035 的核对既不分区也不看档位）。
- "尚未验证"一节列出的各项必须在 plan 阶段查证：哪些遍历类别列表的地方包含或排除 `general`、三段顺序的全部调用点、工具区规则的最小表达、机会成本、底色与文字、贴图的图集度量（度量会删用户的上次日志，先问）。
