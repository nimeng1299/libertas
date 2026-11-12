package neuvillette.libertas.util;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * Libertas 通用 tooltip 样式工具：彩虹斜体文字 + 统一署名行。
 */
public final class LibertasTooltips {

    /** 彩虹循环色序：红 → 金 → 黄 → 绿 → 青 → 蓝 → 紫。 */
    private static final EnumChatFormatting[] RAINBOW = { EnumChatFormatting.RED, EnumChatFormatting.GOLD,
        EnumChatFormatting.YELLOW, EnumChatFormatting.GREEN, EnumChatFormatting.AQUA, EnumChatFormatting.BLUE,
        EnumChatFormatting.LIGHT_PURPLE };

    /** 署名行语言键："§7由 %s 添加" / "§7Added by %s"。 */
    public static final String CREDIT_KEY = "tooltip.libertas.credit";

    private LibertasTooltips() {}

    /**
     * 把整段文字逐字符染成彩虹色，并保持斜体。
     *
     * <p>
     * 颜色码{@code §x}会清空此前的所有格式，所以每个字符前都要重新写一次颜色码和{@link EnumChatFormatting#ITALIC}，
     * 否则只有第一个字是斜体。
     *
     * @param aText 原始文字（不应含格式码）
     * @return 带颜色与斜体格式码的字符串
     */
    public static String rainbowItalic(String aText) {
        if (aText == null || aText.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(aText.length() * 4);
        for (int i = 0; i < aText.length(); i++) {
            builder.append(RAINBOW[i % RAINBOW.length])
                .append(EnumChatFormatting.ITALIC)
                .append(aText.charAt(i));
        }
        return builder.toString();
    }

    /**
     * 统一的署名行："由 <i>Libertas</i> 添加"，中间的 Libertas 为彩虹斜体。
     *
     * @return 已完成国际化与着色的一整行字符串
     */
    public static String credit() {
        return String.format(StatCollector.translateToLocal(CREDIT_KEY), rainbowItalic("Libertas"));
    }

    /**
     * 便捷入口：直接把署名行追加到 tooltip 列表末尾。
     *
     * @param aTooltip 物品 tooltip 行列表
     */
    public static void addCredit(List<String> aTooltip) {
        if (aTooltip == null) return;
        aTooltip.add(credit());
    }
}
