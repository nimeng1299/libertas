package neuvillette.libertas.gui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.function.Supplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import neuvillette.libertas.blocks.TileWirelessControlCenter;
import neuvillette.libertas.wireless.WirelessPowerProfile;

/**
 * 无线电网控制中心的 MUI2 面板。
 *
 * <p>
 * 面板背景、文字配色、槽位外观全部来自 MUI2 主题（{@link gregtech.api.modularui2.GTGuiThemes#STANDARD}，
 * 见 {@link TileWirelessControlCenter#createScreen}），不再手绘色块；这里只负责布局，
 * 版式按 244x240 排布，玩家背包由 {@code bindPlayerInventory()} 贴在底部。
 *
 * <p>
 * 本类同时会在服务端执行（MUI2 的 buildUI 双端都会调用），因此不能引用任何仅客户端的类。
 */
public final class GuiWirelessControlCenter {

    public static final int WIDTH = 244;
    public static final int HEIGHT = 240;
    public static final String PANEL_NAME = "libertas:wireless_control_center";

    /** 内容区宽度（面板左右各留 9px）。 */
    private static final int CONTENT_WIDTH = WIDTH - 18;
    /** 电量屏内部留白。 */
    private static final int SCREEN_PADDING = 6;
    /** 电量文本竖直居中偏移（屏高 24、文字高 10）。 */
    private static final int SCREEN_TEXT_Y = 7;
    /** 电量文本超出这个长度就改用科学计数法，避免溢出屏幕。 */
    private static final int MAX_BALANCE_CHARS = 30;
    /** 悬停提示里每行最多这么多个字符。 */
    private static final int TOOLTIP_LINE_CHARS = 40;

    // 槽位组：单个规格模板槽，允许与玩家背包互相 shift 点击
    public static final String SLOT_GROUP = "item_inv";

    private GuiWirelessControlCenter() {}

    public static ModularPanel build(TileWirelessControlCenter center, PanelSyncManager syncManager) {
        final StringSyncValue status = sync(syncManager, "status", center::statusText);
        final StringSyncValue owner = sync(syncManager, "owner", center::ownerText);
        final StringSyncValue balance = sync(syncManager, "balance", center::balanceText);
        final StringSyncValue balanceFull = sync(syncManager, "balanceFull", center::balanceFullText);
        final StringSyncValue voltage = sync(syncManager, "voltage", center::voltageText);
        final StringSyncValue amperage = sync(syncManager, "amperage", center::amperageText);
        final StringSyncValue template = sync(syncManager, "template", center::templateText);

        syncManager.registerSlotGroup(SLOT_GROUP, 0);

        final ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, WIDTH, HEIGHT)
            .bindPlayerInventory();

        panel.child(
            IKey.lang("container.libertas.wirelessControlCenter")
                .asWidget()
                .widgetTheme(GTWidgetThemes.TEXT_TITLE)
                .pos(9, 7)
                .size(CONTENT_WIDTH, 10));
        panel.child(
            IKey.dynamic(status::getValue)
                .asWidget()
                .pos(9, 21)
                .size(CONTENT_WIDTH, 10));
        panel.child(
            IKey.dynamic(owner::getValue)
                .asWidget()
                .pos(9, 34)
                .size(CONTENT_WIDTH, 10));
        panel.child(
            IKey.lang("libertas.wireless.balance")
                .asWidget()
                .pos(9, 49)
                .size(CONTENT_WIDTH, 10));
        panel.child(createBalanceScreen(balance, balanceFull));
        panel.child(
            IKey.dynamic(voltage::getValue)
                .asWidget()
                .pos(9, 90)
                .size(CONTENT_WIDTH, 10));
        panel.child(
            IKey.dynamic(amperage::getValue)
                .asWidget()
                .pos(9, 103)
                .size(CONTENT_WIDTH, 10));
        panel.child(createTemplateSlot(center));
        panel.child(
            IKey.dynamic(template::getValue)
                .asWidget()
                .pos(30, 124)
                .size(CONTENT_WIDTH - 21, 10));

        return panel;
    }

    /** 黑底显示屏样式的电量区，悬停可看完整数值。 */
    private static ParentWidget<?> createBalanceScreen(StringSyncValue balance, StringSyncValue balanceFull) {
        return new ParentWidget<>().size(CONTENT_WIDTH, 24)
            .pos(9, 60)
            .background(GTGuiTextures.PICTURE_SCREEN_BLACK)
            .child(
                IKey.dynamic(balance::getValue)
                    .asWidget()
                    .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE)
                    .pos(SCREEN_PADDING, SCREEN_TEXT_Y)
                    .size(CONTENT_WIDTH - SCREEN_PADDING * 2, 10))
            .tooltipDynamic(tooltip -> {
                tooltip.add(StatCollector.translateToLocal("libertas.wireless.balance"));
                final String full = balanceFull.getValue();
                for (int i = 0; i < full.length(); i += TOOLTIP_LINE_CHARS) {
                    tooltip.add(full.substring(i, Math.min(i + TOOLTIP_LINE_CHARS, full.length())))
                        .newLine();
                }
                tooltip.add("EU");
            });
    }

    /** 规格模板槽：框体与输入箭头取自主题槽位贴图。 */
    private static ItemSlot createTemplateSlot(TileWirelessControlCenter center) {
        return new ItemSlot().slot(new ModularSlot(new InvWrapper(center), 0).slotGroup(SLOT_GROUP))
            .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD)
            .pos(9, 120);
    }

    private static StringSyncValue sync(PanelSyncManager syncManager, String name, Supplier<String> getter) {
        final StringSyncValue value = new StringSyncValue(getter);
        syncManager.syncValue(name, value);
        return value;
    }

    // -----------------------------------------------------------------------
    // 文本组装：双端共用，只用字符数做截断，不依赖客户端字体
    // -----------------------------------------------------------------------

    public static String statusText(WirelessPowerProfile profile) {
        return (profile.isEnabled() ? EnumChatFormatting.DARK_GREEN : EnumChatFormatting.GOLD) + StatCollector
            .translateToLocal(profile.isEnabled() ? "libertas.wireless.online" : "libertas.wireless.paused");
    }

    public static String balanceText(BigInteger balance) {
        final String exact = balance.toString() + " EU";
        if (exact.length() <= MAX_BALANCE_CHARS) return exact;
        return new BigDecimal(balance).round(new MathContext(8))
            .toEngineeringString() + " EU";
    }
}
