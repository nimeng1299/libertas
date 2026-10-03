package neuvillette.libertas.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;
import thaumcraft.api.crafting.IInfusionStabiliser;

/// 碧空之泪 (azureTear) - 一块天蓝色的水晶, 会把周围注魔祭坛的稳定度压低 10000。
///
/// TC4 的注魔祭坛 (TileInfusionMatrix) 内部用"不稳定度"计量稳定状态:
/// symmetry(结构不对称惩罚) + recipeInstability(配方不稳定性) = instability, 越高越容易在注魔过程中
/// 触发弹飞材料/电击/爆炸/扭曲等事故, 即"稳定度越低"。本方块被 TC 识别为稳定方块
/// ({@link IInfusionStabiliser}, 会被 getSurroundings 扫描计入对称性), 但真正的压制由
/// {@link TileAzureTear} 直接把祭坛的稳定度压低 10000 完成。
public class BlockAzureTear extends BlockContainer implements IInfusionStabiliser {

    public BlockAzureTear() {
        super(Material.rock);
        setBlockName("azureTear");
        setHardness(3.0f);
        setResistance(10.0f);
        setStepSound(soundTypeGlass);
        setLightLevel(0.375f);
        // 碰撞箱/选取框贴着模型本体 (模型含 45° 旋转的水晶, 包围盒 x/z 4.5..11.5, y 0..15.75)
        setBlockBounds(4.5f / 16f, 0.0f, 4.5f / 16f, 11.5f / 16f, 15.75f / 16f, 11.5f / 16f);
        setHarvestLevel("pickaxe", 0);
    }

    // ---- 渲染: 世界里用 ISBRH 画 JSON 模型 (id 在 ClientProxy.init 里注册), 物品形态走 JsonItemRenderer ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.JSON_BLOCK_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    // ---- TileEntity ----

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileAzureTear();
    }

    // ---- TC 稳定方块: 让祭坛的 getSurroundings 把本方块纳入稳定方块扫描 ----

    @Override
    public boolean canStabaliseInfusion(World world, int x, int y, int z) {
        return true;
    }
}
