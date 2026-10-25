# Contract: Item-Bound Backpack Open & Sync (005)

## Side requirement

Dedicated servers MUST install this mod. Item contents live in ItemStack components; clients MUST NOT invent stacks.

## Resolve order (shared)

```text
MAIN_HAND → OFF_HAND → CHEST → INV[0..35]
```

First floating-backpack stack wins.

## Payloads (logical; evolve existing M2 names)

### `BackpackFullSync` (S → C)

| Field | Type | Notes |
| --- | --- | --- |
| source | enum/byte | Ref source |
| invIndex | int | Inventory index or -1 |
| slots | 36 × ItemStack | Full mirror |
| cursor | ItemStack | Panel cursor |

Sent when: successful open/toggle-on, login while panel was to restore (optional), forced resync.

### `BackpackClick` (C → S)

| Field | Type | Notes |
| --- | --- | --- |
| source | enum/byte | Client claimed ref |
| invIndex | int | |
| slotIndex | int | 0–35 |
| button | int | 0 = left |
| shiftDown | bool | |
| screenOpen | bool | |
| panelOpen | bool | Must be true |

Server: re-resolve or verify claimed location still holds a floating backpack; else ignore and optionally tell client to close.

### `BackpackSlotUpdate` (S → C)

| Field | Type | Notes |
| --- | --- | --- |
| slotIndex | int | 0–35 or -1 |
| stack | ItemStack | |
| cursor | ItemStack | |

### `BackpackClose` / deny hint (S → C) — optional

| Field | Type | Notes |
| --- | --- | --- |
| reason | enum | NO_BACKPACK, REF_LOST, … |

## Open / use semantics

1. Key toggle: resolve; if null → actionbar hint; else toggle panel + full sync when opening.
2. Main-hand right-click use: same toggle when not consumed by block interaction.
3. While panel open, every click must validate ref before mutating component on that stack.

## Click semantics (server)

Same as M2/M3 logic, but target container is `contents` of the resolved ItemStack (size 36). Persist by setting the modified stack back into hand/chest/inventory slot.

Cross-GUI merge (003): continue using menu carried bridge; backpack side reads/writes item-bound contents.

## Non-goals

- OP remote open by name/id
- Multi-tier items
- Curios slot refs
