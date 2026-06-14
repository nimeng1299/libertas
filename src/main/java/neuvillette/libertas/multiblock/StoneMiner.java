package neuvillette.libertas.multiblock;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;
import neuvillette.libertas.LiRecipeMaps;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;
import neuvillette.libertas.gui.multigui.StoneMinerGui;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

/**
 * 石头矿机 (Stone Miner)
 * 3x3x3 multi-block, stone walls, hollow center.
 * Burns fuel to generate heat, then uses that heat to produce ores.
 */
public class StoneMiner extends MTESteamMultiBlockBase<StoneMiner> implements ISurvivalConstructable{

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static IStructureDefinition<StoneMiner> STRUCTURE_DEFINITION = null;

    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 9;
    private static final int DEPTH_OFF_SET = 0;

    public long hurt = 0L;
    private static String[][] Shape = new String[][]{{
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   A~A   ",
        "AAA   AAA",
        "AAA   AAA",
        "AAA   AAA"
    }, {
        "   AAA   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   A A   ",
        "   AAA   ",
        "AAAA AAAA",
        "A A   A A",
        "AAA   AAA"
    }, {
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "   AAA   ",
        "AAA   AAA",
        "AAA   AAA",
        "AAA   AAA"
    }};

    public StoneMiner(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public StoneMiner(String aName) {
        super(aName);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        hurt = aNBT.getLong("hurt");
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("hurt", hurt);
    }

    @Override
    public String getMachineType() {
        return "base void miner";
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    protected boolean isHighPressure() {
        return false;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return null;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return null;
    }

    public long getHurt() {
        return hurt;
    }

    public void setHurt(long hurt) {
        this.hurt = hurt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing, int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] {
                Textures.BlockIcons.getCasingTextureForId(Textures.BlockIcons.getTextureIndex(Textures.BlockIcons
                    .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0)))),
                TextureFactory.builder().addIcon(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE)
                    .extFacing().build()
            };
            return new ITexture[] {
                Textures.BlockIcons.getCasingTextureForId(Textures.BlockIcons.getTextureIndex(Textures.BlockIcons
                    .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0)))),
                TextureFactory.builder().addIcon(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE)
                    .extFacing().build()
            };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(Textures.BlockIcons.getTextureIndex(Textures.BlockIcons
            .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0)))) };
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IStructureDefinition<StoneMiner> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<StoneMiner>builder()
                .addShape(STRUCTURE_PIECE_MAIN, Shape)
                .addElement('A', buildHatchAdder(StoneMiner.class)
                    .atLeast(
                        HatchElement.InputBus,
                        HatchElement.OutputBus)
                    .casingIndex(Textures.BlockIcons.getTextureIndex(Textures.BlockIcons
                        .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0))))
                    .hint(1)
                    .buildAndChain(Blocks.stone, 0))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }



    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("石头矿机");
        tt.addInfo("输入燃料产生热量来虚空生产矿石");
        tt.addInfo("输入总线放置对应电路指定种类");
        tt.addInfo("电路 1: 铁矿 | 电路 2: 铜矿 | 电路 3: 锡矿");
        tt.addInfo("热量 ≥ 200 时启动，执行后扣 200 热量");
        tt.addInfo("机器不工作开过量销毁");
        tt.toolTipFinisher("Libertas");
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new StoneMiner(this.mName);
    }

    @Override
    public void checkMaintenance() {}

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        boolean consumedFuel = false;

        // 1. Scan input bus slots for fuel
        for (MTEHatchInputBus tHatch : GTUtility.validMTEList(mInputBusses)) {
            IGregTechTileEntity tile = tHatch.getBaseMetaTileEntity();
            if (tile == null) continue;
            for (int i = tile.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack stack = tile.getStackInSlot(i);
                if (stack == null) continue;

                int burnTime = TileEntityFurnace.getItemBurnTime(stack);
                if (burnTime > 0) {
                    hurt += (long) burnTime * stack.stackSize;
                    tile.setInventorySlotContents(i, null);
                    consumedFuel = true;
                }
            }
            if (tHatch != null) tHatch.updateSlots();
        }

        // 2. If enough heat, try to run a recipe via processingLogic (for GUI display)
        if (hurt >= 200) {
            setupProcessingLogic(processingLogic);
            processingLogic.setInputItems(getAllStoredInputs());

            CheckRecipeResult recipeResult = processingLogic.process();
            if (recipeResult.wasSuccessful()) {
                hurt -= 200;
                mOutputItems = processingLogic.getOutputItems();
                mMaxProgresstime = processingLogic.getDuration();
                mEUt = (int) processingLogic.getCalculatedEut();
                mEfficiency = 10000;
                mEfficiencyIncrease = 10000;

                // Directly place outputs into output buses
                if (mOutputItems != null) {
                    for (ItemStack output : mOutputItems) {
                        if (output != null) {
                            for (IOutputBus bus : GTUtility.validMTEList(mOutputBusses)) {
                                if (bus != null && bus.storePartial(output)) {
                                    break;
                                }
                            }
                        }
                    }
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }

        // 3. If fuel was consumed but no recipe ran, still succeed
        if (consumedFuel) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_FUEL_FOUND;
    }


    @Override
    public RecipeMap<?> getRecipeMap() {
        return LiRecipeMaps.StoneMinerRecipes;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors);
        checkHasOutputBus(errors);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return false;
    }

    public long getHurtGui(){
        return hurt;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new StoneMinerGui(this);
    }

    public void setHurtGui(long hurt){
        this.hurt = hurt;
    }
}
