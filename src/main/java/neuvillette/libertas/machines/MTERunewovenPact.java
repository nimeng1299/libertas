package neuvillette.libertas.machines;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofSpecificTileAdder;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

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
import neuvillette.libertas.blocks.ModBlocks;
import neuvillette.libertas.blocks.TileAzureTear;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.tiles.TileInfusionMatrix;
import thaumcraft.common.tiles.TileInfusionPillar;
import thaumcraft.common.tiles.TilePedestal;

/**
 * 秘纹织契：围绕神秘时代注魔祭坛（符文矩阵 + 台座 + 注魔支柱 + 碧空之泪）搭建的多方块，
 * 自动执行神秘注魔配方（{@link InfusionRecipe}）。中心物品与组件都从输入总线取料，
 * 要素从要素熔炼仓抽取，产物进输出总线；并行 1（每轮只做一次注魔），固定 5 秒（100 tick）耗时。
 *
 * <p>
 * 特殊方块（矩阵/台座/支柱/碧空之泪）不能由多方块代放，需要按结构提示手工摆放，
 * 结构检查只校验它们的存在（{@link com.gtnewhorizon.structurelib.structure.StructureUtility#ofSpecificTileAdder}）。
 */
public class MTERunewovenPact extends MTELibertasMultiBlockBase<MTERunewovenPact> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** 固定耗时：5 秒。 */
    public static final int DURATION_TICKS = 100;

    /**
     * 来自 mte/RunewovenPact.txt 的 Normal Scan 原样数据：外层数组是深度 C（前→后），内层字符串是高度 B
     * （下→上），字符是宽度 A（左→右），与 {@link StructureDefinition.Builder#addShape} 直接消费的顺序一致，
     * 无需 transpose。'~' 为控制器占位；C/D/E/F 为不能代放的特殊方块（碧空之泪/符文矩阵/台座/注魔支柱），
     * B 为注魔支柱柱身（blockStoneDevice meta 6），空格为通配。
     */
    // spotless:off
    private static final String[][] SHAPE = new String[][] {
        { "       ", "       ", "       ", "  EEE  ", "AAA~AAA" },
        { "       ", "       ", "       ", "       ", "AAAAAAA" },
        { "       ", "       ", "  B B  ", "E F F E", "AAAAAAA" },
        { "       ", "   D   ", "       ", "E  E  E", "AAACAAA" },
        { "       ", "       ", "  B B  ", "E F F E", "AAAAAAA" },
        { "       ", "       ", "       ", "       ", "AAAAAAA" },
        { "       ", "       ", "       ", "  EEE  ", "AAAAAAA" } };
    // spotless:on

    // Offsets: 3, 4, 0 —— 控制器在结构中的 (水平, 垂直, 深度) 偏移，即顶层前侧正中
    private static final int OFFSET_HORIZONTAL = 3, OFFSET_VERTICAL = 4, OFFSET_DEPTH = 0;

    private static final IStructureDefinition<MTERunewovenPact> STRUCTURE_DEFINITION = StructureDefinition
        .<MTERunewovenPact>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'A',
            buildHatchAdder(MTERunewovenPact.class)
                .atLeast(HatchElement.InputBus, HatchElement.OutputBus, new EssentiaMelterHatchElement())
                .casingIndex(Casings.FrostProofMachineCasing.getTextureId())
                .hint(1)
                .buildAndChain(ofBlock(GregTechAPI.sBlockCasings2, 1)))
        .addElement('B', ofBlock(ConfigBlocks.blockStoneDevice, 6))
        .addElement('C', ofSpecificTileAdder((t, te) -> true, TileAzureTear.class, ModBlocks.azureTear, 0))
        .addElement(
            'D',
            ofSpecificTileAdder((t, te) -> true, TileInfusionMatrix.class, ConfigBlocks.blockStoneDevice, 2))
        .addElement('E', ofSpecificTileAdder((t, te) -> true, TilePedestal.class, ConfigBlocks.blockStoneDevice, 1))
        .addElement(
            'F',
            ofSpecificTileAdder((t, te) -> true, TileInfusionPillar.class, ConfigBlocks.blockStoneDevice, 3))
        .build();

    // blockbench/runewovenPactController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons.custom("libertas", "machines/runewoven_pact");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();

    /** 挂接在结构里的要素熔炼仓，注魔配方所需的要素从这里抽取。 */
    public final List<MTEHatchEssentiaMelter> mEssentiaMelters = new ArrayList<>();

    public MTERunewovenPact(String aName) {
        super(aName);
    }

    public MTERunewovenPact(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(translateToLocal("libertas.mbtt.runewoven_pact.type"))
            .addInfo(translateToLocal("libertas.mbtt.runewoven_pact.info.1"))
            .addInfo(translateToLocal("libertas.mbtt.runewoven_pact.info.2"))
            .addInfo(translateToLocal("libertas.mbtt.shared.info.essentia"))
            .addInfo(translateToLocal("libertas.mbtt.runewoven_pact.info.3"))
            .addInfo(translateToLocal("libertas.mbtt.shared.info.missing"))
            .beginStructureBlock(7, 5, 7, true)
            .addController(translateToLocal("libertas.mbtt.runewoven_pact.controller"))
            .addCasing("0-47", translateToLocal("libertas.mbtt.runewoven_pact.casing"), false)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.input_bus"),
                translateToLocal("libertas.mbtt.runewoven_pact.input_bus.desc"),
                1)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.output_bus"),
                translateToLocal("libertas.mbtt.part.output_bus.desc"),
                1)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.essentia_melter"),
                translateToLocal("libertas.mbtt.part.essentia_melter.desc"),
                1)
            .addStructureInfo(translateToLocal("libertas.mbtt.runewoven_pact.structure.1"))
            .addStructureInfo(translateToLocal("libertas.mbtt.runewoven_pact.structure.2"))
            .addStructureInfo(translateToLocal("libertas.mbtt.runewoven_pact.structure.3"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.2"))
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTERunewovenPact> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OFFSET_HORIZONTAL, OFFSET_VERTICAL, OFFSET_DEPTH);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            OFFSET_HORIZONTAL,
            OFFSET_VERTICAL,
            OFFSET_DEPTH,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, OFFSET_HORIZONTAL, OFFSET_VERTICAL, OFFSET_DEPTH, errors);
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
        return new MTERunewovenPact(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { FRONT_TEXTURE };
        }
        return new ITexture[] { Casings.FrostProofMachineCasing.getCasingTexture() };
    }

    // -----------------------------------------------------------------------
    // 结构部件：输入/输出总线走标准元素，要素熔炼仓走自定义元素
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

    private static class EssentiaMelterHatchElement implements IHatchElement<MTERunewovenPact> {

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchEssentiaMelter.class);
        }

        @Override
        public IGTHatchAdder<? super MTERunewovenPact> adder() {
            return MTERunewovenPact::addEssentiaMelterToMachineList;
        }

        @Override
        public String name() {
            return "Essentia Melter";
        }

        @Override
        public long count(MTERunewovenPact pact) {
            return pact.mEssentiaMelters.size();
        }
    }

    // -----------------------------------------------------------------------
    // 配方逻辑：注魔配方（Thaumcraft InfusionRecipe），中心物品与组件都从输入总线
    // 取料（TC 的台座/矩阵原料池合并为总线物品池，多余物品不阻碍匹配），要素从
    // 熔炼仓抽取，产物进输出总线；并行 1（每轮只做一次注魔），固定 100 tick。
    // 不做研究解锁与不稳定度判定（机器合成无玩家参与）。
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // 汇总所有熔炼仓的可用要素（可能为空：此时缺口即配方全部需求，GUI 会提示）
        AspectList available = new AspectList();
        for (MTEHatchEssentiaMelter melter : mEssentiaMelters) {
            for (Aspect aspect : melter.getStoredEssentia()
                .getAspects()) {
                available.add(aspect, melter.getAspectAmount(aspect));
            }
        }

        // 单次处理：找到一个原料齐备的配方即停
        final InfusionRecipe recipe = findAndConsumeRecipe();
        if (recipe != null) {
            AspectList missing = computeDeficit(recipe.getAspects(), available);
            if (missing.size() > 0) {
                rollbackLastConsumption();
                return new ResultInsufficientEssentia(missing);
            }
            drainEssentia(recipe.getAspects(), available);
            for (MTEHatchInputBus bus : mInputBusses) {
                bus.getBaseMetaTileEntity()
                    .markDirty();
            }
            mOutputItems = new ItemStack[] { ((ItemStack) recipe.getRecipeOutput()).copy() };
            mMaxProgresstime = DURATION_TICKS;
            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    /**
     * 遍历神秘注魔配方，找到第一个原料齐备（中心物品 + 全部组件）的配方并立即从输入总线扣料；
     * 扣料的槽位记录在 {@link #mLastConsumption} 里，要素不足时用 {@link #rollbackLastConsumption()} 归还。
     */
    private InfusionRecipe findAndConsumeRecipe() {
        for (Object recipeObject : ThaumcraftApi.getCraftingRecipes()) {
            if (!(recipeObject instanceof InfusionRecipe recipe)) continue;
            if (!(recipe.getRecipeOutput() instanceof ItemStack)) continue;
            final ItemStack central = recipe.getRecipeInput();
            final ItemStack[] components = recipe.getComponents();
            if (central == null || components == null) continue;

            mLastConsumption.clear();
            if (!takeOne(central)) {
                mLastConsumption.clear();
                continue;
            }
            boolean complete = true;
            for (ItemStack component : components) {
                // TC 组件数组里的 null 是"任意物品"通配位
                if (!takeOne(component)) {
                    complete = false;
                    break;
                }
            }
            if (complete) return recipe;
            mLastConsumption.clear();
        }
        return null;
    }

    /** 最近一次 findAndConsumeRecipe 扣掉的槽位（同一槽位可能被多次取用，按次记录），用于整体回滚。 */
    private final List<ConsumedSlot> mLastConsumption = new ArrayList<>();

    /**
     * 从输入总线取一件与 expected 匹配的物品（expected 为 null 表示任意物品），取到即扣减 1
     * 并记录进 {@link #mLastConsumption}。
     */
    private boolean takeOne(ItemStack expected) {
        for (MTEHatchInputBus bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack == null || stack.stackSize <= 0) continue;
                if (expected != null && !matchesRecipeStack(stack, expected)) continue;
                mLastConsumption.add(new ConsumedSlot(bus, slot, stack));
                if (--stack.stackSize <= 0) bus.mInventory[slot] = null;
                return true;
            }
        }
        return false;
    }

    private void rollbackLastConsumption() {
        for (ConsumedSlot consumed : mLastConsumption) {
            if (consumed.bus.mInventory[consumed.slot] == null) {
                consumed.bus.mInventory[consumed.slot] = consumed.stack;
            }
            consumed.stack.stackSize++;
        }
        mLastConsumption.clear();
    }

    private static class ConsumedSlot {

        final MTEHatchInputBus bus;
        final int slot;
        /** 被扣减的同一 ItemStack 对象（回滚时归还一件并放回槽位）。 */
        final ItemStack stack;

        ConsumedSlot(MTEHatchInputBus bus, int slot, ItemStack stack) {
            this.bus = bus;
            this.slot = slot;
            this.stack = stack;
        }
    }

    /**
     * 与 TC InfusionRecipe.areItemStacksEqual(candidate, expected, fuzzy=true) 相同的匹配语义：
     * NBT 需满足 areItemStackTagsEqualForCrafting，支持矿物词典模糊匹配与 meta 32767 通配。
     */
    private static boolean matchesRecipeStack(ItemStack candidate, ItemStack expected) {
        ItemStack c = candidate.copy();
        if (expected.getItemDamage() == OreDictionary.WILDCARD_VALUE) c.setItemDamage(OreDictionary.WILDCARD_VALUE);
        if (!ThaumcraftApiHelper.areItemStackTagsEqualForCrafting(c, expected)) return false;
        final int oreId = OreDictionary.getOreID(c);
        if (oreId != -1 && ThaumcraftApiHelper.containsMatch(
            false,
            new ItemStack[] { expected },
            OreDictionary.getOres(oreId)
                .toArray(new ItemStack[0])))
            return true;
        return c.getItem() == expected.getItem() && (c.getItemDamage() == expected.getItemDamage()
            || expected.getItemDamage() == OreDictionary.WILDCARD_VALUE);
    }

    /**
     * 计算配方要素需求与当前可用要素之间的缺口；返回空列表表示要素充足。
     */
    private static AspectList computeDeficit(AspectList required, AspectList available) {
        AspectList deficit = new AspectList();
        if (required == null) return deficit;
        for (Aspect aspect : required.getAspects()) {
            int lack = required.getAmount(aspect) - available.getAmount(aspect);
            if (lack > 0) deficit.add(aspect, lack);
        }
        return deficit;
    }

    private void drainEssentia(AspectList required, AspectList available) {
        if (required == null) return;
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
