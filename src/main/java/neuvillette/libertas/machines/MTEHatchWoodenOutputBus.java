package neuvillette.libertas.machines;

import net.minecraft.init.Blocks;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.render.TextureFactory;

public class MTEHatchWoodenOutputBus extends MTEHatchOutputBus {

    public MTEHatchWoodenOutputBus(int id, String name, String regionalName) {
        super(id, name, regionalName, 0, 1);
    }

    private MTEHatchWoodenOutputBus(String name, String[] description, ITexture[][][] textures) {
        super(name, 0, 1, description, textures);
    }

    @Override
    public MTEHatchWoodenOutputBus newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchWoodenOutputBus(mName, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture casing = super.getCasingTexture();
        return casing == null ? TextureFactory.of(Blocks.planks) : casing;
    }
}
