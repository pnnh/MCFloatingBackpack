# Data Model: 分类扩展：10 个扩展件物品

没有新增持久化或网络数据：行列表、`BackpackContents.rows`、协议 17 都是 035 的。下面是本 feature 新增的类型与资源。

## `CategoryExtensionItem`

```java
public final class CategoryExtensionItem extends Item {
    private final String category;                 // ExtensionCategories.IDS 之一
    public CategoryExtensionItem(String category, Properties properties) { super(properties.stacksTo(1)); ... }
    public String category();
    @Override public void appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag);
        // 加一行：Component.translatable("item.mcfloatingbackpack.category_extension.tooltip",
        //                                Component.translatable(PresetTags.langKey(category))).withStyle(ChatFormatting.GRAY)
}
```

## `ModItems`（改）

```java
public static final Map<String, DeferredHolder<Item, CategoryExtensionItem>> CATEGORY_EXTENSIONS = ...;   // 按 ExtensionCategories.IDS 的顺序
// id = category + "_extension"，例如 mcfloatingbackpack:food_extension
// 创意栏：TAG_MANAGER、TRASH_CAN、AUTO_PICKUP 之后，按同一顺序
```

## 解析器

```java
// MCFloatingBackpack 构造函数
ExtensionPacks.register(stack -> stack.getItem() instanceof CategoryExtensionItem item ? item.category() : null);
```

## 资源文件（每个类别一份，`<id>` = `<类别>_extension`）

| 文件 | 内容 |
|---|---|
| `assets/mcfloatingbackpack/models/item/<id>.json` | `{"parent":"minecraft:item/generated","textures":{"layer0":"mcfloatingbackpack:item/<id>"}}` |
| `assets/mcfloatingbackpack/textures/item/<id>.png` | 16×16，8 位 RGBA |
| `data/mcfloatingbackpack/recipe/<id>.json` | `minecraft:crafting_shaped`，见 contracts/recipes.md |
| 语言键 | `item.mcfloatingbackpack.<id>`（两种语言）；通用 `item.mcfloatingbackpack.category_extension.tooltip`；提示 `message.mcfloatingbackpack.limit.extension_rows` |

## 035 的小补充（R9）

`BackpackClickPayload`：`ExtensionPackRules.canPlace` 为假时，除了 `sendSlotUpdate` 外给玩家发一条 actionbar 消息 `message.mcfloatingbackpack.limit.extension_rows`。

## 与已有数据的关系

- `ExtensionCategories.IDS`、`PresetTags`（035）：类别 id、名字与标签；本 feature 只读。
- `ToolAreaRules`：不改（扩展件不受"每区一个"限制，035 已有单元测试）。
