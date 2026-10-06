package neuvillette.libertas.blocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;
import vazkii.botania.api.mana.IManaPool;

/// 工业凝聚板的 TileEntity: BotanicalMachinery 同款设计 (几何模型也来自该 mod),
/// 把原版泰拉凝聚板多方块 (青金石/活石平台 + 凝聚板) 压缩进单个方块, 自动运行凝聚配方:
/// - 原版配方: 魔力钢锭 + 魔力珍珠 + 魔力钻石 → 泰拉钢 (与 TileTerraPlate 硬编码的配方一致);
/// - 16 个输入槽: 按"数量多重集"匹配 (堆叠有富余也行), 每次运行消耗恰好配方所需;
/// - 16 个输出槽: 产物进槽, 放不下则该配方暂停;
/// - 魔力: 每次配方固定消耗 {@link #MANA_PER_CRAFT} (500000 点, 与原版凝聚板一致),
/// 缓冲上限见 {@link TileManaBase}, 可被魔力束充能, 并自动从相邻魔力池抽取。
///
/// 自动化 (漏斗/管道) 经 {@link ISidedInventory} 接触物品栏: 可向输入槽投料,
/// 但只能从输出槽抽出 (见 {@link #canExtractItem})。
public class TileIndustrialAgglomerationPlate extends TileManaBase implements IInventory, ISidedInventory {

    public static final int INPUT_SLOTS = 16;
    public static final int OUTPUT_SLOTS = 16;
    /// 输出槽起始下标 (0..15 为输入)
    public static final int SLOT_OUTPUT_START = INPUT_SLOTS;
    public static final int TOTAL_SLOTS = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    /// 每次配方固定消耗的魔力 (原版泰拉凝聚板 MAX_MANA = 魔力池容量的一半)
    public static final int MANA_PER_CRAFT = 500000;

    /// 从相邻魔力池的抽取速率 (点/tick)
    public static final int PULL_RATE = 1000;

    private static final String TAG_MANA_ITEM = "PlateMana";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private static final String SOUND_CRAFT = "botania:terrasteelCraft";
    private static final int SOUND_COOLDOWN = 20;

    /// 原版凝聚板配方: 魔力钢锭(0) + 魔力珍珠(1) + 魔力钻石(2) → 泰拉钢(4)
    /// (1.7.10 无凝聚板配方 API, TileTerraPlate 硬编码同款; 想加配方往这里扩展)
    private static final int[] RECIPE_INPUT_META = { 0, 1, 2 };
    private static final int RECIPE_OUTPUT_META = 4;

    /// botania:manaResource 的懒解析缓存 (preInit 注册, 使用时早已就位)
    private static Item manaResourceItem;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];

    private int soundCooldown;

    // -----------------------------------------------------------------------
    // 每 tick: 魔力同步 + 从相邻魔力池抽魔力 + 运行凝聚配方
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (worldObj.isRemote) return;
        if (soundCooldown > 0) soundCooldown--;

        pullManaFromAdjacentPools();
        tryCraft();
    }

    /// 从六个相邻的魔力池抽取魔力填进自身缓冲
    private void pullManaFromAdjacentPools() {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (getCurrentMana() >= getMaxMana()) return;
            final TileEntity te = worldObj
                .getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            if (!(te instanceof IManaPool)) continue;
            final IManaPool pool = (IManaPool) te;
            final int amount = Math.min(PULL_RATE, Math.min(getMaxMana() - getCurrentMana(), pool.getCurrentMana()));
            if (amount <= 0) continue;
            pool.recieveMana(-amount);
            recieveMana(amount);
        }
    }

    /// 尝试运行一次凝聚配方: 配料匹配 + 魔力足够 + 产物放得下才执行
    private void tryCraft() {
        for (int meta : RECIPE_INPUT_META) {
            if (!hasIngredient(meta)) return;
        }
        if (getCurrentMana() < MANA_PER_CRAFT) return;

        final ItemStack output = new ItemStack(manaResource(), 1, RECIPE_OUTPUT_META);
        if (!insertOutput(output, true)) return;

        recieveMana(-MANA_PER_CRAFT);
        insertOutput(output, false);

        // 精确消耗: 每味配料 1 个
        for (int meta : RECIPE_INPUT_META) {
            consumeOne(meta);
        }

        if (soundCooldown == 0) {
            worldObj.playSoundEffect(xCoord, yCoord, zCoord, SOUND_CRAFT, 1f, 1f);
            soundCooldown = SOUND_COOLDOWN;
        }
        markDirty();
    }

    /// 输入槽里是否有指定损伤值的 manaResource (按数量多重集语义, 每味配方消耗 1 个)
    private boolean hasIngredient(int meta) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            final ItemStack stack = inventory[slot];
            if (stack != null && stack.getItem() == manaResource() && stack.getItemDamage() == meta) {
                return true;
            }
        }
        return false;
    }

    private void consumeOne(int meta) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            final ItemStack stack = inventory[slot];
            if (stack != null && stack.getItem() == manaResource() && stack.getItemDamage() == meta) {
                if (stack.stackSize-- <= 1) inventory[slot] = null;
                return;
            }
        }
    }

    private static Item manaResource() {
        if (manaResourceItem == null) manaResourceItem = GameRegistry.findItem("Botania", "manaResource");
        return manaResourceItem;
    }

    /// 产物塞进 16 个输出槽: 先并堆, 再进空槽; 返回是否全部放下。simulate 时不动库存也不改传入的栈
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
    // 挖掉保留内容: 物品栏 + 魔力存量写入掉落物的 NBT, 放置时恢复
    // -----------------------------------------------------------------------

    /// 把物品栏与魔力存量写进方块物品的 NBT (BlockIndustrialAgglomerationPlate.getDrops 调用)
    public void writeItemNBT(NBTTagCompound tag) {
        tag.setInteger(TAG_MANA_ITEM, getCurrentMana());
        tag.setTag(TAG_ITEMS, writeItems());
    }

    /// 方块放置时从物品 NBT 恢复物品栏与魔力存量
    public void readItemNBT(NBTTagCompound tag) {
        if (tag.hasKey(TAG_MANA_ITEM)) mana = tag.getInteger(TAG_MANA_ITEM);
        readItems(tag.getTagList(TAG_ITEMS, 10));
    }

    // -----------------------------------------------------------------------
    // IInventory (供 Container 的槽位直接读写)
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
        return "container.libertas.industrialAgglomerationPlate";
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

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot < INPUT_SLOTS;
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

    /// 只有输出槽能被自动化抽出 (输入槽只能手动取出)
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
        tag.setTag(TAG_ITEMS, writeItems());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readItems(tag.getTagList(TAG_ITEMS, 10));
    }
}
