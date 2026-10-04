package neuvillette.libertas.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import neuvillette.libertas.Libertas;

/// 深海回响 (deepSeaEcho) - 神镐: 挖掘等级 32, 任意方块固定 0.5s 挖掘时长, 无限耐久.
///
/// - 挖掘等级: [#setHarvestLevel] 注册 "pickaxe" 32 级, 且 [#canHarvestBlock] 恒真 -> 任何方块都能正常收割掉落;
/// - 固定时长: 原版可收割分支每 tick 挖掘进度 = 速度/硬度/30 (见 ForgeHooks.blockStrength), 所以监听
/// PlayerEvent.BreakSpeed, 在原版速度算完后直接把最终速度覆写为 硬度*30/10 -> 恒定 10 tick (0.5s) 挖完.
/// 该事件链在客户端 (PlayerControllerMP) 与服务端 (ItemInWorldManager) 都会触发, 两侧行为一致;
/// 硬度 <= 0 (瞬破方块/基岩类不可破坏) 与拿不到坐标的旧式调用不接管, 保持原版行为;
/// - 无限耐久: 普通 Item 耐久默认 0 (isDamageable 恒 false), 永不掉耐久也没有耐久条.
/// 副作用与 18cm 木棍相同: isItemTool 为 false, 附魔台不收; 反正固定时长下效率附魔也无意义.
public class ItemDeepSeaEcho extends Item {

    /// 固定挖掘时长: 10 tick = 0.5s.
    private static final float FIXED_BREAK_TICKS = 10.0F;

    public ItemDeepSeaEcho() {
        this.setUnlocalizedName("deepSeaEcho");
        this.setTextureName(Libertas.MODID + ":deepSeaEcho");
        this.setMaxStackSize(1);
        this.setMaxDamage(0); // 普通 Item 默认即为 0, 显式写出自文档
        this.setHarvestLevel("pickaxe", 32);
        // 物品构造于 preInit, 客户端/服务端两侧都会执行, 各注册一次事件监听
        MinecraftForge.EVENT_BUS.register(this);
        // 创造标签页在 ModItems.init 里设为 Libertas 专属标签页
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass) {
        return "pickaxe".equals(toolClass) ? 32 : super.getHarvestLevel(stack, toolClass);
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return true; // 32 级神镐: 收割判定恒真, 保证任何方块正常掉落
    }

    /// 固定 0.5s 挖掘: 事件在原版速度全部算完后触发, 直接覆写最终速度即可.
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        final ItemStack held = event.entityPlayer.getCurrentEquippedItem();
        if (held == null || held.getItem() != this) return;
        if (event.y < 0) return; // 旧式无坐标调用拿不到硬度, 保持原版速度
        final World world = event.entityPlayer.worldObj;
        final float hardness = event.block.getBlockHardness(world, event.x, event.y, event.z);
        if (hardness <= 0.0F) return; // 硬度 0 原版本就瞬破, 硬度 -1 不可破坏 (基岩等), 都不接管
        // 每 tick 进度 = speed / hardness / 30 -> 10 tick 挖完需要 speed = hardness * 30 / 10
        event.newSpeed = hardness * 30.0F / FIXED_BREAK_TICKS;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.deepSeaEcho.stats"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.deepSeaEcho.lore"));
    }
}
