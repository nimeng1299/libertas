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
import neuvillette.libertas.blocks.TileMechanicalApothecary;

/// 机械花药台 GUI: 1 种子槽 + 16 配料 + 16 输出, 顶部一条魔力存量条
/// (每次配方消耗 1 小麦种子 + 500 魔力, 无需水)。
/// 背景贴图由 tools/gen_mechanical_apothecary_gui.py 生成。
/// 点击输入/输出区之间的箭头可打开 NEI 的花药台配方页 (botania.petalApothecary)。
@SideOnly(Side.CLIENT)
public class GuiMechanicalApothecary extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "libertas",
        "textures/gui/mechanicalApothecary.png");

    /// 与背景贴图中的魔力条凹槽一致: x 8..168, y 15..23 (内容填充再内缩 1px)
    private static final int MANA_BAR_X = 8;
    private static final int MANA_BAR_Y = 15;
    private static final int MANA_BAR_W = 160;
    private static final int MANA_BAR_H = 9;
    private static final int MANA_BAR_COLOR = 0xFF20B2E0;

    /// 魔力数字的字号倍率 (原版字号的 3/4; 缩放空间坐标 = 像素坐标 / 倍率)
    private static final float MANA_TEXT_SCALE = 0.75f;

    /// Botania RecipeHandlerPetalApothecary 的 overlay 标识 (其源码内硬编码, 无公开常量)
    private static final String NEI_PETAL_APOTHECARY_PAGE = "botania.petalApothecary";

    /// 背景层画贴图/填充, 前景层画文字
    private final TileMechanicalApothecary tile;

    public GuiMechanicalApothecary(ContainerMechanicalApothecary container) {
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
        final float fraction = (float) tile.getCurrentMana() / (float) TileMechanicalApothecary.MAX_MANA;
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
            .drawString(StatCollector.translateToLocal("container.libertas.mechanicalApothecary"), 8, 5, 0x404040);

        // 魔力数字居中叠在条上 (0.75 倍字号; 缩放空间坐标 = 像素坐标 / 倍率)
        final String manaText = String.format("%,d / %,d", tile.getCurrentMana(), TileMechanicalApothecary.MAX_MANA);
        GL11.glPushMatrix();
        GL11.glScalef(MANA_TEXT_SCALE, MANA_TEXT_SCALE, MANA_TEXT_SCALE);
        fontRendererObj.drawString(
            manaText,
            Math.round((xSize - fontRendererObj.getStringWidth(manaText) * MANA_TEXT_SCALE) / (2 * MANA_TEXT_SCALE)),
            Math.round((MANA_BAR_Y + 1) / MANA_TEXT_SCALE),
            0x404040);
        GL11.glPopMatrix();

        // 种子槽左/右两侧: 槽位名 + 消耗说明
        fontRendererObj
            .drawString(StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.seed"), 8, 29, 0x404040);
        final String usage = StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.seed.usage");
        fontRendererObj.drawString(usage, xSize - 8 - fontRendererObj.getStringWidth(usage), 29, 0x404040);

        fontRendererObj
            .drawString(StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.input"), 8, 41, 0x404040);
        fontRendererObj
            .drawString(StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.output"), 96, 41, 0x404040);

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 悬停种子槽时提示消耗规则; 悬停箭头时提示可打开 NEI 配方页
        final int x = mouseX - guiLeft;
        final int y = mouseY - guiTop;
        if (x >= ContainerMechanicalApothecary.SEED_X - 1 && x < ContainerMechanicalApothecary.SEED_X + 17
            && y >= ContainerMechanicalApothecary.SEED_Y - 1
            && y < ContainerMechanicalApothecary.SEED_Y + 17) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.seed.usage")),
                mouseX,
                mouseY,
                fontRendererObj);
        } else if (isOverNeiArrow(x, y)) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal("libertas.gui.mechanicalApothecary.recipes")),
                mouseX,
                mouseY,
                fontRendererObj);
        }
    }

    /// 点击输入/输出区之间的箭头: 打开 NEI 的花药台配方页 (仅客户端, NEI 在场时)
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isOverNeiArrow(mouseX - guiLeft, mouseY - guiTop) && Loader.isModLoaded("NotEnoughItems")) {
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
            GuiCraftingRecipe.openRecipeGui(NEI_PETAL_APOTHECARY_PAGE);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static boolean isOverNeiArrow(int x, int y) {
        return x >= ContainerMechanicalApothecary.ARROW_X1 && x < ContainerMechanicalApothecary.ARROW_X2
            && y >= ContainerMechanicalApothecary.ARROW_Y1
            && y < ContainerMechanicalApothecary.ARROW_Y2;
    }
}
