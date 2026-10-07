package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import WayofTime.alchemicalWizardry.common.block.BlockAltar;
import WayofTime.alchemicalWizardry.common.tileEntity.TEAltar;

/// 赤泉 (crimsonSpring) 的 TileEntity: 只要正上方是血魔法(BloodMagic)的血之祭坛,
/// 每 5 tick 通过 {@link TEAltar#fillMainTank(int)} 向祭坛注入 1000 LP。
/// fillMainTank 自己会截断到祭坛当前容量上限, 满了就填不进去, 无需额外判断。
public class TileCrimsonSpring extends TileEntity {

    /// 注入周期 (tick)
    public static final int INJECT_INTERVAL = 5;
    /// 每次注入的生命 essence (LP)
    public static final int INJECT_AMOUNT = 1000;

    private long tickCounter = 0;

    @Override
    public void updateEntity() {
        super.updateEntity();
        tickCounter++;
        if (tickCounter % INJECT_INTERVAL != 0) return;
        if (worldObj == null || worldObj.isRemote) return;

        final Block above = worldObj.getBlock(xCoord, yCoord + 1, zCoord);
        if (!(above instanceof BlockAltar)) return;

        if (worldObj.getTileEntity(xCoord, yCoord + 1, zCoord) instanceof TEAltar) {
            ((TEAltar) worldObj.getTileEntity(xCoord, yCoord + 1, zCoord)).fillMainTank(INJECT_AMOUNT);
        }
    }
}
