package neuvillette.libertas.mixin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emoniph.witchery.Witchery;
import com.emoniph.witchery.blocks.BlockAltar;

import neuvillette.libertas.blocks.BlockSpiritSpring;
import neuvillette.libertas.blocks.ModBlocks;

/// 巫术(Witchery)祭坛 ({@code BlockAltar$TileEntityAltar}) 的灵泉增幅。
///
/// Witchery 祭坛的数值模型 (0.10.x):
/// - 基础魔力容量 {@code maxPower}: {@code updatePower} 扫描祭坛周围 29x29x29 的自然方块
/// (树/草/花/水等, 每种有固定分值) 累加得出, 即"用智慧之书测量自然";
/// - 文物倍率 {@code powerScale}(容量) / {@code rechargeScale}(回魔): {@code updateArtefacts}
/// 检查祭坛正上方的文物 (圣杯/头颅/烛台/无限之蛋等), 每次重算都从 1 重新推导;
/// - 实际容量上限 = maxPower * powerScale, 回魔速度 = 每秒 10 * rechargeScale 点。
///
/// 本 Mixin 不另起一套字段改写, 而是挂在 Witchery 自己的两处重算出口:
/// 每次重算先得出 Witchery 的原始结果, 之后若祭坛正上方是灵泉则追加灵泉的倍率。
/// 因此数值永远从 Witchery 的结果重新推导 - 灵泉放上/拆下由祭坛的邻居重算自动生效/还原,
/// 与原生文物(圣杯等)完全兼容, 也不会互相覆盖。
///
/// 两个方法都只会在祭坛核心 TE 上被调用 (isValidAndUpdate / onNeighborBlockChange 均先取 core),
/// 所以灵泉检查用与 Witchery 一致的水平泛洪遍历整个相连祭坛结构, 多方块祭坛的任意一格正上方
/// 放灵泉都生效; 同一祭坛放多个灵泉不叠加, 只算一次。
@Mixin(BlockAltar.TileEntityAltar.class)
public abstract class TileEntityAltarMixin {

    /// 泛洪遍历的安全上限 (Witchery 的连通判定本身没有上限, 正常祭坛远小于此值)
    private static final int MAX_ALTAR_BLOCKS = 256;

    @Shadow(remap = false)
    private float maxPower;
    @Shadow(remap = false)
    private int powerScale;
    @Shadow(remap = false)
    private int rechargeScale;

    /// 文物重算出口: 在 Witchery 算出 powerScale/rechargeScale 之后整体 ×100。
    /// 重算以本地变量从零推导, 这里只做一次乘法, 反复触发也不会累加。
    @Inject(method = "updateArtefacts", at = @At("TAIL"), remap = false)
    private void libertas$amplifyScales(CallbackInfo ci) {
        if (spiritSpringOnAltar()) {
            powerScale *= BlockSpiritSpring.CAPACITY_MULTIPLIER;
            rechargeScale *= BlockSpiritSpring.RECHARGE_MULTIPLIER;
        }
    }

    /// 基础容量重算出口: 注入点挂在"maxPower 被写成本次扫描值"的写指令上 (updatePower 里唯一的
    /// putfield maxPower, 位于比较 total != maxPower 的写入分支), 在其之后追加 +10。
    /// updatePower 的 5 秒重扫限流早退路径不经过该写入点, 所以不会在限流期间重复累加。
    @Inject(
        method = "updatePower",
        at = @At(
            value = "FIELD",
            target = "Lcom/emoniph/witchery/blocks/BlockAltar$TileEntityAltar;maxPower:F",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER),
        remap = false)
    private void libertas$addBaseCapacity(CallbackInfo ci) {
        if (spiritSpringOnAltar()) {
            maxPower += BlockSpiritSpring.BASE_CAPACITY_BONUS;
        }
    }

    /// 检查本祭坛结构的任意一格正上方是否有灵泉: 从自身(核心)出发, 沿水平相邻的 Witchery 祭坛
    /// 方块泛洪 (与 updateArtefacts 判定多方块祭坛用的连通口径一致), 逐格查看 y+1。
    private boolean spiritSpringOnAltar() {
        final TileEntity self = (TileEntity) (Object) this;
        final World world = self.getWorldObj();
        if (world.isRemote) return false;
        final Block altar = Witchery.Blocks.ALTAR;
        final Set<Long> visited = new HashSet<>();
        final Deque<int[]> queue = new ArrayDeque<>();
        final int[] start = { self.xCoord, self.yCoord, self.zCoord };
        visited.add(packPos(start[0], start[1], start[2]));
        queue.add(start);
        while (!queue.isEmpty() && visited.size() <= MAX_ALTAR_BLOCKS) {
            final int[] c = queue.poll();
            if (world.getBlock(c[0], c[1] + 1, c[2]) == ModBlocks.spiritSpring) return true;
            for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                final int nx = c[0] + dir[0];
                final int nz = c[2] + dir[1];
                final long key = packPos(nx, c[1], nz);
                if (!visited.contains(key) && world.getBlock(nx, c[1], nz) == altar) {
                    visited.add(key);
                    queue.add(new int[] { nx, c[1], nz });
                }
            }
        }
        return false;
    }

    private static long packPos(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }
}
