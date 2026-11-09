package neuvillette.libertas.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.BlockBeautyGlass;

/// 美容玻璃的 ISBRH。
///
/// 每个面只画贴边的 2px 边框, 面中央完全留空; 凡是朝着同种玻璃的那一条边 (整个面, 面上的单条边) 都不画 ——
/// 于是单块看着像一扇"玻璃窗框", 几块接在一起时接缝处同样贯通, 连成一截中空的玻璃箱体,
/// 框条在接缝处自然连成一条直线, 不会多出一道隔断。
///
/// 每条边框都是一张贴在方块边界上的薄片; 每个矩形正反各画一遍, 从箱体内部看过去才不会穿帮
/// (GL_CULL_FACE 会自动剔掉朝向错误的那一半, 所以不会重复叠加)。
@SideOnly(Side.CLIENT)
public class BeautyGlassRenderer implements ISimpleBlockRenderingHandler {

    /// 边框宽度 (方块单位): 面上只留贴边的 2px
    private static final double BORDER = 2.0 / 16.0;

    /// 各面的面内轴方向, 依次是 u 负向、u 正向、v 负向、v 正向 (与 {@link #addVertex} 的映射一致)
    private static final ForgeDirection[] SIDES_Y = { ForgeDirection.WEST, ForgeDirection.EAST, ForgeDirection.NORTH,
        ForgeDirection.SOUTH };
    private static final ForgeDirection[] SIDES_Z = { ForgeDirection.WEST, ForgeDirection.EAST, ForgeDirection.DOWN,
        ForgeDirection.UP };
    private static final ForgeDirection[] SIDES_X = { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.DOWN,
        ForgeDirection.UP };

    private final int renderId;

    public BeautyGlassRenderer() {
        this.renderId = RenderingRegistry.getNextAvailableRenderId();
    }

    /// 方块必须从 {@link Block#getRenderType} 返回这个 id;
    /// 再用 `RenderingRegistry.registerBlockHandler(id, this)` 注册到渲染器表里。
    public int getRenderId() {
        return renderId;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // 物品形态由 JsonItemRenderer 负责 (注册在方块的 ItemBlock 上)
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        final IIcon icon = renderer.hasOverrideBlockTexture() ? renderer.overrideBlockTexture : block.getIcon(0, 0);
        if (icon == null) return false;

        final Tessellator tessellator = Tessellator.instance;
        // 方块自身发光, 采样光照时把自发光算进去, 边框才不会比旁边暗
        final int brightness = world.getLightBrightnessForSkyBlocks(x, y, z, BlockBeautyGlass.LIGHT_VALUE);
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            // 整面朝着的邻居就是同种玻璃: 这一整圈边框都不画
            if (connected(world, block, x, y, z, face)) continue;

            final float shade = shade(face);
            tessellator.setBrightness(brightness);
            tessellator.setColorOpaque_F(shade, shade, shade);
            addRing(tessellator, icon, world, block, face, x, y, z);
        }
        return true;
    }

    /// 一个面 = 4 条矩形拼成的边框; 每条边朝着的邻居是同种玻璃时, 那条边也留空
    private static void addRing(Tessellator tessellator, IIcon icon, IBlockAccess world, Block block,
        ForgeDirection face, int x, int y, int z) {
        final ForgeDirection[] sides = inPlaneSides(face);
        final double inner = 1.0 - BORDER;
        if (!connected(world, block, x, y, z, sides[0])) {
            addQuad(tessellator, icon, face, x, y, z, 0.0, BORDER, BORDER, inner); // 贴 u 负向边
        }
        if (!connected(world, block, x, y, z, sides[1])) {
            addQuad(tessellator, icon, face, x, y, z, inner, BORDER, 1.0, inner); // 贴 u 正向边
        }
        if (!connected(world, block, x, y, z, sides[2])) {
            addQuad(tessellator, icon, face, x, y, z, 0.0, 0.0, 1.0, BORDER); // 贴 v 负向边
        }
        if (!connected(world, block, x, y, z, sides[3])) {
            addQuad(tessellator, icon, face, x, y, z, 0.0, inner, 1.0, 1.0); // 贴 v 正向边
        }
    }

    private static ForgeDirection[] inPlaneSides(ForgeDirection face) {
        switch (face) {
            case UP:
            case DOWN:
                return SIDES_Y;
            case NORTH:
            case SOUTH:
                return SIDES_Z;
            default: // WEST / EAST
                return SIDES_X;
        }
    }

    private static boolean connected(IBlockAccess world, Block block, int x, int y, int z, ForgeDirection side) {
        return world.getBlock(x + side.offsetX, y + side.offsetY, z + side.offsetZ) == block;
    }

    private static void addQuad(Tessellator tessellator, IIcon icon, ForgeDirection face, int x, int y, int z,
        double u0, double v0, double u1, double v1) {
        addVertex(tessellator, icon, face, x, y, z, u0, v0);
        addVertex(tessellator, icon, face, x, y, z, u1, v0);
        addVertex(tessellator, icon, face, x, y, z, u1, v1);
        addVertex(tessellator, icon, face, x, y, z, u0, v1);

        // 反面: 顶点顺序倒过来
        addVertex(tessellator, icon, face, x, y, z, u0, v1);
        addVertex(tessellator, icon, face, x, y, z, u1, v1);
        addVertex(tessellator, icon, face, x, y, z, u1, v0);
        addVertex(tessellator, icon, face, x, y, z, u0, v0);
    }

    /// 面内坐标 (u, v) -> 方块局部坐标; 贴图 UV 直接按方块坐标铺, 相邻两块的边框纹理才能接上
    private static void addVertex(Tessellator tessellator, IIcon icon, ForgeDirection face, int x, int y, int z,
        double u, double v) {
        double px = 0.0, py = 0.0, pz = 0.0;
        switch (face) {
            case DOWN: // 平面 y=0, 面内轴 u=x, v=z
                px = u;
                py = 0.0;
                pz = v;
                break;
            case UP: // 平面 y=1
                px = u;
                py = 1.0;
                pz = v;
                break;
            case NORTH: // 平面 z=0, 面内轴 u=x, v=y
                px = u;
                py = v;
                pz = 0.0;
                break;
            case SOUTH: // 平面 z=1
                px = u;
                py = v;
                pz = 1.0;
                break;
            case WEST: // 平面 x=0, 面内轴 u=z, v=y
                px = 0.0;
                py = v;
                pz = u;
                break;
            case EAST: // 平面 x=1
                px = 1.0;
                py = v;
                pz = u;
                break;
            default:
                break;
        }
        tessellator
            .addVertexWithUV(x + px, y + py, z + pz, icon.getInterpolatedU(u * 16.0), icon.getInterpolatedV(v * 16.0));
    }

    /// 与原版一致的固定方向明暗系数 (下 0.5 / 上 1.0 / 南北 0.8 / 东西 0.6)
    private static float shade(ForgeDirection face) {
        switch (face) {
            case DOWN:
                return 0.5f;
            case UP:
                return 1.0f;
            case NORTH:
            case SOUTH:
                return 0.8f;
            default:
                return 0.6f;
        }
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        // 物品形态由 JsonItemRenderer 负责
        return false;
    }
}
