package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.common.registry.GameRegistry;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.ManaNetworkEvent;
import vazkii.botania.api.recipe.RecipeManaInfusion;

/// 机械魔力池的 TileEntity: 一座装在玻璃罩里的魔力池, 接收魔力 (上限见 {@link TileManaBase}),
/// 并像 Botania 魔力池一样运行魔力注入 (mana infusion) 配方。
///
/// 与原版魔力池"物品扔进池子"不同, 这里改用物品栏:
/// - 16 个输入槽: 每 tick 逐槽匹配 BotaniaAPI.manaInfusionRecipes, 匹配即扣魔力产出;
/// - 16 个输出槽: 产物进槽, 放不下则该配方暂停 (输入不消耗);
/// - 1 个升级槽: 空 = 普通配方; 放炼金催化器 = 追加炼金配方; 放炼造催化器 = 追加炼造配方
/// (与 Botania 魔力池的催化器语义一致: 放催化器时普通配方依旧可用)。
///
/// 自动化 (漏斗/管道) 经 {@link ISidedInventory} 接触物品栏: 可向输入槽投料、向升级槽投催化器,
/// 但只能从输出槽抽出 (见 {@link #canExtractItem})。
public class TileMechanicalManaPool extends TileManaBase implements IManaPool, IInventory, ISidedInventory {

    public static final int INPUT_SLOTS = 16;
    public static final int OUTPUT_SLOTS = 16;
    public static final int SLOT_UPGRADE = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int TOTAL_SLOTS = SLOT_UPGRADE + 1;

    /// 升级槽状态: 空槽 (普通配方)
    public static final int MODE_NORMAL = 0;
    /// 升级槽放的是炼金催化器
    public static final int MODE_ALCHEMY = 1;
    /// 升级槽放的是炼造催化器
    public static final int MODE_CONJURATION = 2;

    private static final String TAG_MANA_ITEM = "PoolMana";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private static final String SOUND_CRAFT = "botania:manaPoolCraft";
    private static final int SOUND_COOLDOWN = 6;

    /// Botania 催化器方块懒解析缓存 (preInit 注册, 使用时早已就位)
    private static Block alchemyCatalystBlock;
    private static Block conjurationCatalystBlock;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];

    /// 是否已注册进 Botania 魔力网络 (功能花从中抽取魔力), 对应 ManaNetworkEvent 的 add/remove
    private boolean poolRegistered;
    private int soundCooldown;

    // -----------------------------------------------------------------------
    // 升级槽 / 催化器
    // -----------------------------------------------------------------------

    /// 升级槽当前状态: 普通 / 炼金 / 炼造
    public int getCatalystMode() {
        return catalystType(inventory[SLOT_UPGRADE]);
    }

    /// 物品对应的催化器类型 (非催化器返回 {@link #MODE_NORMAL})
    public static int catalystType(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return MODE_NORMAL;
        final Block block = ((ItemBlock) stack.getItem()).field_150939_a;
        if (block == alchemyCatalystBlock()) return MODE_ALCHEMY;
        if (block == conjurationCatalystBlock()) return MODE_CONJURATION;
        return MODE_NORMAL;
    }

    /// 物品是否是升级槽可用的催化器
    public static boolean isCatalyst(ItemStack stack) {
        return catalystType(stack) != MODE_NORMAL;
    }

    private static Block alchemyCatalystBlock() {
        if (alchemyCatalystBlock == null) alchemyCatalystBlock = GameRegistry.findBlock("Botania", "alchemyCatalyst");
        return alchemyCatalystBlock;
    }

    private static Block conjurationCatalystBlock() {
        if (conjurationCatalystBlock == null) {
            conjurationCatalystBlock = GameRegistry.findBlock("Botania", "conjurationCatalyst");
        }
        return conjurationCatalystBlock;
    }

    // -----------------------------------------------------------------------
    // Botania 魔力网络: 注册成魔力池, 功能花可直接抽取
    // -----------------------------------------------------------------------

    @Override
    public boolean isOutputtingPower() {
        return false;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unregisterFromManaNetwork();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unregisterFromManaNetwork();
    }

    private void unregisterFromManaNetwork() {
        if (poolRegistered) {
            poolRegistered = false;
            ManaNetworkEvent.removePool(this);
        }
    }

    // -----------------------------------------------------------------------
    // 每 tick: 魔力同步 + 逐输入槽运行魔力注入配方
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (worldObj.isRemote) return;
        if (soundCooldown > 0) soundCooldown--;

        // IManaPool 约定: 第一个 tick 注册进魔力网络 (功能花靠它找到可抽取的池子)
        if (!poolRegistered) {
            ManaNetworkEvent.addPool(this);
            poolRegistered = true;
        }

        final boolean alchemy = getCatalystMode() == MODE_ALCHEMY;
        final boolean conjuration = getCatalystMode() == MODE_CONJURATION;

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (inventory[slot] == null) continue;
            tryCraft(inventory[slot], slot, alchemy, conjuration);
        }
    }

    /// 对一个输入槽尝试运行配方: 匹配 + 魔力足够 + 产物放得下才执行 (all-or-nothing, 不留半成品)
    private void tryCraft(ItemStack input, int slot, boolean alchemy, boolean conjuration) {
        for (RecipeManaInfusion recipe : BotaniaAPI.manaInfusionRecipes) {
            if (!recipe.matches(input)) continue;
            // Botania 语义: 催化器只放开对应系列配方的准入, 普通配方任何模式下都能跑
            if (recipe.isAlchemy() && !alchemy) continue;
            if (recipe.isConjuration() && !conjuration) continue;

            final int cost = recipe.getManaToConsume();
            if (getCurrentMana() < cost) continue;

            final ItemStack output = recipe.getOutput()
                .copy();
            if (!insertOutput(output, true)) continue;

            recieveMana(-cost);
            insertOutput(output, false);

            if (input.stackSize-- <= 1) inventory[slot] = null;

            if (soundCooldown == 0) {
                worldObj.playSoundEffect(xCoord, yCoord, zCoord, SOUND_CRAFT, 0.2f, 4f);
                soundCooldown = SOUND_COOLDOWN;
            }
            markDirty();
            return;
        }
    }

    /// 产物塞进 16 个输出槽: 先并堆, 再进空槽; 返回是否全部放下。simulate 时不动库存也不改传入的栈
    private boolean insertOutput(ItemStack output, boolean simulate) {
        int remaining = output.stackSize;
        for (int i = INPUT_SLOTS; i < INPUT_SLOTS + OUTPUT_SLOTS && remaining > 0; i++) {
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

    /// 当前催化器模式的说明文本键 (GUI 用)
    public String getModeTooltipKey() {
        switch (getCatalystMode()) {
            case MODE_ALCHEMY:
                return "libertas.gui.mechanicalManaPool.mode.alchemy";
            case MODE_CONJURATION:
                return "libertas.gui.mechanicalManaPool.mode.conjuration";
            default:
                return "libertas.gui.mechanicalManaPool.mode.normal";
        }
    }

    // -----------------------------------------------------------------------
    // 挖掉保留内容: 物品栏 + 魔力存量写入掉落物的 NBT, 放置时恢复
    // -----------------------------------------------------------------------

    /// 把物品栏与魔力存量写进方块物品的 NBT (BlockMechanicalManaPool.getDrops 调用)
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
        return "container.libertas.mechanicalManaPool";
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
        if (slot == SLOT_UPGRADE) return isCatalyst(stack);
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

    /// 只有输出槽能被自动化抽出 (输入槽与升级槽只能手动取出)
    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot >= INPUT_SLOTS && slot < INPUT_SLOTS + OUTPUT_SLOTS;
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
