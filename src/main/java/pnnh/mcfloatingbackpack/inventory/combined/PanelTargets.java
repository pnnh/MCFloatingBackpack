package pnnh.mcfloatingbackpack.inventory.combined;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 065: which backpack a request from the panel is for. Outside a combined session it is exactly {@link BackpackRef#resolve} (the backpack the B
 * key opens); inside one it is the tab's backpack, as long as that is still exactly what was chosen (see {@link PanelTargetRules}), and when it
 * is not, the B key's backpack, which the stale-panel guard of {@link BackpackGate} then notices.
 *
 * <p>Only panel requests ask here. What belongs to "the backpack the B key opens" (the auto pickup, the mobile tools, the jukebox, the back
 * layer...) keeps asking {@link BackpackRef#resolve}. A test keeps both lists honest.
 */
public final class PanelTargets {
    private PanelTargets() {
    }

    public static BackpackRef resolve(Player player) {
        CombinedSessions.Session session = CombinedSessions.get(player.getUUID());
        if (session != null && decision(player, session) == PanelTargetRules.Decision.TARGET) {
            return session.target();
        }
        return BackpackRef.resolve(player);
    }

    /** Whether the session's target still holds (looks at the target alone: the member table is not worked out). */
    static PanelTargetRules.Decision decision(Player player, CombinedSessions.Session session) {
        ItemStack stack = session.target().getStack(player);
        boolean backpack = FloatingBackpackItem.isBackpack(stack);
        return PanelTargetRules.decide(
                true,
                CombinedConfig.enabled(),
                WornCombined.tier(player),
                backpack,
                backpack ? CombinedCandidates.identityOf(stack) : null,
                session.identity(),
                backpack ? FloatingBackpackItem.tierOf(stack).ordinal() : 0,
                backpack && BackpackGate.isProtected(stack));
    }
}
