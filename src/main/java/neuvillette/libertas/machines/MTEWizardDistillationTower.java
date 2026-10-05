package neuvillette.libertas.machines;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.emoniph.witchery.Witchery;
import com.emoniph.witchery.crafting.DistilleryRecipes;
import com.emoniph.witchery.crafting.DistilleryRecipes.DistilleryRecipe;
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
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import thaumcraft.common.config.ConfigBlocks;

/**
 * 巫师蒸馏塔：神秘时代风格的 6x5x4 多方块，自动运行巫术（Witchery）蒸馏塔的蒸馏配方。
 *
 * <p>
 * 配方数据不做静态复制，每轮直接镜像读取 {@link DistilleryRecipes} 的实时配方列表（与
 * {@code getDistillingResult} 相同的遍历顺序与匹配语义：两原料无序匹配、按注册顺序取第一个），
 * 原料各消耗 1 份，消耗配方所需的空黏土罐数（原版蒸馏塔槽 2 的语义）。原版的祭坛供能与
 * COOK_TIME 淡化为固定 3 秒（60 tick），每轮并行 1。
 *
 * <p>
 * 与原版唯一的语义差异：输入总线允许多余物品存在——原版双原料配方要求"另一个原料槽为空"
 * （单原料配方时），GT 输入总线没有槽位概念，沿用 GT 惯例忽略多余物品。
 *
 * <p>
 * 结构来自 mte/WizardDistillationTower.txt 的 Normal Scan 原样数据。Normal Scan 逐行遍历的
 * b 轴对直立机器朝下（ExtendedFacing 中水平朝向的 b 均为 DOWN），所以外层数组是深度 C（前→后）、
 * 内层字符串是高度 B（上→下）、字符是宽度 A（左→右），与
 * {@link StructureDefinition.Builder#addShape} 直接消费的顺序一致，无需 transpose。
 * '~' 为控制器占位，空格为通配。
 */
public class MTEWizardDistillationTower extends MTELibertasMultiBlockBase<MTEWizardDistillationTower>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** 固定耗时：3 秒。 */
    public static final int DURATION_TICKS = 60;

    /** 并行：每轮只蒸馏一份。 */
    public static final int MAX_PARALLEL = 1;

    /** 配料匹配但空黏土罐不足时的 GUI 提示（SimpleCheckRecipeResult 只需补本地化键）。 */
    public static final CheckRecipeResult RESULT_MISSING_JAR = SimpleCheckRecipeResult
        .ofFailure("libertas_distillery_missing_jar");

    // spotless:off
    private static final String[][] SHAPE = new String[][] {
        { "    C ",
        "    C ",
        "    C ",
        "    C ",
        "   DCD"
        }, {
        "    C ",
        "AAA   ",
        "BBB   ",
        "BBB   ",
        "B~BD D"
        }, {
        " CCCC ",
        "ACA   ",
        "BBB   ",
        "B B   ",
        "BBBD D"
        }, {
        "      ",
        "AAA   ",
        "BBB   ",
        "BBB   ",
        "BBBDDD"
        }};
    // spotless:on

    // Offsets: 1, 4, 1 —— 控制器在结构中的 (水平, 垂直, 深度) 偏移，即主体底层、进深第二格
    private static final int OFFSET_A = 1, OFFSET_B = 4, OFFSET_C = 1;

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

    private static final IStructureDefinition<MTEWizardDistillationTower> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEWizardDistillationTower>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'B',
            buildHatchAdder(MTEWizardDistillationTower.class).atLeast(HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(CASING_INDEX_ARCANE_STONE)
                .hint(1)
                .buildAndChain(ofBlock(ConfigBlocks.blockCosmeticSolid, 6)))
        .addElement('A', ofBlock(ConfigBlocks.blockSlabStone, 0))
        .addElement('C', ofBlock(ConfigBlocks.blockCosmeticSolid, 7))
        .addElement('D', ofBlock(Blocks.wooden_slab, 0))
        .build();

    // blockbench/wizardDistillationTowerController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons
        .custom("libertas", "machines/wizard_distillation_tower");
    private static final IIconContainer FRONT_ICON_ACTIVE = Textures.BlockIcons
        .custom("libertas", "machines/wizard_distillation_tower_active");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();
    private static final ITexture FRONT_TEXTURE_ACTIVE = TextureFactory.builder()
        .addIcon(FRONT_ICON_ACTIVE)
        .extFacing()
        .build();
    /** 朝向以外的面直接用奥术石块贴图，与结构外壳融为一体。 */
    private static final ITexture SIDE_TEXTURE = TextureFactory.of(ConfigBlocks.blockCosmeticSolid, 6);

    public MTEWizardDistillationTower(String aName) {
        super(aName);
    }

    public MTEWizardDistillationTower(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addMachineType(translateToLocal("libertas.mbtt.wizard_distillation_tower.type"))
            .addInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.info.1"))
            .addInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.info.2"))
            .addInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.info.3"))
            .beginStructureBlock(6, 5, 4, true)
            .addController(translateToLocal("libertas.mbtt.wizard_distillation_tower.controller"))
            .addCasing("0-26", translateToLocal("libertas.mbtt.wizard_distillation_tower.casing"), false)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.input_bus"),
                translateToLocal("libertas.mbtt.wizard_distillation_tower.input_bus.desc"),
                1)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.output_bus"),
                translateToLocal("libertas.mbtt.part.output_bus.desc"),
                1)
            .addStructureInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.structure.1"))
            .addStructureInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.structure.2"))
            .addStructureInfo(translateToLocal("libertas.mbtt.wizard_distillation_tower.structure.3"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.2"))
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTEWizardDistillationTower> getStructureDefinition() {
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
        return new MTEWizardDistillationTower(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { aActive ? FRONT_TEXTURE_ACTIVE : FRONT_TEXTURE };
        }
        return new ITexture[] { SIDE_TEXTURE };
    }

    // -----------------------------------------------------------------------
    // 配方逻辑：镜像 Witchery DistilleryRecipes 的实时配方列表。语义与
    // TileEntityDistillery 一致：两原料无序匹配（各消耗 1 份）、按注册顺序取
    // 第一个匹配配方、消耗配方所需数量的空黏土罐、产物 100% 产出；
    // 每轮并行 1，固定 60 tick，不消耗电力。
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // 扫描输入总线：原料按槽位引用收集，空黏土罐单独计数（对应原版槽 2 只收空黏土罐）
        List<IngredientRef> ingredients = new ArrayList<>();
        List<IngredientRef> jars = new ArrayList<>();
        for (var bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack == null) continue;
                IngredientRef ref = new IngredientRef(bus, slot, stack);
                if (Witchery.Items.GENERIC.itemEmptyClayJar.isMatch(stack)) {
                    jars.add(ref);
                } else {
                    ingredients.add(ref);
                }
            }
        }

        // 与 Witchery getDistillingResult 相同：按注册顺序取第一个完整匹配的配方
        DistilleryRecipe matched = null;
        int[] matchedPlan = null;
        boolean ingredientMatchButShortJars = false;
        for (DistilleryRecipe recipe : DistilleryRecipes.instance().recipes) {
            int[] plan = tryAllocateIngredients(ingredients, recipe);
            if (plan == null) continue;
            if (recipe.getJars() > countJars(jars)) {
                ingredientMatchButShortJars = true;
                continue;
            }
            matched = recipe;
            matchedPlan = plan;
            break;
        }

        if (matched == null) {
            if (ingredientMatchButShortJars) return RESULT_MISSING_JAR;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 输出容量核算：GT 在产物塞不下输出总线时会直接销毁多余产物，先模拟再放入
        ItemStack[] outputs = nonNullCopies(matched.getOutputs());
        if (!outputsFit(outputs)) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;

        // 容量通过，真正扣料：原料各 1 份，空黏土罐按配方数量
        consume(ingredients, matchedPlan);
        consumeJars(jars, matched.getJars());

        mOutputItems = outputs;
        mMaxProgresstime = DURATION_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 尝试为配方的两个原料位各预留 1 份：与 Witchery {@code isMatch} 一致（物品相同，
     * 且配方原料带子类型时 meta 必须相同），两原料无序。同一物品可以由同一槽位的多份
     * 承担（原版要求分居两个槽位，GT 总线按数量语义更自然）。返回扣料计划，不匹配返回
     * null；inputs[0] 为 null 的退化配方一律跳过。
     */
    private static int[] tryAllocateIngredients(List<IngredientRef> ingredients, DistilleryRecipe recipe) {
        ItemStack first = recipe.inputs[0];
        if (first == null) return null;
        int[] plan = new int[ingredients.size()];
        if (!allocateOne(ingredients, plan, first)) return null;
        if (!allocateOne(ingredients, plan, recipe.inputs[1])) return null;
        return plan;
    }

    private static boolean allocateOne(List<IngredientRef> ingredients, int[] plan, ItemStack recipeInput) {
        if (recipeInput == null) return true;
        for (int i = 0; i < ingredients.size(); i++) {
            IngredientRef ref = ingredients.get(i);
            if (plan[i] >= ref.stack.stackSize) continue;
            if (witcheryMatches(ref.stack, recipeInput)) {
                plan[i]++;
                return true;
            }
        }
        return false;
    }

    private static boolean witcheryMatches(ItemStack candidate, ItemStack recipeInput) {
        if (candidate == null || recipeInput == null) return false;
        if (candidate.getItem() != recipeInput.getItem()) return false;
        return !recipeInput.getHasSubtypes() || candidate.getItemDamage() == recipeInput.getItemDamage();
    }

    private static int countJars(List<IngredientRef> jars) {
        int count = 0;
        for (IngredientRef jar : jars) count += jar.stack.stackSize;
        return count;
    }

    private static ItemStack[] nonNullCopies(ItemStack[] outputs) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output != null) result.add(output.copy());
        }
        return result.toArray(new ItemStack[0]);
    }

    /**
     * 输出总线剩余容量的轻量模拟：同一物品跨槽位合并计余量，空槽按"先到先得、整槽归一种物品"
     * 处理（首个物品进槽后剩余空间记回该物品的合并余量），用于在扣料前判断产物是否放得下。
     */
    private boolean outputsFit(ItemStack[] outputs) {
        Map<Long, Integer> mergeFree = new HashMap<>();
        int emptySlots = 0;
        for (var bus : mOutputBusses) {
            for (ItemStack stack : bus.mInventory) {
                if (stack == null) {
                    emptySlots++;
                } else {
                    long key = stackKey(stack);
                    mergeFree.merge(key, stack.getMaxStackSize() - stack.stackSize, Integer::sum);
                }
            }
        }
        for (ItemStack output : outputs) {
            long key = stackKey(output);
            int free = mergeFree.getOrDefault(key, 0);
            if (free > 0) {
                mergeFree.put(key, free - 1);
            } else if (emptySlots > 0) {
                emptySlots--;
                mergeFree.put(key, output.getMaxStackSize() - 1);
            } else {
                return false;
            }
        }
        return true;
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

    private static void consumeJars(List<IngredientRef> jars, int total) {
        for (IngredientRef jar : jars) {
            if (total <= 0) break;
            int take = Math.min(total, jar.stack.stackSize);
            if (take <= 0) continue;
            total -= take;
            jar.stack.stackSize -= take;
            if (jar.stack.stackSize <= 0) jar.bus.mInventory[jar.slot] = null;
            jar.bus.getBaseMetaTileEntity()
                .markDirty();
        }
    }

    /** 输入总线上的一个槽位引用（扣料用），原料与空黏土罐共用。 */
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
