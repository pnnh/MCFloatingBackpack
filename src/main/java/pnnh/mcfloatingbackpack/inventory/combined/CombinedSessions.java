package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ContainerMenuAccess;
import pnnh.mcfloatingbackpack.inventory.ContainerToBackpack;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.network.AllSync;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;
import pnnh.mcfloatingbackpack.network.CombinedMembersPayload;

/**
 * 065: the combined panels that are open, one session per player, in memory only (nothing is stored; a restart or leaving ends them). A
 * session knows which backpack the panel is on (the target and the identity it had when chosen), the tier of the combined backpack it was
 * opened with, and the member table last sent, so that a new table is sent only when it differs.
 *
 * <p>Everything here runs on the server's main thread. Opening and choosing a tab go through {@code BackpackGate.acquire} (the one way into a
 * backpack, which also gives it an identity), then the existing {@link BackpackNetwork#sendSession} puts that backpack on the panel. The
 * member table is worked out when a tab is chosen and once a second for a panel that is open, never for each request.
 */
public final class CombinedSessions {
    /**
     * The backpack the panel is on (a single tab), or the "all" tab's state (066), and what the server last told the client. On a single tab
     * {@code all} is null; on the "all" tab {@code target} and {@code identity} are null. {@code lastEpoch} only ever grows over the session.
     */
    public static final class Session {
        private final BackpackRef target;
        private final UUID identity;
        private final int combinedTier;
        private final CombinedMembers.Result<BackpackRef> lastSent;
        private final AllState all;
        private final int lastEpoch;

        private Session(
                BackpackRef target, UUID identity, int combinedTier, CombinedMembers.Result<BackpackRef> lastSent, AllState all, int lastEpoch) {
            this.target = target;
            this.identity = identity;
            this.combinedTier = combinedTier;
            this.lastSent = lastSent;
            this.all = all;
            this.lastEpoch = lastEpoch;
        }

        public static Session tab(BackpackRef target, UUID identity, int combinedTier, CombinedMembers.Result<BackpackRef> lastSent, int lastEpoch) {
            return new Session(target, identity, combinedTier, lastSent, null, lastEpoch);
        }

        public static Session all(int combinedTier, CombinedMembers.Result<BackpackRef> lastSent, AllState all) {
            return new Session(null, null, combinedTier, lastSent, all, all.epoch());
        }

        public BackpackRef target() {
            return target;
        }

        public UUID identity() {
            return identity;
        }

        public int combinedTier() {
            return combinedTier;
        }

        public CombinedMembers.Result<BackpackRef> lastSent() {
            return lastSent;
        }

        /** The "all" tab's state, or null on a single backpack's tab. */
        public AllState all() {
            return all;
        }

        public boolean inAll() {
            return all != null;
        }

        public int lastEpoch() {
            return lastEpoch;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private CombinedSessions() {
    }

    public static Session get(UUID player) {
        return SESSIONS.get(player);
    }

    public static boolean has(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** 066: whether the player's combined panel is on the "all" tab. */
    public static boolean inAll(UUID player) {
        Session session = SESSIONS.get(player);
        return session != null && session.inAll();
    }

    /** The player's panel is closed, or they left: forget the session, nothing is sent. */
    public static void end(UUID player) {
        SESSIONS.remove(player);
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** Ends the session and tells the client the combined panel is over (the ordinary panel goes on, or the stale-panel guard closes it). */
    public static void endAndTell(ServerPlayer player) {
        // 066: what the "all" tab's cursor holds goes back first (to the members, the inventory, the ground)
        flush(player);
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            PacketDistributor.sendToPlayer(player, CombinedMembersPayload.inactive());
            if (session.inAll()) {
                // the panel was showing the "all" grid, not a backpack the ordinary panel can go on with: close it
                BackpackNetwork.sendSession(player, false, null);
            }
        }
    }

    private static CombinedMembers.Result<BackpackRef> members(ServerPlayer player, int tier) {
        return CombinedMembers.compute(CombinedCandidates.of(player), tier);
    }

    private static int indexOf(CombinedMembers.Result<BackpackRef> result, BackpackRef ref) {
        for (int i = 0; i < result.members().size(); i++) {
            if (result.members().get(i).ref().equals(ref)) {
                return i;
            }
        }
        return -1;
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static void sendMembers(ServerPlayer player, int tier, BackpackRef target, CombinedMembers.Result<BackpackRef> result) {
        PacketDistributor.sendToPlayer(player, CombinedMembersPayload.of(tier, target == null ? -1 : indexOf(result, target), result));
    }

    /** The combined key: opens the combined panel on the backpack the B key would open (or the first tab). */
    public static void open(ServerPlayer player) {
        if (!CombinedConfig.enabled()) {
            tell(player, "message.mcfloatingbackpack.combined.disabled");
            return;
        }
        int tier = WornCombined.tier(player);
        if (tier < 0) {
            tell(player, "message.mcfloatingbackpack.combined.need_worn");
            return;
        }
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        if (result.members().isEmpty()) {
            tell(player, "message.mcfloatingbackpack.combined.no_members");
            return;
        }
        // 066: the combined key opens the "all" tab
        DiagLog.record(player.getUUID(), "combined open: tier " + tier + ", " + result.members().size() + " members, on the all tab");
        Session before = SESSIONS.get(player.getUUID());
        if (before != null) {
            flush(player);
        }
        if (!activateAll(player, tier, before == null ? 0 : before.lastEpoch())) {
            tell(player, "message.mcfloatingbackpack.combined.no_members");
        }
    }

    /** The "all" tab was clicked. */
    public static void selectAll(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        int tier = WornCombined.tier(player);
        if (session == null || !CombinedConfig.enabled() || tier < 0) {
            endAndTell(player);
            return;
        }
        if (session.inAll()) {
            resend(player, session, tier);
            return;
        }
        if (cursorHeld(player, session)) {
            tell(player, "message.mcfloatingbackpack.combined.empty_cursor");
            resend(player, session, tier);
            return;
        }
        DiagLog.record(player.getUUID(), "combined select: the all tab");
        if (!activateAll(player, tier, session.lastEpoch())) {
            resend(player, session, tier);
        }
    }

    /**
     * Puts the panel on the "all" tab: every member is given an identity (the one way into a backpack), the epoch's table is fixed, the table is
     * sent and then each member's main area. False if there is no member that can be used.
     */
    private static boolean activateAll(ServerPlayer player, int tier, int previousEpoch) {
        CombinedMembers.Result<BackpackRef> first = members(player, tier);
        for (CombinedMembers.Member<BackpackRef> member : first.members()) {
            BackpackGate.acquire(player, member.ref());
        }
        CombinedMembers.Result<BackpackRef> fresh = members(player, tier);
        List<CombinedMembers.Member<BackpackRef>> usable = new ArrayList<>();
        List<AllState.Member> states = new ArrayList<>();
        for (CombinedMembers.Member<BackpackRef> member : fresh.members()) {
            if (member.identity() == null) {
                continue;
            }
            BackpackInventory inventory = member.ref().loadInventory(player);
            states.add(new AllState.Member(member.ref(), member.identity(), member.tier(), inventory.size()));
            usable.add(member);
        }
        if (usable.isEmpty()) {
            return false;
        }
        CombinedMembers.Result<BackpackRef> shown = shownOf(fresh);
        AllState all = new AllState(previousEpoch + 1, states);
        // lastSent is what the table looks like unfiltered, so that the once-a-second comparison does not see a difference that is not there
        SESSIONS.put(player.getUUID(), Session.all(tier, fresh, all));
        PacketDistributor.sendToPlayer(player, CombinedMembersPayload.of(tier, -1, all.epoch(), shown));
        if (!AllSync.sendAll(player, all)) {
            DiagLog.record(player.getUUID(), "combined: a member's main area is over the packet limit; the combined panel ends");
            endAndTell(player);
            return false;
        }
        return true;
    }

    /** The table as the "all" tab shows it: the members that have an identity (every one, but a backpack that could not be given one). */
    private static CombinedMembers.Result<BackpackRef> shownOf(CombinedMembers.Result<BackpackRef> table) {
        List<CombinedMembers.Member<BackpackRef>> usable = new ArrayList<>();
        for (CombinedMembers.Member<BackpackRef> member : table.members()) {
            if (member.identity() != null) {
                usable.add(member);
            }
        }
        return new CombinedMembers.Result<>(usable, table.skippedTier(), table.skippedNewer(), table.skippedOverCap());
    }

    /**
     * The "all" tab's members, sizes or rows are not what the client has (or a request did not fit the epoch): its cursor goes back to where it can,
     * and the tab begins again with a new epoch.
     *
     * @return true if the "all" tab goes on; false if there was none or it ended
     */
    public static boolean resyncAll(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.inAll()) {
            return false;
        }
        int tier = WornCombined.tier(player);
        if (!CombinedConfig.enabled() || tier < 0) {
            endAndTell(player);
            return false;
        }
        flush(player);
        if (!activateAll(player, tier, session.lastEpoch())) {
            DiagLog.record(player.getUUID(), "combined: no backpack left for the all tab; the combined panel ends");
            endAndTell(player);
            return false;
        }
        return true;
    }

    /**
     * Gives up what the "all" tab's cursor holds: into the members one after the other (each by its own rules), then the player's inventory, then
     * onto the ground. Nothing is lost. Safe to call at any time and for a player with no "all" tab (it does nothing then).
     */
    public static void flush(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.inAll()) {
            return;
        }
        AllState all = session.all();
        ItemStack held = all.cursor();
        if (held.isEmpty()) {
            return;
        }
        all.setCursor(ItemStack.EMPTY);
        int combinedTier = WornCombined.tier(player);
        ContainerToBackpack.Fallback<ItemStack> fallback = new ContainerMenuAccess(player).fallback();
        List<java.util.function.UnaryOperator<ItemStack>> acceptors = new ArrayList<>();
        for (AllState.Member member : all.members()) {
            acceptors.add(stack -> {
                ItemStack at = member.ref.getStack(player);
                boolean backpack = FloatingBackpackItem.isBackpack(at);
                if (!AllRequestRules.memberOk(
                        backpack,
                        backpack ? CombinedCandidates.identityOf(at) : null,
                        member.identity,
                        backpack ? FloatingBackpackItem.tierOf(at).ordinal() : 0,
                        combinedTier,
                        backpack && BackpackGate.isProtected(at))) {
                    return stack;
                }
                Optional<BackpackInventory> inventory = BackpackGate.acquire(player, member.ref);
                if (inventory.isEmpty()) {
                    return stack;
                }
                ItemStack left = inventory.get().insertIntoMain(stack.copy());
                if (left.getCount() != stack.getCount()) {
                    member.ref.saveInventory(player, inventory.get());
                }
                return left;
            });
        }
        CursorFlush.Result<ItemStack> result =
                CursorFlush.flush(held, acceptors, fallback::toInventory, ItemStack::isEmpty, ItemStack::getCount);
        if (!result.dropped().isEmpty()) {
            fallback.drop(result.dropped());
        }
        int toMembers = result.placedInMembers().stream().mapToInt(Integer::intValue).sum();
        DiagLog.record(player.getUUID(), "combined: the all tab's cursor " + DiagLog.stack(held) + " goes back: " + toMembers + " into backpacks, "
                + result.placedInInventory() + " into the inventory, " + (result.dropped().isEmpty() ? 0 : result.dropped().getCount()) + " on the ground");
        if (result.placedInInventory() > 0 || !result.dropped().isEmpty()) {
            tell(player, "message.mcfloatingbackpack.combined.flushed");
        }
        player.getInventory().setChanged();
    }

    /** Puts the panel on member {@code first} (or, if that cannot be opened, the next one that can). */
    private static boolean activateFirstThatWorks(ServerPlayer player, int tier, CombinedMembers.Result<BackpackRef> result, int first) {
        int count = result.members().size();
        for (int step = 0; step < count; step++) {
            int index = (first + step) % count;
            if (activate(player, tier, result.members().get(index).ref())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makes {@code ref} the session's target: {@code acquire} it (which gives it an identity), record it, tell the client the table, then put the
     * panel on it. False if the backpack cannot be used (not a backpack any more, or saved by a newer version).
     */
    private static boolean activate(ServerPlayer player, int tier, BackpackRef ref) {
        Optional<BackpackInventory> inventory = BackpackGate.acquire(player, ref);
        if (inventory.isEmpty()) {
            return false;
        }
        UUID identity = CombinedCandidates.identityOf(ref.getStack(player));
        CombinedMembers.Result<BackpackRef> fresh = members(player, tier);
        Session previous = SESSIONS.get(player.getUUID());
        SESSIONS.put(player.getUUID(), Session.tab(ref, identity, tier, fresh, previous == null ? 0 : previous.lastEpoch()));
        // the table first, then the panel's contents: the client takes the panel as the tab it was just told about
        sendMembers(player, tier, ref, fresh);
        BackpackNetwork.sendSession(player, true, ref);
        return true;
    }

    /** A tab was clicked. */
    public static void select(ServerPlayer player, int index, Optional<UUID> seenIdentity) {
        Session session = SESSIONS.get(player.getUUID());
        int tier = WornCombined.tier(player);
        if (session == null || !CombinedConfig.enabled() || tier < 0) {
            endAndTell(player);
            return;
        }
        if (cursorHeld(player, session)) {
            tell(player, "message.mcfloatingbackpack.combined.empty_cursor");
            resend(player, session, tier);
            return;
        }
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        if (index < 0 || index >= result.members().size()) {
            resend(player, session, tier);
            return;
        }
        CombinedMembers.Member<BackpackRef> member = result.members().get(index);
        if (!Objects.equals(member.identity(), seenIdentity.orElse(null))) {
            resend(player, session, tier);
            return;
        }
        DiagLog.record(player.getUUID(), "combined select: tab " + index + " " + member.ref());
        if (session.inAll()) {
            // 066: leaving the "all" tab: what its cursor holds goes back first
            flush(player);
        }
        if (!activate(player, tier, member.ref())) {
            resend(player, session, tier);
        }
    }

    /** What is held on the panel's cursor or on a container menu's. */
    private static boolean cursorHeld(ServerPlayer player, Session session) {
        if (!player.containerMenu.getCarried().isEmpty()) {
            return true;
        }
        if (session.inAll()) {
            // the "all" tab's own cursor is given up by the switch (flushed), it does not stop it
            return false;
        }
        ItemStack stack = session.target().getStack(player);
        return pnnh.mcfloatingbackpack.item.FloatingBackpackItem.isBackpack(stack)
                && !session.target().loadInventory(player).getCursor().isEmpty();
    }

    /** Tells the client the table as it is now, for a request that was refused (so it leaves its "loading" state). */
    private static void resend(ServerPlayer player, Session session, int tier) {
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        if (session.inAll()) {
            // the "all" tab stays as it is (same epoch): only the client's "loading" is ended by the table that says it is on "all"
            PacketDistributor.sendToPlayer(player, CombinedMembersPayload.of(tier, -1, session.all().epoch(), shownOf(session.lastSent())));
            return;
        }
        SESSIONS.put(player.getUUID(), Session.tab(session.target(), session.identity(), tier, result, session.lastEpoch()));
        sendMembers(player, tier, session.target(), result);
    }

    /**
     * Once a second for a player with a combined panel open (called by {@code BackpackGate.tickOpenSession}). Ends the session if the combined
     * backpack is gone or switched off, finds the backpack again if it moved, and sends the table when it changed.
     *
     * @return true if the session is going on and nothing else needs doing; false if there is no session, or it just ended (the ordinary panel
     *         check then runs)
     */
    public static boolean tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        int tier = WornCombined.tier(player);
        if (!CombinedConfig.enabled() || tier < 0) {
            endAndTell(player);
            return false;
        }
        if (session.inAll()) {
            return tickAll(player, session, tier);
        }
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        boolean holds = PanelTargets.decision(player, session) == PanelTargetRules.Decision.TARGET && indexOf(result, session.target()) >= 0;
        if (!holds) {
            return recoverTo(player, session, tier, result);
        }
        if (!result.equals(session.lastSent()) || session.combinedTier() != tier) {
            SESSIONS.put(player.getUUID(), Session.tab(session.target(), session.identity(), tier, result, session.lastEpoch()));
            sendMembers(player, tier, session.target(), result);
        }
        return true;
    }

    /**
     * The panel's backpack is no longer what it was (moved, dropped, replaced): called by the stale-panel guard. Follows the backpack by its
     * identity if it is found once, goes to the first tab otherwise; with no backpack left the session ends.
     *
     * @return true if the combined panel was put on a backpack (the guard has nothing more to do); false if there is no session or it ended
     */
    public static boolean recover(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        int tier = WornCombined.tier(player);
        if (!CombinedConfig.enabled() || tier < 0) {
            endAndTell(player);
            return false;
        }
        if (session.inAll()) {
            return resyncAll(player);
        }
        return recoverTo(player, session, tier, members(player, tier));
    }

    /**
     * Once a second for the "all" tab: the member table (backpacks coming and going, moving, a name changed) or a member's size is not what the
     * epoch was made with: begin again with a new epoch. Otherwise each member whose stack was saved since it was sent is compared by what the
     * client shows of it, and sent again only if that differs.
     */
    private static boolean tickAll(ServerPlayer player, Session session, int tier) {
        AllState all = session.all();
        if (!members(player, tier).equals(session.lastSent()) || session.combinedTier() != tier) {
            return resyncAll(player);
        }
        for (int i = 0; i < all.members().size(); i++) {
            AllState.Member member = all.members().get(i);
            ItemStack stack = member.ref.getStack(player);
            if (!memberHolds(player, member, stack, tier)) {
                return resyncAll(player);
            }
            Object contents = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
            if (contents == member.lastContents) {
                continue;
            }
            BackpackInventory inventory = member.ref.loadInventory(player);
            if (inventory.size() != member.mainSize) {
                return resyncAll(player);
            }
            List<ItemStack> main = new ArrayList<>(inventory.size());
            for (int cell = 0; cell < inventory.size(); cell++) {
                main.add(inventory.getStackInSlot(cell).copy());
            }
            boolean changed = AllContentDiff.changed(
                    false,
                    () -> AllContentDiff.listsMatch(member.lastMain, main, ItemStack::matches),
                    () -> inventory.rowView().equals(member.lastRows),
                    () -> inventory.tags().equals(member.lastTags));
            if (changed) {
                if (!AllSync.sendMember(player, all, i, inventory)) {
                    endAndTell(player);
                    return false;
                }
            } else {
                member.lastContents = contents;
            }
        }
        return true;
    }

    /** Whether the stack at a member's place is still the backpack the epoch was made with. */
    static boolean memberHolds(ServerPlayer player, AllState.Member member, ItemStack stack, int combinedTier) {
        boolean backpack = FloatingBackpackItem.isBackpack(stack);
        return AllRequestRules.memberOk(
                backpack,
                backpack ? CombinedCandidates.identityOf(stack) : null,
                member.identity,
                backpack ? FloatingBackpackItem.tierOf(stack).ordinal() : 0,
                combinedTier,
                backpack && BackpackGate.isProtected(stack));
    }

    private static boolean recoverTo(ServerPlayer player, Session session, int tier, CombinedMembers.Result<BackpackRef> result) {
        List<UUID> identities = new ArrayList<>();
        for (CombinedMembers.Member<BackpackRef> member : result.members()) {
            identities.add(member.identity());
        }
        CombinedRecovery.Result outcome = CombinedRecovery.recover(identities, session.identity());
        if (outcome.outcome() == CombinedRecovery.Outcome.END
                || !activateFirstThatWorks(player, tier, result, outcome.index())) {
            DiagLog.record(player.getUUID(), "combined: no backpack left to show; the combined panel ends");
            endAndTell(player);
            return false;
        }
        DiagLog.record(player.getUUID(), "combined: the panel's backpack moved or went (" + outcome.outcome() + "); the panel follows");
        return true;
    }
}
