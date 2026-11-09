package pnnh.mcfloatingbackpack.inventory.worn;

import java.util.ArrayList;
import java.util.List;

/**
 * 032: where a player's floating backpack is, in one fixed order: a hand holding one first, then what is worn (the sources of
 * {@link WornSource} in their order; the first backpack found counts), then the inventory. Pure logic with no Minecraft types, so
 * the order is tested with fake players; the real one uses {@code Player} and {@code ItemStack}.
 *
 * <p>The order is the same on the server and on a client, which each work it out alone from what the player carries; a backpack
 * after the first worn one is just an item in a slot (not drawn, not opened by the panel key until the ones before it are gone).
 */
public final class BackpackSearch<P, S> {
    public enum Kind {
        MAIN_HAND,
        OFF_HAND,
        WORN,
        INVENTORY
    }

    /**
     * Where a backpack was found.
     *
     * @param sourceId the number of the place on the wire (a hand's or the inventory's own, or the worn source's)
     * @param slot the slot within it: -1 for a hand, the inventory index, or the slot within the worn source
     * @param hidden for a worn backpack: the player has switched off drawing its slot (it still counts as worn)
     */
    public record Hit(Kind kind, int sourceId, int slot, boolean hidden) {
    }

    /** What the search needs to know about a player outside the worn sources. */
    public interface Probe<P, S> {
        S mainHand(P player);

        S offHand(P player);

        /** How many inventory slots are searched (the main inventory and hotbar: 36). */
        int inventorySlots(P player);

        S inventory(P player, int index);

        boolean isBackpack(S stack);
    }

    private final Probe<P, S> probe;
    private final int mainHandId;
    private final int offHandId;
    private final int inventoryId;
    private volatile List<WornSource<P, S>> sources = List.of();

    public BackpackSearch(Probe<P, S> probe, int mainHandId, int offHandId, int inventoryId) {
        this.probe = probe;
        this.mainHandId = mainHandId;
        this.offHandId = offHandId;
        this.inventoryId = inventoryId;
    }

    /** Adds a worn source in its place by {@link WornSource#order()} (after the ones of the same order). Each id may be used once. */
    public synchronized void register(WornSource<P, S> source) {
        for (WornSource<P, S> existing : sources) {
            if (existing.id() == source.id()) {
                throw new IllegalArgumentException("worn source id " + source.id() + " is already registered");
            }
        }
        List<WornSource<P, S>> next = new ArrayList<>(sources);
        int at = next.size();
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).order() > source.order()) {
                at = i;
                break;
            }
        }
        next.add(at, source);
        sources = List.copyOf(next);
    }

    /** The worn source with this number if it is registered and active; {@code null} otherwise (a stale reference, a mod that is not there). */
    public WornSource<P, S> activeSource(int id) {
        for (WornSource<P, S> source : sources) {
            if (source.id() == id) {
                return source.active() ? source : null;
            }
        }
        return null;
    }

    /** The first backpack worn, or {@code null}. */
    public Hit firstWorn(P player) {
        for (WornSource<P, S> source : sources) {
            if (!source.active()) {
                continue;
            }
            int slots = source.slotCount(player);
            for (int slot = 0; slot < slots; slot++) {
                if (probe.isBackpack(source.get(player, slot))) {
                    return new Hit(Kind.WORN, source.id(), slot, source.hiddenRender(player, slot));
                }
            }
        }
        return null;
    }

    /** Main hand, then off hand, then the first backpack worn, then the inventory (the lowest index); {@code null} if there is none. */
    public Hit resolve(P player) {
        if (probe.isBackpack(probe.mainHand(player))) {
            return new Hit(Kind.MAIN_HAND, mainHandId, -1, false);
        }
        if (probe.isBackpack(probe.offHand(player))) {
            return new Hit(Kind.OFF_HAND, offHandId, -1, false);
        }
        Hit worn = firstWorn(player);
        if (worn != null) {
            return worn;
        }
        int slots = probe.inventorySlots(player);
        for (int i = 0; i < slots; i++) {
            if (probe.isBackpack(probe.inventory(player, i))) {
                return new Hit(Kind.INVENTORY, inventoryId, i, false);
            }
        }
        return null;
    }
}
