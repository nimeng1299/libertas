package neuvillette.libertas.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.TileIndustrialAgglomerationPlate;

/// 工业凝聚板 GUI: 16 输入 + 16 输出, 顶部一条魔力存量条 (每次配方固定 500000 魔力)。
/// 背景贴图由 tools/gen_industrial_agglomeration_plate_gui.py 生成。
/// 1.7.10 的 Botania 没有凝聚板配方 NEI 页, 故没有 NEI 跳转箭头。
@SideOnly(Side.CLIENT)
public class GuiIndustrialAgglomerationPlate extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "libertas",
        "textures/gui/industrialAgglomerationPlate.png");

    /// 与背景贴图中的魔力条凹槽一致: x 8..168, y 15..23 (内容填充再内缩 1px)
    private static final int MANA_BAR_X = 8;
    private static final int MANA_BAR_Y = 15;
    private static final int MANA_BAR_W = 160;
    private static final int MANA_BAR_H = 9;
    private static final int MANA_BAR_COLOR = 0xFF20B2E0;

    /// 魔力数字的字号倍率 (原版字号的 3/4; 缩放空间坐标 = 像素坐标 / 倍率)
    private static final float MANA_TEXT_SCALE = 0.75f;

    /// 背景层画贴图/填充, 前景层画文字
    private final TileIndustrialAgglomerationPlate tile;

    public GuiIndustrialAgglomerationPlate(ContainerIndustrialAgglomerationPlate container) {
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
        final float fraction = (float) tile.getCurrentMana() / (float) TileIndustrialAgglomerationPlate.MAX_MANA;
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
        fontRendererObj.drawString(
            StatCollector.translateToLocal("container.libertas.industrialAgglomerationPlate"),
            8,
            5,
            0x404040);

        // 魔力数字居中叠在条上 (0.75 倍字号; 缩放空间坐标 = 像素坐标 / 倍率)
        final String manaText = String
            .format("%,d / %,d", tile.getCurrentMana(), TileIndustrialAgglomerationPlate.MAX_MANA);
        GL11.glPushMatrix();
        GL11.glScalef(MANA_TEXT_SCALE, MANA_TEXT_SCALE, MANA_TEXT_SCALE);
        fontRendererObj.drawString(
            manaText,
            Math.round((xSize - fontRendererObj.getStringWidth(manaText) * MANA_TEXT_SCALE) / (2 * MANA_TEXT_SCALE)),
            Math.round((MANA_BAR_Y + 1) / MANA_TEXT_SCALE),
            0x404040);
        GL11.glPopMatrix();

        // 每次配方固定消耗 500000 魔力的提示
        fontRendererObj.drawString(
            StatCollector.translateToLocal("libertas.gui.industrialAgglomerationPlate.mana"),
            8,
            29,
            0x404040);

        fontRendererObj.drawString(
            StatCollector.translateToLocal("libertas.gui.industrialAgglomerationPlate.input"),
            8,
            41,
            0x404040);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("libertas.gui.industrialAgglomerationPlate.output"),
            96,
            41,
            0x404040);

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }
}
