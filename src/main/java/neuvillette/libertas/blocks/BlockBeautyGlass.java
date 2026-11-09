package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;

/// 美容玻璃 (beautyGlass) - 与玻璃同样通透的装饰方块, 只是每个面的中央都是空的:
/// 面上只留贴边的 2px 玻璃边框 (几何由 {@link neuvillette.libertas.client.render.BeautyGlassRenderer} 绘制)。
/// 相邻两块相接时, 接缝处那一圈边框也一并消失, 两块于是连成一根贯通的中空玻璃管。
/// 自身带 4 级亮度, 暗处会透出一点微光。
public class BlockBeautyGlass extends Block {

    /// 自身发光等级 (0..15)。渲染器同样用它给边框补光, 免得边框比周围暗
    public static final int LIGHT_VALUE = 4;

    public BlockBeautyGlass() {
        super(Material.glass);
        setBlockName("beautyGlass");
        setBlockTextureName("libertas:beauty_glass");
        setHardness(0.3f);
        setResistance(0.3f);
        setStepSound(soundTypeGlass);
        setLightLevel(LIGHT_VALUE / 15f);
        // 与玻璃一致: 不挡光, 也不遮挡相邻方块的面
        setLightOpacity(0);
    }

    // ---- 渲染 ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.BEAUTY_GLASS_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /// 与玻璃一致: 同种方块之间不互相遮挡 (接缝处的边框由渲染器直接跳过)
    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return world.getBlock(x, y, z) != this && super.shouldSideBeRendered(world, x, y, z, side);
    }
}
