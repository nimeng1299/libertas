package neuvillette.libertas.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.TileEntityGuiFactory;

import neuvillette.libertas.ClientProxy;

/**
 * 区块重塑机：右键打开 MUI2 面板（{@link TileChunkReshaper}），面板内两个按钮分别启动
 * 「清空本区块机器高度以下的方块（基岩除外）」与「在本区块最高建筑高度铺设美容玻璃」。
 *
 * <p>
 * 机器自己不耗电、不耗材，作业进度与暂停/继续都记在 TileEntity 里。
 */
public class BlockChunkReshaper extends BlockContainer {

    public BlockChunkReshaper() {
        super(Material.iron);
        setBlockName("chunkReshaper");
        setBlockTextureName("libertas:blocks/chunk_reshaper");
        setHardness(5.0f);
        setResistance(10.0f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
    }

    // ---- 渲染：世界里用 ISBRH 画 JSON 模型 (id 在 ClientProxy.init 里注册)，物品形态走 JsonItemRenderer ----

    @Override
    public int getRenderType() {
        return ClientProxy.CHUNK_RESHAPER_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    // ---- 交互：右键打开 MUI2 面板 ----

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            player.swingItem();
            return true;
        }
        if (world.getTileEntity(x, y, z) instanceof TileChunkReshaper reshaper) {
            TileEntityGuiFactory.INSTANCE.open(player, reshaper);
        }
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileChunkReshaper();
    }
}
