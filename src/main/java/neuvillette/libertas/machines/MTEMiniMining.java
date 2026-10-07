package neuvillette.libertas.machines;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTEMiniMining extends MTELibertasMultiBlockBase<MTEMiniMining> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * Libertas 自定义 casing 贴图页：GT 占用 0/1/2/8/16/17，GoodGenerator=12，KekzTech=42，
     * 这里用 77 避开。index 0 = 机身侧面贴图，供结构里的输出总线复用，让舱室与机身浑然一体。
     */
    private static final byte TEXTURE_PAGE = 77;
    private static final int SIDE_CASING_INDEX = (TEXTURE_PAGE << 7) | 0;

    // blockbench/miniMiningController.bbmodel 设计，对应 assets/libertas/textures/blocks/machines/ 下贴图
    private static final IIconContainer FRONT_ICON = Textures.BlockIcons.custom("libertas", "machines/mini_mining");
    private static final IIconContainer SIDE_ICON = Textures.BlockIcons.custom("libertas", "machines/mini_mining_side");

    static {
        GTUtility.addTexturePage(TEXTURE_PAGE);
        Textures.BlockIcons.setCasingTextureForId(SIDE_CASING_INDEX, TextureFactory.of(SIDE_ICON));
    }

    private static final ITexture FRONT_TEXTURE = TextureFactory.builder()
        .addIcon(FRONT_ICON)
        .extFacing()
        .build();
    private static final ITexture SIDE_TEXTURE = TextureFactory.of(SIDE_ICON);

    /**
     * 来自 mte/MiniMining.txt 的 Normal Scan 原样数据：外层数组是深度 C（前→后），内层字符串是高度 B
     * （上→下，StructureLib 的 ABC 坐标系为 (X, -Y, Z)），字符是宽度 A（左→右），
     * 正是 {@link StructureDefinition.Builder#addShape} 直接消费的顺序，无需 transpose。
     * '~' 为控制器占位，空格为通配（空气），'A' 为圆石墙，'B' 为圆石。
     */
    private static final String[][] SHAPE = new String[][] { { "   ", "AAA", "A A", "A A", "B~B", "A A", "A A" },
        { " A ", "AAA", " A ", "   ", "BBB", " A ", "   " }, { "   ", "AAA", "A A", "A A", "BBB", "A A", "A A" } };

    // Offsets: 1, 4, 0 —— 控制器在结构中的 (A, B, C) 偏移，即机身带正面正中
    private static final int OFFSET_A = 1, OFFSET_B = 4, OFFSET_C = 0;

    private static final IStructureDefinition<MTEMiniMining> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEMiniMining>builder()
        .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
        .addElement(
            'A',
            buildHatchAdder(MTEMiniMining.class).atLeast(HatchElement.OutputBus)
                .casingIndex(SIDE_CASING_INDEX)
                .hint(1)
                .buildAndChain(ofBlock(Blocks.cobblestone_wall, 0)))
        .addElement(
            'B',
            buildHatchAdder(MTEMiniMining.class).atLeast(HatchElement.OutputBus)
                .casingIndex(SIDE_CASING_INDEX)
                .hint(2)
                .buildAndChain(ofBlock(Blocks.cobblestone, 0)))
        .build();

    /** 每 3 秒（60 tick）随机产出其中一种矿石。 */
    private static final int CYCLE_TICKS = 60;
    private static final Materials[] ORE_MATERIALS = { Materials.Iron, Materials.Copper, Materials.Tin,
        Materials.Coal };

    private static volatile ItemStack[] cachedOreOutputs;

    private final Random random = new Random();

    public MTEMiniMining(String aName) {
        super(aName);
    }

    public MTEMiniMining(int aID, String aName, String aRegionalName) {
        super(aID, aName, aRegionalName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(translateToLocal("libertas.mbtt.mini_mining.type"))
            .addInfo(translateToLocal("libertas.mbtt.mini_mining.info.1"))
            .addInfo(translateToLocal("libertas.mbtt.mini_mining.info.2"))
            .addInfo(translateToLocal("libertas.mbtt.mini_mining.info.3"))
            .beginStructureBlock(3, 7, 3, true)
            .addController(translateToLocal("libertas.mbtt.mini_mining.controller"))
            .addCasing("0-35", translateToLocal("libertas.mbtt.mini_mining.casing"), false)
            .addOtherStructurePart(
                translateToLocal("libertas.mbtt.part.output_bus"),
                translateToLocal("libertas.mbtt.part.output_bus.desc"),
                1)
            .addStructureInfo(translateToLocal("libertas.mbtt.mini_mining.structure.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.1"))
            .addStructureFooter(translateToLocal("libertas.mbtt.footer.2"))
            .toolTipFinisher("Libertas");
    }

    @Override
    public IStructureDefinition<MTEMiniMining> getStructureDefinition() {
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
        // 结构本身不强制带舱室，但采矿产物必须有去处：至少 1 个输出总线
        checkHasOutputBus(errors);
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
        return new MTEMiniMining(mName);
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
    // 采矿逻辑：不消耗任何输入，每 3 秒随机产出 1 个 GT 矿石（铁/铜/锡/煤），
    // 由基类排入输出总线
    // -----------------------------------------------------------------------

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        ItemStack[] ores = getOreOutputs();
        ItemStack picked = ores[random.nextInt(ores.length)];
        if (picked == null) return CheckRecipeResultRegistry.NO_RECIPE;

        mOutputItems = new ItemStack[] { picked.copy() };
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 解析四类 GT 矿石（ore 前缀统一产物：铁/煤为原版矿石，铜/锡为 GT 矿块）。
     * 首次调用时惰性解析并缓存，避免在类加载期依赖 oredict 就绪。
     */
    private static ItemStack[] getOreOutputs() {
        ItemStack[] ores = cachedOreOutputs;
        if (ores == null) {
            ores = new ItemStack[ORE_MATERIALS.length];
            for (int i = 0; i < ORE_MATERIALS.length; i++) {
                ores[i] = GTOreDictUnificator.get(OrePrefixes.ore, ORE_MATERIALS[i], 1L);
            }
            cachedOreOutputs = ores;
        }
        return ores;
    }
}
