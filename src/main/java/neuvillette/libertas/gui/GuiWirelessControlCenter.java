package neuvillette.libertas.gui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.blocks.TileWirelessControlCenter;
import neuvillette.libertas.wireless.WirelessPowerProfile;

@SideOnly(Side.CLIENT)
public class GuiWirelessControlCenter extends GuiContainer {

    private final TileWirelessControlCenter center;

    public GuiWirelessControlCenter(ContainerWirelessControlCenter container) {
        super(container);
        center = container.getCenter();
        xSize = 244;
        ySize = 240;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF101C2D);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + xSize - 1, guiTop + 23, 0xFF173A48);
        drawRect(guiLeft + 10, guiTop + 68, guiLeft + xSize - 10, guiTop + 88, 0xFF07121F);
        drawRect(guiLeft + 10, guiTop + 92, guiLeft + xSize - 10, guiTop + 128, 0xFF17283B);
        drawSlot(ContainerWirelessControlCenter.SLOT_X, ContainerWirelessControlCenter.SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(
                    ContainerWirelessControlCenter.INVENTORY_X + col * 18,
                    ContainerWirelessControlCenter.INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(ContainerWirelessControlCenter.INVENTORY_X + col * 18, ContainerWirelessControlCenter.HOTBAR_Y);
        }
    }

    private void drawSlot(int x, int y) {
        drawRect(guiLeft + x - 1, guiTop + y - 1, guiLeft + x + 17, guiTop + y + 17, 0xFF477280);
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 16, guiTop + y + 16, 0xFF07121F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        text("container.libertas.wirelessControlCenter", 12, 8, 0xB8FFF3);
        WirelessPowerProfile profile = center.getProfile();
        text(
            profile.isEnabled() ? "libertas.wireless.online" : "libertas.wireless.paused",
            12,
            29,
            profile.isEnabled() ? 0x79F0C1 : 0xFFCC80);
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(
                StatCollector.translateToLocalFormatted("libertas.wireless.owner", center.getOwnerName()),
                218),
            12,
            44,
            0xCAD9E5);
        text("libertas.wireless.balance", 12, 58, 0x8AAABD);
        BigInteger balance = center.getNetworkBalance();
        String exact = balance.toString() + " EU";
        String amount = fontRendererObj.getStringWidth(exact) <= 214 ? exact
            : new BigDecimal(balance).round(new MathContext(8))
                .toEngineeringString() + " EU";
        fontRendererObj.drawString(amount, 14, 74, 0x8FFFEA);
        fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("libertas.wireless.voltage", profile.voltage),
            48,
            99,
            0xD7EAF5);
        fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("libertas.wireless.amperage", profile.amperage),
            48,
            113,
            0xD7EAF5);
        ItemStack template = center.getStackInSlot(0);
        fontRendererObj.drawString(
            StatCollector
                .translateToLocalFormatted("libertas.wireless.template", template == null ? 0 : template.stackSize),
            12,
            132,
            0x8AAABD);
        text("container.inventory", ContainerWirelessControlCenter.INVENTORY_X, 143, 0x8AAABD);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mouseX >= guiLeft + 10 && mouseX < guiLeft + xSize - 10 && mouseY >= guiTop + 68 && mouseY < guiTop + 88) {
            List<String> lines = new ArrayList<>();
            lines.add(StatCollector.translateToLocal("libertas.wireless.balance"));
            String value = center.getNetworkBalance()
                .toString();
            for (int i = 0; i < value.length(); i += 36)
                lines.add(value.substring(i, Math.min(i + 36, value.length())));
            lines.add("EU");
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        }
    }

    private void text(String key, int x, int y, int color) {
        fontRendererObj.drawString(StatCollector.translateToLocal(key), x, y, color);
    }
}
