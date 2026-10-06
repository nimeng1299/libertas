package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.emoniph.witchery.blocks.BlockAltar;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.ClientProxy;

/// 灵泉 (spiritSpring) - 一泓小小的灵泉, 架在巫术(Witchery)祭坛正上方, 祭坛便会涌出不尽的魔力。
///
/// 具体增幅 (基础魔力容量 +10, 容量上限 ×100, 魔力恢复 ×100) 由
/// {@link neuvillette.libertas.mixin.TileEntityAltarMixin} 挂进 Witchery 祭坛自己的
/// updatePower/updateArtefacts 重算流程实现, 本方块只负责外观与"放置/拆除时立刻通知祭坛重算"。
/// 灵泉不需要 TileEntity - 祭坛的邻居变化本身就会触发文物重算, 数值自动增减、无每刻开销。
public class BlockSpiritSpring extends Block {

    /// 基础魔力容量加值 (直接加在祭坛 maxPower 上, 在 ×powerScale 之前生效)
    public static final float BASE_CAPACITY_BONUS = 10.0f;
    /// 容量倍率 (乘在祭坛 powerScale 上)
    public static final int CAPACITY_MULTIPLIER = 100;
    /// 回魔倍率 (乘在祭坛 rechargeScale 上)
    public static final int RECHARGE_MULTIPLIER = 100;

    public BlockSpiritSpring() {
        super(Material.rock);
        setBlockName("spiritSpring");
        setHardness(1.5f);
        setResistance(8.0f);
        setStepSound(soundTypeStone);
        setLightLevel(0.75f);
        // 碰撞箱/选取框贴着模型本体 (泉池 x/z 2..14, 水滴晶顶到 y 9.5)
        setBlockBounds(2.0f / 16f, 0.0f, 2.0f / 16f, 14.0f / 16f, 9.5f / 16f, 14.0f / 16f);
        setHarvestLevel("pickaxe", 0);
    }

    // ---- 渲染: 与碧空之泪同一条 JSON 模型管线 (id 在 ClientProxy.init 里注册) ----

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderType() {
        return ClientProxy.SPIRIT_SPRING_RENDER_ID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    // ---- 祭坛增幅: 放置/拆除时立刻通知正下方祭坛重算, 不必等玩家右键或仪式触发 ----

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        nudgeAltarBelow(world, x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block oldBlock, int oldMeta) {
        nudgeAltarBelow(world, x, y, z);
        super.breakBlock(world, x, y, z, oldBlock, oldMeta);
    }

    /// 灵泉直接架在祭坛上方: 放置/拆除灵泉都会作为祭坛的邻居变化触发文物重算(容量/回魔倍率即时生效),
    /// 这里再调一次 Witchery 自己的公共入口 isValidAndUpdate (右键祭坛也走它),
    /// 让基础容量的 +10 同样立刻重扫, 不必等下一次仪式。
    private static void nudgeAltarBelow(World world, int x, int y, int z) {
        if (world.isRemote) return;
        final TileEntity te = world.getTileEntity(x, y - 1, z);
        if (te instanceof BlockAltar.TileEntityAltar) {
            ((BlockAltar.TileEntityAltar) te).isValidAndUpdate();
        }
    }
}
