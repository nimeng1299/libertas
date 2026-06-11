package neuvillette.libertas.multiblock;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

/**
 * 石头矿机 (Stone Miner)
 * 3x3x3 multi-block, stone walls, hollow center.
 * Burns fuel to generate heat, then uses that heat to produce ores.
 */
public class StoneMiner extends GTPPMultiBlockBase<StoneMiner> implements ISurvivalConstructable, ISecondaryDescribable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static IStructureDefinition<StoneMiner> STRUCTURE_DEFINITION = null;
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
    public String getMachineType() {
        return "base void miner";
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 4, 9, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 4, 9, 0, elementBudget, env, false, true);
    }


    @Override
    public IStructureDefinition<StoneMiner> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<StoneMiner>builder()
                .addShape(STRUCTURE_PIECE_MAIN, Shape)
                .addElement('A', buildHatchAdder(StoneMiner.class)
                    .atLeast(InputHatch, OutputHatch)
                    .casingIndex(Textures.BlockIcons.getTextureIndex(Textures.BlockIcons
                        .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0))))
                    .dot(1)
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
        tt.addSeparator();
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new StoneMiner(mName);
    }
}
