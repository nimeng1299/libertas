package neuvillette.libertas.machines;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.emoniph.witchery.brewing.WitcheryBrewRegistry;
import com.emoniph.witchery.brewing.action.BrewActionRitualRecipe;
import com.emoniph.witchery.brewing.action.BrewActionRitualRecipe.Recipe;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import thaumcraft.common.config.ConfigBlocks;

/**
 * 大巫师炼药锅：神秘时代风格的 3x3x3 多方块，自动运行巫术（Witchery）巫师炼药锅
 * （Witch's Cauldron，{@code Witchery.Blocks.CAULDRON}；注意不是 Kettle 水壶）的仪式配方。
 *
 * <p>
 * 配方数据不做静态复制，每轮直接镜像读取 {@link WitcheryBrewRegistry} 的实时仪式配方列表
 * （与 NEI 的 NEICauldronRecipeHandler，即 "witchery_brewing_plus" 页相同的数据源与遍历顺序：
 * 仪式按注册顺序、展开配方按声明顺序，取第一个原料齐全的配方）。展开配方的原料 =
 * 配方原料 + 触发物（BrewItemKey），每份各消耗 1 个（与原版一致：扔进锅里的物品包括触发物
 * 全部消耗），产物 100% 产出（如 6 份变异泰迪斯、无限水/燃料瓶、烤肉、各色粉笔、玩家指南针等）。
 * 原版的满水罐、下方沸腾热源与祭坛供能淡化省略，固定耗时 3 秒（60 tick），每轮只炼一剂
 * （并行 1，对应原版单锅单次仪式）。
 *
 * <p>
 * 与原版的语义差异（沿用巫师蒸馏塔的 GT 端口惯例）：
 * <ul>
 * <li>输入总线允许多余物品存在——原版按"往锅里扔物品"逐个登记动作、最后扔入的必须是触发物；
 * GT 输入总线没有时序与槽位概念，改为全量匹配 + 按注册顺序取第一个完整配方，多余物品忽略。</li>
 * <li>原版配错不触发仪式（物品留在锅里），此处配方不匹配时同样不清空原料。</li>
 * </ul>
 *
 * <p>
 * 结构来自 mte/GreatWizardCauldron.txt 的 Normal Scan 原样数据。Normal Scan 逐行遍历的
 * b 轴对直立机器朝下（ExtendedFacing 中水平朝向的 b 均为 DOWN），所以外层数组是深度 C
 * （前→后）、内层字符串是高度 B（上→下）、字符是宽度 A（左→右），与
 * {@link StructureDefinition.Builder#addShape} 直接消费的顺序一致，无需 transpose。
 * '~' 为控制器占位，空格为通配。
 */
public class MTEGreatWizardCauldron extends MTELibertasMultiBlockBase<MTEGreatWizardCauldron>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** 固定耗时：3 秒。 */
    public static final int DURATION_TICKS = 60;

    /** 并行：每轮只炼一剂。 */
    public static final int MAX_PARALLEL = 1;

    // spotless:off
    private static final String[][] SHAPE = new String[][] {
        { "BBB",
        "A~A",
        "B B"
        },{
        "B B",
        "AAA",
        "   "
        },{
        "BBB",
        "AAA",
        "B B"
        }};
    // spotless:on

    // Offsets: 1, 1, 0 —— 控制器在结构中的 (水平, 垂直, 深度) 偏移，即锅圈正面正中
    private static final int OFFSET_A = 1, OFFSET_B = 1, OFFSET_C = 0;

    /**
     * 给总线舱室使用神秘奥术石块贴图的 GT 外壳贴图 ID（与大巫师烤炉共用同一页/同一位图，
     * {@code setCasingTextureForId} 重复注册同值无副作用）。
     */
    private static final int CASING_INDEX_ARCANE_STONE = (7 << 7);
    static {
        if (Textures.BlockIcons.casingTexturePages[7] == null) {
            Textures.BlockIcons.casingTexturePages[7] = new ITexture[128];
        }
        Textures.BlockIcons
            .setCasingTextureForId(CASING_INDEX_ARCANE_STONE, TextureFactory.of(ConfigBlocks.blockCosmeticSolid, 6));
    }

    private static final IStructureDefinition<MTEGreatWizardCauldron> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEGreatWizardCauldron>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'A',
            buildHatchAdder(MTEGreatWizardCauldron.class).atLeast(HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(CASING_INDEX_ARCANE_STONE)
                .hint(1)
                .buildAndChain(ofBlock(ConfigBlocks.blockCosmeticSolid, 6)))
        .addElement('B', ofBlock(Blocks.cobblestone_wall, 0))
        .build();

    // blockbench/greatWizardCauldronController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons
        .custom("libertas", "machines/great_wizard_cauldron");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();
    /** 朝向以外的面直接用奥术石块贴图，与结构外壳融为一体。 */
    private static final ITexture SIDE_TEXTURE = TextureFactory.of(ConfigBlocks.blockCosmeticSolid, 6);

    public MTEGreatWizardCauldron(String aName) {
        super(aName);
    }

    public MTEGreatWizardCauldron(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addMachineType(translateToLocal("libertas.mbtt.great_wizard_cauldron.type"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.info.1"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.info.2"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.info.3"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.info.4"))
            .beginStructureBlock(3, 3, 3, true)
            .addController(translateToLocal("libertas.mbtt.great_wizard_cauldron.controller"))
            .addCasing("0-26", translateToLocal("libertas.mbtt.great_wizard_cauldron.casing"), false)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.input_bus"),
                translateToLocal("libertas.mbtt.great_wizard_cauldron.input_bus.desc"),
                1)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.output_bus"),
                translateToLocal("libertas.mbtt.part.output_bus.desc"),
                1)
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.structure.1"))
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.structure.2"))
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_cauldron.structure.3"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.2"))
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTEGreatWizardCauldron> getStructureDefinition() {
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
        return new MTEGreatWizardCauldron(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { FRONT_TEXTURE };
        }
        return new ITexture[] { SIDE_TEXTURE };
    }

    // -----------------------------------------------------------------------
    // 配方逻辑：镜像 WitcheryBrewRegistry 的实时仪式配方列表（巫师炼药锅的
    // "witchery_brewing_plus" NEI 页数据源）。语义与原版炼药锅一致：展开配方的
    // 每份原料（含触发物）各消耗 1 个、产物 100% 产出；匹配用 (物品, meta) 精确
    // 相等（与 BrewActionRitualRecipe.removeFromNeededItems 一致）。每轮并行 1，
    // 固定 60 tick，不消耗电力。
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // 扫描输入总线：原料按槽位引用收集
        List<IngredientRef> ingredients = new ArrayList<>();
        for (var bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack != null) ingredients.add(new IngredientRef(bus, slot, stack));
            }
        }
        if (ingredients.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        // 与 NEI NEICauldronRecipeHandler 相同的遍历顺序：仪式按注册顺序、
        // 展开配方按声明顺序，取第一个原料齐全的配方
        Recipe matched = null;
        int[] matchedPlan = null;
        outer: for (BrewActionRitualRecipe ritual : WitcheryBrewRegistry.INSTANCE.getRecipes()) {
            for (Recipe recipe : ritual.getExpandedRecipes()) {
                int[] plan = tryAllocateIngredients(ingredients, recipe);
                if (plan != null) {
                    matched = recipe;
                    matchedPlan = plan;
                    break outer;
                }
            }
        }

        if (matched == null) return CheckRecipeResultRegistry.NO_RECIPE;

        // 输出容量核算：GT 在产物塞不下输出总线时会直接销毁多余产物，先模拟再放入
        ItemStack output = matched.result.copy();
        if (!outputFits(output)) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;

        consume(ingredients, matchedPlan);

        mOutputItems = new ItemStack[] { output };
        mMaxProgresstime = DURATION_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 尝试为展开配方的每份原料（含触发物）各预留 1 个：与 Witchery
     * {@code removeFromNeededItems} 一致，(物品, meta) 精确相等，同一原料可由
     * 同一槽位的多份承担。返回扣料计划，不匹配返回 null。
     */
    private static int[] tryAllocateIngredients(List<IngredientRef> ingredients, Recipe recipe) {
        int[] plan = new int[ingredients.size()];
        for (ItemStack recipeInput : recipe.ingredients) {
            if (recipeInput == null) return null;
            if (!allocateOne(ingredients, plan, recipeInput)) return null;
        }
        return plan;
    }

    private static boolean allocateOne(List<IngredientRef> ingredients, int[] plan, ItemStack recipeInput) {
        for (int i = 0; i < ingredients.size(); i++) {
            IngredientRef ref = ingredients.get(i);
            if (plan[i] >= ref.stack.stackSize) continue;
            if (ref.stack.getItem() == recipeInput.getItem()
                && ref.stack.getItemDamage() == recipeInput.getItemDamage()) {
                plan[i]++;
                return true;
            }
        }
        return false;
    }

    /**
     * 输出总线剩余容量的轻量模拟：同一物品跨槽位合并计余量，空槽按"先到先得、整槽归一种物品"
     * 处理，用于在扣料前判断产物是否放得下。
     */
    private boolean outputFits(ItemStack output) {
        long key = stackKey(output);
        int mergeFree = 0, emptySlots = 0;
        for (var bus : mOutputBusses) {
            for (ItemStack stack : bus.mInventory) {
                if (stack == null) emptySlots++;
                else if (stackKey(stack) == key) mergeFree += stack.getMaxStackSize() - stack.stackSize;
            }
        }
        return mergeFree > 0 || emptySlots > 0;
    }

    private static long stackKey(ItemStack stack) {
        return ((long) net.minecraft.item.Item.getIdFromItem(stack.getItem()) << 16) | (stack.getItemDamage() & 0xFFFF);
    }

    private static void consume(List<IngredientRef> ingredients, int[] plan) {
        for (int i = 0; i < ingredients.size(); i++) {
            int toTake = plan[i];
            if (toTake <= 0) continue;
            IngredientRef ref = ingredients.get(i);
            ref.stack.stackSize -= toTake;
            if (ref.stack.stackSize <= 0) ref.bus.mInventory[ref.slot] = null;
            ref.bus.getBaseMetaTileEntity()
                .markDirty();
        }
    }

    /** 输入总线上的一个槽位引用（扣料用）。 */
    private static final class IngredientRef {

        final MTEHatchInputBus bus;
        final int slot;
        final ItemStack stack;

        IngredientRef(MTEHatchInputBus bus, int slot, ItemStack stack) {
            this.bus = bus;
            this.slot = slot;
            this.stack = stack;
        }
    }
}
