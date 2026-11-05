package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;
import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagMatcher;
import pnnh.mcfloatingbackpack.item.ModTags;

/**
 * 018: ties the pure tag-panel classes to the game: the model for the current selection, the label shown for a tag,
 * and the layout of the panel. Used by both the renderer and the input, so they always agree on where things are.
 */
@OnlyIn(Dist.CLIENT)
public final class TagPanelContext {
    private TagPanelContext() {
    }

    /** The item category of a main-area slot (the item's registry id), or null for an empty slot. */
    static ResourceLocation categoryAt(int slot) {
        ItemStack stack = ClientBackpackView.get().getStackInSlot(slot);
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static TagPanelModel<ResourceLocation> model() {
        List<Integer> selected = new ArrayList<>();
        for (int slot : PanelSelection.get().snapshot()) {
            selected.add(slot);
        }
        return TagPanelModel.of(
                selected,
                TagPanelContext::categoryAt,
                ClientBackpackView.get().tags().toBook(),
                ClientBackpackView.get().hasTagManager(),
                TagPanelContext::isProtected);
    }

    /** 022: whether the item category is on the protection list (it may not be marked as trash). */
    static boolean isProtected(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id).getDefaultInstance().is(ModTags.NEVER_TRASH);
    }

    /** 022: "these items cannot be marked as trash: a, b" for the selected protected items. */
    public static Component protectedMessage(TagPanelModel<ResourceLocation> model) {
        List<Component> names = new ArrayList<>();
        for (ResourceLocation id : model.protectedItems()) {
            names.add(itemName(id));
        }
        return Component.translatable("tag.mcfloatingbackpack.protected", ComponentUtils.formatList(names, Component.literal(", ")));
    }

    /** What a tag is called on screen: a preset in the current language, a custom tag as typed. */
    public static String label(String tag) {
        return PresetTags.isPreset(tag) ? Component.translatable(PresetTags.langKey(tag)).getString() : tag;
    }

    /** The name of an item category, for the "full" tooltip. */
    public static Component itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id).getDescription();
    }

    /** The preset ids the drop-down offers right now: fuzzy matches of the typed text, only while editing. */
    public static List<String> dropdown() {
        TagPanelState state = TagPanelState.get();
        if (!state.isEditing()) {
            return List.of();
        }
        List<TagMatcher.Entry> entries = new ArrayList<>();
        for (String id : PresetTags.ids()) {
            entries.add(new TagMatcher.Entry(id, label(id)));
        }
        return TagMatcher.match(state.input().text(), entries);
    }

    public static TagPanelLayout layout(int width, int height, TagPanelModel<ResourceLocation> model) {
        TagPanelState state = TagPanelState.get();
        Font font = Minecraft.getInstance().font;
        List<String> labels = new ArrayList<>();
        int pendingIndex = -1;
        for (int i = 0; i < model.commonTags().size(); i++) {
            String tag = model.commonTags().get(i);
            labels.add(label(tag));
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
                Component.translatable("tag.mcfloatingbackpack.none").getString(),
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
