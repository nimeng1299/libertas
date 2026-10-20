package neuvillette.libertas.wireless;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

/** Overworld-backed controller directory; references remain usable while the controller chunk is unloaded. */
public class WirelessControlWorldData extends WorldSavedData {

    private static final String DATA_NAME = "libertas_wireless_controllers";

    private final Map<UUID, Controller> controllers = new HashMap<>();

    public WirelessControlWorldData() {
        super(DATA_NAME);
    }

    public WirelessControlWorldData(String name) {
        super(name);
    }

    public static WirelessControlWorldData get(World world) {
        if (world == null || world.isRemote) return null;
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) return null;
        MapStorage storage = overworld.mapStorage;
        WirelessControlWorldData data = (WirelessControlWorldData) storage
            .loadData(WirelessControlWorldData.class, DATA_NAME);
        if (data == null) {
            data = new WirelessControlWorldData();
            storage.setData(DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    public Controller find(UUID id) {
        return id == null ? null : controllers.get(id);
    }

    public void publish(UUID id, UUID owner, int dimension, int x, int y, int z, WirelessPowerProfile profile) {
        if (id == null || owner == null) return;
        Controller old = controllers.get(id);
        if (old != null && old.owner.equals(owner)
            && old.dimension == dimension
            && old.x == x
            && old.y == y
            && old.z == z
            && old.profile.voltage == profile.voltage
            && old.profile.amperage == profile.amperage) return;
        controllers.put(id, new Controller(owner, dimension, x, y, z, profile));
        markDirty();
    }

    public void remove(UUID id) {
        if (controllers.remove(id) != null) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        controllers.clear();
        NBTTagList entries = tag.getTagList("Controllers", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            UUID id = readUuid(entry, "Id");
            UUID owner = readUuid(entry, "Owner");
            if (id == null || owner == null) continue;
            controllers.put(
                id,
                new Controller(
                    owner,
                    entry.getInteger("Dimension"),
                    entry.getInteger("X"),
                    entry.getInteger("Y"),
                    entry.getInteger("Z"),
                    new WirelessPowerProfile(entry.getLong("Voltage"), entry.getLong("Amperage"))));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<UUID, Controller> element : controllers.entrySet()) {
            Controller controller = element.getValue();
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(
                "Id",
                element.getKey()
                    .toString());
            entry.setString("Owner", controller.owner.toString());
            entry.setInteger("Dimension", controller.dimension);
            entry.setInteger("X", controller.x);
            entry.setInteger("Y", controller.y);
            entry.setInteger("Z", controller.z);
            entry.setLong("Voltage", controller.profile.voltage);
            entry.setLong("Amperage", controller.profile.amperage);
            entries.appendTag(entry);
        }
        tag.setTag("Controllers", entries);
    }

    public static UUID readUuid(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 8)) return null;
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final class Controller {

        public final UUID owner;
        public final int dimension;
        public final int x;
        public final int y;
        public final int z;
        public final WirelessPowerProfile profile;

        private Controller(UUID owner, int dimension, int x, int y, int z, WirelessPowerProfile profile) {
            this.owner = owner;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.profile = profile;
        }
    }
}
