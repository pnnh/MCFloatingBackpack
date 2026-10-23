# Contract: Cross-GUI Cursor (M3)

## Click (`BackpackClickPayload`) when `panelOpen`

### `screenOpen == false`

Unchanged from M2: Shift ↔ main hand; else panel cursor ↔ backpack slot.

### `screenOpen == true`

1. Resolve `menu = player.containerMenu`.
2. If `inventory.panelCursor` non-empty and `menu.getCarried()` empty → move panel cursor into carried.
3. Apply left-click logic between backpack `slotIndex` and `menu.getCarried()`.
4. `menu.setCarried(result)`; clear `inventory.panelCursor` if using menu carried.
5. `menu.broadcastChanges()`; send backpack slot (+ optional cursor EMPTY) update.

## Client render

- Hover: translucent white over hovered floating slot.
- Cursor item: from `menu.getCarried()` if `AbstractContainerScreen`, else panel view cursor.

## Non-goals

- Third-party backpack GUIs (M4).
- Changing vanilla slot click code paths beyond sharing carried.
