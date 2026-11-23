package pnnh.mcfloatingbackpack.compat.ae2;

import pnnh.mcfloatingbackpack.client.compat.NetworkTerminalClients;
import pnnh.mcfloatingbackpack.compat.NetworkStorageBridges;
import pnnh.mcfloatingbackpack.compat.ThirdPartyTable;

/**
 * 060: the entry points of the AE2 compatibility. Called only when AE2 is loaded (see {@code MCFloatingBackpack} and {@code ClientSetup}),
 * so none of its classes is touched otherwise. Registering is unconditional; whether the bridge may run is decided at every call by the
 * version table, the server config and the server's level, as for Integrated Terminals.
 */
public final class Ae2Compat {
    private Ae2Compat() {
    }

    public static void register() {
        NetworkStorageBridges.register(new Ae2Bridge());
    }

    /** The client's recogniser of the grid's entries. */
    public static void registerClient() {
        NetworkTerminalClients.register(ThirdPartyTable.AE2, new Ae2Client());
    }
}
