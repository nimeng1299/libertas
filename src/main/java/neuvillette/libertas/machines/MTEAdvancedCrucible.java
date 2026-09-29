package neuvillette.libertas.machines;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTEAdvancedCrucible extends MTEEnhancedMultiBlockBase<MTEAdvancedCrucible>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * 来自 mte/AdvancedCrucible.txt 的 Normal Scan 原样数据：外层数组是深度 C（前→后），内层字符串是高度 B
     * （下→上），字符是宽度 A（左→右），正是 {@link StructureDefinition.Builder#addShape} 直接消费的顺序，
     * 无需 transpose。'~' 为控制器占位，空格为通配，'A' 为青铜镶砖外壳。
     */
    private static final String[][] SHAPE = new String[][] { { "AAA", "A~A", "AAA" }, { "A A", "A A", "AAA" },
        { "AAA", "AAA", "AAA" } };

    // Offsets: 1, 1, 0 —— 控制器在结构中的 (A, B, C) 偏移，即前面正中
    private static final int OFFSET_A = 1, OFFSET_B = 1, OFFSET_C = 0;

    private static final IStructureDefinition<MTEAdvancedCrucible> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEAdvancedCrucible>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement('A', Casings.BronzePlatedBricks.asElement())
        .build();

    // blockbench/advancedCrucibleController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons
        .custom("libertas", "machines/advanced_crucible");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();

    public MTEAdvancedCrucible(String aName) {
        super(aName);
    }

    public MTEAdvancedCrucible(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Advanced Crucible")
            .addInfo("A crucible built from Bronze Plated Bricks")
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center")
            .addCasing("24", "Bronze Plated Bricks", false)
            .addStructureInfo("Interior: air column behind the controller")
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTEAdvancedCrucible> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OFFSET_A, OFFSET_B, OFFSET_C);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            OFFSET_A,
            OFFSET_B,
            OFFSET_C,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, OFFSET_A, OFFSET_B, OFFSET_C, errors);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UPRIGHT;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAdvancedCrucible(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { FRONT_TEXTURE };
        }
        return new ITexture[] { Casings.BronzePlatedBricks.getCasingTexture() };
    }
}
