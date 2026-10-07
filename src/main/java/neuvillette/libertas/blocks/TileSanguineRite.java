package neuvillette.libertas.blocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import WayofTime.alchemicalWizardry.api.altarRecipeRegistry.AltarRecipe;
import WayofTime.alchemicalWizardry.api.altarRecipeRegistry.AltarRecipeRegistry;
import WayofTime.alchemicalWizardry.common.tileEntity.TEAltar;
import vazkii.botania.api.internal.VanillaPacketDispatcher;

/// 猩红祭仪的 TileEntity: 自动把输入槽的物品投进正后方的血魔法血之祭坛 (TEAltar),
/// 祭坛完成后把产物收回输出槽。
///
/// - 输入槽有物品、后方是血之祭坛、祭坛空置且不活跃、且 {@link AltarRecipeRegistry}
/// 存在"该物品 + 祭坛当前等级"匹配的配方 (产物非空, 注魔球充能类配方产物为空, 跳过) 时,
/// 把整组输入放进祭坛并立即 {@link TEAltar#startCycle()};
/// - 投入的原始物品记在 {@link #insertedStack}, 之后祭坛槽位不再是该物品即视为完成,
/// 产物移入输出槽 (放不下则留在祭坛里, 下个 tick 重试);
/// - 一次只跟踪一件物品; 输入槽有货时每 tick 重试, 直到等级/结构就绪。
///
/// 自动化 (漏斗/管道) 经 {@link ISidedInventory}: 只能向输入槽插入, 只能从输出槽抽出。
public class TileSanguineRite extends TileEntity implements IInventory, ISidedInventory {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int TOTAL_SLOTS = 2;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_INSERTED = "Inserted";
    private static final String TAG_LIQUID = "LiquidRequired";

    private final ItemStack[] inventory = new ItemStack[TOTAL_SLOTS];

    /// 已投入祭坛的输入物品 (非 null 即在跟踪); 祭坛完成后其槽位不再是该物品
    private ItemStack insertedStack;
    /// 跟踪中配方所需的血量 (GUI 进度显示用)
    private int liquidRequired;

    // -----------------------------------------------------------------------
    // 每 tick: 有输入则尝试投放; 跟踪中则轮询祭坛
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        tryStartCraft();
        if (insertedStack != null) {
            pollAltar();
            // 注入期间持续刷新, 让 GUI 的进度文字走节流同步窗口
            syncPending = true;
        }
        syncToClients();
    }

    /// 正面朝向 (放置时确定, meta 2..5); 血之祭坛必须在正后方
    public ForgeDirection getFacing() {
        return ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
    }

    /// 正后方的血之祭坛, 不是祭坛时返回 null
    public TEAltar getAltar() {
        final ForgeDirection back = getFacing().getOpposite();
        final TileEntity te = worldObj
            .getTileEntity(xCoord + back.offsetX, yCoord + back.offsetY, zCoord + back.offsetZ);
        return te instanceof TEAltar ? (TEAltar) te : null;
    }

    /// 尝试把输入槽整组物品投进后方祭坛
    private void tryStartCraft() {
        if (insertedStack != null || inventory[SLOT_INPUT] == null) return;

        final TEAltar altar = getAltar();
        if (altar == null || altar.isActive() || altar.getStackInSlot(0) != null) return;

        final AltarRecipe recipe = findRecipe(inventory[SLOT_INPUT], altar.getTier());
        if (recipe == null) return;
        // 产物放不进输出槽就不执行, 免得物品滞留在祭坛里
        if (!canOutputFit(recipe, inventory[SLOT_INPUT].stackSize)) return;

        final ItemStack inserted = inventory[SLOT_INPUT].copy();
        altar.setInventorySlotContents(0, inserted);
        inventory[SLOT_INPUT] = null;
        insertedStack = inserted;
        liquidRequired = recipe.getLiquidRequired();
        altar.startCycle();
        markDirty();
    }

    /// 产物 (数量随整组输入倍增, 与 TEAltar 完成时的逻辑一致) 能否完整放进输出槽
    private boolean canOutputFit(AltarRecipe recipe, int inputCount) {
        final ItemStack result = recipe.getResult();
        final int needed = result.stackSize * inputCount;
        final ItemStack output = inventory[SLOT_OUTPUT];

        if (output == null) {
            return needed <= Math.min(result.getMaxStackSize(), getInventoryStackLimit());
        }
        return output.isItemEqual(result) && ItemStack.areItemStackTagsEqual(output, result)
            && output.stackSize <= output.getMaxStackSize() - needed;
    }

    /// 第一个"物品 + 等级"匹配且产物非空的祭坛配方 (等级不足时 doesRequiredItemMatch 自行否决)
    private static AltarRecipe findRecipe(ItemStack stack, int tier) {
        for (AltarRecipe recipe : AltarRecipeRegistry.altarRecipes) {
            if (recipe.getResult() != null && recipe.doesRequiredItemMatch(stack, tier)) return recipe;
        }
        return null;
    }

    /// 轮询祭坛: 槽位不再是投入的物品即完成 (或被人取走), 产物收进输出槽
    private void pollAltar() {
        final TEAltar altar = getAltar();
        if (altar == null) {
            // 祭坛被拆, 里面的物品已随祭坛破坏掉落, 放弃跟踪
            resetRite();
            return;
        }
        final ItemStack current = altar.getStackInSlot(0);
        if (ItemStack.areItemStacksEqual(current, insertedStack)) return;

        if (current != null && !takeResult(altar)) return;
        resetRite();
    }

    /// 祭坛产物收进输出槽 (可并堆); 放不下返回 false, 产物留在祭坛下 tick 重试
    private boolean takeResult(TEAltar altar) {
        final ItemStack result = altar.getStackInSlot(0);
        final ItemStack output = inventory[SLOT_OUTPUT];

        if (output == null) {
            inventory[SLOT_OUTPUT] = result;
            altar.setInventorySlotContents(0, null);
            markDirty();
            return true;
        }
        if (output.isItemEqual(result) && ItemStack.areItemStackTagsEqual(output, result)) {
            final int moved = Math.min(result.stackSize, output.getMaxStackSize() - output.stackSize);
            if (moved <= 0) return false;
            output.stackSize += moved;
            result.stackSize -= moved;
            altar.setInventorySlotContents(0, result.stackSize > 0 ? result : null);
            markDirty();
            return true;
        }
        return false;
    }

    /// 重置按钮/祭坛丢失: 忘记当前跟踪 (已在祭坛里的物品须自行取回)
    public void resetRite() {
        insertedStack = null;
        liquidRequired = 0;
        markDirty();
    }

    // -----------------------------------------------------------------------
    // GUI 状态 (经 getDescriptionPacket 同步)
    // -----------------------------------------------------------------------

    public boolean isTracking() {
        return insertedStack != null;
    }

    public int getLiquidRequired() {
        return liquidRequired;
    }

    /// 祭坛当前注入进度 (0..liquidRequired), 不在跟踪时为 0
    public int getProgress() {
        final TEAltar altar = getAltar();
        return insertedStack != null && altar != null ? altar.getProgress() : 0;
    }

    private boolean syncPending;
    private int syncTimer;

    /// 状态变化后节流下发 (每 10 tick 至多一次, TileManaBase 同款)
    private void syncToClients() {
        if (syncPending && ++syncTimer % 10 == 0) {
            syncPending = false;
            VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
        }
    }

    private void markDirtyAndSync() {
        markDirty();
        syncPending = true;
    }

    /// 打开 GUI 时立即下发一次状态, 避免状态文字短暂为空 (BlockSanguineRite.onBlockActivated 调用)
    public void syncNow() {
        if (worldObj != null && !worldObj.isRemote) {
            syncPending = false;
            VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
        }
    }

    // -----------------------------------------------------------------------
    // 挖掉保留内容: 物品栏 + 跟踪状态写入掉落物的 NBT, 放置时恢复
    // -----------------------------------------------------------------------

    /// 把物品栏写进方块物品的 NBT (BlockSanguineRite.getDrops 调用)
    public void writeItemNBT(NBTTagCompound tag) {
        tag.setTag(TAG_ITEMS, writeItems());
    }

    /// 方块放置时从物品 NBT 恢复物品栏
    public void readItemNBT(NBTTagCompound tag) {
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
            markDirtyAndSync();
            return stack;
        }
        final ItemStack split = inventory[slot].splitStack(amount);
        if (inventory[slot].stackSize == 0) inventory[slot] = null;
        markDirtyAndSync();
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
        markDirtyAndSync();
    }

    @Override
    public String getInventoryName() {
        return "container.libertas.sanguineRite";
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
        return slot == SLOT_INPUT;
    }

    // -----------------------------------------------------------------------
    // ISidedInventory: 自动化的插入/抽取规则
    // -----------------------------------------------------------------------

    /// 所有面都可接触全部槽位; 能否插/抽由 canInsertItem / canExtractItem 决定
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[] { SLOT_INPUT, SLOT_OUTPUT };
    }

    /// 只有输入槽能被自动化插入
    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_INPUT;
    }

    /// 只有输出槽能被自动化抽出
    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_OUTPUT;
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
        if (insertedStack != null) {
            tag.setTag(TAG_INSERTED, insertedStack.writeToNBT(new NBTTagCompound()));
            tag.setInteger(TAG_LIQUID, liquidRequired);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readItems(tag.getTagList(TAG_ITEMS, 10));
        insertedStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_INSERTED));
        liquidRequired = tag.getInteger(TAG_LIQUID);
    }

    // -----------------------------------------------------------------------
    // 客户端同步 (GUI 状态文字)
    // -----------------------------------------------------------------------

    @Override
    public Packet getDescriptionPacket() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Tracking", insertedStack != null);
        tag.setInteger("LiquidRequired", liquidRequired);
        final TEAltar altar = getAltar();
        tag.setBoolean("Altar", altar != null);
        tag.setInteger("Tier", altar != null ? altar.getTier() : 0);
        tag.setInteger("Progress", altar != null ? altar.getProgress() : 0);
        tag.setBoolean("ResultWaiting", altar != null && !altar.isActive() && altar.getStackInSlot(0) != null);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        final NBTTagCompound tag = pkt.func_148857_g();
        tracking = tag.getBoolean("Tracking");
        liquidRequiredClient = tag.getInteger("LiquidRequired");
        altarPresent = tag.getBoolean("Altar");
        altarTier = tag.getInteger("Tier");
        progress = tag.getInteger("Progress");
        resultWaiting = tag.getBoolean("ResultWaiting");
    }

    // 客户端镜像字段 (仅 GUI 读取)
    public boolean tracking;
    public int liquidRequiredClient;
    public boolean altarPresent;
    public int altarTier;
    public int progress;
    public boolean resultWaiting;
}
