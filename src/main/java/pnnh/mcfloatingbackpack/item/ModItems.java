package pnnh.mcfloatingbackpack.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.pipe.PipeKind;
import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.tank.TankCatalog;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MCFloatingBackpack.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MCFloatingBackpack.MOD_ID);

    public static final DeferredHolder<Item, FloatingBackpackItem> LEATHER_FLOATING_BACKPACK =
            register(BackpackTier.LEATHER);
    public static final DeferredHolder<Item, FloatingBackpackItem> IRON_FLOATING_BACKPACK =
            register(BackpackTier.IRON);
    public static final DeferredHolder<Item, FloatingBackpackItem> GOLD_FLOATING_BACKPACK =
            register(BackpackTier.GOLD);
    public static final DeferredHolder<Item, FloatingBackpackItem> DIAMOND_FLOATING_BACKPACK =
            register(BackpackTier.DIAMOND);
    public static final DeferredHolder<Item, FloatingBackpackItem> NETHERITE_FLOATING_BACKPACK =
            register(BackpackTier.NETHERITE);

    /** 018: lives in the panel's function area; see {@link TagManagerItem}. */
    public static final DeferredHolder<Item, TagManagerItem> TAG_MANAGER =
            ITEMS.register("tag_manager", () -> new TagManagerItem(new Item.Properties()));

    /** 021: lives in the panel's right area; see {@link TrashCanItem}. */
    public static final DeferredHolder<Item, TrashCanItem> TRASH_CAN =
            ITEMS.register("trash_can", () -> new TrashCanItem(new Item.Properties()));

    /** 024: lives in the panel's left area; see {@link AutoPickupItem}. */
    public static final DeferredHolder<Item, AutoPickupItem> AUTO_PICKUP =
            ITEMS.register("auto_pickup", () -> new AutoPickupItem(new Item.Properties()));

    /** 036, 051: the category extensions, in the fixed order of the categories; ids are {@code <category>_extension}. */
    public static final Map<String, DeferredHolder<Item, CategoryExtensionItem>> CATEGORY_EXTENSIONS = registerCategoryExtensions();

    /** 038: the five universal extensions, one for each backpack tier, in the order of the tiers; ids are {@code <tier>_general_extension}. */
    public static final Map<BackpackTier, DeferredHolder<Item, UniversalExtensionItem>> UNIVERSAL_EXTENSIONS = registerUniversalExtensions();

    /** 048: the 19 liquid tanks, in the order of {@link TankCatalog#all()}; ids are {@code <tier>_<liquid>_tank}. */
    public static final Map<TankCatalog.Tank, DeferredHolder<Item, TankItem>> TANKS = registerTanks();

    /** 049: the pipes (water, oil, lava), in the order of {@link PipeKind}; ids are {@code <liquid>_pipe}. */
    public static final Map<PipeKind, DeferredHolder<Item, PipeItem>> PIPES = registerPipes();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register(
                    "main",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + MCFloatingBackpack.MOD_ID))
                            .icon(() -> new ItemStack(LEATHER_FLOATING_BACKPACK.get()))
                            .displayItems((params, output) -> {
                                for (BackpackTier tier : BackpackTier.values()) {
                                    output.accept(stackFor(tier));
                                }
                                output.accept(TAG_MANAGER.get());
                                output.accept(TRASH_CAN.get());
                                output.accept(AUTO_PICKUP.get());
                                for (DeferredHolder<Item, TankItem> tank : TANKS.values()) {
                                    output.accept(tank.get());
                                }
                                for (DeferredHolder<Item, PipeItem> pipe : PIPES.values()) {
                                    output.accept(pipe.get());
                                }
                                for (DeferredHolder<Item, CategoryExtensionItem> extension : CATEGORY_EXTENSIONS.values()) {
                                    output.accept(extension.get());
                                }
                                for (DeferredHolder<Item, UniversalExtensionItem> extension : UNIVERSAL_EXTENSIONS.values()) {
                                    output.accept(extension.get());
                                }
                            })
                            .build());

    private ModItems() {
    }

    private static Map<String, DeferredHolder<Item, CategoryExtensionItem>> registerCategoryExtensions() {
        Map<String, DeferredHolder<Item, CategoryExtensionItem>> extensions = new LinkedHashMap<>();
        for (String category : ExtensionCategories.IDS) {
            extensions.put(
                    category,
                    ITEMS.register(
                            category + "_extension", () -> new CategoryExtensionItem(category, new Item.Properties())));
        }
        return Collections.unmodifiableMap(extensions);
    }

    private static Map<TankCatalog.Tank, DeferredHolder<Item, TankItem>> registerTanks() {
        Map<TankCatalog.Tank, DeferredHolder<Item, TankItem>> tanks = new LinkedHashMap<>();
        for (TankCatalog.Tank tank : TankCatalog.all()) {
            tanks.put(
                    tank,
                    ITEMS.register(
                            TankCatalog.itemId(tank.kind(), tank.tier()),
                            () -> new TankItem(tank.kind(), tank.tier(), new Item.Properties())));
        }
        return Collections.unmodifiableMap(tanks);
    }

    private static Map<PipeKind, DeferredHolder<Item, PipeItem>> registerPipes() {
        Map<PipeKind, DeferredHolder<Item, PipeItem>> pipes = new LinkedHashMap<>();
        for (PipeKind kind : PipeKind.values()) {
            pipes.put(kind, ITEMS.register(kind.itemId(), () -> new PipeItem(kind, new Item.Properties())));
        }
        return Collections.unmodifiableMap(pipes);
    }

    /** 049: the pipe item of a liquid. */
    public static PipeItem pipe(PipeKind kind) {
        return PIPES.get(kind).get();
    }

    /** 048: the tank item of a liquid and a tier. */
    public static TankItem tank(FluidKind kind, BackpackTier tier) {
        return TANKS.get(new TankCatalog.Tank(kind, tier)).get();
    }

    private static Map<BackpackTier, DeferredHolder<Item, UniversalExtensionItem>> registerUniversalExtensions() {
        Map<BackpackTier, DeferredHolder<Item, UniversalExtensionItem>> extensions = new LinkedHashMap<>();
        for (BackpackTier tier : BackpackTier.values()) {
            extensions.put(
                    tier,
                    ITEMS.register(
                            tier.name().toLowerCase() + "_general_extension",
                            () -> new UniversalExtensionItem(tier, new Item.Properties())));
        }
        return Collections.unmodifiableMap(extensions);
    }

    private static DeferredHolder<Item, FloatingBackpackItem> register(BackpackTier tier) {
        return ITEMS.register(
                tier.id(),
                () -> new FloatingBackpackItem(tier, FloatingBackpackItem.baseProperties(tier)));
    }

    public static ItemStack stackFor(BackpackTier tier) {
        Item item = switch (tier) {
            case LEATHER -> LEATHER_FLOATING_BACKPACK.get();
            case IRON -> IRON_FLOATING_BACKPACK.get();
            case GOLD -> GOLD_FLOATING_BACKPACK.get();
            case DIAMOND -> DIAMOND_FLOATING_BACKPACK.get();
            case NETHERITE -> NETHERITE_FLOATING_BACKPACK.get();
        };
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(tier.capacity()));
        return stack;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
