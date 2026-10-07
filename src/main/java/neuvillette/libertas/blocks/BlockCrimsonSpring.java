package neuvillette.libertas.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;

/// 赤泉 (crimsonSpring) - 一泓赤色的泉, 架在血魔法(BloodMagic)血之祭坛正下方,
/// 每 {@link TileCrimsonSpring#INJECT_INTERVAL} tick 向祭坛涌上
/// {@link TileCrimsonSpring#INJECT_AMOUNT} LP (与灵泉 spiritSpring 同一家族,
/// 灵泉喂巫术祭坛, 赤泉喂血之祭坛)。
public class BlockCrimsonSpring extends BlockContainer {

    public BlockCrimsonSpring() {
        super(Material.rock);
        setBlockName("crimsonSpring");
        setHardness(1.5f);
        setResistance(8.0f);
        setStepSound(soundTypeStone);
        setLightLevel(0.6f);
        // 碰撞箱/选取框贴着模型本体 (泉池 x/z 2..14, 血珠晶柱顶到 y 9.5)
        setBlockBounds(2.0f / 16f, 0.0f, 2.0f / 16f, 14.0f / 16f, 9.5f / 16f, 14.0f / 16f);
        setHarvestLevel("pickaxe", 0);
    }

    // ---- 渲染: 与灵泉同一条 JSON 模型管线 (id 在 ClientProxy.init 里注册) ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.CRIMSON_SPRING_RENDER_ID;
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
        return new TileCrimsonSpring();
    }
}
