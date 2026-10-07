package neuvillette.libertas.gui;

import java.util.Arrays;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.nei.recipe.GuiCraftingRecipe;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.TileSanguineRite;
import neuvillette.libertas.network.ModNetwork;
import neuvillette.libertas.network.PacketSanguineRiteReset;

/// 猩红祭仪 GUI: 1 输入槽 + 1 输出槽 + 重置按钮, 下方两行状态文字
/// (后方祭坛有无与等级 / 注入进度)。
/// 点击重置按钮发送 {@link PacketSanguineRiteReset}, 服务端忘记当前跟踪的配方
/// (已在祭坛里的物品须自行取回)。点击输入/输出区之间的箭头可打开 NEI 的
/// 血之祭坛配方页 (alchemicalwizardry.altar)。
@SideOnly(Side.CLIENT)
public class GuiSanguineRite extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "libertas",
        "textures/gui/sanguineRite.png");

    /// BloodMagic NEIAltarRecipeHandler 的配方页标识 (其源码内硬编码, 无公开常量)
    private static final String NEI_ALTAR_PAGE = "alchemicalwizardry.altar";

    private static final int BUTTON_RESET_ID = 0;
    private static final int BUTTON_X = 116;
    private static final int BUTTON_Y = 50;
    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 18;

    private final TileSanguineRite tile;

    public GuiSanguineRite(ContainerSanguineRite container) {
        super(container);
        this.tile = container.getTile();
        xSize = 176;
        ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(
            new GuiButton(
                BUTTON_RESET_ID,
                guiLeft + BUTTON_X,
                guiTop + BUTTON_Y,
                BUTTON_W,
                BUTTON_H,
                StatCollector.translateToLocal("libertas.gui.sanguineRite.reset")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_RESET_ID) {
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
            ModNetwork.INSTANCE.sendToServer(new PacketSanguineRiteReset(tile.xCoord, tile.yCoord, tile.zCoord));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        mc.getTextureManager()
            .bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("container.libertas.sanguineRite"), 8, 5, 0x404040);

        fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.shared.input"), 44, 20, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.shared.output"), 116, 20, 0x404040);

        // 状态两行: 祭坛有无与等级 / 工作状态
        fontRendererObj.drawString(altarLine(), 8, 54, 0x404040);
        fontRendererObj.drawString(stateLine(), 8, 64, 0x404040);

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }

    private String altarLine() {
        if (!tile.altarPresent) return StatCollector.translateToLocal("libertas.gui.sanguineRite.noAltar");
        return StatCollector.translateToLocalFormatted("libertas.gui.sanguineRite.altar", tile.altarTier);
    }

    private String stateLine() {
        if (!tile.altarPresent) return "";
        if (tile.tracking) {
            if (tile.resultWaiting) return StatCollector.translateToLocal("libertas.gui.sanguineRite.blocked");
            final int percent = tile.liquidRequiredClient > 0
                ? Math.min(100, tile.progress * 100 / tile.liquidRequiredClient)
                : 0;
            return StatCollector.translateToLocalFormatted("libertas.gui.sanguineRite.working", percent);
        }
        return StatCollector.translateToLocal("libertas.gui.sanguineRite.idle");
    }

    /// 悬停重置按钮/箭头时提示作用
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        final int x = mouseX - guiLeft;
        final int y = mouseY - guiTop;
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_W && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_H) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal("libertas.gui.sanguineRite.reset.usage")),
                mouseX,
                mouseY,
                fontRendererObj);
        } else if (isOverNeiArrow(x, y)) {
            drawHoveringText(
                Arrays.asList(StatCollector.translateToLocal("libertas.gui.sanguineRite.recipes")),
                mouseX,
                mouseY,
                fontRendererObj);
        }
    }

    /// 点击输入/输出区之间的箭头: 打开 NEI 的血之祭坛配方页 (仅客户端, NEI 在场时)
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isOverNeiArrow(mouseX - guiLeft, mouseY - guiTop) && Loader.isModLoaded("NotEnoughItems")) {
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
            GuiCraftingRecipe.openRecipeGui(NEI_ALTAR_PAGE);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static boolean isOverNeiArrow(int x, int y) {
        return x >= ContainerSanguineRite.ARROW_X1 && x < ContainerSanguineRite.ARROW_X2
            && y >= ContainerSanguineRite.ARROW_Y1
            && y < ContainerSanguineRite.ARROW_Y2;
    }
}
