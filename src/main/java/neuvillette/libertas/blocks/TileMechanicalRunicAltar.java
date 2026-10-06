package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.recipe.RecipeRuneAltar;

/// 机械符文祭坛的 TileEntity: BotanicalMachinery 同款设计 (几何模型也来自该 mod)。
/// 自动运行 Botania 符文祭坛 (runic altar) 配方:
/// - 1 个活石槽: 每运行一次配方消耗 1 个活石 (与原版祭坛"合成时投活石"一致);
/// - 16 个配料槽: 紧凑排列后与 BotaniaAPI.runeAltarRecipes 逐配方匹配;
/// - 16 个输出槽: 产物进槽, 放不下则该配方暂停 (配料不消耗);
/// - 魔力: 自带魔力缓冲 (上限见 {@link TileManaBase}), 可被魔力束充能,
/// 并自动从相邻的魔力池 ({@link IManaPool}) 抽取。
///
/// 与原版祭坛的差异: 配料中的符文照常消耗 (原版会返还符文, 机械化后若不消耗,
/// 以符文为配方的 2/3 阶符文配方会每 tick 重复合成)。
///
/// 自动化 (漏斗/管道) 经 {@link ISidedInventory} 接触物品栏: 可向活石槽投活石、
/// 向配料槽投料, 但只能从输出槽抽出 (见 {@link #canExtractItem})。
public class TileMechanicalRunicAltar extends TileManaBase implements IInventory, ISidedInventory {

    /// 活石槽下标
    public static final int SLOT_LIVINGROCK = 0;
    /// 配料槽数量 (与原版祭坛一致, 最多 16 件配料)
    public static final int INPUT_SLOTS = 16;
    /// 配料槽起始下标
    public static final int SLOT_INPUT_START = SLOT_LIVINGROCK + 1;
    /// 输出槽数量
    public static final int OUTPUT_SLOTS = 16;
    /// 输出槽起始下标
    public static final int SLOT_OUTPUT_START = SLOT_INPUT_START + INPUT_SLOTS;
    public static final int TOTAL_SLOTS = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    /// 从相邻魔力池的抽取速率 (点/tick, 与魔力池-魔力物品的传输速率一致)
    public static final int PULL_RATE = 1000;

    private static final String TAG_MANA_ITEM = "AltarMana";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private static final String SOUND_CRAFT = "botania:runeAltarCraft";
    private static final int SOUND_COOLDOWN = 20;

    /// Botania 活石方块懒解析缓存 (preInit 注册, 使用时早已就位)
    private static Item livingrockItem;

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];

    private int soundCooldown;

    // -----------------------------------------------------------------------
    // 活石
    // -----------------------------------------------------------------------

    /// 物品是否是活石 (任意损伤值, 原版祭坛同样只按物品判断)
    public static boolean isLivingrock(ItemStack stack) {
        return stack != null && stack.getItem() == livingrockItem();
    }

    private static Item livingrockItem() {
        if (livingrockItem == null) {
            final Block block = GameRegistry.findBlock("Botania", "livingrock");
            livingrockItem = block == null ? null : Item.getItemFromBlock(block);
        }
        return livingrockItem;
    }

    // -----------------------------------------------------------------------
    // 每 tick: 魔力同步 + 从相邻魔力池抽魔力 + 运行符文祭坛配方
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

    /// 尝试运行一次符文祭坛配方: 配料匹配 + 魔力足够 + 活石在位 + 产物放得下才执行
    private void tryCraft() {
        if (inventory[SLOT_LIVINGROCK] == null) return;

        for (RecipeRuneAltar recipe : BotaniaAPI.runeAltarRecipes) {
            if (!matchesIngredients(recipe)) continue;

            final int cost = recipe.getManaUsage();
            if (getCurrentMana() < cost) continue;

            final ItemStack output = recipe.getOutput()
                .copy();
            if (!insertOutput(output, true)) continue;

            recieveMana(-cost);
            insertOutput(output, false);

            if (inventory[SLOT_LIVINGROCK].stackSize-- <= 1) inventory[SLOT_LIVINGROCK] = null;

            // 按配方所需精确消耗全部配料 (含符文; 原版祭坛返还符文, 这里若不消耗,
            // 以符文为配方的 2/3 阶符文配方会重复合成)
            consumeIngredients(recipe);

            if (soundCooldown == 0) {
                worldObj.playSoundEffect(xCoord, yCoord, zCoord, SOUND_CRAFT, 0.4f, 1f);
                soundCooldown = SOUND_COOLDOWN;
            }
            markDirty();
            return;
        }
    }

    /// 配料按"数量多重集"匹配: 每个配料槽的堆叠按其数量展开计份 (1 格 3 个树苗 = 3 份),
    /// 允许堆叠有富余 (配方要 3 个, 放 5 个也能做), 只要求配方所需都有货。
    /// 不能复用 RecipePetals.matches —— 那是按"槽"逐个比对的, 一格多件的多份配料凑不齐。
    private boolean matchesIngredients(RecipeRuneAltar recipe) {
        final int[] available = new int[TOTAL_SLOTS];
        for (int slot = SLOT_INPUT_START; slot < SLOT_OUTPUT_START; slot++) {
            available[slot] = inventory[slot] == null ? 0 : inventory[slot].stackSize;
        }

        inputs: for (Object input : recipe.getInputs()) {
            for (int slot = SLOT_INPUT_START; slot < SLOT_OUTPUT_START; slot++) {
                if (available[slot] > 0 && stackMatches(inventory[slot], input)) {
                    available[slot]--;
                    continue inputs;
                }
            }
            return false;
        }
        return true;
    }

    /// 按配方所需精确消耗配料 (与 {@link #matchesIngredients} 同序的贪心, 消耗的正是匹配时预留的份)
    private void consumeIngredients(RecipeRuneAltar recipe) {
        for (Object input : recipe.getInputs()) {
            for (int slot = SLOT_INPUT_START; slot < SLOT_OUTPUT_START; slot++) {
                final ItemStack stack = inventory[slot];
                if (stack != null && stackMatches(stack, input)) {
                    if (stack.stackSize-- <= 1) inventory[slot] = null;
                    break;
                }
            }
        }
    }

    /// 配料等价判定, 与 Botania RecipePetals 的语义一致:
    /// 字符串 = OreDict 条目 (通配损伤展开后 isItemEqual); 物品组 = 物品 + 损伤值精确相等
    private static boolean stackMatches(ItemStack stack, Object input) {
        if (input instanceof String) {
            for (ItemStack ostack : OreDictionary.getOres((String) input)) {
                ItemStack cstack = ostack.copy();
                if (cstack.getItemDamage() == Short.MAX_VALUE) cstack.setItemDamage(stack.getItemDamage());
                if (stack.isItemEqual(cstack)) return true;
            }
            return false;
        }
        if (input instanceof ItemStack) {
            final ItemStack req = (ItemStack) input;
            return stack.getItem() == req.getItem() && stack.getItemDamage() == req.getItemDamage();
        }
        return false;
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

    /// 把物品栏与魔力存量写进方块物品的 NBT (BlockMechanicalRunicAltar.getDrops 调用)
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
        return "container.libertas.mechanicalRunicAltar";
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
        if (slot == SLOT_LIVINGROCK) return isLivingrock(stack);
        return slot >= SLOT_INPUT_START && slot < SLOT_OUTPUT_START;
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

    /// 只有输出槽能被自动化抽出 (活石槽与配料槽只能手动取出)
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
