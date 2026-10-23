# Data Model: 004-backpack-compat

## ContainerScreenCompat (client utility)

| Field / API | Meaning |
| --- | --- |
| `isOpenContainerScreen(Screen)` | true if screen uses menu-carried path (`AbstractContainerScreen` or soft-matched known backpack UI) |
| `identify(Screen)` | `CompatTarget`: VANILLA_CONTAINER, SOPHISTICATED, TRAVELERS, UNKNOWN, NONE |
| Soft markers | class-name substrings / package prefixes for SB & TB |

No new persisted player data. Item state remains Attachment backpack + open menu carried.
