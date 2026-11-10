package pnnh.mcfloatingbackpack.diag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Looks at the main hand of a player every tick and says when a backpack comes into it or goes out of it. When the selected
 * hotbar slot is the same as a moment ago and the stack in it is another one, something replaced the stack (the player moving the
 * selection is not that): that is the one worth a warning, with the time and where every backpack of the player is now.
 *
 * <p>One instance per side (the server has one for all its players, the client one for the local player). Only the thread of
 * that side's tick calls it.
 */
public final class MainHandTrace {
    private record Seen(int selected, String key, String text) {
    }

    /** What {@link #observe} found; {@code sameSlot} is true when the selection did not move. */
    public record Change(boolean sameSlot, String text) {
    }

    private final Map<UUID, Seen> last = new HashMap<>();

    public void forget(UUID player) {
        last.remove(player);
    }

    private static String key(ItemStack stack) {
        // The item and the identity, never the count or the contents (those change with every panel click).
        String text = DiagLog.stack(stack);
        int x = text.indexOf(" x");
        if (x < 0) {
            return text;
        }
        int hash = text.indexOf(" #");
        return text.substring(0, x) + (hash < 0 ? "" : text.substring(hash));
    }

    /** @return null when nothing a backpack is involved in changed since the last look */
    public Change observe(UUID player, int selected, ItemStack hand) {
        String key = key(hand);
        Seen before = last.put(player, new Seen(selected, key, DiagLog.stack(hand)));
        if (before == null || (before.selected == selected && before.key.equals(key))) {
            return null;
        }
        if (!FloatingBackpackItem.isBackpack(hand) && !before.key.contains("floating_backpack")) {
            return null;
        }
        boolean sameSlot = before.selected == selected;
        return new Change(
                sameSlot,
                "main hand: slot " + before.selected + " " + before.text + " -> slot " + selected + " " + DiagLog.stack(hand)
                        + (sameSlot ? "  [the stack in the selected slot was replaced]" : ""));
    }

    /** Every backpack in the vanilla inventory and where it is: {@code 0=leather#ab12cd34 7=gold#...}. */
    public static String backpacks(Inventory inventory) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (FloatingBackpackItem.isBackpack(stack)) {
                out.append(out.length() == 0 ? "" : " ").append(i).append('=').append(DiagLog.stack(stack).replace("mcfloatingbackpack:", ""));
            }
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = inventory.armor.get(i);
            if (FloatingBackpackItem.isBackpack(stack)) {
                out.append(out.length() == 0 ? "" : " ").append("armor").append(i).append('=').append(DiagLog.stack(stack).replace("mcfloatingbackpack:", ""));
            }
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            ItemStack stack = inventory.offhand.get(i);
            if (FloatingBackpackItem.isBackpack(stack)) {
                out.append(out.length() == 0 ? "" : " ").append("offhand=").append(DiagLog.stack(stack).replace("mcfloatingbackpack:", ""));
            }
        }
        return out.length() == 0 ? "none" : out.toString();
    }
}
