package neuvillette.libertas;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.items.ModItems;
import neuvillette.libertas.machines.ModMachines;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        ModItems.init();

        ModBlocks.init();

        Libertas.LOG.info(Config.greeting);
        Libertas.LOG.info("I am Libertas at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
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
