package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import neuvillette.libertas.blocks.TileMechanicalManaPool;
import neuvillette.libertas.blocks.TileMechanicalRunicAltar;
import neuvillette.libertas.blocks.TileMiniElfPortal;

/// FML GUI 处理器: 机械魔力池、机械符文祭坛、微型精灵门。在 preInit 里注册 (CommonProxy)。
public class ModGuiHandler implements IGuiHandler {

    public static final int ID_MECHANICAL_MANA_POOL = 0;
    public static final int ID_MECHANICAL_RUNIC_ALTAR = 1;
    public static final int ID_MINI_ELF_PORTAL = 2;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == ID_MECHANICAL_MANA_POOL && world.getTileEntity(x, y, z) instanceof TileMechanicalManaPool) {
            return new ContainerMechanicalManaPool(
                player.inventory,
                (TileMechanicalManaPool) world.getTileEntity(x, y, z));
        }
        if (id == ID_MECHANICAL_RUNIC_ALTAR && world.getTileEntity(x, y, z) instanceof TileMechanicalRunicAltar) {
            return new ContainerMechanicalRunicAltar(
                player.inventory,
                (TileMechanicalRunicAltar) world.getTileEntity(x, y, z));
        }
        if (id == ID_MINI_ELF_PORTAL && world.getTileEntity(x, y, z) instanceof TileMiniElfPortal) {
            return new ContainerMiniElfPortal(player.inventory, (TileMiniElfPortal) world.getTileEntity(x, y, z));
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
        if (id == ID_MECHANICAL_RUNIC_ALTAR && world.getTileEntity(x, y, z) instanceof TileMechanicalRunicAltar) {
            return new GuiMechanicalRunicAltar(
                new ContainerMechanicalRunicAltar(
                    player.inventory,
                    (TileMechanicalRunicAltar) world.getTileEntity(x, y, z)));
        }
        if (id == ID_MINI_ELF_PORTAL && world.getTileEntity(x, y, z) instanceof TileMiniElfPortal) {
            return new GuiMiniElfPortal(
                new ContainerMiniElfPortal(player.inventory, (TileMiniElfPortal) world.getTileEntity(x, y, z)));
        }
        return null;
    }
}
