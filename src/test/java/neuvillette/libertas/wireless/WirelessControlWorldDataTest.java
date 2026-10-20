package neuvillette.libertas.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class WirelessControlWorldDataTest {

    @Test
    public void bindingsAndProfilesSurviveSavingAndReloading() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        WirelessControlWorldData data = new WirelessControlWorldData();
        data.publish(id, owner, -1, 20, 80, -300, new WirelessPowerProfile(2048, 64));
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        WirelessControlWorldData reloaded = new WirelessControlWorldData();
        reloaded.readFromNBT(tag);
        WirelessControlWorldData.Controller controller = reloaded.find(id);
        assertNotNull(controller);
        assertEquals(owner, controller.owner);
        assertEquals(-1, controller.dimension);
        assertEquals(-300, controller.z);
        assertEquals(2048, controller.profile.voltage);
        assertEquals(64, controller.profile.amperage);
    }

    @Test
    public void replacingTemplatesUpdatesAllLookupsAndRemovingInvalidatesTheOldId() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        WirelessControlWorldData data = new WirelessControlWorldData();
        data.publish(id, owner, 0, 1, 2, 3, new WirelessPowerProfile(32, 2));
        data.publish(id, owner, 0, 1, 2, 3, new WirelessPowerProfile(8192, 16));
        assertEquals(8192, data.find(id).profile.voltage);
        data.publish(id, owner, 0, 1, 2, 3, WirelessPowerProfile.DISABLED);
        assertEquals(0, data.find(id).profile.maxEuPerTick());
        data.remove(id);
        assertNull(data.find(id));
        UUID replacement = UUID.randomUUID();
        data.publish(replacement, owner, 0, 1, 2, 3, new WirelessPowerProfile(128, 2));
        assertNull(data.find(id));
        assertNotNull(data.find(replacement));
    }

    @Test
    public void separateControllersDoNotOverwriteEachOthersAccounts() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        WirelessControlWorldData data = new WirelessControlWorldData();
        data.publish(first, firstOwner, 0, 1, 2, 3, new WirelessPowerProfile(32, 2));
        data.publish(second, secondOwner, 1, 1, 2, 3, new WirelessPowerProfile(512, 16));
        data.remove(first);
        assertEquals(secondOwner, data.find(second).owner);
        assertEquals(512, data.find(second).profile.voltage);
    }

    @Test
    public void malformedBindingIdsAreIgnoredDuringLoading() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Id", "invalid");
        entry.setString(
            "Owner",
            UUID.randomUUID()
                .toString());
        NBTTagList entries = new NBTTagList();
        entries.appendTag(entry);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Controllers", entries);
        WirelessControlWorldData data = new WirelessControlWorldData();
        data.readFromNBT(tag);
        assertNull(data.find(null));
        assertNull(WirelessControlWorldData.readUuid(entry, "Id"));
    }
}
