package neuvillette.libertas.machines;

import net.minecraft.init.Blocks;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;

public class MTEHatchWoodenOutput extends MTEHatchOutput {

    public MTEHatchWoodenOutput(int id, String name, String regionalName) {
        super(id, name, regionalName, 0);
    }

    private MTEHatchWoodenOutput(String name, String[] description, ITexture[][][] textures) {
        super(name, 0, description, textures);
    }

    @Override
    public MTEHatchWoodenOutput newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHatchWoodenOutput(mName, mDescriptionArray, mTextures);
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
