package neuvillette.libertas.items;

import java.util.List;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import neuvillette.libertas.Libertas;

/// 棉签 (cottonSwab) - 采样工具: 右键生物把该生物记录在签上, 已有记录则覆盖更新;
/// 潜行右键空气擦除记录. boss 类生物 (IBossDisplayData) 无法被记录.
///
/// 记录存于 ItemStack NBT (EntityList 注册 id, 如 "Zombie"), 服务端落账;
/// 1.7.10 只要 getShareTag() 为真 (默认) 就会把完整 NBT 随背包/手部同步包发给客户端,
/// tooltip 可直接在客户端读取. 聊天反馈用 ChatComponentTranslation 发 key,
/// 由客户端按自己的语言渲染 (含嵌套的 entity.<id>.name).
public class ItemCottonSwab extends Item {

    /// NBT key: 当前记录的生物 (EntityList 注册 id).
    private static final String NBT_RECORDED_ENTITY = "libertasRecordedEntity";

    public ItemCottonSwab() {
        this.setUnlocalizedName("cottonSwab");
        this.setTextureName(Libertas.MODID + ":items/cottonSwab");
        this.setMaxStackSize(1);
        // 创造标签页在 ModItems.init 里设为 Libertas 专属标签页
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target) {
        if (target instanceof EntityPlayer) return false;
        // 客户端按相同判定直接消费本次点击 (否则客户端回落到 onItemRightClick 造成分叉), 数据只在服务端落账
        if (player.worldObj.isRemote) return true;

        final String id = EntityList.getEntityString(target);
        if (id == null) {
            player.addChatMessage(new ChatComponentTranslation("msg.libertas.cottonSwab.unrecordable"));
            return true;
        }
        if (target instanceof IBossDisplayData) {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "msg.libertas.cottonSwab.boss",
                    new ChatComponentTranslation("entity." + id + ".name")));
            return true;
        }

        stack.setTagInfo(NBT_RECORDED_ENTITY, new NBTTagString(id));
        player.addChatMessage(
            new ChatComponentTranslation(
                "msg.libertas.cottonSwab.recorded",
                new ChatComponentTranslation("entity." + id + ".name")));
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                if (getRecordedEntity(stack) != null) {
                    stack.getTagCompound()
                        .removeTag(NBT_RECORDED_ENTITY);
                    player.addChatMessage(new ChatComponentTranslation("msg.libertas.cottonSwab.cleared"));
                } else {
                    player.addChatMessage(new ChatComponentTranslation("msg.libertas.cottonSwab.noRecord"));
                }
            }
            player.swingItem();
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.cottonSwab.usage"));
        final String id = getRecordedEntity(stack);
        if (id == null) {
            tooltip.add(StatCollector.translateToLocal("tooltip.libertas.cottonSwab.empty"));
        } else {
            tooltip
                .add(StatCollector.translateToLocalFormatted("tooltip.libertas.cottonSwab.recorded", entityName(id)));
        }
    }

    /// 读取记录的生物 id, 无记录返回 null. 供未来其他功能 (MTE 等) 复用.
    public static String getRecordedEntity(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        final NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(NBT_RECORDED_ENTITY) ? tag.getString(NBT_RECORDED_ENTITY) : null;
    }

    /// 实体 id -> 本地化展示名; 没有 lang 条目的 id 原样显示.
    public static String entityName(String id) {
        final String key = "entity." + id + ".name";
        final String translated = StatCollector.translateToLocal(key);
        return translated.equals(key) ? id : translated;
    }
}
