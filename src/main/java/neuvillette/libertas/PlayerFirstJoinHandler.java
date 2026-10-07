package neuvillette.libertas;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/// 玩家第一次进入世界时发放一个改名的基岩 (迷你矿机的合成提示), 标记写在 PlayerPersisted 里, 死亡重生不会重复发放
public class PlayerFirstJoinHandler {

    private static final String GIVEN_TAG = "libertas.givenFirstJoinBedrock";

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerFirstJoinHandler());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player.worldObj.isRemote) return;

        NBTTagCompound forgeData = player.getEntityData();
        NBTTagCompound persisted = forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_TAG)) return;

        ItemStack bedrock = new ItemStack(Blocks.bedrock);
        bedrock.setStackDisplayName(EnumChatFormatting.ITALIC + "用于合成迷你矿机");
        if (!player.inventory.addItemStackToInventory(bedrock)) {
            // 背包满了就丢在脚下
            player.dropPlayerItemWithRandomChoice(bedrock, false);
        }

        persisted.setBoolean(GIVEN_TAG, true);
        forgeData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        Libertas.LOG.info("Gave first-join bedrock to " + player.getCommandSenderName());
    }
}
