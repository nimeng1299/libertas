package neuvillette.libertas.wireless;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.ExoticEnergyInputHelper;
import neuvillette.libertas.machines.MTEHatchLinkedWirelessEnergy;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;

public final class EnergyHatchTemplate {

    public static final int REQUIRED_COUNT = 64;

    private EnergyHatchTemplate() {}

    public static WirelessPowerProfile read(ItemStack stack) {
        if (stack == null || stack.getItem() != Item.getItemFromBlock(GregTechAPI.sBlockMachines)) return null;
        int id = stack.getItemDamage();
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length) return null;
        IMetaTileEntity meta = GregTechAPI.METATILEENTITIES[id];
        if (!(meta instanceof MTEHatch hatch) || meta instanceof MTEHatchLinkedWirelessEnergy) return null;
        if (!(hatch instanceof MTEHatchEnergy) && !ExoticEnergyInputHelper.isExoticEnergyInput(hatch)) return null;
        long amperage = hatch.maxWorkingAmperesIn();
        if (hatch instanceof MTEHatchEnergyMulti multi && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("amperes", 99)) {
            amperage = Math.max(
                0,
                Math.min(
                    multi.maxAmperes,
                    stack.getTagCompound()
                        .getInteger("amperes")));
        }
        WirelessPowerProfile profile = new WirelessPowerProfile(hatch.maxEUInput(), amperage);
        return profile.isEnabled() ? profile : null;
    }

    public static WirelessPowerProfile activeProfile(ItemStack stack) {
        if (stack == null || stack.stackSize != REQUIRED_COUNT) return WirelessPowerProfile.DISABLED;
        WirelessPowerProfile profile = read(stack);
        return profile == null ? WirelessPowerProfile.DISABLED : profile;
    }
}
