package pnnh.mcfloatingbackpack.compat.ae2;

import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.me.common.GridInventoryEntry;

import pnnh.mcfloatingbackpack.client.compat.NetworkEntry;
import pnnh.mcfloatingbackpack.client.compat.NetworkTerminalClient;
import pnnh.mcfloatingbackpack.client.compat.ScreenSlots;

/**
 * 060: recognises, on the client, a grid cell of an AE2 ME terminal screen that shows an item the network really holds: a
 * {@link RepoSlot} of a {@link MEStorageScreen} (the screen every terminal that shows the ME grid is built on) whose entry is an item with a
 * stored amount above zero. An entry that only shows what can be crafted has nothing to take. The item is the entry's key; the handle is its
 * number in the grid, which the server's menu knows how to read. Public methods of AE2 only.
 */
@OnlyIn(Dist.CLIENT)
final class Ae2Client implements NetworkTerminalClient {
    @Override
    public Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Slot slot = ScreenSlots.slotAt(screen, mouseX, mouseY);
        if (!(screen instanceof MEStorageScreen<?>) || !(slot instanceof RepoSlot repoSlot)) {
            return Optional.empty();
        }
        GridInventoryEntry entry = repoSlot.getEntry();
        if (entry == null || !(entry.getWhat() instanceof AEItemKey key) || entry.getStoredAmount() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new NetworkEntry(key.toStack(1), entry.getSerial()));
    }
}
