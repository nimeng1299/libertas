package neuvillette.libertas.items;

import static detrav.items.DetravMetaGeneratedTool01.MODE_ALL_ORES;
import static detrav.items.DetravMetaGeneratedTool01.MODE_BIG_ORES;
import static detrav.items.DetravMetaGeneratedTool01.MODE_FLUIDS;
import static detrav.items.DetravMetaGeneratedTool01.MODE_POLLUTION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import detrav.net.DetravNetwork;
import detrav.net.ProspectingPacket;
import gregtech.api.task.CoopTask;
import gregtech.api.task.CooperativeScheduler;
import gregtech.api.task.ICoopTaskContext;
import gregtech.common.UndergroundOil;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import gregtech.common.pollution.Pollution;

/** 石制探矿仪：复用 GT 探矿地图，不消耗电力或耐久，每次打开施加 3 秒中毒。 */
public class ItemStoneProspector extends Item {

    private static final int SCAN_RADIUS = 3;
    private static final int SCAN_DIAMETER = SCAN_RADIUS * 2 + 1;
    private static final int POISON_TICKS = 3 * 20;
    private static final String NBT_MODE = "libertasProspectorMode";

    private final Map<UUID, Future<?>> pendingScans = new HashMap<>();

    public ItemStoneProspector() {
        setUnlocalizedName("stoneProspector");
        setTextureName("gregtech:materialicons/NONE/toolProspectorElectricLuV");
        setMaxStackSize(1);
    }

    private static int getMode(ItemStack stack) {
        return stack.hasTagCompound() ? Math.floorMod(
            stack.getTagCompound()
                .getInteger(NBT_MODE),
            MODE_POLLUTION + 1) : MODE_BIG_ORES;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack.copy();
        if (!(player instanceof EntityPlayerMP serverPlayer)) return stack;

        UUID playerId = player.getUniqueID();
        Future<?> previous = pendingScans.remove(playerId);
        if (previous != null) previous.cancel(false);

        if (player.isSneaking()) {
            int mode = (getMode(stack) + 1) % (MODE_POLLUTION + 1);
            stack.setTagInfo(NBT_MODE, new NBTTagInt(mode));
            player.addChatMessage(new ChatComponentTranslation("detrav.scanner.mode." + mode));
            return stack;
        }

        int posX = MathHelper.floor_double(player.posX);
        int posZ = MathHelper.floor_double(player.posZ);
        ProspectingPacket packet = new ProspectingPacket(posX >> 4, posZ >> 4, posX, posZ, SCAN_RADIUS, getMode(stack));

        player.addChatMessage(new ChatComponentTranslation("msg.libertas.stoneProspector.scanning"));
        Future<?> scan = CooperativeScheduler.INSTANCE.schedule(new ScanTask(world, serverPlayer, packet))
            .onFinished(ignored -> {
                pendingScans.remove(playerId);
                // 在地图实际打开时结算，扫描耗时不会提前耗尽中毒时间。
                player.addPotionEffect(new PotionEffect(Potion.poison.id, POISON_TICKS, 0));
                DetravNetwork.INSTANCE.sendToPlayer(packet, serverPlayer);
            });
        pendingScans.put(playerId, scan);
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.stoneProspector.stats"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.stoneProspector.usage"));
        tooltip.add(StatCollector.translateToLocal("tooltip.libertas.stoneProspector.poison"));
        tooltip.add(StatCollector.translateToLocal("detrav.scanner.mode." + getMode(stack)));
    }

    private class ScanTask implements CoopTask<Void> {

        private final World world;
        private final EntityPlayerMP player;
        private final ProspectingPacket packet;
        private int chunkIndex;
        private int columnIndex;
        private Chunk chunk;

        private ScanTask(World world, EntityPlayerMP player, ProspectingPacket packet) {
            this.world = world;
            this.player = player;
            this.packet = packet;
        }

        @Override
        public void run(ICoopTaskContext<Void> context) {
            if (player.isDead || player.worldObj != world || !world.playerEntities.contains(player)) {
                Future<?> scan = pendingScans.remove(player.getUniqueID());
                if (scan != null) scan.cancel(false);
                return;
            }

            // 每列检查一次时间预算，避免一次遍历 49 个区块阻塞服务端。
            while (!context.shouldYield()) {
                if (chunkIndex == SCAN_DIAMETER * SCAN_DIAMETER) {
                    context.stop(null);
                    return;
                }
                if (chunk == null) {
                    int chunkX = packet.chunkX + chunkIndex / SCAN_DIAMETER - SCAN_RADIUS;
                    int chunkZ = packet.chunkZ + chunkIndex % SCAN_DIAMETER - SCAN_RADIUS;
                    chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
                }

                switch (packet.ptype) {
                    case MODE_BIG_ORES, MODE_ALL_ORES -> scanOreColumn();
                    case MODE_FLUIDS -> {
                        packet.addFluid(chunk.xPosition, chunk.zPosition, UndergroundOil.undergroundOil(chunk, -1));
                        nextChunk();
                    }
                    case MODE_POLLUTION -> {
                        packet.addPollution(chunk.xPosition, chunk.zPosition, Pollution.getPollution(chunk));
                        nextChunk();
                    }
                }
            }
        }

        private void scanOreColumn() {
            int x = columnIndex / 16;
            int z = columnIndex % 16;
            int height = chunk.getTopFilledSegment() + 16;
            for (int y = 0; y < height; y++) {
                Block block = chunk.getBlock(x, y, z);
                int meta = chunk.getBlockMetadata(x, y, z);
                try (OreInfo<?> info = OreManager.getOreInfo(block, meta)) {
                    if (info == null || !info.isNatural) continue;
                    if (packet.ptype != MODE_ALL_ORES && info.isSmall) continue;
                    packet.addBlock(chunk.xPosition * 16 + x, y, chunk.zPosition * 16 + z, block, meta);
                }
            }
            if (++columnIndex == 16 * 16) nextChunk();
        }

        private void nextChunk() {
            chunkIndex++;
            columnIndex = 0;
            chunk = null;
        }
    }
}
