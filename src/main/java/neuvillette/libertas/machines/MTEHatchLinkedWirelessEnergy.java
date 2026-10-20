package neuvillette.libertas.machines;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.WirelessNetworkManager;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import neuvillette.libertas.wireless.WirelessControlWorldData;
import neuvillette.libertas.wireless.WirelessEnergyBudget;
import neuvillette.libertas.wireless.WirelessPowerProfile;

/**
 * A normal GT energy hatch whose virtual buffer debits its controller's wireless account on consumption.
 * There is no prepaid local EU, so changing the template or removing the controller takes effect immediately.
 */
public class MTEHatchLinkedWirelessEnergy extends MTEHatchEnergy {

    private static final String WAILA_TAG = "libertas.LinkedWirelessEnergy";
    private static final ITexture FRONT = TextureFactory
        .of(Textures.BlockIcons.custom("libertas", "machines/linked_wireless_energy"));

    private final WirelessEnergyBudget budget = new WirelessEnergyBudget();
    private UUID controllerId;
    private WirelessPowerProfile lastProfile = WirelessPowerProfile.DISABLED;

    public MTEHatchLinkedWirelessEnergy(int id, String name, String regionalName) {
        super(id, name, regionalName, 0);
    }

    private MTEHatchLinkedWirelessEnergy(String name, String[] description, ITexture[][][] textures) {
        super(name, 0, description, textures);
    }

    @Override
    public MTEHatchLinkedWirelessEnergy newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchLinkedWirelessEnergy(mName, mDescriptionArray, mTextures);
    }

    public void bindController(UUID id) {
        controllerId = id;
        markDirty();
        refreshProfile();
    }

    private WirelessControlWorldData.Controller controller() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        WirelessControlWorldData data = base == null ? null : WirelessControlWorldData.get(base.getWorld());
        return data == null ? null : data.find(controllerId);
    }

    private WirelessPowerProfile profile() {
        WirelessControlWorldData.Controller controller = controller();
        return controller == null ? WirelessPowerProfile.DISABLED : controller.profile;
    }

    @Override
    public long maxEUInput() {
        return profile().voltage;
    }

    @Override
    public long maxAmperesIn() {
        return profile().amperage;
    }

    @Override
    public long maxWorkingAmperesIn() {
        return maxAmperesIn();
    }

    @Override
    public long maxEUStore() {
        return profile().maxEuPerTick();
    }

    @Override
    public long getMinimumStoredEU() {
        return 0;
    }

    @Override
    public long getInputTier() {
        return GTUtility.getTier(maxEUInput());
    }

    @Override
    public byte getTierForStructure() {
        return (byte) getInputTier();
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.WIRELESS;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public boolean willExplodeInRain() {
        return false;
    }

    @Override
    public long getEUVar() {
        WirelessControlWorldData.Controller controller = controller();
        if (controller == null || !controller.profile.isEnabled()) return 0;
        return budget.available(
            getBaseMetaTileEntity().getWorld()
                .getTotalWorldTime(),
            controller.profile.maxEuPerTick(),
            WirelessNetworkManager.getUserEU(controller.owner));
    }

    @Override
    public void setEUVar(long newValue) {
        WirelessControlWorldData.Controller controller = controller();
        if (controller == null || !controller.profile.isEnabled()) return;
        budget.update(
            getBaseMetaTileEntity().getWorld()
                .getTotalWorldTime(),
            controller.profile.maxEuPerTick(),
            WirelessNetworkManager.getUserEU(controller.owner),
            newValue,
            cost -> WirelessNetworkManager.addEUToGlobalEnergyMap(controller.owner, cost));
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (base.isServerSide()) refreshProfile();
    }

    private void refreshProfile() {
        WirelessPowerProfile profile = profile();
        if (profile.voltage == lastProfile.voltage && profile.amperage == lastProfile.amperage) return;
        lastProfile = profile;
        notifyWatchers();
        getBaseMetaTileEntity().issueBlockUpdate();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        if (controllerId != null) tag.setString("WirelessController", controllerId.toString());
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        controllerId = WirelessControlWorldData.readUuid(tag, "WirelessController");
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        // Binding belongs to the placed hatch, not to its item drop.
        tag.removeTag("WirelessController");
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("tooltip.libertas.wireless.hatch.bind"),
            StatCollector.translateToLocal("tooltip.libertas.wireless.hatch.power"),
            StatCollector.translateToLocal("tooltip.libertas.wireless.hatch.update") };
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines.hatch.linkedwirelessenergy.name");
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, FRONT };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return getTexturesActive(baseTexture);
    }

    private String statusText() {
        WirelessControlWorldData.Controller controller = controller();
        if (controller == null) return StatCollector.translateToLocal("libertas.wireless.disconnected");
        WirelessPowerProfile power = controller.profile;
        return StatCollector
            .translateToLocal(power.isEnabled() ? "libertas.wireless.online" : "libertas.wireless.paused") + "\n"
            + StatCollector.translateToLocalFormatted("libertas.wireless.voltage", power.voltage)
            + "\n"
            + StatCollector.translateToLocalFormatted("libertas.wireless.amperage", power.amperage)
            + "\n"
            + StatCollector.translateToLocalFormatted(
                "libertas.wireless.location",
                GTUtility.getDimensionName(controller.dimension),
                controller.x,
                controller.y,
                controller.z);
    }

    @Override
    public String[] getInfoData() {
        return statusText().split("\n");
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        WirelessControlWorldData.Controller controller = controller();
        WirelessPowerProfile power = controller == null ? WirelessPowerProfile.DISABLED : controller.profile;
        NBTTagCompound info = new NBTTagCompound();
        info.setLong("Voltage", power.voltage);
        info.setLong("Amperage", power.amperage);
        info.setBoolean("Bound", controller != null);
        if (controller != null) {
            info.setInteger("Dimension", controller.dimension);
            info.setInteger("X", controller.x);
            info.setInteger("Y", controller.y);
            info.setInteger("Z", controller.z);
        }
        tag.setTag(WAILA_TAG, info);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (!tag.hasKey(WAILA_TAG, 10)) return;
        NBTTagCompound info = tag.getCompoundTag(WAILA_TAG);
        currenttip.add(info.getLong("Voltage") + "EU / " + info.getLong("Amperage") + "A");
        currenttip
            .add(
                info.getBoolean("Bound")
                    ? StatCollector.translateToLocalFormatted(
                        "libertas.wireless.location",
                        GTUtility.getDimensionName(info.getInteger("Dimension")),
                        info.getInteger("X"),
                        info.getInteger("Y"),
                        info.getInteger("Z"))
                    : StatCollector.translateToLocal("libertas.wireless.disconnected"));
    }
}
