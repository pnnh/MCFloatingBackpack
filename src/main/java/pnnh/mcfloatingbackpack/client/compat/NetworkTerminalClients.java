package pnnh.mcfloatingbackpack.client.compat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.compat.ClientCompatVersions;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ThirdPartyCompat;
import pnnh.mcfloatingbackpack.compat.ThirdPartyTable;

/**
 * 060: the recognisers of network terminal entries, one for each mod, and the one question the Ctrl+click on a screen asks first: "is the
 * cursor over an entry of a network's list that the panel can take from?" A mod's recogniser is only asked while the mod is
 * switched on for this client: its installed version is one this mod was verified with ({@link ClientCompatVersions}: no warning here,
 * the server gives those) and the client config's level for it is FULL. The server does its own checks whatever the client says.
 */
public final class NetworkTerminalClients {
    private static final Map<String, NetworkTerminalClient> CLIENTS = new ConcurrentHashMap<>();

    private NetworkTerminalClients() {
    }

    public static void register(String modId, NetworkTerminalClient client) {
        CLIENTS.put(modId, client);
    }

    public static Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (CLIENTS.isEmpty()) {
            return Optional.empty();
        }
        String owner = MenuOwner.ownerOf(screen.getMenu());
        NetworkTerminalClient client = CLIENTS.get(owner);
        if (client == null || !enabled(owner)) {
            return Optional.empty();
        }
        try {
            return client.entryAt(screen, mouseX, mouseY);
        } catch (LinkageError | RuntimeException e) {
            // another version of the mod than the one this was written for: leave the click to the game, and stop trying
            CLIENTS.remove(owner);
            pnnh.mcfloatingbackpack.MCFloatingBackpack.LOGGER.error(
                    "The recogniser of network terminal entries for {} failed and is switched off until the game restarts", owner, e);
            return Optional.empty();
        }
    }

    /** The item held on the open terminal's own cursor, for the panel to draw above itself and to take the drop of. */
    public static Optional<NetworkHeld> held(AbstractContainerScreen<?> screen) {
        NetworkTerminalClient client = clientFor(screen);
        if (client == null) {
            return Optional.empty();
        }
        try {
            return client.held(screen);
        } catch (LinkageError | RuntimeException e) {
            disable(screen, e);
            return Optional.empty();
        }
    }

    public static void release(AbstractContainerScreen<?> screen) {
        NetworkTerminalClient client = clientFor(screen);
        if (client == null) {
            return;
        }
        try {
            client.release(screen);
        } catch (LinkageError | RuntimeException e) {
            disable(screen, e);
        }
    }

    private static NetworkTerminalClient clientFor(AbstractContainerScreen<?> screen) {
        if (CLIENTS.isEmpty() || screen == null) {
            return null;
        }
        String owner = MenuOwner.ownerOf(screen.getMenu());
        NetworkTerminalClient client = CLIENTS.get(owner);
        return client == null || !enabled(owner) ? null : client;
    }

    private static void disable(AbstractContainerScreen<?> screen, Throwable e) {
        String owner = MenuOwner.ownerOf(screen.getMenu());
        CLIENTS.remove(owner);
        pnnh.mcfloatingbackpack.MCFloatingBackpack.LOGGER.error(
                "The recogniser of network terminal entries for {} failed and is switched off until the game restarts", owner, e);
    }

    private static boolean enabled(String modId) {
        for (ThirdPartyCompat row : ThirdPartyTable.ALL) {
            if (row.modId().equals(modId)) {
                return ClientCompatVersions.inRange(row) && CompatConfig.policy().specificAdaptation(modId);
            }
        }
        return false;
    }
}
