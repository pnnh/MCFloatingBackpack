package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers.Candidate;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers.Place;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch;
import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 065: collects the floating backpacks a player carries, in the order the member table wants them: every backpack worn (all of them, not
 * only the first), the off hand, inventory slots 0-35. The main hand is the selected inventory slot, so it is listed there and never by
 * itself. Only reads: nothing is given an identity and no stack is changed.
 */
public final class CombinedCandidates {
    /** The longest custom name sent for a tab. */
    public static final int MAX_NAME = 64;

    private CombinedCandidates() {
    }

    public static List<Candidate<BackpackRef>> of(Player player) {
        List<Candidate<BackpackRef>> found = new ArrayList<>();
        for (BackpackSearch.Hit hit : WornBackpacks.allWornPlayer(player)) {
            BackpackRef ref = new BackpackRef(BackpackRef.Source.values()[hit.sourceId()], hit.slot());
            add(found, ref, new Place(Place.Kind.WORN, hit.sourceId(), hit.slot()), ref.getStack(player));
        }
        BackpackRef off = new BackpackRef(BackpackRef.Source.OFF_HAND, -1);
        add(found, off, new Place(Place.Kind.OFF_HAND, 0, -1), off.getStack(player));
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (FloatingBackpackItem.isBackpack(stack)) {
                add(found, new BackpackRef(BackpackRef.Source.INVENTORY, i), new Place(Place.Kind.INVENTORY, 0, i), stack);
            }
        }
        return found;
    }

    private static void add(List<Candidate<BackpackRef>> found, BackpackRef ref, Place place, ItemStack stack) {
        if (!FloatingBackpackItem.isBackpack(stack)) {
            return;
        }
        found.add(new Candidate<>(
                ref, place, identityOf(stack), FloatingBackpackItem.tierOf(stack).ordinal(), nameOf(stack), BackpackGate.isProtected(stack)));
    }

    /** The backpack's identity, or {@code null} if it has none yet. */
    public static UUID identityOf(ItemStack stack) {
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity == null ? null : identity.uuid();
    }

    /** The name given in an anvil, or the empty string. */
    public static String nameOf(ItemStack stack) {
        net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            return "";
        }
        String text = name.getString();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }
}
