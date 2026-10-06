package neuvillette.libertas.botania;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import vazkii.botania.api.subtile.SubTileGenerating;
import vazkii.botania.api.subtile.signature.BasicSignature;

/// 风息花: 恒定产能花, 每 tick 产出 {@link #GENERATION_PER_TICK} 点魔力。
///
/// 走 SubTileGenerating 的被动产出通道: canGeneratePassively 恒真、间隔 1 tick、单次 5000,
/// 产出的魔力先存入花内缓冲再流入相连的魔力池; 池满时在缓冲内暂存, 饱和即停止产出。
///
/// 不枯萎: Botania 的枯萎(passiveDecayTicks 衰减成枯灌木)只对 isPassiveFlower()==true 的
/// 花生效(向阳花/暗影花/水仙花这类), 普通产能花默认非 passive, 天生不受影响。
public class SubTileZephyrBloom extends SubTileGenerating {

    /// 每 tick 产出的魔力量。
    public static final int GENERATION_PER_TICK = 5000;
    /// 花内魔力缓冲(魔力池已满时暂存, 上限即缓冲上限)。
    private static final int BUFFER = 10000;
    /// HUD/粒子的主题色(薄荷青)。
    private static final int COLOR = 0x7FE0C8;

    @Override
    public boolean canGeneratePassively() {
        return true;
    }

    @Override
    public int getDelayBetweenPassiveGeneration() {
        return 1;
    }

    @Override
    public int getValueForPassiveGeneration() {
        return GENERATION_PER_TICK;
    }

    @Override
    public int getMaxMana() {
        return BUFFER;
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    /// 特花的物品签名: 图标沿用 BasicSignature(botania 资源域贴图), 在 tooltip 里补一行效果说明。
    public static final class Signature extends BasicSignature {

        public Signature() {
            super(ModBotania.SUBTILE_ZEPHYR_BLOOM);
        }

        @Override
        public void addTooltip(ItemStack stack, EntityPlayer player, List<String> tooltip) {
            super.addTooltip(stack, player, tooltip);
            tooltip.add(StatCollector.translateToLocal("tooltip.libertas.zephyrBloom.effect"));
        }
    }
}
