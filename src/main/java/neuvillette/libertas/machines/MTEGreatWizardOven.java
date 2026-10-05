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
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.common.util.ForgeDirection;

import com.emoniph.witchery.Witchery;
import com.emoniph.witchery.item.ItemGeneral.SubItem;
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
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import thaumcraft.common.config.ConfigBlocks;

/**
 * 大巫师烤炉：神秘时代风格的 5x5x5 多方块，自动运行巫术（Witchery）巫师烤炉的熔烧配方。
 *
 * <p>
 * 配方语义与 TileEntityWitchesOven 一致：被烤物为任意"熔炉配方产物是煤/食物/木灰"的物品，
 * 无需燃料；产烟气时额外消耗 1 个空黏土罐（与原版巫师烤炉放罐产烟一致），主产物与烟气都是
 * 100% 产出；固定 5 秒（100 tick）完成，每轮并行 16。
 *
 * <p>
 * 结构来自 mte/GreatWizardOven.txt 的 Normal Scan 原样数据。Normal Scan 逐行遍历的 b 轴
 * 对直立机器朝下（ExtendedFacing 中水平朝向的 b 均为 DOWN），所以外层数组是深度 C（前→后）、
 * 内层字符串是高度 B（上→下）、字符是宽度 A（左→右），与
 * {@link StructureDefinition.Builder#addShape} 直接消费的顺序一致，无需 transpose。
 * '~' 为控制器占位，空格为通配。
 */
public class MTEGreatWizardOven extends MTELibertasMultiBlockBase<MTEGreatWizardOven>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** 固定耗时：5 秒。 */
    public static final int DURATION_TICKS = 100;

    /** 并行：每轮最多同时处理 16 份。 */
    public static final int MAX_PARALLEL = 16;

    /** 有可烤物但输入总线里没有空黏土罐时的 GUI 提示（ModMachines.init 里注册后才能同步显示）。 */
    public static final CheckRecipeResult RESULT_MISSING_JAR = SimpleCheckRecipeResult
        .ofFailure("libertas_missing_clay_jar");

    // spotless:off
    private static final String[][] SHAPE = new String[][] {
        { "ABBBA", "ABBBA", "AA~AA", "C   C", "C   C" },
        { "B   B", "B   B", "AAAAA", " DDD ", " EEE " },
        { "B   B", "B   B", "AAAAA", " DDD ", " EEE " },
        { "B   B", "B   B", "AAAAA", " DDD ", " EEE " },
        { "ABBBA", "ABBBA", "AAAAA", "C   C", "C   C" } };
    // spotless:on

    // Offsets: 2, 2, 0 —— 控制器在结构中的 (水平, 垂直, 深度) 偏移，即前面正中
    private static final int OFFSET_A = 2, OFFSET_B = 2, OFFSET_C = 0;

    /**
     * 给总线舱室使用神秘奥术石块贴图的 GT 外壳贴图 ID。GT 的贴图索引是 (page << 7) | index，
     * GT 本体只占用低页（TAE 扩展占 page 0 的 64-127），这里申请一个无人使用的高页。
     */
    private static final int CASING_INDEX_ARCANE_STONE = (7 << 7);
    static {
        if (Textures.BlockIcons.casingTexturePages[7] == null) {
            Textures.BlockIcons.casingTexturePages[7] = new ITexture[128];
        }
        Textures.BlockIcons
            .setCasingTextureForId(CASING_INDEX_ARCANE_STONE, TextureFactory.of(ConfigBlocks.blockCosmeticSolid, 6));
    }

    private static final IStructureDefinition<MTEGreatWizardOven> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEGreatWizardOven>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'A',
            buildHatchAdder(MTEGreatWizardOven.class).atLeast(HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(CASING_INDEX_ARCANE_STONE)
                .hint(1)
                .buildAndChain(ofBlock(ConfigBlocks.blockCosmeticSolid, 6)))
        .addElement('B', ofBlock(ConfigBlocks.blockCosmeticSolid, 7))
        .addElement('C', ofBlock(ganymedes01.etfuturum.ModBlocks.END_ROD.get(), 0))
        .addElement('D', ofBlock(Blocks.fire, 0))
        .addElement('E', ofBlock(Blocks.netherrack, 0))
        .build();

    // blockbench/greatWizardOvenController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons
        .custom("libertas", "machines/great_wizard_oven");
    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();
    /** 朝向以外的面直接用奥术石块贴图，与结构外壳融为一体。 */
    private static final ITexture SIDE_TEXTURE = TextureFactory.of(ConfigBlocks.blockCosmeticSolid, 6);

    public MTEGreatWizardOven(String aName) {
        super(aName);
    }

    public MTEGreatWizardOven(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(translateToLocal("libertas.mbtt.great_wizard_oven.type"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_oven.info.1"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_oven.info.2"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_oven.info.3"))
            .addInfo(translateToLocal("libertas.mbtt.great_wizard_oven.info.4"))
            .beginStructureBlock(5, 5, 5, true)
            .addController(translateToLocal("libertas.mbtt.great_wizard_oven.controller"))
            .addCasing("0-31", translateToLocal("libertas.mbtt.great_wizard_oven.casing"), false)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.input_bus"),
                translateToLocal("libertas.mbtt.great_wizard_oven.input_bus.desc"),
                1)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.output_bus"),
                translateToLocal("libertas.mbtt.part.output_bus.desc"),
                1)
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_oven.structure.1"))
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_oven.structure.2"))
            .addStructureInfo(translateToLocal("libertas.mbtt.great_wizard_oven.structure.3"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.2"))
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTEGreatWizardOven> getStructureDefinition() {
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
        return new MTEGreatWizardOven(mName);
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
    // 配方逻辑：与 Witchery TileEntityWitchesOven 相同的配方集合 ——
    // 原版熔炉配方中"产物是煤/食物/木灰"的那部分，无需燃料；产烟气的配方
    // 额外消耗 1 个空黏土罐。每轮并行 16，固定 100 tick。
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // 输出容量核算：GT 在产物塞不下输出总线时会直接销毁多余产物，
        // 所以这里模拟插入顺序（先与同物品合并、再占用空槽），放不下的不做
        OutputCapacity capacity = OutputCapacity.measure(mOutputBusses);

        List<ItemStack> outputs = new ArrayList<>();
        boolean crafted = false;
        boolean outputFull = false;
        boolean missingJar = false;
        int crafts = 0;

        for (var bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack == null) continue;
                // 空黏土罐是"容器原料"，不作为被烤物
                if (Witchery.Items.GENERIC.itemEmptyClayJar.isMatch(stack)) continue;

                while (stack.stackSize > 0 && crafts < MAX_PARALLEL) {
                    ItemStack result = FurnaceRecipes.smelting()
                        .getSmeltingResult(stack);
                    if (result == null || !isAllowedOvenOutput(result)) break;

                    ItemStack byproduct = getByproduct(stack);
                    if (!capacity.tryReserve(result)) {
                        outputFull = true;
                        break;
                    }
                    if (byproduct != null && !capacity.tryReserve(byproduct)) {
                        capacity.release(result);
                        outputFull = true;
                        break;
                    }
                    // 产烟气需要从输入总线真正取走一个空黏土罐（原版巫师烤炉的产烟机制）；
                    // 先占容量再扣罐，容量不足时不会白扣
                    if (byproduct != null && !takeEmptyClayJar()) {
                        capacity.release(byproduct);
                        capacity.release(result);
                        missingJar = true;
                        break;
                    }
                    if (byproduct != null) outputs.add(byproduct);
                    outputs.add(result.copy());
                    crafted = true;
                    crafts++;
                    stack.stackSize--;
                }
                if (stack.stackSize <= 0) bus.mInventory[slot] = null;
            }
            bus.getBaseMetaTileEntity()
                .markDirty();
        }

        if (crafted) {
            mOutputItems = outputs.toArray(new ItemStack[0]);
            mMaxProgresstime = DURATION_TICKS;
            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (missingJar) return RESULT_MISSING_JAR;
        if (outputFull) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    /**
     * 从输入总线里取走一个空黏土罐（扣减槽位并 markDirty）；没有则返回 false。
     */
    private boolean takeEmptyClayJar() {
        for (var bus : mInputBusses) {
            for (int slot = 0; slot < bus.mInventory.length; slot++) {
                ItemStack stack = bus.mInventory[slot];
                if (stack != null && Witchery.Items.GENERIC.itemEmptyClayJar.isMatch(stack)) {
                    if (--stack.stackSize <= 0) bus.mInventory[slot] = null;
                    bus.getBaseMetaTileEntity()
                        .markDirty();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 巫师烤炉只接受"熔炉产物为煤（含木炭）、食物或巫术木灰"的熔炉配方。
     */
    private static boolean isAllowedOvenOutput(ItemStack result) {
        Item item = result.getItem();
        return item == Items.coal || item instanceof ItemFood || Witchery.Items.GENERIC.itemAshWood.isMatch(result);
    }

    /**
     * 依据被烤物确定烟气副产物，语义与 Witchery generateByProduct 一致：
     * 原版树苗 0/1/2 -> 角兽之息/重生之兆/女神之息，丛林树苗落入默认分支（恶臭烟气），
     * 金合欢/深色橡树树苗无副产物；巫术树苗 0/1/2 -> 魔法气息/厄运之臭/纯净之气；
     * BOP 树苗 meta 6 -> 重生之兆；其余（含 Forestry 树木基因组分支，未安装故省略）一律恶臭烟气。
     * 返回 null 表示该配方不产烟气，也不消耗黏土罐。
     */
    private static ItemStack getByproduct(ItemStack input) {
        if (input.getItem() == Item.getItemFromBlock(Blocks.sapling)) {
            switch (input.getItemDamage()) {
                case 0 -> {
                    return fume(Witchery.Items.GENERIC.itemExhaleOfTheHornedOne);
                }
                case 1 -> {
                    return fume(Witchery.Items.GENERIC.itemHintOfRebirth);
                }
                case 2 -> {
                    return fume(Witchery.Items.GENERIC.itemBreathOfTheGoddess);
                }
                case 3 -> {
                    return fume(Witchery.Items.GENERIC.itemFoulFume);
                }
                default -> {
                    return null;
                }
            }
        }
        if (input.getItem() == Item.getItemFromBlock(Witchery.Blocks.SAPLING)) {
            switch (input.getItemDamage()) {
                case 0 -> {
                    return fume(Witchery.Items.GENERIC.itemWhiffOfMagic);
                }
                case 1 -> {
                    return fume(Witchery.Items.GENERIC.itemReekOfMisfortune);
                }
                case 2 -> {
                    return fume(Witchery.Items.GENERIC.itemOdourOfPurity);
                }
                default -> {
                    return null;
                }
            }
        }
        if (input.getUnlocalizedName()
            .equals("tile.bop.saplings") && input.getItemDamage() == 6) {
            return fume(Witchery.Items.GENERIC.itemHintOfRebirth);
        }
        return fume(Witchery.Items.GENERIC.itemFoulFume);
    }

    private static ItemStack fume(SubItem fume) {
        return fume.createStack(1);
    }

    /**
     * 输出总线剩余容量的轻量模拟：同一物品跨槽位合并计余量，空槽按"先到先得、整槽归一种物品"
     * 处理（首个物品进槽后剩余空间记回该物品的合并余量），用于在扣料前判断产物是否放得下。
     */
    private static final class OutputCapacity {

        /** key = item|meta，value = 该物品还能再合并的个数。 */
        private final Map<Long, Integer> mergeFree = new HashMap<>();
        private int emptySlots;

        static OutputCapacity measure(List<MTEHatchOutputBus> busses) {
            OutputCapacity capacity = new OutputCapacity();
            for (var bus : busses) {
                for (ItemStack stack : bus.mInventory) {
                    if (stack == null) capacity.emptySlots++;
                    else capacity.mergeFree.merge(key(stack), stack.getMaxStackSize() - stack.stackSize, Integer::sum);
                }
            }
            return capacity;
        }

        boolean tryReserve(ItemStack stack) {
            long k = key(stack);
            int free = mergeFree.getOrDefault(k, 0);
            if (free > 0) {
                mergeFree.put(k, free - 1);
                return true;
            }
            if (emptySlots > 0) {
                emptySlots--;
                mergeFree.put(k, stack.getMaxStackSize() - 1);
                return true;
            }
            return false;
        }

        void release(ItemStack stack) {
            mergeFree.merge(key(stack), 1, Integer::sum);
        }

        private static long key(ItemStack stack) {
            return ((long) Item.getIdFromItem(stack.getItem()) << 16) | (stack.getItemDamage() & 0xFFFF);
        }
    }
}
