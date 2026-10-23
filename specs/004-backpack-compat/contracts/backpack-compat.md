# Contract: backpack container compat

## Client → server (unchanged payload)

`BackpackClickPayload(slotIndex, shiftDown, screenOpen, panelOpen)`

- `screenOpen` MUST be true when `ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)`.
- Server continues to use `player.containerMenu.getCarried()` when `screenOpen`.

## Soft detection (no network)

| Marker | Match |
| --- | --- |
| Sophisticated | class name contains `sophisticated` and (`backpack` or `storage` screen package), or package `net.p3pp3rf1y` |
| Traveler's | class name / package contains `travelersbackpack` |

Any `AbstractContainerScreen` is compatible even if not tagged.

## Optional mods.toml

```toml
[[dependencies.mcfloatingbackpack]]
    modId="sophisticatedbackpacks"
    type="optional"
    ...
[[dependencies.mcfloatingbackpack]]
    modId="travelersbackpack"
    type="optional"
    ...
```
