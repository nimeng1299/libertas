package neuvillette.libertas.blocks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.ReflectionHelper;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.items.ItemCottonSwab;

/// 神奇的刷怪笼 (magicSpawner) 的 TileEntity: 槽 0 为输入槽 (只收棉签), 槽 1..16 为输出槽。
///
/// 自由运行的 10 秒时钟走满一轮时, 若输入槽的棉签记录了生物, 就用记录的 EntityList id
/// 空造一只该生物实例, 置 captureDrops 捕获掉落, 反射调用受保护 EntityLiving#dropFewItems(true, 0)
/// (SRG func_70628_a, 按"玩家击杀 + 无抢夺"语义掉落, 不真正生成实体/播放死亡事件),
/// 把捕获的掉落物并堆塞进 16 个输出槽; 放不下则本轮放弃 (计时照常清零, 下轮重试)。
///
/// 自动化 (漏斗/管道) 经 {@link ISidedInventory} 接触物品栏: 可向输入槽投棉签,
/// 但只能从输出槽抽出 (见 {@link #canExtractItem})。
public class TileMagicSpawner extends TileEntity implements IInventory, ISidedInventory {

    public static final int SLOT_INPUT = 0;
    /// 输出槽起始下标 (槽 0 为输入)
    public static final int SLOT_OUTPUT_START = 1;
    public static final int OUTPUT_SLOTS = 16;
    public static final int TOTAL_SLOTS = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    /// 击杀间隔: 10 秒
    public static final int KILL_INTERVAL_TICKS = 200;

    /// 击杀音效
    private static final String SOUND_KILL = "mob.endermen.portal";

    /// dropFewItems 的 MCP/SRG 双名 (开发环境走 MCP, 生产环境走 SRG)
    private static final String[] DROP_FEW_ITEMS_NAMES = { "dropFewItems", "func_70628_a" };

    private static final String TAG_TIMER = "KillTimer";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];

    /// 击杀时钟, 0..KILL_INTERVAL_TICKS 自由运行 (客户端也同步计数, 供 GUI 进度条)
    private int killTimer;

    // -----------------------------------------------------------------------
    // 每 tick: 计时 + 轮到时模拟击杀 (红石信号激活时暂停)
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        // 红石信号: 计时冻结 (双侧一致, GUI 进度条同步停走)
        if (isPausedByRedstone()) return;
        if (killTimer < KILL_INTERVAL_TICKS) killTimer++;
        // 走满归零对双侧执行 (客户端进度条才能循环滚动), 击杀只在服务端
        if (killTimer >= KILL_INTERVAL_TICKS) {
            killTimer = 0;
            if (worldObj.isRemote) return;
            simulateKillIfRecorded();
        }
    }

    /// 是否被红石信号暂停 (同 TNT/门的判定: 邻居的弱/强供电均算)
    public boolean isPausedByRedstone() {
        return worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
    }

    /// 输入槽的棉签记录了可生成生物时, 模拟击杀一次并把掉落收进输出槽
    private void simulateKillIfRecorded() {
        final String entityId = recordedEntityId();
        if (entityId == null) return;

        final World world = worldObj;
        final Entity entity = EntityList.createEntityByName(entityId, world);
        if (!(entity instanceof EntityLiving)) return;
        final EntityLiving living = (EntityLiving) entity;

        // 用 captureDrops 捕获掉落物 (Forge 字段, 不真正生成掉落实体)
        entity.setPositionAndRotation(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 0.0f, 0.0f);
        entity.captureDrops = true;
        final List<EntityItem> drops = new ArrayList<>();
        try {
            final Method dropFewItems = ReflectionHelper
                .findMethod(EntityLiving.class, living, DROP_FEW_ITEMS_NAMES, boolean.class, int.class);
            dropFewItems.invoke(living, true, 0); // 玩家击杀语义, 无抢夺
            drops.addAll(entity.capturedDrops);
        } catch (Exception e) {
            Libertas.LOG.warn("magic spawner failed to simulate a kill of {}", entityId, e);
            return;
        } finally {
            entity.captureDrops = false;
            entity.capturedDrops.clear();
            entity.setDead();
        }

        // 全部放得下才执行 (放不下则本轮放弃, 下轮重试)
        for (EntityItem item : drops) {
            if (item.getEntityItem() == null) continue;
            if (!insertOutput(item.getEntityItem(), true)) return;
        }
        boolean anyDropped = false;
        for (EntityItem item : drops) {
            if (item.getEntityItem() == null) continue;
            insertOutput(item.getEntityItem(), false);
            anyDropped = true;
        }
        if (anyDropped) {
            world.playSoundEffect(xCoord, yCoord, zCoord, SOUND_KILL, 0.2f, 1.4f);
            markDirty();
        }
    }

    /// 读取输入槽棉签记录的生物 id, 无棉签/无记录返回 null
    public String recordedEntityId() {
        final ItemStack swab = inventory[SLOT_INPUT];
        return swab != null && swab.getItem() instanceof ItemCottonSwab ? ItemCottonSwab.getRecordedEntity(swab) : null;
    }

    /// 击杀时钟当前值 (0..KILL_INTERVAL_TICKS), 供 GUI 进度条
    public int getKillTimer() {
        return killTimer;
    }

    /// 产物塞进 16 个输出槽: 先并堆, 再进空槽; 返回是否全部放下。simulate 时不动库存
    private boolean insertOutput(ItemStack output, boolean simulate) {
        int remaining = output.stackSize;
        for (int i = SLOT_OUTPUT_START; i < TOTAL_SLOTS && remaining > 0; i++) {
            final ItemStack target = inventory[i];
            if (target == null) {
                final int place = Math.min(remaining, Math.min(output.getMaxStackSize(), getInventoryStackLimit()));
                if (!simulate) inventory[i] = output.splitStack(place);
                remaining -= place;
            } else if (target.isItemEqual(output) && ItemStack.areItemStackTagsEqual(target, output)) {
                final int moved = Math.min(remaining, target.getMaxStackSize() - target.stackSize);
                if (!simulate) target.stackSize += moved;
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    // -----------------------------------------------------------------------
    // 挖掉保留内容: 物品栏写入掉落物的 NBT, 放置时恢复 (BlockMagicSpawner 调用)
    // -----------------------------------------------------------------------

    public void writeItemNBT(NBTTagCompound tag) {
        tag.setInteger(TAG_TIMER, killTimer);
        tag.setTag(TAG_ITEMS, writeItems());
    }

    public void readItemNBT(NBTTagCompound tag) {
        if (tag.hasKey(TAG_TIMER)) killTimer = tag.getInteger(TAG_TIMER);
        readItems(tag.getTagList(TAG_ITEMS, 10));
    }

    // -----------------------------------------------------------------------
    // IInventory
    // -----------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return TOTAL_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < TOTAL_SLOTS ? inventory[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (inventory[slot] == null) return null;
        if (inventory[slot].stackSize <= amount) {
            final ItemStack stack = inventory[slot];
            inventory[slot] = null;
            markDirty();
            return stack;
        }
        final ItemStack split = inventory[slot].splitStack(amount);
        if (inventory[slot].stackSize == 0) inventory[slot] = null;
        markDirty();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        final ItemStack stack = inventory[slot];
        inventory[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.libertas.magicSpawner";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    /// 只有输入槽收棉签
    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && stack != null && stack.getItem() instanceof ItemCottonSwab;
    }

    // -----------------------------------------------------------------------
    // ISidedInventory: 自动化的插入/抽取规则
    // -----------------------------------------------------------------------

    /// 所有面都可接触全部槽位; 能否插/抽由 canInsertItem / canExtractItem 决定
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        final int[] slots = new int[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return isItemValidForSlot(slot, stack);
    }

    /// 只有输出槽能被其他方块主动抽出 (输入槽只能手动取出)
    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot >= SLOT_OUTPUT_START;
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    private NBTTagList writeItems() {
        final NBTTagList list = new NBTTagList();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (inventory[i] == null) continue;
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setByte(TAG_SLOT, (byte) i);
            inventory[i].writeToNBT(entry);
            list.appendTag(entry);
        }
        return list;
    }

    private void readItems(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound entry = list.getCompoundTagAt(i);
            final int slot = entry.getByte(TAG_SLOT) & 255;
            if (slot < TOTAL_SLOTS) inventory[slot] = ItemStack.loadItemStackFromNBT(entry);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger(TAG_TIMER, killTimer);
        tag.setTag(TAG_ITEMS, writeItems());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey(TAG_TIMER)) killTimer = tag.getInteger(TAG_TIMER);
        readItems(tag.getTagList(TAG_ITEMS, 10));
    }
}
