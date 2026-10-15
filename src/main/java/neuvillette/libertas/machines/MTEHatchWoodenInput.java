package neuvillette.libertas.machines;

import net.minecraft.init.Blocks;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.render.TextureFactory;

public class MTEHatchWoodenInput extends MTEHatchInput {

    public MTEHatchWoodenInput(int id, String name, String regionalName) {
        super(id, name, regionalName, 0);
    }

    private MTEHatchWoodenInput(String name, String[] description, ITexture[][][] textures) {
        super(name, 4, 0, description, textures);
    }

    @Override
    public MTEHatchWoodenInput newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchWoodenInput(mName, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return 1000;
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture casing = super.getCasingTexture();
        return casing == null ? TextureFactory.of(Blocks.planks) : casing;
    }
}
