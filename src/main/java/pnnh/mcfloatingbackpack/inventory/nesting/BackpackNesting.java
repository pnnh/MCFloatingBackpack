package pnnh.mcfloatingbackpack.inventory.nesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 064: the real item stacks behind {@link NestingRules} and {@link NestedOrigins}, and the message a refusal shows. A floating
 * backpack does not go into another floating backpack, nor does an item that carries one (a shulker box with a backpack in it).
 * Vanilla's own containers are read piece by piece; another mod's container data is searched for the backpacks' item ids.
 */
public final class BackpackNesting {
    private static final NestedOrigins<UUID, BackpackRef, ItemStack> ORIGINS = new NestedOrigins<>(ItemStack::matches);

    private static final List<String> BACKPACK_IDS = backpackIds();

    private static final NestingRules.Probe<ItemStack> PROBE = new NestingRules.Probe<>() {
        @Override
        public boolean isEmpty(ItemStack stack) {
            return stack.isEmpty();
        }

        @Override
        public boolean isBackpack(ItemStack stack) {
            // 065: a combined backpack holds nothing, but it does not belong inside a backpack either
            return FloatingBackpackItem.isBackpack(stack) || pnnh.mcfloatingbackpack.item.CombinedBackpackItem.isCombined(stack);
        }

        @Override
        public boolean hasContainerData(ItemStack stack) {
            return stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS);
        }

        @Override
        public boolean hasOpaqueData(ItemStack stack) {
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue; // a component the stack lost
                }
                ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
                if (id == null || !"minecraft".equals(id.getNamespace()) || "custom_data".equals(id.getPath())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<ItemStack> children(ItemStack stack) {
            List<ItemStack> inside = new ArrayList<>();
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                container.nonEmptyItems().forEach(inside::add);
            }
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                bundle.items().forEach(inside::add);
            }
            return inside;
        }

        @Override
        public boolean mentionsBackpack(ItemStack stack) {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return true; // cannot write it out: the safe answer
                }
                RegistryAccess access = server.registryAccess();
                Tag tag = ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, access), stack).getOrThrow();
                String written = tag.toString();
                for (String id : BACKPACK_IDS) {
                    if (written.contains(id)) {
                        return true;
                    }
                }
                return false;
            } catch (RuntimeException failed) {
                return true;
            }
        }
    };

    private BackpackNesting() {
    }

    private static List<String> backpackIds() {
        List<String> ids = new ArrayList<>();
        for (BackpackTier tier : BackpackTier.values()) {
            ids.add(MCFloatingBackpack.MOD_ID + ":" + tier.id());
            ids.add(MCFloatingBackpack.MOD_ID + ":" + pnnh.mcfloatingbackpack.item.CombinedBackpackItem.idOf(tier)); // 065
        }
        return List.copyOf(ids);
    }

    /** Whether {@code stack} may go into a floating backpack as a new arrival. */
    public static Verdict check(ItemStack stack) {
        return NestingRules.check(PROBE, stack);
    }

    public static boolean isRisky(ItemStack stack) {
        return check(stack) != Verdict.OK;
    }

    /**
     * Whether putting {@code held} into {@code backpack} must be refused: it is nesting and it is not what the player just took out
     * of this same backpack.
     */
    public static Verdict refusal(ServerPlayer player, BackpackRef backpack, ItemStack held) {
        Verdict verdict = check(held);
        if (verdict != Verdict.OK && ORIGINS.matches(player.getUUID(), backpack, held)) {
            return Verdict.OK;
        }
        return verdict;
    }

    /** A refusal that no origin can lift (the trash can). */
    public static Verdict refusalAlways(ItemStack held) {
        return check(held);
    }

    public static void rememberTakenOut(ServerPlayer player, BackpackRef backpack, ItemStack stack) {
        if (isRisky(stack)) {
            ORIGINS.remember(player.getUUID(), backpack, stack.copy());
        }
    }

    private static final java.util.Set<String> NOTED = new java.util.HashSet<>();

    /**
     * 064: at most one INFO line for a backpack that holds a floating backpack (or an item with one inside) put there by an older
     * version: how many, and where. Once for each backpack and player until the player leaves. Nothing is moved or changed.
     */
    public static void noteLegacy(ServerPlayer player, BackpackRef ref, pnnh.mcfloatingbackpack.inventory.BackpackInventory inventory) {
        // the backpack's own number tells two backpacks apart; a reference does not (the main hand is "the main hand" for any of them)
        pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity identity =
                ref.getStack(player).get(pnnh.mcfloatingbackpack.inventory.ModDataComponents.BACKPACK_IDENTITY.get());
        String key = player.getUUID() + ":" + (identity == null ? ref.toString() : identity.uuid().toString());
        if (NOTED.contains(key)) {
            return;
        }
        try {
            int main = 0;
            for (int i = 0; i < inventory.size(); i++) {
                if (isRisky(inventory.getStackInSlot(i))) {
                    main++;
                }
            }
            int tools = 0;
            for (pnnh.mcfloatingbackpack.inventory.PanelArea area : pnnh.mcfloatingbackpack.inventory.PanelArea.values()) {
                if (area.isTool()) {
                    for (ItemStack stack : inventory.toolStacks(area)) {
                        if (isRisky(stack)) {
                            tools++;
                        }
                    }
                }
            }
            int trash = 0;
            for (ItemStack group : inventory.trash().groups()) {
                if (isRisky(group)) {
                    trash++;
                }
            }
            NOTED.add(key);
            if (main + tools + trash > 0) {
                MCFloatingBackpack.LOGGER.info(
                        "Backpack {} of {} holds floating backpacks (or items carrying one) left by an older version: {} in the main area, {} in the tool areas, {} in the trash can. They stay and can be taken out, but no more go in.",
                        ref, player.getGameProfile().getName(), main, tools, trash);
            }
        } catch (RuntimeException failed) {
            MCFloatingBackpack.LOGGER.debug("Could not look for nested floating backpacks in {}", ref, failed);
        }
    }

    public static void forgetNoted(UUID player) {
        NOTED.removeIf(key -> key.startsWith(player.toString() + ":"));
    }

    public static void forget(UUID player) {
        ORIGINS.forget(player);
    }

    public static void clear() {
        ORIGINS.clear();
        NOTED.clear();
    }

    /** The action-bar line for a refusal (nothing for {@link Verdict#OK}). */
    public static void tell(ServerPlayer player, Verdict verdict) {
        switch (verdict) {
            case IS_BACKPACK -> player.displayClientMessage(Component.translatable("message.mcfloatingbackpack.nesting.refused"), true);
            case CONTAINS_BACKPACK -> player.displayClientMessage(Component.translatable("message.mcfloatingbackpack.nesting.contains"), true);
            default -> {
            }
        }
    }
}
