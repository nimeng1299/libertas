package neuvillette.libertas.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/// 方块魔力基类: 拥有 {@link TileManaBase} 魔力存储的容器方块的公共部分。
///
/// - 非不透明方块 (子类用 ISBRH 渲染 JSON 模型, 玻璃外壳需要邻居面正常绘制);
/// - 比较器按魔力存量比例输出 0..15。
public abstract class BlockManaBase extends BlockContainer {

    protected BlockManaBase(Material material) {
        super(material);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        if (!(world.getTileEntity(x, y, z) instanceof TileManaBase)) return 0;
        final TileManaBase mana = (TileManaBase) world.getTileEntity(x, y, z);
        return mana.getCurrentMana() * 15 / mana.getMaxMana();
    }

    /// 方块被挖掉时倾倒 TileEntity 物品栏的公共工具 (在 TE 销毁前调用)
    protected static void dropInventory(World world, int x, int y, int z, IInventory inventory) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            final ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null) {
                dropItemStack(world, x, y, z, stack);
                inventory.setInventorySlotContents(i, null);
            }
        }
    }

    private static void dropItemStack(World world, int x, int y, int z, ItemStack stack) {
        final float spread = 0.7f;
        final double dx = world.rand.nextFloat() * spread + (1.0f - spread) * 0.5;
        final double dy = world.rand.nextFloat() * spread + (1.0f - spread) * 0.5;
        final double dz = world.rand.nextFloat() * spread + (1.0f - spread) * 0.5;
        final EntityItem entity = new EntityItem(world, x + dx, y + dy, z + dz, stack);
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        world.spawnEntityInWorld(entity);
    }
}
