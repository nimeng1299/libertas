package neuvillette.libertas.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.wands.ItemWandCasting;

/// 紫色心情 (purpleMood) - 自动充能的法杖 (继承 TC4 ItemWandCasting, 对源质/焦点/注魔等全套法杖 API 透明).
///
/// 每种基础元素 (aer/terra/ignis/aqua/ordo/perditio) 各存 1000 点; 手持时每秒给每种元素独立随机
/// 充入 0~10 点 (服务端结算, NBT 记下次秒时间戳, 容器自动同步到客户端).
///
/// TC4 的 vis 内部按 x100 存储 (WandRod capacity 25 -> getMaxVis 2500, addVis 每点入账 100),
/// 所以 "1000 点" 在 [#getMaxVis] 里要返回 100000; 充能/消耗/悬浮窗显示都走 x100 之前的显示单位.
public class ItemPurpleMood extends ItemWandCasting {

    /// 每种元素的可存储上限 (显示单位).
    public static final int MAX_VIS_PER_ASPECT = 1000;
    /// 充能间隔: 每秒结算一次.
    private static final int CHARGE_INTERVAL_TICKS = 20;
    /// 每次结算每种元素最多充入的点数 (nextInt 上界, 实际 0~10 含两端).
    private static final int MAX_CHARGE_PER_ASPECT = 10;
    /// NBT key: 下一次允许结算充能的世界时间.
    private static final String NBT_NEXT_CHARGE = "libertasNextCharge";

    public ItemPurpleMood() {
        this.setUnlocalizedName("purpleMood");
        // 创造标签页在 ModItems.init 里设为 Libertas 专属标签页
        // tooltip 不覆写: 基类 addInformation 就是神秘原生的法杖样式 (元素彩色数值 + 金色容量行, shift 看明细)
    }

    @Override
    public int getMaxVis(ItemStack stack) {
        return MAX_VIS_PER_ASPECT * 100;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        // 基类实现用 %CAP/%ROD 拼木杖/杖帽名, 本杖固定命名
        return StatCollector.translateToLocal(getUnlocalizedName() + ".name");
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean currentItem) {
        // 基类 onUpdate 服务端直接强转 EntityPlayer, 先挡一层
        if (!world.isRemote && entity instanceof EntityPlayer) {
            super.onUpdate(stack, world, entity, slot, currentItem);
            if (currentItem) {
                chargeTick(stack, world);
            }
        }
    }

    /// 每秒一次: 六种基础元素各自独立随机 +0~MAX_CHARGE_PER_ASPECT 点, addVis 内部自动封顶.
    private void chargeTick(ItemStack stack, World world) {
        final long now = world.getTotalWorldTime();
        final long next = stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(NBT_NEXT_CHARGE) ? stack.getTagCompound()
                .getLong(NBT_NEXT_CHARGE) : 0L;
        if (now < next) return;

        stack.setTagInfo(NBT_NEXT_CHARGE, new NBTTagLong(now + CHARGE_INTERVAL_TICKS));
        for (Aspect aspect : Aspect.getPrimalAspects()) {
            addVis(stack, aspect, world.rand.nextInt(MAX_CHARGE_PER_ASPECT + 1), true);
        }
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // 全 0 元素: 无 NBT 即六元素全空, 方便从零调试充能
        list.add(new ItemStack(item, 1, 0));
        final ItemStack full = new ItemStack(item, 1, 0);
        for (Aspect aspect : Aspect.getPrimalAspects()) {
            addVis(full, aspect, MAX_VIS_PER_ASPECT, true);
        }
        list.add(full);
    }
}
