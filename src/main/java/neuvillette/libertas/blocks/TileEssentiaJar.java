package neuvillette.libertas.blocks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;

/// 要素罐的 TileEntity: 一个 AspectList 存任意多种要素, 每种上限 Integer.MAX_VALUE (int 最大值,
/// 实际上受内存限制), 要素不衰减不消失。
///
/// 接口与 TC4 的 TileJarFillable 对齐, 差别只在"罐子不挑要素、也装不满":
/// - {@link IAspectSource}: 通用要素容器, 注魔祭坛 (TileInfusionMatrix) 与其他模组可直接抽取;
/// - {@link IEssentiaTransport}: 与要素管道互通, 和 TC 罐一样只连接/输入/输出上方一面,
/// 吸力恒为 {@link #SUCTION} (TC 无过滤罐的值), 每 {@link #PULL_INTERVAL} tick 从上方吸
/// {@link #PULL_AMOUNT} 点 (TC fillJar 的节奏), 因此要素会顺着管线上方的管道源源不断吸入。
public class TileEssentiaJar extends TileEntity implements IAspectSource, IEssentiaTransport {

    /// 与 TC TileJarFillable.getSuctionAmount (无过滤分支) 一致的基础吸力
    public static final int SUCTION = 32;
    /// TC 罐每 5 tick 调一次 fillJar, 每次吸 1 点
    public static final int PULL_INTERVAL = 5;
    public static final int PULL_AMOUNT = 1;
    /// 瓶子一次装取的要素量 (TC 的要素瓶容量)
    public static final int PHIAL_AMOUNT = 8;

    private final AspectList essentia = new AspectList();

    /** 当前存储的要素只读视图 (调用方不得修改)。 */
    public AspectList getEssentia() {
        return essentia;
    }

    // -----------------------------------------------------------------------
    // IAspectContainer (IAspectSource 的父接口)
    // -----------------------------------------------------------------------

    @Override
    public AspectList getAspects() {
        return essentia;
    }

    @Override
    public void setAspects(AspectList aspectList) {
        essentia.aspects.clear();
        if (aspectList != null) essentia.add(aspectList);
        markDirty();
    }

    /// 不挑要素, 什么都收 (TC 罐只收当前/空罐时的那一种)
    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return aspect != null;
    }

    /// 收下 amount 点并返回装不下的余量 (无限容量 → 余量为 0, 除非 amount 非法)。
    /// 按 int 上限做饱和加法, 避免 long 语义外的溢出。
    @Override
    public int addToContainer(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return amount;
        int current = essentia.getAmount(aspect);
        int added = (int) Math.min((long) amount, (long) Integer.MAX_VALUE - current);
        if (added > 0) {
            essentia.add(aspect, added);
            markDirty();
        }
        return amount - added;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        if (aspect == null || essentia.getAmount(aspect) < amount) return false;
        essentia.remove(aspect, amount);
        markDirty();
        return true;
    }

    /// 与 TC 语义一致: 只有全部要素都足够时才一次性扣掉 (原子性, 不做部分扣除)
    @Override
    public boolean takeFromContainer(AspectList aspectList) {
        if (aspectList == null || !doesContainerContain(aspectList)) return false;
        for (Aspect aspect : aspectList.getAspects()) {
            essentia.remove(aspect, aspectList.getAmount(aspect));
        }
        markDirty();
        return true;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return essentia.getAmount(aspect) >= amount;
    }

    @Override
    public boolean doesContainerContain(AspectList aspectList) {
        if (aspectList == null) return false;
        for (Aspect aspect : aspectList.getAspects()) {
            if (essentia.getAmount(aspect) < aspectList.getAmount(aspect)) return false;
        }
        return true;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return essentia.getAmount(aspect);
    }

    // -----------------------------------------------------------------------
    // IEssentiaTransport: 与 TC 罐一致, 三个方向判定都只认上方
    // -----------------------------------------------------------------------

    @Override
    public boolean isConnectable(ForgeDirection direction) {
        return direction == ForgeDirection.UP;
    }

    @Override
    public boolean canInputFrom(ForgeDirection direction) {
        return direction == ForgeDirection.UP;
    }

    @Override
    public boolean canOutputTo(ForgeDirection direction) {
        return direction == ForgeDirection.UP;
    }

    /** 罐子不接受外部设置吸力 (TC 同款空实现)。 */
    @Override
    public void setSuction(Aspect aspectFilter, int suction) {}

    /// 吸取的要素类型: 罐子不挑要素 → 无过滤 (TC 罐未设过滤时同样返回 null)
    @Override
    public Aspect getSuctionType(ForgeDirection direction) {
        return null;
    }

    /// 恒定吸力: TC 罐装满就停吸, 这罐永远装不满 → 永远保持吸力
    @Override
    public int getMinimumSuction() {
        return SUCTION;
    }

    @Override
    public int getSuctionAmount(ForgeDirection direction) {
        return SUCTION;
    }

    /// 管道读取罐内"当前要素": 多要素罐返回存量最多的一种 (并列时取 AspectList 顺序靠前的)
    @Override
    public Aspect getEssentiaType(ForgeDirection direction) {
        Aspect[] sorted = essentia.getAspectsSortedAmount();
        return sorted.length > 0 ? sorted[0] : null;
    }

    @Override
    public int getEssentiaAmount(ForgeDirection direction) {
        Aspect most = getEssentiaType(direction);
        return most == null ? 0 : essentia.getAmount(most);
    }

    /// all-or-nothing 抽取 (TC 同款): 要不到就一点不给
    @Override
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection direction) {
        return canOutputTo(direction) && takeFromContainer(aspect, amount) ? amount : 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, ForgeDirection direction) {
        return canInputFrom(direction) ? amount - addToContainer(aspect, amount) : 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // -----------------------------------------------------------------------
    // 每 5 tick 从上方吸 1 点要素 (TileJarFillable.updateEntity + fillJar 的多要素版)
    // -----------------------------------------------------------------------

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (worldObj.getTotalWorldTime() % PULL_INTERVAL != 0L) return;

        // getConnectableTile(.., UP) = 上方相邻且 isConnectable 的 IEssentiaTransport
        TileEntity te = ThaumcraftApiHelper.getConnectableTile(worldObj, xCoord, yCoord, zCoord, ForgeDirection.UP);
        if (!(te instanceof IEssentiaTransport)) return;
        IEssentiaTransport source = (IEssentiaTransport) te;
        if (!source.canOutputTo(ForgeDirection.DOWN)) return;
        if (source.getEssentiaAmount(ForgeDirection.DOWN) <= 0) return;
        if (source.getSuctionAmount(ForgeDirection.DOWN) >= getSuctionAmount(ForgeDirection.UP)) return;
        if (getSuctionAmount(ForgeDirection.UP) < source.getMinimumSuction()) return;

        Aspect type = source.getEssentiaType(ForgeDirection.DOWN);
        if (type == null) return;
        addToContainer(type, source.takeEssentia(type, PULL_AMOUNT, ForgeDirection.DOWN));
    }

    // -----------------------------------------------------------------------
    // NBT: 内容随 TE 持久化; 挖掉时经 BlockEssentiaJar.getDrops 写入掉落物
    // -----------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        essentia.writeToNBT(tag, "essentia");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        essentia.readFromNBT(tag, "essentia");
    }

    // -----------------------------------------------------------------------
    // 内容展示: 物品 tooltip 用的文本行 (WAILA 走 Waila 内置 thaumcraft 模块的要素图标显示)
    // -----------------------------------------------------------------------

    /** 把要素列表整理成 "要素 x数量" 行, 超出 maxLines 以省略号收尾。 */
    public static List<String> summarizeLines(AspectList essentia, int maxLines) {
        final List<String> lines = new ArrayList<>();
        if (essentia == null || essentia.size() == 0) {
            lines.add(StatCollector.translateToLocal("libertas.essentia_jar.empty"));
            return lines;
        }
        int shown = 0;
        for (Aspect aspect : essentia.getAspects()) {
            if (shown++ >= maxLines) {
                lines.add(EnumChatFormatting.GRAY + "...");
                break;
            }
            // Aspect.getName() = tag 首字母大写 (TC 自身的显示方式); getChatcolor() 在部分要素上为 null, 不使用
            lines.add(
                EnumChatFormatting.WHITE + aspect.getName()
                    + EnumChatFormatting.GRAY
                    + " x"
                    + essentia.getAmount(aspect));
        }
        return lines;
    }
}
