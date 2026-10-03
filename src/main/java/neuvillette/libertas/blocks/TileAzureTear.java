package neuvillette.libertas.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import thaumcraft.common.tiles.TileInfusionMatrix;

/// 碧空之泪的 TileEntity: 服务端持续把周围注魔祭坛的稳定度压低 {@link #STABILITY_PENALTY}。
///
/// TC4 里祭坛扫描稳定方块的范围是以祭坛为中心水平 ±12 格、垂直方向从祭坛下方 10 格到上方 5 格
/// (TileInfusionMatrix.getSurroundings 的 xx/zz ∈ [-12,12], yy ∈ [-5,10])。反向推算: 以本方块为中心
/// 水平 ±12 格、垂直 [y-5, y+10] 内的 TileInfusionMatrix 都会被本方块影响。
///
/// "稳定度 -10000" 在 TC 的实现里等价于不稳定度(越高越乱)不低于 10000:
/// - 未开始注魔时压 symmetry (craftingStart 会以 symmetry + recipeInstability 算出 instability);
/// - 注魔过程中直接压 instability (craftCycle 每 countDelay tick 用 instability 做事故判定, >=499 必触发)。
/// 写成"不低于"的下限而不是每 tick 累加, 避免长时间注魔溢出, 也保证多块同放效果一致 (不叠加)。
public class TileAzureTear extends TileEntity {

    /// 稳定度降低量 (即祭坛 symmetry/instability 的下限).
    public static final int STABILITY_PENALTY = 10000;
    /// 找不到祭坛时的全量扫描间隔 (tick).
    private static final int SCAN_INTERVAL = 40;

    /// 缓存的祭坛位置: 全量扫描很贵, 找到后每次 tick 只校验这一个位置.
    private boolean matrixFound;
    private int matrixX;
    private int matrixY;
    private int matrixZ;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        if (matrixFound) {
            TileEntity te = worldObj.getTileEntity(matrixX, matrixY, matrixZ);
            if (te instanceof TileInfusionMatrix) {
                destabilise((TileInfusionMatrix) te);
                return;
            }
            // 缓存失效 (祭坛被拆/区块重载), 重新扫描
            matrixFound = false;
        }

        // 按坐标错开相位, 避免多个方块同一刻全量扫描
        if ((worldObj.getTotalWorldTime() + phase()) % SCAN_INTERVAL == 0L) {
            scanForMatrices();
        }
    }

    private long phase() {
        return (Math.abs(xCoord * 31 + yCoord * 7 + zCoord * 17)) % SCAN_INTERVAL;
    }

    /// 全量扫描影响范围内所有已加载区块里的注魔祭坛, 逐个压制并缓存第一个位置.
    private void scanForMatrices() {
        // 先按区块过滤, 避免 getTileEntity 把边缘未加载区块强制加载进来
        final int minCX = (xCoord - 12) >> 4, maxCX = (xCoord + 12) >> 4;
        final int minCZ = (zCoord - 12) >> 4, maxCZ = (zCoord + 12) >> 4;
        boolean found = false;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                // blockExists 即 y 合法 + chunkExists, 不会把未加载区块强制加载进来
                if (!worldObj.blockExists(cx << 4, 70, cz << 4)) continue;

                final int x0 = Math.max(xCoord - 12, cx << 4), x1 = Math.min(xCoord + 12, (cx << 4) + 15);
                final int z0 = Math.max(zCoord - 12, cz << 4), z1 = Math.min(zCoord + 12, (cz << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int dy = -5; dy <= 10; dy++) {
                            TileEntity te = worldObj.getTileEntity(x, yCoord + dy, z);
                            if (te instanceof TileInfusionMatrix) {
                                destabilise((TileInfusionMatrix) te);
                                if (!found) {
                                    found = true;
                                    matrixFound = true;
                                    matrixX = te.xCoord;
                                    matrixY = te.yCoord;
                                    matrixZ = te.zCoord;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (found) markDirty();
    }

    private static void destabilise(TileInfusionMatrix matrix) {
        if (matrix.crafting) {
            if (matrix.instability < STABILITY_PENALTY) matrix.instability = STABILITY_PENALTY;
        } else {
            if (matrix.symmetry < STABILITY_PENALTY) matrix.symmetry = STABILITY_PENALTY;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("matrixFound", matrixFound);
        tag.setInteger("matrixX", matrixX);
        tag.setInteger("matrixY", matrixY);
        tag.setInteger("matrixZ", matrixZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        matrixFound = tag.getBoolean("matrixFound");
        matrixX = tag.getInteger("matrixX");
        matrixY = tag.getInteger("matrixY");
        matrixZ = tag.getInteger("matrixZ");
    }
}
