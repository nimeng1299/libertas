package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import neuvillette.libertas.blocks.TileMechanicalManaPool;

/// FML GUI 处理器: 目前只有机械魔力池。在 preInit 里注册 (CommonProxy)。
public class ModGuiHandler implements IGuiHandler {

    public static final int ID_MECHANICAL_MANA_POOL = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == ID_MECHANICAL_MANA_POOL && world.getTileEntity(x, y, z) instanceof TileMechanicalManaPool) {
            return new ContainerMechanicalManaPool(
                player.inventory,
                (TileMechanicalManaPool) world.getTileEntity(x, y, z));
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == ID_MECHANICAL_MANA_POOL && world.getTileEntity(x, y, z) instanceof TileMechanicalManaPool) {
            return new GuiMechanicalManaPool(
                new ContainerMechanicalManaPool(
                    player.inventory,
                    (TileMechanicalManaPool) world.getTileEntity(x, y, z)));
        }
        return null;
    }
}
