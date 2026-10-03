package neuvillette.libertas.machines;

import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.JsonBuilder;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.modularui2.GTGuiTheme;
import gregtech.api.modularui2.GTGuiThemes;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 未来科技风 GUI 主题（深海军蓝面板 + 青色荧光描边），是本 mod 所有多方块机器的默认外观。
 * GT 的 GTGuiTheme.registerThemes() 只在 GT preInit 消费一次，外部 mod 赶不上，
 * 因此按 GTGuiTheme.Builder 产出的相同 JSON 结构直接走 IThemeApi 注册——
 * 主题 JSON 是资源重载时惰性构建的，init 阶段注册仍会生效。
 *
 * <p>
 * 多方块控制器请继承 {@link MTELibertasMultiBlockBase} 自动获得本主题；
 * 特殊层级（如电力类基类）可在 getGui() 里调 {@link #createMultiblockGui(MTEMultiBlockBase)} 兜底。
 */
public final class ModGuiThemes {

    public static final String BACKGROUND_ID = "libertas:bg_multiblock";
    public static final String POPUP_ID = "libertas:bg_popup_advanced_crucible";
    public static final String TITLE_ID = "libertas:bg_title_advanced_crucible";
    public static final String THEME_ID = "libertas:machine";

    public static final String MELTER_BACKGROUND_ID = "libertas:bg_essentia_melter";
    public static final String MELTER_THEME_ID = "libertas:essentia_melter";

    // 多方块基础 GUI 面板尺寸是 198x203（见 MTEMultiBlockBaseGui.getBasePanelWidth/Height），
    // 背景贴图必须与之同尺寸，否则九宫格拉伸会变形
    public static final UITexture MULTIBLOCK_BACKGROUND = UITexture.builder()
        .location("libertas", "gui/background/advanced_crucible")
        .imageSize(198, 203)
        .adaptable(4)
        .canApplyTheme()
        .name(BACKGROUND_ID)
        .build();

    public static final UITexture POPUP = UITexture.builder()
        .location("libertas", "gui/background/advanced_crucible_popup")
        .imageSize(195, 136)
        .adaptable(4)
        .canApplyTheme()
        .name(POPUP_ID)
        .build();

    public static final UITexture TITLE = UITexture.builder()
        .location("libertas", "gui/tab/advanced_crucible_title")
        .imageSize(28, 28)
        .adaptable(4)
        .canApplyTheme()
        .name(TITLE_ID)
        .build();

    // 通过 builder 拿到 GTGuiTheme 实例（供 getGuiTheme() 返回）；实际注册在 init() 里手动完成
    public static final GTGuiTheme LIBERTAS_MACHINE = GTGuiTheme.builder(THEME_ID)
        .parent(GTGuiThemes.STANDARD)
        .panel(BACKGROUND_ID)
        .themedTexture(GTWidgetThemes.BACKGROUND_POPUP.getFullName(), POPUP_ID)
        .themedTexture(GTWidgetThemes.BACKGROUND_TITLE.getFullName(), TITLE_ID)
        .textColor(0xB8E9FF)
        .customTextColor(GTWidgetThemes.TEXT_TITLE.getFullName(), 0x7DF9FF)
        .build();

    public static final UITexture MELTER_BACKGROUND = UITexture.builder()
        .location("libertas", "gui/background/essentia_melter")
        .imageSize(176, 166)
        .adaptable(4)
        .canApplyTheme()
        .name(MELTER_BACKGROUND_ID)
        .build();

    /** 要素熔炼舱主题：复用 popup/title 贴图，仅面板背景不同（blockbench/essentiaMelterGui.bbmodel）。 */
    public static final GTGuiTheme ESSENTIA_MELTER = GTGuiTheme.builder(MELTER_THEME_ID)
        .parent(GTGuiThemes.STANDARD)
        .panel(MELTER_BACKGROUND_ID)
        .themedTexture(GTWidgetThemes.BACKGROUND_POPUP.getFullName(), POPUP_ID)
        .themedTexture(GTWidgetThemes.BACKGROUND_TITLE.getFullName(), TITLE_ID)
        .textColor(0xB8E9FF)
        .customTextColor(GTWidgetThemes.TEXT_TITLE.getFullName(), 0x7DF9FF)
        .build();

    private ModGuiThemes() {}

    public static void init() {
        IThemeApi.get()
            .registerTheme(THEME_ID, themeJson(BACKGROUND_ID));
        IThemeApi.get()
            .registerTheme(MELTER_THEME_ID, themeJson(MELTER_BACKGROUND_ID));
    }

    /**
     * 创建带 Libertas 背景兜底的多方块 GUI。主题面板背景在部分环境下不生效（主题 JSON 间接解析），
     * 这里在 GUI 构建时显式指定背景贴图。
     */
    public static <T extends MTEMultiBlockBase> MTEMultiBlockBaseGui<T> createMultiblockGui(T machine) {
        return new MTEMultiBlockBaseGui<T>(machine) {

            @Override
            protected ModularPanel getBasePanel(PosGuiData guiData, PanelSyncManager syncManager,
                UISettings uiSettings) {
                return super.getBasePanel(guiData, syncManager, uiSettings).background(MULTIBLOCK_BACKGROUND);
            }
        };
    }

    private static JsonBuilder themeJson(String backgroundId) {
        return new JsonBuilder().add("parent", "gregtech:standard")
            .add("panel", background(backgroundId))
            .add(GTWidgetThemes.BACKGROUND_POPUP.getFullName(), background(POPUP_ID))
            .add(GTWidgetThemes.BACKGROUND_TITLE.getFullName(), background(TITLE_ID))
            .add("textColor", 0xB8E9FF)
            .add(GTWidgetThemes.TEXT_TITLE.getFullName(), new JsonBuilder().add("textColor", 0x7DF9FF));
    }

    private static JsonBuilder background(String id) {
        return new JsonBuilder().add(
            "background",
            new JsonBuilder().add("type", "texture")
                .add("id", id));
    }
}
