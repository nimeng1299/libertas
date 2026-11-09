package neuvillette.libertas.blocks;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;
import gregtech.api.modularui2.GTModularScreen;
import neuvillette.libertas.gui.GuiChunkReshaper;

/**
 * 区块重塑机 (chunkReshaper) 的 TileEntity。
 *
 * <p>
 * 机器只作用于自己所在的区块，每 tick 处理 1 个方块：
 * <ul>
 * <li>{@link Mode#EXCAVATE} 掘空：从机器所在高度往下一层一层地把方块变成空气（基岩保留）。</li>
 * <li>{@link Mode#PAVE} 铺顶：在本维度的最高建筑高度逐格铺美容玻璃，格子上已有方块就跳过。</li>
 * </ul>
 * 两种作业都以「区块内方块序号」为游标，进度、已处理数量与当前层会同步给 MUI2 面板。
 * 作业进行中再次点击同一个按钮会暂停，暂停后再点同一按钮从游标处继续；点击另一个按钮则切换到该模式并从头开始。
 */
public class TileChunkReshaper extends TileEntity implements IGuiHolder<PosGuiData> {

    /** 区块边长（方块）。 */
    public static final int CHUNK_WIDTH = 16;
    /** 一层（16x16）的方块数量，也是铺设模式的总量。 */
    public static final int BLOCKS_PER_LAYER = CHUNK_WIDTH * CHUNK_WIDTH;
    /** 处理速度：每 tick 1 个方块。 */
    public static final int BLOCKS_PER_TICK = 1;

    public enum Mode {

        /** 没有选中任何作业。 */
        NONE,
        /** 掘空机器高度以下的方块。 */
        EXCAVATE,
        /** 在最高建筑高度铺设美容玻璃。 */
        PAVE
    }

    private static final String TAG_ACTIVE = "ActiveMode";
    private static final String TAG_SELECTED = "SelectedMode";
    private static final String TAG_CURSOR = "Cursor";
    private static final String TAG_TOTAL = "Total";
    private static final String TAG_CHANGED = "Changed";
    private static final String TAG_FINISHED = "Finished";

    /** 正在运行的作业；IDLE 时为 {@link Mode#NONE}。 */
    private Mode activeMode = Mode.NONE;
    /** 最近一次选择的作业，暂停/完成后仍保留，供面板显示。 */
    private Mode selectedMode = Mode.NONE;
    /** 下一个要处理的方块序号（已处理数量）。 */
    private int cursor;
    /** 本次作业的方块总数。 */
    private int total;
    /** 实际被改成空气 / 铺上玻璃的方块数量（跳过的空气、基岩与已占用格不计）。 */
    private int changed;
    /** 本次作业是否已走完。 */
    private boolean finished;

    // -----------------------------------------------------------------------
    // 每 tick 工作
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (activeMode == Mode.NONE) return;
        processNextBlock();
    }

    /** 处理游标处的 1 个方块，并推进进度。 */
    private void processNextBlock() {
        if (cursor >= total) {
            finish();
            return;
        }

        final int index = cursor++;
        final int baseX = xCoord & ~(CHUNK_WIDTH - 1);
        final int baseZ = zCoord & ~(CHUNK_WIDTH - 1);

        if (selectedMode == Mode.EXCAVATE) {
            final int layer = index / BLOCKS_PER_LAYER;
            final int offset = index % BLOCKS_PER_LAYER;
            final int x = baseX + (offset % CHUNK_WIDTH);
            final int z = baseZ + (offset / CHUNK_WIDTH);
            final int y = yCoord - 1 - layer;
            final Block block = worldObj.getBlock(x, y, z);
            // 只保留基岩；空气本来就不用动
            if (block != Blocks.air && block != Blocks.bedrock) {
                worldObj.setBlockToAir(x, y, z);
                changed++;
            }
        } else if (selectedMode == Mode.PAVE) {
            final int x = baseX + (index % CHUNK_WIDTH);
            final int z = baseZ + (index / CHUNK_WIDTH);
            final int y = topBuildY();
            // 已有方块就跳过（不替换）
            if (worldObj.isAirBlock(x, y, z)) {
                worldObj.setBlock(x, y, z, ModBlocks.beautyGlass, 0, 3);
                changed++;
            }
        }

        if (cursor >= total) {
            finish();
        } else {
            markDirty();
        }
    }

    private void finish() {
        activeMode = Mode.NONE;
        finished = true;
        markDirty();
    }

    // -----------------------------------------------------------------------
    // 按钮入口
    // -----------------------------------------------------------------------

    /**
     * 面板按钮：正在运行该作业时暂停；暂停中的同一作业从游标处继续；否则从头开始该作业。
     * 只在服务端生效，客户端点击经 MUI2 的 InteractionSyncHandler 转发到这里。
     */
    public void press(Mode mode) {
        if (worldObj == null || worldObj.isRemote || mode == Mode.NONE) return;

        if (activeMode == mode) {
            activeMode = Mode.NONE;
            markDirty();
            return;
        }
        if (selectedMode == mode && !finished && cursor > 0 && cursor < total) {
            activeMode = mode;
            markDirty();
            return;
        }
        start(mode);
    }

    private void start(Mode mode) {
        selectedMode = mode;
        activeMode = mode;
        cursor = 0;
        changed = 0;
        finished = false;
        total = mode == Mode.EXCAVATE ? yCoord * BLOCKS_PER_LAYER : BLOCKS_PER_LAYER;
        if (total <= 0) {
            // 机器已经在 y=0，下面没有可挖的方块
            total = 0;
            activeMode = Mode.NONE;
            finished = true;
        }
        markDirty();
    }

    // -----------------------------------------------------------------------
    // 面板读取的数据（MUI2 每个 tick 拉取一次，只在变化时发包）
    // -----------------------------------------------------------------------

    public int getActiveModeOrdinal() {
        return activeMode.ordinal();
    }

    public int getSelectedModeOrdinal() {
        return selectedMode.ordinal();
    }

    public int getCursor() {
        return cursor;
    }

    public int getTotal() {
        return total;
    }

    public int getChanged() {
        return changed;
    }

    public int getFinishedFlag() {
        return finished ? 1 : 0;
    }

    /** 当前正在处理的 Y 层；没有作业时返回 -1。 */
    public int getCurrentY() {
        if (selectedMode == Mode.NONE || total <= 0) return -1;
        if (selectedMode == Mode.PAVE) return topBuildY();
        final int index = Math.min(cursor, total - 1);
        return yCoord - 1 - index / BLOCKS_PER_LAYER;
    }

    /** 本维度可以放置方块的最高 Y（1.7.10 主世界为 255）。 */
    public int topBuildY() {
        final int height = worldObj == null ? 256 : worldObj.getActualHeight();
        return Math.max(0, height - 1);
    }

    // -----------------------------------------------------------------------
    // NBT：重新加载后继续未完成的作业
    // -----------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte(TAG_ACTIVE, (byte) activeMode.ordinal());
        tag.setByte(TAG_SELECTED, (byte) selectedMode.ordinal());
        tag.setInteger(TAG_CURSOR, cursor);
        tag.setInteger(TAG_TOTAL, total);
        tag.setInteger(TAG_CHANGED, changed);
        tag.setBoolean(TAG_FINISHED, finished);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        activeMode = modeFromOrdinal(tag.getByte(TAG_ACTIVE));
        selectedMode = modeFromOrdinal(tag.getByte(TAG_SELECTED));
        cursor = Math.max(0, tag.getInteger(TAG_CURSOR));
        total = Math.max(0, tag.getInteger(TAG_TOTAL));
        changed = Math.max(0, tag.getInteger(TAG_CHANGED));
        finished = tag.getBoolean(TAG_FINISHED);
        if (cursor > total) cursor = total;
        if (activeMode != Mode.NONE && total <= 0) activeMode = Mode.NONE;
    }

    private static Mode modeFromOrdinal(int ordinal) {
        final Mode[] modes = Mode.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.NONE;
    }

    // -----------------------------------------------------------------------
    // MUI2 面板
    // -----------------------------------------------------------------------

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return GuiChunkReshaper.build(this, syncManager);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData guiData, ModularPanel mainPanel) {
        return new GTModularScreen(mainPanel, GTGuiThemes.STANDARD);
    }
}
