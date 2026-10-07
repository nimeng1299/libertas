package neuvillette.libertas.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import neuvillette.libertas.blocks.TileIndustrialAgglomerationPlate;
import neuvillette.libertas.blocks.TileMagicSpawner;
import neuvillette.libertas.blocks.TileMechanicalApothecary;
import neuvillette.libertas.blocks.TileMechanicalBrewery;
import neuvillette.libertas.blocks.TileMechanicalDaisy;
import neuvillette.libertas.blocks.TileMechanicalManaPool;
import neuvillette.libertas.blocks.TileMechanicalRunicAltar;
import neuvillette.libertas.blocks.TileMiniElfPortal;
import neuvillette.libertas.blocks.TileSanguineRite;

/// FML GUI 处理器: 机械魔力池、机械符文祭坛、微型精灵门、工业凝聚板、机械花药台、机械白雏菊、
/// 机械植物酿造台、猩红祭仪、神奇的刷怪笼。在 preInit 里注册 (CommonProxy)。
public class ModGuiHandler implements IGuiHandler {

    public static final int ID_MECHANICAL_MANA_POOL = 0;
    public static final int ID_MECHANICAL_RUNIC_ALTAR = 1;
    public static final int ID_MINI_ELF_PORTAL = 2;
    public static final int ID_INDUSTRIAL_AGGLOMERATION_PLATE = 3;
    public static final int ID_MECHANICAL_APOTHECARY = 4;
    public static final int ID_MECHANICAL_DAISY = 5;
    public static final int ID_MECHANICAL_BREWERY = 6;
    public static final int ID_SANGUINE_RITE = 7;
    public static final int ID_MAGIC_SPAWNER = 8;

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
        if (id == ID_INDUSTRIAL_AGGLOMERATION_PLATE
            && world.getTileEntity(x, y, z) instanceof TileIndustrialAgglomerationPlate) {
            return new ContainerIndustrialAgglomerationPlate(
                player.inventory,
                (TileIndustrialAgglomerationPlate) world.getTileEntity(x, y, z));
        }
        if (id == ID_MECHANICAL_APOTHECARY && world.getTileEntity(x, y, z) instanceof TileMechanicalApothecary) {
            return new ContainerMechanicalApothecary(
                player.inventory,
                (TileMechanicalApothecary) world.getTileEntity(x, y, z));
        }
        if (id == ID_MECHANICAL_DAISY && world.getTileEntity(x, y, z) instanceof TileMechanicalDaisy) {
            return new ContainerMechanicalDaisy(player.inventory, (TileMechanicalDaisy) world.getTileEntity(x, y, z));
        }
        if (id == ID_MECHANICAL_BREWERY && world.getTileEntity(x, y, z) instanceof TileMechanicalBrewery) {
            return new ContainerMechanicalBrewery(
                player.inventory,
                (TileMechanicalBrewery) world.getTileEntity(x, y, z));
        }
        if (id == ID_SANGUINE_RITE && world.getTileEntity(x, y, z) instanceof TileSanguineRite) {
            return new ContainerSanguineRite(player.inventory, (TileSanguineRite) world.getTileEntity(x, y, z));
        }
        if (id == ID_MAGIC_SPAWNER && world.getTileEntity(x, y, z) instanceof TileMagicSpawner) {
            return new ContainerMagicSpawner(player.inventory, (TileMagicSpawner) world.getTileEntity(x, y, z));
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
        if (id == ID_INDUSTRIAL_AGGLOMERATION_PLATE
            && world.getTileEntity(x, y, z) instanceof TileIndustrialAgglomerationPlate) {
            return new GuiIndustrialAgglomerationPlate(
                new ContainerIndustrialAgglomerationPlate(
                    player.inventory,
                    (TileIndustrialAgglomerationPlate) world.getTileEntity(x, y, z)));
        }
        if (id == ID_MECHANICAL_APOTHECARY && world.getTileEntity(x, y, z) instanceof TileMechanicalApothecary) {
            return new GuiMechanicalApothecary(
                new ContainerMechanicalApothecary(
                    player.inventory,
                    (TileMechanicalApothecary) world.getTileEntity(x, y, z)));
        }
        if (id == ID_MECHANICAL_DAISY && world.getTileEntity(x, y, z) instanceof TileMechanicalDaisy) {
            return new GuiMechanicalDaisy(
                new ContainerMechanicalDaisy(player.inventory, (TileMechanicalDaisy) world.getTileEntity(x, y, z)));
        }
        if (id == ID_MECHANICAL_BREWERY && world.getTileEntity(x, y, z) instanceof TileMechanicalBrewery) {
            return new GuiMechanicalBrewery(
                new ContainerMechanicalBrewery(player.inventory, (TileMechanicalBrewery) world.getTileEntity(x, y, z)));
        }
        if (id == ID_SANGUINE_RITE && world.getTileEntity(x, y, z) instanceof TileSanguineRite) {
            return new GuiSanguineRite(
                new ContainerSanguineRite(player.inventory, (TileSanguineRite) world.getTileEntity(x, y, z)));
        }
        if (id == ID_MAGIC_SPAWNER && world.getTileEntity(x, y, z) instanceof TileMagicSpawner) {
            return new GuiMagicSpawner(
                new ContainerMagicSpawner(player.inventory, (TileMagicSpawner) world.getTileEntity(x, y, z)));
        }
        return null;
    }
}
