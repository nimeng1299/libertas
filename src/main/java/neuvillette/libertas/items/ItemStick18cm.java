package neuvillette.libertas.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import neuvillette.libertas.Libertas;

/// 18cm的木棍 - 吞噬经验的木棍武器 (只能通过代码 stack.addEnchantment 等附魔).
///
/// 经验内储: 手持 sneak+右键 将玩家全部经验 (experienceTotal) 吸入物品 NBT, 创造模式固定 +1000;
/// 伤害 = 武器经验等级 (原版 xpBarCap 算法), tooltip 显示等级/本等级溢出经验/距下一级所需经验.
///
/// 不可通过附魔台 / 铁砧附魔:
/// - 耐久为 0 (不消耗耐久), 原版 Item.isItemTool 判定为 false, 附魔台直接不收;
/// - [#getItemEnchantability] 返回 0, 即使放进去也摇不出附魔;
/// - [#isBookEnchantable] 返回 false, 铁砧上无法与附魔书合成;
/// - setNoRepair + [#getIsRepairable] 返回 false, 铁砧上无法修复/同名合并.
/// 以上都不影响 NBT 层面的附魔, 所以 stack.addEnchantment / 指令附魔依旧生效.
public class ItemStick18cm extends ItemSword {

    /// NBT key holding the weapon's stored XP points (long).
    private static final String NBT_XP = "WeaponXP";
    /// Stored-XP clamp; also keeps levelFromXP's arithmetic comfortably inside long range.
    private static final long MAX_STORED_XP = Long.MAX_VALUE / 8;
    /// levelFromXP binary-search upper bound: pointsToReach(MAX_LEVEL) ~ 7.9e18, no long overflow.
    private static final int MAX_LEVEL = 1_500_000_000;

    public ItemStick18cm() {
        super(ToolMaterial.WOOD);
        this.setUnlocalizedName("18cm");
        // 无限耐久: 最大耐久设为 0 -> isDamageable() 恒为 false, damageItem 永远不生效
        this.setMaxDamage(0);
        this.setNoRepair();
        this.setCreativeTab(CreativeTabs.tabCombat);
        // Fallback icon for non-IItemRenderer code paths. Resolves to
        // assets/libertas/textures/blocks/sakura_tech_stick.png.
        this.setTextureName(Libertas.MODID + ":blocks/sakura_tech_stick");
    }

    // ==================== 经验存取 ====================

    public static long getStoredXP(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(NBT_XP) ? stack.getTagCompound()
                .getLong(NBT_XP) : 0L;
    }

    private static void setStoredXP(ItemStack stack, long xp) {
        NBTTagCompound tag;
        if (stack.hasTagCompound()) {
            tag = stack.getTagCompound();
        } else {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setLong(NBT_XP, Math.min(MAX_STORED_XP, Math.max(0L, xp)));
    }

    // ==================== 原版经验等级算法 (EntityPlayer.xpBarCap, 1.7.10) ====================

    /// 每一级的经验条容量, 与 EntityPlayer.xpBarCap 逐项一致.
    private static long xpBarCap(int level) {
        if (level >= 30) return 62L + (level - 30) * 7L;
        if (level >= 15) return 17L + (level - 15) * 3L;
        return 17L;
    }

    /// 升到 level 级所需的总经验 (level 0..level-1 的 xpBarCap 之和, 分段闭式).
    private static long pointsToReach(int level) {
        if (level <= 0) return 0L;
        if (level <= 15) return 17L * level;
        if (level <= 30) {
            long n = level - 15;
            return 255L + 17L * n + 3L * n * (n - 1) / 2L;
        }
        long n = level - 30;
        return 825L + 62L * n + 7L * n * (n - 1) / 2L;
    }

    /// 总经验 -> 当前等级, 对 pointsToReach 二分逆算 (逐级累加在等级很高时太慢, tooltip 每帧都会调用).
    private static int levelFromXP(long xp) {
        int lo = 0;
        int hi = MAX_LEVEL;
        while (lo < hi) {
            int mid = (int) (((long) lo + hi + 1) >>> 1);
            if (pointsToReach(mid) <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    public int getWeaponLevel(ItemStack stack) {
        return levelFromXP(getStoredXP(stack));
    }

    // ==================== 交互 ====================

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                long added;
                if (player.capabilities.isCreativeMode) {
                    added = 1000L;
                } else {
                    added = player.experienceTotal;
                    player.experienceTotal = 0;
                    player.experienceLevel = 0;
                    player.experience = 0.0F;
                }
                if (added > 0) {
                    setStoredXP(stack, getStoredXP(stack) + added);
                    world.playSoundAtEntity(player, "random.orb", 0.6F, 1.0F);
                    player.addChatComponentMessage(
                        new ChatComponentTranslation("msg.libertas.18cm.absorb", added, getWeaponLevel(stack)));
                }
            }
            return stack;
        }
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        long xp = getStoredXP(stack);
        int level = levelFromXP(xp);
        long into = xp - pointsToReach(level);
        long cap = xpBarCap(level);
        tooltip.add(StatCollector.translateToLocalFormatted("tooltip.libertas.18cm.level", level));
        tooltip.add(StatCollector.translateToLocalFormatted("tooltip.libertas.18cm.progress", into, cap, cap - into));
    }

    // ==================== 伤害 = 经验等级 ====================

    /// 攻击力 = 经验等级. 玩家 attackDamage 属性基础值为 1.0 (EntityPlayer.applyEntityAttributes), 修改器取
    /// level - 1 即总伤害等于等级; 但总伤害为 0 时原版 attackTargetEntityWithCurrentItem 会直接跳过攻击,
    /// 所以修改器至少钳到 0 (总伤害至少 1). 攻击力不过 0 时不放修改器, tooltip 也就不显示多余的 +0 行.
    /// 装备 NBT 变化时 EntityLivingBase.onUpdate 会自动重算属性修改器, 存经验后无需手动刷新.
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack) {
        Multimap<String, AttributeModifier> map = HashMultimap.create();
        int amount = Math.max(0, getWeaponLevel(stack) - 1);
        if (amount > 0) {
            map.put(
                SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
                new AttributeModifier(field_111210_e, "Weapon modifier", (double) amount, 0));
        }
        return map;
    }
}
