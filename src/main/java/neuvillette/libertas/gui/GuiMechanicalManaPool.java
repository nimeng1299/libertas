package neuvillette.libertas.gui;

import java.util.Arrays;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.nei.recipe.GuiCraftingRecipe;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.TileMechanicalManaPool;

/// 机械魔力池 GUI: 16 输入 + 16 输出 + 1 升级槽, 顶部一条魔力存量条。
/// 背景贴图由 tools/gen_mechanical_mana_pool_gui.py 生成。
/// 点击输入/输出区之间的箭头可打开 NEI 的魔力池配方页 (botania.manaPool)。
@SideOnly(Side.CLIENT)
public class GuiMechanicalManaPool extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "libertas",
        "textures/gui/mechanicalManaPool.png");

    /// 与背景贴图中的魔力条凹槽一致: x 8..168, y 15..23 (内容填充再内缩 1px)
    private static final int MANA_BAR_X = 8;
    private static final int MANA_BAR_Y = 15;
    private static final int MANA_BAR_W = 160;
    private static final int MANA_BAR_H = 9;
    private static final int MANA_BAR_COLOR = 0xFF20B2E0;

    /// 魔力数字的字号倍率 (原版字号的 3/4; 缩放空间坐标 = 像素坐标 / 倍率)
    private static final float MANA_TEXT_SCALE = 0.75f;

    /// 输入/输出区之间的 NEI 箭头按钮 (贴图同款位置, 略放宽热区)
    private static final int NEI_ARROW_X1 = 80;
    private static final int NEI_ARROW_X2 = 96;
    private static final int NEI_ARROW_Y1 = 75;
    private static final int NEI_ARROW_Y2 = 92;

    /// Botania RecipeHandlerManaPool 的 overlay 标识 (其源码内硬编码, 无公开常量)
    private static final String NEI_MANA_POOL_PAGE = "botania.manaPool";

    /// 背景层画贴图/填充, 前景层画文字
    private final TileMechanicalManaPool tile;

    public GuiMechanicalManaPool(ContainerMechanicalManaPool container) {
        super(container);
        this.tile = container.getTile();
        xSize = 176;
        ySize = 216;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.getTextureManager()
            .bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // 魔力条填充 (贴图里只有凹槽)
        final float fraction = (float) tile.getCurrentMana() / (float) TileMechanicalManaPool.MAX_MANA;
        final int fill = Math.max(0, (int) ((MANA_BAR_W - 2) * Math.min(1f, fraction)));
        if (fill > 0) {
            drawRect(
                guiLeft + MANA_BAR_X + 1,
                guiTop + MANA_BAR_Y + 1,
                guiLeft + MANA_BAR_X + 1 + fill,
                guiTop + MANA_BAR_Y + MANA_BAR_H - 1,
                MANA_BAR_COLOR);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj
            .drawString(StatCollector.translateToLocal("container.libertas.mechanicalManaPool"), 8, 5, 0x404040);

        // 魔力数字居中叠在条上 (0.75 倍字号; 缩放空间坐标 = 像素坐标 / 倍率)
        final String manaText = String.format("%,d / %,d", tile.getCurrentMana(), TileMechanicalManaPool.MAX_MANA);
        GL11.glPushMatrix();
        GL11.glScalef(MANA_TEXT_SCALE, MANA_TEXT_SCALE, MANA_TEXT_SCALE);
        fontRendererObj.drawString(
            manaText,
            Math.round((xSize - fontRendererObj.getStringWidth(manaText) * MANA_TEXT_SCALE) / (2 * MANA_TEXT_SCALE)),
            Math.round((MANA_BAR_Y + 1) / MANA_TEXT_SCALE),
            0x404040);
        GL11.glPopMatrix();

        // 升级槽左/右两侧: 当前配方模式 + 槽位名
        fontRendererObj.drawString(StatCollector.translateToLocal(tile.getModeTooltipKey()), 8, 29, 0x404040);
        final String upgrade = StatCollector.translateToLocal("libertas.gui.mechanicalManaPool.upgrade");
        fontRendererObj.drawString(upgrade, xSize - 8 - fontRendererObj.getStringWidth(upgrade), 29, 0x404040);

        fontRendererObj
            .drawString(StatCollector.translateToLocal("libertas.gui.mechanicalManaPool.input"), 8, 41, 0x404040);
        fontRendererObj
            .drawString(StatCollector.translateToLocal("libertas.gui.mechanicalManaPool.output"), 96, 41, 0x404040);

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 悬停升级槽时提示当前配方模式; 悬停箭头时提示可打开 NEI 配方页
        final int x = mouseX - guiLeft;
        final int y = mouseY - guiTop;
        if (x >= ContainerMechanicalManaPool.UPGRADE_X - 1 && x < ContainerMechanicalManaPool.UPGRADE_X + 17
            && y >= ContainerMechanicalManaPool.UPGRADE_Y - 1
            && y < ContainerMechanicalManaPool.UPGRADE_Y + 17) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal(tile.getModeTooltipKey())),
                mouseX,
                mouseY,
                fontRendererObj);
        } else if (isOverNeiArrow(x, y)) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal("libertas.gui.mechanicalManaPool.recipes")),
                mouseX,
                mouseY,
                fontRendererObj);
        }
    }

    /// 点击输入/输出区之间的箭头: 打开 NEI 的魔力池配方页 (仅客户端, NEI 在场时)
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isOverNeiArrow(mouseX - guiLeft, mouseY - guiTop) && Loader.isModLoaded("NotEnoughItems")) {
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
            GuiCraftingRecipe.openRecipeGui(NEI_MANA_POOL_PAGE);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static boolean isOverNeiArrow(int x, int y) {
        return x >= NEI_ARROW_X1 && x < NEI_ARROW_X2 && y >= NEI_ARROW_Y1 && y < NEI_ARROW_Y2;
    }
}
