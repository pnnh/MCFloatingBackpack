# DELIVERY: 005-item-backpack

**Date**: 2026-10-25  
**Status**: A/C/E source complete. `./gradlew compileJava test` **BUILD SUCCESSFUL**. Manual `runClient` (T024/T025) still pending.

## Implemented (A / C / E)

| Area | Notes |
| --- | --- |
| Item + component | `leather_floating_backpack`, `BackpackContents` data component, 36 slots |
| Attachment removed | `BackpackAttachments` deleted; inventory bound to ItemStack |
| Open gate | B / main-hand use → `BackpackOpenRequestPayload`; deny message if none |
| Resolve order | MAIN_HAND → OFF_HAND → CHEST → INV 0–35 |
| Chest equip | Extends `ArmorItem` leather chestplate; Shift-use keeps vanilla equip |
| Back render | `BackpackBackLayer` placeholder box when chest slot is backpack |
| Panel | 4×9 layout via `PanelSlots` |
| JEI | `localRuntime` JEI 19.25.0.325 unless `-PnoJei`; optional in mods.toml |
| Recipe / lang / textures | Shaped recipe + en/zh + placeholder PNGs |

## Verify

```bash
./gradlew test compileJava   # done — success
./gradlew runClient          # pending manual (quickstart.md)
```

## Environment notes (this machine)

- JVM PKIX failed against Maven/Gradle HTTPS (Avast/corporate intercept). Workaround:
  - Local Gradle zip: `gradle/wrapper/gradle-9.2.1-bin.zip` (gitignored); `distributionUrl=gradle-9.2.1-bin.zip`
  - Custom truststore: `.gradle-truststore.jks` + user `~/.gradle/gradle.properties` `javax.net.ssl.trustStore=...`
- Machines with a healthy Java trust store can restore the normal Gradle distribution URL.
