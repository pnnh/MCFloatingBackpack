package pnnh.mcfloatingbackpack.inventory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

public final class BackpackAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCFloatingBackpack.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BackpackInventory>> BACKPACK =
            ATTACHMENT_TYPES.register(
                    "backpack",
                    () -> AttachmentType.serializable(BackpackInventory::new).copyOnDeath().build());

    private BackpackAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
