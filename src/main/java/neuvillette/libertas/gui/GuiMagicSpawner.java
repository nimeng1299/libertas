package neuvillette.libertas.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.TileMagicSpawner;
import neuvillette.libertas.items.ItemCottonSwab;

/// 神奇的刷怪笼 GUI: 1 输入槽 (棉签) + 16 输出槽 (4x4), 顶部进度凹槽显示击杀计时 (已执行/总时间),
/// 输入槽下方显示签上记录与红石暂停状态。背景贴图由 miniElfPortal.png 改制。
@SideOnly(Side.CLIENT)
public class GuiMagicSpawner extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "libertas",
        "textures/gui/magicSpawner.png");

    /// 与背景贴图中的进度凹槽一致 (x 8..167, y 15..23), 内容填充再内缩 1px (微型精灵门魔力条同款)
    private static final int PROGRESS_BAR_X = 8;
    private static final int PROGRESS_BAR_Y = 15;
    private static final int PROGRESS_BAR_W = 160;
    private static final int PROGRESS_BAR_H = 9;
    private static final int PROGRESS_BAR_COLOR = 0xFFB040E0;

    /// 背景层画贴图/填充, 前景层画文字
    private final TileMagicSpawner tile;

    public GuiMagicSpawner(ContainerMagicSpawner container) {
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

        // 击杀计时: 凹槽内左往右填充 (客户端 TE 同步自由计数)
        final int fill = (int) ((PROGRESS_BAR_W - 2)
            * Math.min(1f, (float) tile.getKillTimer() / (float) TileMagicSpawner.KILL_INTERVAL_TICKS));
        if (fill > 0) {
            drawRect(
                guiLeft + PROGRESS_BAR_X + 1,
                guiTop + PROGRESS_BAR_Y + 1,
                guiLeft + PROGRESS_BAR_X + 1 + fill,
                guiTop + PROGRESS_BAR_Y + PROGRESS_BAR_H - 1,
                PROGRESS_BAR_COLOR);
        }
    }

    /// 计时文本的字号倍率与排版: 0.75 倍字号, 水平居中叠在进度凹槽上 (中心 x 88, y 16)
    private static final float TIMER_TEXT_SCALE = 0.75f;
    private static final int TIMER_TEXT_CENTER_X = PROGRESS_BAR_X + PROGRESS_BAR_W / 2;
    private static final int TIMER_TEXT_Y = PROGRESS_BAR_Y + 1;

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("container.libertas.magicSpawner"), 8, 5, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.magicSpawner.hint"), 8, 29, 0x404040);

        fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.shared.input"), 8, 41, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.shared.output"), 96, 41, 0x404040);

        // 输入槽下方: 签上记录的生物 (复用棉签 tooltip 文案; 长名裁到输入区宽度内)
        fontRendererObj.drawString(recordStatusText(), 8, 72, 0x404040);

        // 红石信号暂停提示
        if (tile.isPausedByRedstone()) {
            fontRendererObj.drawString(StatCollector.translateToLocal("libertas.gui.magicSpawner.paused"), 8, 84, 0);
        }

        // 击杀计时: 已执行时间/总时间 (秒), 0.75 倍字号 (缩放空间坐标 = 像素坐标 / 倍率)
        final String timerText = String
            .format("%.1fs / %.0fs", tile.getKillTimer() / 20.0f, TileMagicSpawner.KILL_INTERVAL_TICKS / 20.0f);
        GL11.glPushMatrix();
        GL11.glScalef(TIMER_TEXT_SCALE, TIMER_TEXT_SCALE, TIMER_TEXT_SCALE);
        fontRendererObj.drawString(
            timerText,
            Math.round(
                (TIMER_TEXT_CENTER_X - fontRendererObj.getStringWidth(timerText) * TIMER_TEXT_SCALE / 2f)
                    / TIMER_TEXT_SCALE),
            Math.round(TIMER_TEXT_Y / TIMER_TEXT_SCALE),
            0xFFFFFF,
            true);
        GL11.glPopMatrix();

        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }

    /// 签上记录状态的一行字
    private String recordStatusText() {
        final ItemStack swab = tile.getStackInSlot(TileMagicSpawner.SLOT_INPUT);
        final String id = ItemCottonSwab.getRecordedEntity(swab);
        final String text = id == null ? StatCollector.translateToLocal("tooltip.libertas.cottonSwab.empty")
            : StatCollector
                .translateToLocalFormatted("tooltip.libertas.cottonSwab.recorded", ItemCottonSwab.entityName(id));
        return fontRendererObj.trimStringToWidth(text, 72);
    }
}
