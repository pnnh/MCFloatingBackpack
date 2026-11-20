package pnnh.mcfloatingbackpack.compat.integratedterminals;

import pnnh.mcfloatingbackpack.compat.NetworkStorageBridges;

/**
 * 058: the entry point of the Integrated Terminals compatibility. Called only when the mod is loaded (see {@code MCFloatingBackpack}), so
 * none of the Integrated Terminals classes is touched otherwise. Registering is unconditional; whether the bridge may run is decided at
 * every call by the version check of the table ({@code ThirdPartyTable}) and the server config.
 */
public final class IntegratedTerminalsCompat {
    private IntegratedTerminalsCompat() {
    }

    public static void register() {
        NetworkStorageBridges.register(new IntegratedTerminalsBridge());
    }
}
