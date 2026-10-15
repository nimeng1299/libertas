package neuvillette.libertas.machines;

import net.minecraft.init.Blocks;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.render.TextureFactory;

public class MTEHatchWoodenInputBus extends MTEHatchInputBus {

    public MTEHatchWoodenInputBus(int id, String name, String regionalName) {
        super(id, name, regionalName, 0, 1);
    }

    private MTEHatchWoodenInputBus(String name, String[] description, ITexture[][][] textures) {
        super(name, 0, 1, description, textures);
    }

    @Override
    public MTEHatchWoodenInputBus newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchWoodenInputBus(mName, mDescriptionArray, mTextures);
    }

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public void updateSlots() {
        // The native bus reserves its last slot for a circuit; this bus uses its only slot for items.
        if (mInventory[0] != null && mInventory[0].stackSize <= 0) {
            mInventory[0] = null;
        }
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture casing = super.getCasingTexture();
        return casing == null ? TextureFactory.of(Blocks.planks) : casing;
    }
}
