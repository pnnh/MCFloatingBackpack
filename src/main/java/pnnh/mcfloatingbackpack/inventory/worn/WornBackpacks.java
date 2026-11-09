package pnnh.mcfloatingbackpack.inventory.worn;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 032: the one place that knows where a player's floating backpack is, in one fixed order (see {@link BackpackSearch}): a hand, then
 * the first one worn, then the inventory. The back layer and {@link BackpackRef#resolve} both ask here, so the backpack that is drawn
 * on the back and the backpack the panel key opens are chosen by the same rule, on the server and on a client alike.
 *
 * <p>What is worn is a list of {@link WornSource}s in their order. The chest slot is the first; 033 and 034 add an accessories mod's
 * back slot each, from the mod's constructor (both sides run it), with a fixed {@link WornSource#order()}, so the order does not depend
 * on which mod registers first.
 */
public final class WornBackpacks {
    /** The first backpack worn, as the back layer needs it: the stack, and whether the player switched drawing off for its slot. */
    public record Found(ItemStack stack, boolean hidden) {
    }

    private static final BackpackSearch.Probe<LivingEntity, ItemStack> PROBE = new BackpackSearch.Probe<>() {
        @Override
        public ItemStack mainHand(LivingEntity entity) {
            return entity.getMainHandItem();
        }

        @Override
        public ItemStack offHand(LivingEntity entity) {
            return entity.getOffhandItem();
        }

        @Override
        public int inventorySlots(LivingEntity entity) {
            // the main inventory and the hotbar; armor and the off hand are beyond 36
            return entity instanceof Player ? 36 : 0;
        }

        @Override
        public ItemStack inventory(LivingEntity entity, int index) {
            return entity instanceof Player player ? player.getInventory().getItem(index) : ItemStack.EMPTY;
        }

        @Override
        public boolean isBackpack(ItemStack stack) {
            return FloatingBackpackItem.isBackpack(stack);
        }
    };

    private static final BackpackSearch<LivingEntity, ItemStack> SEARCH = new BackpackSearch<>(
            PROBE,
            BackpackRef.Source.MAIN_HAND.ordinal(),
            BackpackRef.Source.OFF_HAND.ordinal(),
            BackpackRef.Source.INVENTORY.ordinal());

    static {
        SEARCH.register(new ChestWornSource());
    }

    private WornBackpacks() {
    }

    /** Adds a source of worn backpacks (033, 034); call it from the mod's constructor. */
    public static void register(WornSource<LivingEntity, ItemStack> source) {
        SEARCH.register(source);
    }

    /** Main hand, off hand, the first backpack worn, then the inventory; {@code null} if the player has none. */
    public static BackpackSearch.Hit resolve(LivingEntity entity) {
        return SEARCH.resolve(entity);
    }

    /** The first backpack the entity wears (any player, on the client too), or {@code null}. A hand or the inventory is not worn. */
    public static Found first(LivingEntity entity) {
        BackpackSearch.Hit hit = SEARCH.firstWorn(entity);
        if (hit == null) {
            return null;
        }
        WornSource<LivingEntity, ItemStack> source = SEARCH.activeSource(hit.sourceId());
        return source == null ? null : new Found(source.get(entity, hit.slot()), hit.hidden());
    }

    /** The worn source with this number, if it is registered and active; {@code null} for a stale reference or a mod that is not there. */
    public static WornSource<LivingEntity, ItemStack> activeSource(int id) {
        return SEARCH.activeSource(id);
    }

    // The three below take a Player and not a LivingEntity on purpose: BackpackRef calls them, and a class that stays free of
    // LivingEntity in its code can be loaded by the plain unit tests (the JVM's verifier loads a type to check it is assignable).

    /** Main hand, off hand, the first backpack worn, then the inventory (see {@link #resolve(LivingEntity)}). */
    public static BackpackSearch.Hit resolvePlayer(Player player) {
        return SEARCH.resolve(player);
    }

    /** What a reference into a worn source points at: the stack, or the empty stack for a source that is not there or a slot out of range. */
    public static ItemStack get(Player player, int sourceId, int slot) {
        WornSource<LivingEntity, ItemStack> source = SEARCH.activeSource(sourceId);
        return source == null ? ItemStack.EMPTY : source.get(player, slot);
    }

    /** Writes a stack back into a worn source; nothing happens for a source that is not there. */
    public static void set(Player player, int sourceId, int slot, ItemStack stack) {
        WornSource<LivingEntity, ItemStack> source = SEARCH.activeSource(sourceId);
        if (source != null) {
            source.set(player, slot, stack);
        }
    }
}
