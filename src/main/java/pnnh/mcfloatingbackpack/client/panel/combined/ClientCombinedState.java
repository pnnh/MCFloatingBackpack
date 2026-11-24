package pnnh.mcfloatingbackpack.client.panel.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.network.CombinedMembersPayload;

/**
 * 065: what the client knows about the combined panel: whether it is on, the tabs (the table the server sent), which is selected, the counts
 * of backpacks left out, whether the panel is waiting for the contents of a tab just chosen, and how far the tab strip is scrolled. Only the
 * server's {@link CombinedMembersPayload} changes the tabs; {@link ClientBackpackView} stays the mirror of the selected tab's backpack.
 *
 * <p>While {@link #loading()} no request goes to the server: the panel still shows the previous tab, and a click on it would reach the
 * backpack of the new one.
 */
public final class ClientCombinedState {
    private static final ClientCombinedState INSTANCE = new ClientCombinedState();
    private static final long LOADING_TIMEOUT_MS = 5000L;

    /** A tab. */
    public record MemberView(BackpackRef ref, UUID identity, int tier, String name) {
    }

    private boolean active;
    private List<MemberView> members = List.of();
    private int selected = -1;
    private int combinedTier;
    private int skippedTier;
    private int skippedNewer;
    private int skippedOverCap;
    private boolean loading;
    private long loadingSinceMs;
    private boolean revealSelected;
    private int tabScroll;
    private BackpackRef bActive;
    private int bActiveTick = Integer.MIN_VALUE;

    private ClientCombinedState() {
    }

    public static ClientCombinedState get() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public List<MemberView> members() {
        return members;
    }

    public int selected() {
        return selected;
    }

    public MemberView selectedMember() {
        return selected >= 0 && selected < members.size() ? members.get(selected) : null;
    }

    public int combinedTier() {
        return combinedTier;
    }

    public int skippedTier() {
        return skippedTier;
    }

    public int skippedNewer() {
        return skippedNewer;
    }

    public int skippedOverCap() {
        return skippedOverCap;
    }

    public int skippedTotal() {
        return skippedTier + skippedNewer + skippedOverCap;
    }

    public boolean loading() {
        return loading;
    }

    /** The panel asked for a tab's contents: no request goes out until they arrive (or, if the server never answers, a few seconds pass). */
    public void beginLoading() {
        loading = true;
        loadingSinceMs = System.currentTimeMillis();
    }

    /** Gives up waiting after 5 seconds, so a lost answer cannot leave the panel dead. */
    public void expireLoading() {
        if (loading && System.currentTimeMillis() - loadingSinceMs > LOADING_TIMEOUT_MS) {
            loading = false;
        }
    }

    /** True once after the selection changed: the tab strip scrolls the selected tab into view. */
    public boolean takeRevealSelected() {
        boolean was = revealSelected;
        revealSelected = false;
        return was;
    }

    /**
     * The backpack the B key would open, as one reference with the member table's (the main hand is the selected inventory slot); worked out
     * at most once a tick. Null if there is none. Not from the server: scrolling the hotbar changes it without the table changing.
     */
    public BackpackRef bActiveRef() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        int tick = minecraft.player.tickCount;
        if (tick != bActiveTick) {
            BackpackRef ref = BackpackRef.resolve(minecraft.player);
            bActive = ref == null ? null : ref.normalized(minecraft.player.getInventory().selected);
            bActiveTick = tick;
        }
        return bActive;
    }

    /** Whether the backpack of tab {@code member} is the one the B key opens (which the mobile tools and the auto pickup follow). */
    public boolean isBActive(MemberView member) {
        BackpackRef ref = bActiveRef();
        return member != null && ref != null && ref.equals(member.ref());
    }

    /** Whether the combined panel is on a backpack other than the one the B key opens: the mobile tools of that panel do nothing then. */
    public boolean toolsDisabled() {
        return active && !isBActive(selectedMember());
    }

    /** Whether the panel must not send requests now (the combined panel is waiting for a tab's contents). */
    public boolean blocksRequests() {
        return active && loading;
    }

    public int tabScroll() {
        return tabScroll;
    }

    public void setTabScroll(int tabScroll) {
        this.tabScroll = Math.max(0, tabScroll);
    }

    /** The server's table arrived. */
    public void apply(CombinedMembersPayload payload) {
        if (!payload.active()) {
            clear();
            return;
        }
        List<MemberView> views = new ArrayList<>(payload.members().size());
        for (CombinedMembersPayload.Entry entry : payload.members()) {
            views.add(new MemberView(entry.ref(), entry.identity().orElse(null), entry.tier(), entry.name()));
        }
        this.active = true;
        this.members = List.copyOf(views);
        this.selected = payload.selected();
        this.combinedTier = payload.combinedTier();
        this.skippedTier = payload.skippedTier();
        this.skippedNewer = payload.skippedNewer();
        this.skippedOverCap = payload.skippedOverCap();
        // the panel shows the selected tab's backpack only once the contents of it have arrived
        MemberView now = selectedMember();
        boolean wasLoading = loading;
        this.loading = now != null && !now.ref().equals(ClientBackpackView.get().getActiveRef());
        if (loading && !wasLoading) {
            loadingSinceMs = System.currentTimeMillis();
        }
        this.revealSelected = true;
        reserveTop(true);
    }

    /** The server put the panel on this backpack (a session payload): that ends the waiting if it is the selected tab. */
    public void onSessionOpened(BackpackRef ref) {
        MemberView now = selectedMember();
        if (active && now != null && now.ref().equals(ref)) {
            loading = false;
        }
    }

    /** The combined panel is over (the server said so, the panel was closed, or the world was left). */
    public void clear() {
        active = false;
        members = List.of();
        selected = -1;
        loading = false;
        tabScroll = 0;
        skippedTier = 0;
        skippedNewer = 0;
        skippedOverCap = 0;
        reserveTop(false);
    }

    /** The identity the client saw at a tab (what a SELECT request carries). */
    public Optional<UUID> identityAt(int index) {
        return index >= 0 && index < members.size() ? Optional.ofNullable(members.get(index).identity()) : Optional.empty();
    }

    /** The panel's top edge may not be higher than the bars hung above it are tall; with them gone the plain panel's rule is back. */
    private static void reserveTop(boolean on) {
        FloatingPanelState state = FloatingPanelState.get();
        state.setTopReserve(on ? CombinedBarLayout.BARS_H : 0);
        Minecraft minecraft = Minecraft.getInstance();
        if (on && minecraft != null && minecraft.getWindow() != null) {
            state.clampToScreen(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
    }
}
