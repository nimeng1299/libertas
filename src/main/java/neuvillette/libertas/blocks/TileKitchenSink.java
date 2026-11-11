package neuvillette.libertas.blocks;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * 水槽 (kitchenSink) 的 TileEntity: 一个永不枯竭、也永远灌不满的水源。
 *
 * <p>
 * 内部恒为 {@link Integer#MAX_VALUE} mB 的水, 这个数字不会被任何操作改变:
 * <ul>
 * <li>抽水 ({@link #drain}): 按请求量直接发放, 不做扣减 —— 等于无限速率, 抽多少都还是 int 上限;</li>
 * <li>灌水 ({@link #fill}): 来多少水都收下并报告"已接收" —— 等于无限容量, 但存量仍是 int 上限。</li>
 * </ul>
 * 六个面都是同一个无限罐 (方向参数一律忽略), 因此既能从任意一面无限抽水, 也能从任意一面无限倒水。
 *
 * <p>
 * 任何通过 Forge {@link IFluidHandler} 交互的管道/泵/机器 (GT 泵模块、BuildCraft 流体管、
 * EIO 流体导管、AE2 流体导入总线等) 都能从任意一面无限抽水, 也可以把水倒进来。
 */
public class TileKitchenSink extends TileEntity implements IFluidHandler {

    /// 水槽恒定存量 (mB): int 上限
    public static final int INFINITE_WATER = Integer.MAX_VALUE;

    /// 每次调用都新建 FluidStack: 它是可变对象, 缓存实例会被调用方改坏
    private static FluidStack water(int amount) {
        final Fluid water = FluidRegistry.WATER;
        return water == null ? null : new FluidStack(water, amount);
    }

    private static boolean isWater(FluidStack stack) {
        return stack != null && stack.getFluid() == FluidRegistry.WATER;
    }

    // ---- IFluidHandler ----

    /// 六面无限进水: 收下任意量的水并原样报账 (管道据此认为倒空了), 存量仍是 int 上限
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!isWater(resource) || resource.amount <= 0) return 0;
        return resource.amount;
    }

    /// 只要请求的是水, 就按请求量 (上限 int 上限) 发货, 且永不扣减存量
    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (!isWater(resource)) return null;
        return drain(from, resource.amount, doDrain);
    }

    /// 无限速率: 要多少给多少 (最多 int 上限), doDrain 不影响存量
    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        return water(maxDrain);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid == null || fluid == FluidRegistry.WATER;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return fluid == null || fluid == FluidRegistry.WATER;
    }

    /// 单储罐, 六个面看到的是同一个罐; 存量恒报 int 上限
    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { new FluidTankInfo(water(INFINITE_WATER), INFINITE_WATER) };
    }

    // ---- TileEntity ----

    /// 没有需要 tick 的状态 (水源是常数), 关掉 ticking 省性能
    @Override
    public boolean canUpdate() {
        return false;
    }
}
