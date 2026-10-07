package neuvillette.libertas.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/// FML SimpleNetworkWrapper 通道。在 CommonProxy.preInit 里注册。
public final class ModNetwork {

    public static final String CHANNEL = "libertas";

    public static SimpleNetworkWrapper INSTANCE;

    private static final int ID_SANGUINE_RITE_RESET = 0;

    private ModNetwork() {}

    public static void init() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        INSTANCE.registerMessage(
            PacketSanguineRiteReset.Handler.class,
            PacketSanguineRiteReset.class,
            ID_SANGUINE_RITE_RESET,
            Side.SERVER);
    }
}
