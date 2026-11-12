package pnnh.mcfloatingbackpack.client.panel.search;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/** 044: the item tags were sent again (logging in, {@code /reload}): what the search worked out from them is out of date. */
@EventBusSubscriber(modid = MCFloatingBackpack.MOD_ID, value = Dist.CLIENT)
public final class SearchFactsEvents {
    private SearchFactsEvents() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemFacts.invalidate();
    }
}
