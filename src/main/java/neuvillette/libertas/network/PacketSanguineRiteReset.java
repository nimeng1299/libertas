package neuvillette.libertas.network;

import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import neuvillette.libertas.blocks.TileSanguineRite;

/// GUI 按钮点击包: 服务端对指定坐标的猩红祭仪执行重置 (忘记当前跟踪的配方)。
public class PacketSanguineRiteReset implements IMessage {

    private int x;
    private int y;
    private int z;

    public PacketSanguineRiteReset() {}

    public PacketSanguineRiteReset(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketSanguineRiteReset, IMessage> {

        @Override
        public IMessage onMessage(PacketSanguineRiteReset message, MessageContext ctx) {
            final TileEntity te = ctx.getServerHandler().playerEntity.worldObj
                .getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileSanguineRite) {
                ((TileSanguineRite) te).resetRite();
            }
            return null;
        }
    }
}
