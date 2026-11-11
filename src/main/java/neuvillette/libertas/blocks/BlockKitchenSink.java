package neuvillette.libertas.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;

/**
 * 水槽 (kitchenSink) - 模型取自 Blockbench 的厨房水槽, 内含 int 上限的水.
 *
 * <p>
 * 本身只是一个壳: 无限水源的逻辑全在 {@link TileKitchenSink} 里 (六面可抽、六面可灌、速率无限、
 * 存量不变)。方块本体不耗电、不 tick, 只负责渲染、右键接水/倒水和掉落。
 *
 * <p>
 * 模型原始朝向为北 (-Z), 放置时按玩家朝向旋转 meta (2=北 3=南 4=西 5=东); 整块占 1×1,
 * 水龙头高出方块顶面约 7.4 像素, 因此碰撞箱仍取整块立方体 (1×1×1), 高出来的龙头不参与碰撞。
 */
public class BlockKitchenSink extends BlockContainer {

    /// 破坏粒子用的贴图 (模型本身由 JSON 渲染器绘制)
    private IIcon icon;

    public BlockKitchenSink() {
        super(Material.iron);
        setBlockName("kitchenSink");
        setHardness(5.0f);
        setResistance(10.0f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
    }

    // ---- 渲染: 与区块重塑机同一条 JSON 模型管线 (id 在 ClientProxy.init 里注册) ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.KITCHEN_SINK_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /// 方块图集的名字不带 "blocks/" 前缀 (TextureMap 的 basePath 已经是 textures/blocks),
    /// 与 JSON 渲染器注册模型贴图时的名字保持一致
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icon = register.registerIcon("libertas:kitchen_sink");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icon;
    }

    /// 放置时按玩家朝向旋转柜体, 让水槽正面朝向玩家 (与熔炉的 meta 约定一致:
    /// 2=北 3=南 4=西 5=东, 模型原始朝向为北)
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        switch (MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3) {
            case 0:
                world.setBlockMetadataWithNotify(x, y, z, 2, 2);
                break;
            case 1:
                world.setBlockMetadataWithNotify(x, y, z, 5, 2);
                break;
            case 2:
                world.setBlockMetadataWithNotify(x, y, z, 3, 2);
                break;
            default:
                world.setBlockMetadataWithNotify(x, y, z, 4, 2);
                break;
        }
    }

    // ---- 交互: 当成流体容器用 (空桶/空单元接水, 水桶把水倒进来) ----

    /// 右键交互, 两种用法:
    /// <ul>
    /// <li>手持空桶、空单元等可装流体的容器: 无限接满水 (容器由 {@link FluidContainerRegistry} 决定);</li>
    /// <li>手持装满水的容器: 把水倒进水槽 —— 水直接消失, 存量仍是 int 上限, 并返还空容器。</li>
    /// </ul>
    /// 其他物品一律返回 false, 不影响正常放置/使用。
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        final ItemStack held = player.getCurrentEquippedItem();
        if (held == null) return false;

        final FluidStack infiniteWater = new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE);
        final boolean canFillContainer = FluidContainerRegistry.fillFluidContainer(infiniteWater, held) != null;
        final FluidStack contained = canFillContainer ? null : FluidContainerRegistry.getFluidForFilledItem(held);
        final boolean canPourIn = contained != null && contained.getFluid() == FluidRegistry.WATER;
        if (!canFillContainer && !canPourIn) return false;

        if (world.isRemote) {
            player.swingItem();
            return true;
        }

        final ItemStack result = canFillContainer ? FluidContainerRegistry.fillFluidContainer(infiniteWater, held)
            : FluidContainerRegistry.drainFluidContainer(held);
        if (result == null) return false;

        swapHeldItem(player, held, result);
        return true;
    }

    /// 把手上的这一格换成 result: 一叠里只消耗一个 (多的塞背包或掉在原地), 创造模式不消耗
    private static void swapHeldItem(EntityPlayer player, ItemStack held, ItemStack result) {
        if (player.capabilities.isCreativeMode) {
            if (!player.inventory.addItemStackToInventory(result.copy())) {
                player.dropPlayerItemWithRandomChoice(result.copy(), false);
            }
        } else if (held.stackSize > 1) {
            held.stackSize--;
            if (!player.inventory.addItemStackToInventory(result)) {
                player.dropPlayerItemWithRandomChoice(result, false);
            }
        } else {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
        }
    }

    // ---- TileEntity ----

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileKitchenSink();
    }
}
