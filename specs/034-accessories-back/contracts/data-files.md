# Contract: 数据文件与依赖声明

## `data/accessories/tags/item/back.json`

```json
{
  "replace": false,
  "values": [
    "mcfloatingbackpack:leather_floating_backpack",
    "mcfloatingbackpack:iron_floating_backpack",
    "mcfloatingbackpack:gold_floating_backpack",
    "mcfloatingbackpack:diamond_floating_backpack",
    "mcfloatingbackpack:netherite_floating_backpack"
  ]
}
```

要求：`replace` 必须是 `false`；5 个档位的 id 与 `BackpackTier.id()` 一致（测试检查）；没有多余项；Accessories 没装时这个文件被忽略。**不需要**槽位分配文件（Accessories 默认已经给玩家 `back` 槽）。

## `neoforge.mods.toml`

```toml
[[dependencies.mcfloatingbackpack]]
modId = "accessories"
type = "optional"
versionRange = "[1,)"
ordering = "NONE"
side = "BOTH"
```

（格式以文件里已有的 Curios / JEI 依赖为准。）

## `build.gradle`

```groovy
maven { name = "Wisp Forest"; url = "https://maven.wispforest.io/releases"; content { includeGroup "io.wispforest" } }
compileOnly("io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1") { transitive = false }
```

（`transitive = false`：POM 会带进 owo-lib、forgified-fabric-api 等；只编译 Accessories 自己的 API。若 javac 抱怨缺类型，再按需单独 `compileOnly`。）
