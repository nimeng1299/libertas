package neuvillette.libertas.machines;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;

public class MTEAdvancedCrucible extends MTELibertasMultiBlockBase<MTEAdvancedCrucible>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * 来自 mte/AdvancedCrucible.txt 的 Normal Scan 原样数据：外层数组是深度 C（前→后），内层字符串是高度 B
     * （下→上），字符是宽度 A（左→右），正是 {@link StructureDefinition.Builder#addShape} 直接消费的顺序，
     * 无需 transpose。'~' 为控制器占位，空格为通配，'A' 为青铜镶砖外壳或任意总线/提炼仓。
     */
    private static final String[][] SHAPE = new String[][] { { "AAA", "A~A", "AAA" }, { "A A", "A A", "AAA" },
        { "AAA", "AAA", "AAA" } };

    // Offsets: 1, 1, 0 —— 控制器在结构中的 (A, B, C) 偏移，即前面正中
    private static final int OFFSET_A = 1, OFFSET_B = 1, OFFSET_C = 0;

    private static final IStructureDefinition<MTEAdvancedCrucible> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEAdvancedCrucible>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'A',
            buildHatchAdder(MTEAdvancedCrucible.class)
                .atLeast(HatchElement.InputBus, HatchElement.OutputBus, new EssentiaMelterHatchElement())
                .casingIndex(Casings.BronzePlatedBricks.getTextureId())
                .hint(1)
                .buildAndChain(ofBlock(GregTechAPI.sBlockCasings1, 10)))
        .build();

    // blockbench/advancedCrucibleController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons
        .custom("libertas", "machines/advanced_crucible");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();

    /** 挂接在结构里的要素提炼仓，坩埚配方所需的要素从这里抽取。 */
    public final List<MTEHatchEssentiaMelter> mEssentiaMelters = new ArrayList<>();

    public MTEAdvancedCrucible(String aName) {
        super(aName);
    }

    public MTEAdvancedCrucible(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Crucible")
            .addInfo("Automatically runs Thaumcraft crucible crafting recipes")
            .addInfo("Catalysts come from Input Busses, results go to Output Busses")
            .addInfo("Essentia is drained from the attached Essentia Melters")
            .addInfo("Infinite parallel, fixed 1 tick duration")
            .addInfo("If essentia is lacking, the GUI shows what is missing")
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center")
            .addCasing("0-24", "Bronze Plated Bricks", false)
            .addOtherStructurePart("Input Bus", "Catalyst items for crucible recipes", 1)
            .addOtherStructurePart("Output Bus", "Receives the crafted items", 1)
            .addOtherStructurePart("Essentia Melter", "Stores essentia consumed by recipes", 1)
            .addStructureInfo("Interior: air column behind the controller")
            .addStructureInfo("Any casing position may be replaced by a bus or an Essentia Melter")
            .addStructureFooter("GregTech multiblocks may wallshare each of their sides")
            .addStructureFooter("to save on blocks, casings, glass, buses/hatches, etc.")
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
    public void clearHatches() {
        super.clearHatches();
        mEssentiaMelters.clear();
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

    // -----------------------------------------------------------------------
    // 结构部件：输入/输出总线走标准元素，要素提炼仓走自定义元素
    // -----------------------------------------------------------------------

    private boolean addEssentiaMelterToMachineList(IGregTechTileEntity tileEntity, Short baseCasingIndex) {
        if (tileEntity == null) return false;
        IMetaTileEntity metaTileEntity = tileEntity.getMetaTileEntity();
        if (metaTileEntity instanceof MTEHatchEssentiaMelter melter && !mEssentiaMelters.contains(melter)) {
            melter.updateTexture(baseCasingIndex);
            addIfSmartInput(melter);
            return mEssentiaMelters.add(melter);
        }
        return false;
    }

    private static class EssentiaMelterHatchElement implements IHatchElement<MTEAdvancedCrucible> {

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchEssentiaMelter.class);
        }

        @Override
        public IGTHatchAdder<? super MTEAdvancedCrucible> adder() {
            return MTEAdvancedCrucible::addEssentiaMelterToMachineList;
        }

        @Override
        public String name() {
            return "Essentia Melter";
        }

        @Override
        public long count(MTEAdvancedCrucible crucible) {
            return crucible.mEssentiaMelters.size();
        }
    }

    // -----------------------------------------------------------------------
    // 配方逻辑：坩埚配方（Thaumcraft CrucibleRecipe），催化剂来自输入总线，
    // 要素从提炼仓抽取，产物进输出总线；无限并行，固定 1 tick
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // 汇总所有提炼仓的可用要素（可能为空：此时缺口即配方全部需求，GUI 会提示）
        AspectList available = new AspectList();
        for (MTEHatchEssentiaMelter melter : mEssentiaMelters) {
            for (Aspect aspect : melter.getStoredEssentia()
                .getAspects()) {
                available.add(aspect, melter.getAspectAmount(aspect));
            }
        }

        List<ItemStack> outputs = new ArrayList<>();
        boolean crafted = false;
        // 有催化剂命中配方但要素不足时，记录缺口用于 GUI 提示
        AspectList deficit = null;

        // 无限并行：扫过所有输入总线的所有物品，能做多少做多少
        for (MTEHatchInputBus bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack == null) continue;
                while (stack.stackSize > 0) {
                    CrucibleRecipe craftable = null;
                    AspectList stackDeficit = null;
                    for (Object recipeObject : ThaumcraftApi.getCraftingRecipes()) {
                        if (!(recipeObject instanceof CrucibleRecipe recipe)) continue;
                        if (!recipe.catalystMatches(stack)) continue;
                        AspectList missing = computeDeficit(recipe.aspects, available);
                        if (missing.size() == 0) {
                            craftable = recipe;
                            break;
                        }
                        if (stackDeficit == null) stackDeficit = missing;
                    }
                    if (craftable == null) {
                        if (deficit == null && stackDeficit != null) deficit = stackDeficit;
                        break;
                    }
                    drainEssentia(craftable.aspects, available);
                    crafted = true;
                    stack.stackSize--;
                    outputs.add(
                        craftable.getRecipeOutput()
                            .copy());
                }
                if (stack.stackSize <= 0) bus.mInventory[slot] = null;
            }
            bus.getBaseMetaTileEntity()
                .markDirty();
        }

        if (crafted) {
            mOutputItems = outputs.toArray(new ItemStack[0]);
            mMaxProgresstime = 1;
            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (deficit != null) return new ResultInsufficientEssentia(deficit);
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    /**
     * 计算配方要素需求与当前可用要素之间的缺口；返回空列表表示要素充足。
     */
    private static AspectList computeDeficit(AspectList required, AspectList available) {
        AspectList deficit = new AspectList();
        for (Aspect aspect : required.getAspects()) {
            int lack = required.getAmount(aspect) - available.getAmount(aspect);
            if (lack > 0) deficit.add(aspect, lack);
        }
        return deficit;
    }

    private void drainEssentia(AspectList required, AspectList available) {
        for (Aspect aspect : required.getAspects()) {
            int remaining = required.getAmount(aspect);
            for (MTEHatchEssentiaMelter melter : mEssentiaMelters) {
                if (remaining <= 0) break;
                remaining -= melter.takeAspect(aspect, remaining);
            }
            available.remove(aspect, required.getAmount(aspect));
        }
    }
}
