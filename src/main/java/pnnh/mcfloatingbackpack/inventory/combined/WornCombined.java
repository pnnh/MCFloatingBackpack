package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch;
import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.CombinedBackpackItem;

/** 065: which combined backpack a player wears counts (see {@link CombinedWorn}); asked on the server and on a client alike. */
public final class WornCombined {
    private WornCombined() {
    }

    /** The tier (ordinal) of the combined backpack that counts, or -1 if the player wears none. */
    public static int tier(Player player) {
        List<Integer> tiers = new ArrayList<>();
        for (BackpackSearch.Hit hit : WornBackpacks.allCombinedWornPlayer(player)) {
            ItemStack stack = WornBackpacks.get(player, hit.sourceId(), hit.slot());
            pnnh.mcfloatingbackpack.item.BackpackTier tier = CombinedBackpackItem.tierOf(stack);
            tiers.add(tier == null ? -1 : tier.ordinal());
        }
        int index = CombinedWorn.pick(tiers);
        return index < 0 ? -1 : tiers.get(index);
    }
}
