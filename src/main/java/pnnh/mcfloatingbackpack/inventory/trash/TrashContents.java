package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;

/**
 * 021: what the trash can of one backpack holds, newest group first, at most {@link TrashBin#MAX_GROUPS} groups of
 * one item stack each. Stored as an optional field of {@code BackpackContents}; the trash can <em>item</em> holds
 * nothing, so taking it out of the right area leaves this untouched. A backpack saved before 021 reads as empty.
 */
public record TrashContents(List<ItemStack> groups) {
    public static final TrashContents EMPTY = new TrashContents(List.of());

    /**
     * Most bytes one group may take on the wire when it is dropped in. Nine groups of this size stay below the
     * 1 MiB a server-to-client custom payload may carry. Measure with real full shulker boxes and nested backpacks
     * before trusting the number (021 research, decision 5).
     */
    public static final int MAX_GROUP_ENCODED_BYTES = 96 * 1024;

    public static final Codec<TrashContents> CODEC =
            ItemStack.OPTIONAL_CODEC.listOf().xmap(TrashContents::new, TrashContents::groups);

    public static final StreamCodec<RegistryFriendlyByteBuf, TrashContents> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC
                    .apply(ByteBufCodecs.list(TrashBin.MAX_GROUPS))
                    .map(TrashContents::new, TrashContents::groups);

    /** Copies the stacks, drops empty ones and keeps at most nine. */
    public TrashContents {
        List<ItemStack> clean = new ArrayList<>();
        for (ItemStack stack : TrashBin.sanitize(ItemStackTransferOps.INSTANCE, groups)) {
            clean.add(stack.copy());
        }
        groups = List.copyOf(clean);
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /** The group in trash slot {@code index}, or {@link ItemStack#EMPTY}. */
    public ItemStack get(int index) {
        return index >= 0 && index < groups.size() ? groups.get(index) : ItemStack.EMPTY;
    }
}
