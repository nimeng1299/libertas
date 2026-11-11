package neuvillette.libertas;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.botania.ModBotania;
import neuvillette.libertas.gui.ModGuiHandler;
import neuvillette.libertas.items.ModItems;
import neuvillette.libertas.machines.ModMachines;
import neuvillette.libertas.network.ModNetwork;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        NetworkRegistry.INSTANCE.registerGuiHandler(Libertas.instance, new ModGuiHandler());

        ModNetwork.init();

        ModItems.init();

        ModBlocks.init();

        // Botania 特花的 subtile 注册须在 init 之前的资源拼接期完成
        ModBotania.init();

        // Waila 提示: 水槽显示流体与容量。没装 Waila 时这条 IMC 会被直接丢弃,
        // 回调类 KitchenSinkWailaProvider 也就永远不会被反射加载
        if (Loader.isModLoaded("Waila")) {
            FMLInterModComms
                .sendMessage("Waila", "register", "neuvillette.libertas.waila.KitchenSinkWailaProvider.register");
        }

        Libertas.LOG.info(Config.greeting);
        Libertas.LOG.info("I am Libertas at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        PlayerFirstJoinHandler.init();

        // GT 的 MetaTileEntity 只能在 GT preInit 之后、postInit 之前注册，init 阶段正处窗口内
        ModMachines.init();

        // 工作台配方引用 MTE 的 ItemStack 和各 mod preInit 注册的 OreDict，须在其后注册
        ModRecipes.init();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
