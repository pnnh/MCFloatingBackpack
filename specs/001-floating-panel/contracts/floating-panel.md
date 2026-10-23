# Contract: Floating Panel Overlay (M1)

## Purpose

Define the client-facing behavior contract for the empty floating panel. No network protocol in this feature.

## Toggle

| Input | Precondition | Effect |
| --- | --- | --- |
| Toggle key press | In world; not in chat screen | Flip `visible` |
| Toggle key press | Main menu / loading | No panel |
| Toggle key press | Chat screen open | Ignore (no toggle) |

## Render

| Condition | Effect |
| --- | --- |
| `visible == false` | Draw nothing |
| `visible == true`, no Screen | Draw panel in GUI space |
| `visible == true`, container/inventory Screen open | Draw panel after that Screen's content so both are visible |

Panel MUST include a visible drag bar (title) and body. Exact pixels/colors are implementation detail.

## Hit testing (mouse)

| Region | Mouse down | Mouse drag | Mouse up |
| --- | --- | --- | --- |
| dragBar | Start drag; consume | Move panel; consume | End drag; clamp; save; consume |
| body | Consume; no item logic | If dragging from bar, N/A | Consume |
| outside | Pass through | Pass through | Pass through |

Consume means lower Screen widgets under that point do not receive the event for that press.

## Persistence

| Event | Behavior |
| --- | --- |
| Drag end | Write `posX`/`posY` to client local config |
| Client start / config load | Read into state, then clamp |
| World join | Apply saved position; `visible` starts false |

## Non-goals (explicit)

- No slot clicks, no `ItemStack` moves, no server packets for this contract.
- No guarantee of z-order above every third-party overlay (JEI etc.) in M1.
