package pnnh.mcfloatingbackpack.item;

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
                            })
                            .build());

    private ModItems() {
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
