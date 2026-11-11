package neuvillette.libertas.waila;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import neuvillette.libertas.blocks.BlockKitchenSink;
import neuvillette.libertas.blocks.TileKitchenSink;

/**
 * 水槽 (kitchenSink) 的 Waila 提示: 在方块名称下面显示罐里的流体与容量。
 *
 * <p>
 * 水槽是常量水源 (储量恒为 int 上限), 客户端本地就能算出全部信息, 因此只注册 body provider,
 * 不需要 NBT 同步。注册走 Waila 的 IMC 通道:
 * `FMLInterModComms.sendMessage("Waila", "register", "neuvillette.libertas.waila.KitchenSinkWailaProvider.register")`
 * —— 没装 Waila 时这条消息会被丢弃, 本类也不会被加载。
 */
public final class KitchenSinkWailaProvider implements IWailaDataProvider {

    private static final KitchenSinkWailaProvider INSTANCE = new KitchenSinkWailaProvider();

    private KitchenSinkWailaProvider() {}

    /// Waila 的 IMC 回调入口 (必须是 public static, 参数为 IWailaRegistrar)
    public static void register(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(INSTANCE, BlockKitchenSink.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final FluidStack water = new FluidStack(FluidRegistry.WATER, TileKitchenSink.INFINITE_WATER);
        currenttip
            .add(StatCollector.translateToLocalFormatted("libertas.waila.kitchenSink.fluid", water.getLocalizedName()));
        currenttip.add(
            StatCollector.translateToLocalFormatted(
                "libertas.waila.kitchenSink.amount",
                format(TileKitchenSink.INFINITE_WATER)));
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    /// 水槽没有需要同步给客户端的 NBT
    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        return tag;
    }

    /// 2,147,483,647 —— 用 Locale.ROOT 保证千分位永远是逗号, 不随系统语言变化
    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
