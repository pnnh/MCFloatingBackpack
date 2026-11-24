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
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
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
    /** The backpack the panel is on, and what the server last told the client. */
    public record Session(BackpackRef target, UUID identity, int combinedTier, CombinedMembers.Result<BackpackRef> lastSent) {
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
        if (SESSIONS.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, CombinedMembersPayload.inactive());
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
        PacketDistributor.sendToPlayer(player, CombinedMembersPayload.of(tier, indexOf(result, target), result));
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
        BackpackRef active = BackpackRef.resolve(player);
        int preferred = active == null ? -1 : indexOf(result, active.normalized(player.getInventory().selected));
        DiagLog.record(player.getUUID(), "combined open: tier " + tier + ", " + result.members().size() + " members, preferred " + preferred);
        if (!activateFirstThatWorks(player, tier, result, Math.max(preferred, 0))) {
            tell(player, "message.mcfloatingbackpack.combined.no_members");
        }
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
        SESSIONS.put(player.getUUID(), new Session(ref, identity, tier, fresh));
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
        if (!activate(player, tier, member.ref())) {
            resend(player, session, tier);
        }
    }

    /** What is held on the panel's cursor or on a container menu's. */
    private static boolean cursorHeld(ServerPlayer player, Session session) {
        if (!player.containerMenu.getCarried().isEmpty()) {
            return true;
        }
        ItemStack stack = session.target().getStack(player);
        return pnnh.mcfloatingbackpack.item.FloatingBackpackItem.isBackpack(stack)
                && !session.target().loadInventory(player).getCursor().isEmpty();
    }

    /** Tells the client the table as it is now, for a request that was refused (so it leaves its "loading" state). */
    private static void resend(ServerPlayer player, Session session, int tier) {
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        SESSIONS.put(player.getUUID(), new Session(session.target(), session.identity(), tier, result));
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
        CombinedMembers.Result<BackpackRef> result = members(player, tier);
        boolean holds = PanelTargets.decision(player, session) == PanelTargetRules.Decision.TARGET && indexOf(result, session.target()) >= 0;
        if (!holds) {
            return recoverTo(player, session, tier, result);
        }
        if (!result.equals(session.lastSent()) || session.combinedTier() != tier) {
            SESSIONS.put(player.getUUID(), new Session(session.target(), session.identity(), tier, result));
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
        return recoverTo(player, session, tier, members(player, tier));
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
