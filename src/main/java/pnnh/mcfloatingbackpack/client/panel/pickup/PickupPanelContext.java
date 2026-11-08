package pnnh.mcfloatingbackpack.client.panel.pickup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelContext;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout;
import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagMatcher;

/**
 * 024: ties the pure pickup-page classes to the game: the model for the open backpack, the drop-down's entries and the
 * layout of the page (018's {@link TagPanelLayout}, fed with the blacklist instead of the selection's common tags). Used by
 * both the renderer and the input, so they always agree on where things are.
 */
@OnlyIn(Dist.CLIENT)
public final class PickupPanelContext {
    private PickupPanelContext() {
    }

    public static PickupPanelModel model() {
        ClientBackpackView view = ClientBackpackView.get();
        return PickupPanelModel.of(view.pickupBlacklist().tags(), view.hasAutoPickup());
    }

    /** The tags this backpack has put on items (any category), each once, in the order they are stored. */
    private static List<String> usedTags() {
        Set<String> used = new LinkedHashSet<>();
        for (List<String> tags : ClientBackpackView.get().tags().map().values()) {
            used.addAll(tags);
        }
        return new ArrayList<>(used);
    }

    /** The tag ids the drop-down offers right now: fuzzy matches of the typed text, only while editing. */
    public static List<String> dropdown() {
        PickupPanelState state = PickupPanelState.get();
        if (!state.isEditing()) {
            return List.of();
        }
        List<String> ids = PickupPanelModel.candidates(
                PresetTags.ids(), usedTags(), ClientBackpackView.get().pickupBlacklist().tags());
        List<TagMatcher.Entry> entries = new ArrayList<>();
        for (String id : ids) {
            entries.add(new TagMatcher.Entry(id, TagPanelContext.label(id)));
        }
        return TagMatcher.match(state.input().text(), entries);
    }

    public static TagPanelLayout layout(int width, int height, PickupPanelModel model) {
        PickupPanelState state = PickupPanelState.get();
        Font font = Minecraft.getInstance().font;
        List<String> labels = new ArrayList<>();
        int pendingIndex = -1;
        for (int i = 0; i < model.tags().size(); i++) {
            String tag = model.tags().get(i);
            labels.add(TagPanelContext.label(tag));
            if (tag.equals(state.pendingDelete())) {
                pendingIndex = i;
            }
        }
        if (pendingIndex < 0) {
            state.setPendingDelete(null);
        }
        TagPanelLayout layout = TagPanelLayout.of(
                width,
                height,
                font::width,
                labels,
                Component.translatable("pickup.mcfloatingbackpack.none").getString(),
                state.isEditing(),
                dropdown().size(),
                state.scroll(),
                pendingIndex);
        if (state.scroll() > layout.maxScroll()) {
            state.setScroll(layout.maxScroll());
        }
        return layout;
    }
}
