package neuvillette.libertas.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import vazkii.botania.api.internal.VanillaPacketDispatcher;
import vazkii.botania.api.mana.IManaReceiver;

/// 方块魔力基类的 TileEntity: 实现 Botania 的 {@link IManaReceiver}, 可被魔力发射器 (mana spreader)
/// 的魔力束充能, 存储上限 {@link #MAX_MANA} (一百万)。
///
/// 魔力变化经 {@link #syncManaToClients()} 节流同步到客户端 (每 10 tick 至多一次), GUI 与比较器读数
/// 都基于它; 子类覆写 updateEntity 时须调用 super。
public class TileManaBase extends TileEntity implements IManaReceiver {

    /// 魔力存储上限: 一百万 (与 Botania 常规魔力池一致)
    public static final int MAX_MANA = 1000000;

    private static final String TAG_MANA = "mana";

    protected int mana;

    /// 待同步标记 (服务端): 魔力变动后置位, 由 updateEntity 节流下发
    private boolean syncPending;
    private int syncTimer;

    public int getMaxMana() {
        return MAX_MANA;
    }

    @Override
    public int getCurrentMana() {
        return mana;
    }

    @Override
    public boolean isFull() {
        return mana >= getMaxMana();
    }

    /// 存入/扣除魔力 (负数扣除), 饱和到 0..容量, 并安排客户端同步
    @Override
    public void recieveMana(int amount) {
        mana = Math.max(0, Math.min(mana + amount, getMaxMana()));
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            // 比较器读数依赖魔力存量, 通知相邻比较器重算 (Botania TilePool 同款)
            worldObj.func_147453_f(xCoord, yCoord, zCoord, worldObj.getBlock(xCoord, yCoord, zCoord));
            syncManaToClients();
        }
    }

    @Override
    public boolean canRecieveManaFromBursts() {
        return true;
    }

    /// 标记魔力已变化, 下一个同步窗口把存量下发到客户端
    protected void syncManaToClients() {
        syncPending = true;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (syncPending && ++syncTimer % 10 == 0) {
            syncPending = false;
            VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
        }
    }

    /// 只携带魔力存量的描述包 (GUI 魔力条用), 物品栏等大块状态由 Container 自行同步
    @Override
    public Packet getDescriptionPacket() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_MANA, mana);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        mana = pkt.func_148857_g()
            .getInteger(TAG_MANA);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger(TAG_MANA, mana);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        mana = tag.getInteger(TAG_MANA);
    }
}
