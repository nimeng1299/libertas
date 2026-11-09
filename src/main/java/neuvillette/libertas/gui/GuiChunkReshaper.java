package neuvillette.libertas.gui;

import java.util.Locale;
import java.util.function.IntSupplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import neuvillette.libertas.blocks.TileChunkReshaper;
import neuvillette.libertas.blocks.TileChunkReshaper.Mode;

/**
 * 区块重塑机的 MUI2 面板：状态行 + 数据屏（模式 / 进度 / 已处理数量与当前层）+ 进度条 + 两个作业按钮。
 *
 * <p>
 * 面板背景、按钮与文字样式全部来自 GT 标准主题（见 {@link TileChunkReshaper#createScreen}），这里只排布局。
 * 本类会在服务端一起执行（MUI2 的 buildUI 双端都会调用），因此不能引用任何仅客户端的类；
 * 所有数字都从 {@link IntSyncValue} 的客户端缓存读取，不依赖客户端 TE 实例。
 */
public final class GuiChunkReshaper {

    public static final int WIDTH = 200;
    public static final int HEIGHT = 122;
    public static final String PANEL_NAME = "libertas:chunk_reshaper";

    /** 面板左右各留 9px。 */
    private static final int CONTENT_WIDTH = WIDTH - 18;
    /** 数据屏内部留白。 */
    private static final int SCREEN_PADDING = 6;
    /** 数据屏每行的高度与行距。 */
    private static final int LINE_HEIGHT = 10;
    private static final int LINE_GAP = 2;

    private static final int SCREEN_X = 9;
    private static final int SCREEN_Y = 32;
    private static final int SCREEN_HEIGHT = 44;
    private static final int PROGRESS_Y = 80;
    private static final int PROGRESS_HEIGHT = 8;
    private static final int BUTTON_Y = 94;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_HEIGHT = 20;

    /** 进度条贴图：上半 8px 空槽、下半 8px 满条（{@code blockbench/generate_chunk_reshaper.py} 生成）。 */
    public static final UITexture PROGRESS_BAR = UITexture.builder()
        .location("libertas", "gui/chunk_reshaper_progress")
        .imageSize(CONTENT_WIDTH, 16)
        .name("libertas:chunk_reshaper_progress")
        .build();

    private GuiChunkReshaper() {}

    public static ModularPanel build(TileChunkReshaper tile, PanelSyncManager syncManager) {
        final IntSyncValue active = sync(syncManager, "active", tile::getActiveModeOrdinal);
        final IntSyncValue selected = sync(syncManager, "selected", tile::getSelectedModeOrdinal);
        final IntSyncValue cursor = sync(syncManager, "cursor", tile::getCursor);
        final IntSyncValue total = sync(syncManager, "total", tile::getTotal);
        final IntSyncValue changed = sync(syncManager, "changed", tile::getChanged);
        final IntSyncValue finished = sync(syncManager, "finished", tile::getFinishedFlag);
        final IntSyncValue currentY = sync(syncManager, "currentY", tile::getCurrentY);

        final ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, WIDTH, HEIGHT);

        panel.child(
            IKey.lang("container.libertas.chunkReshaper")
                .asWidget()
                .widgetTheme(GTWidgetThemes.TEXT_TITLE)
                .pos(9, 7)
                .size(CONTENT_WIDTH, LINE_HEIGHT));

        // 状态行：运行中 / 已暂停 / 已完成 / 待机
        panel.child(
            IKey.dynamic(() -> stateText(active, selected, cursor, total, finished))
                .asWidget()
                .pos(9, 20)
                .size(CONTENT_WIDTH, LINE_HEIGHT));

        panel.child(createDataScreen(selected, cursor, total, changed, currentY));

        // 进度条：读客户端同步缓存，随数据包刷新
        panel.child(
            new ProgressWidget()
                .value(
                    new DoubleValue.Dynamic(
                        () -> total.getIntValue() <= 0 ? 0d
                            : (double) cursor.getIntValue() / (double) total.getIntValue(),
                        null))
                .texture(PROGRESS_BAR, CONTENT_WIDTH)
                .pos(SCREEN_X, PROGRESS_Y)
                .size(CONTENT_WIDTH, PROGRESS_HEIGHT));

        panel.child(createActionButton("excavate", tile, Mode.EXCAVATE, syncManager, 9));
        panel.child(createActionButton("pave", tile, Mode.PAVE, syncManager, 105));

        panel.child(ButtonWidget.panelCloseButton());
        return panel;
    }

    /** 黑底数据屏：模式、进度、已处理数量与当前层。 */
    private static ParentWidget<?> createDataScreen(IntSyncValue selected, IntSyncValue cursor, IntSyncValue total,
        IntSyncValue changed, IntSyncValue currentY) {
        final ParentWidget<?> screen = new ParentWidget<>().pos(SCREEN_X, SCREEN_Y)
            .size(CONTENT_WIDTH, SCREEN_HEIGHT)
            .background(GTGuiTextures.PICTURE_SCREEN_BLACK);

        screen.child(line(IKey.dynamic(() -> modeLine(selected)), 0));
        screen.child(line(IKey.dynamic(() -> progressLine(cursor, total)), (LINE_HEIGHT + LINE_GAP)));
        screen.child(line(IKey.dynamic(() -> changedLine(selected, changed, currentY)), (LINE_HEIGHT + LINE_GAP) * 2));
        return screen;
    }

    private static TextWidget<?> line(IKey text, int y) {
        return text.asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE)
            .pos(SCREEN_PADDING, SCREEN_PADDING + y)
            .size(CONTENT_WIDTH - SCREEN_PADDING * 2, LINE_HEIGHT);
    }

    /** 作业按钮：点击经 MUI2 的 InteractionSyncHandler 发到服务端，由 TE 决定开始 / 暂停 / 继续。 */
    private static ButtonWidget<?> createActionButton(String id, TileChunkReshaper tile, Mode mode,
        PanelSyncManager syncManager, int x) {
        final String syncKey = "press_" + id;
        final InteractionSyncHandler handler = new InteractionSyncHandler();
        handler.setOnMousePressed(data -> {
            // 客户端点击时 handler 会先本地执行一次，这里只处理服务端转发过来的那次
            if (data.isClient() || data.mouseButton != 0) return;
            tile.press(mode);
        });
        syncManager.syncValue(syncKey, handler);

        return new ButtonWidget<>().syncHandler(syncKey)
            .pos(x, BUTTON_Y)
            .size(BUTTON_WIDTH, BUTTON_HEIGHT)
            .overlay(IKey.lang("libertas.chunkReshaper.button." + id))
            .tooltipDynamic(
                tooltip -> tooltip.add(StatCollector.translateToLocal("libertas.chunkReshaper.button." + id + ".tip")));
    }

    private static IntSyncValue sync(PanelSyncManager syncManager, String name, IntSupplier getter) {
        final IntSyncValue value = new IntSyncValue(getter);
        syncManager.syncValue(name, value);
        return value;
    }

    // -----------------------------------------------------------------------
    // 文本组装：双端共用，只做字符串拼接
    // -----------------------------------------------------------------------

    private static String stateText(IntSyncValue active, IntSyncValue selected, IntSyncValue cursor, IntSyncValue total,
        IntSyncValue finished) {
        final int activeMode = active.getIntValue();
        final int selectedMode = selected.getIntValue();
        if (activeMode != 0) {
            return EnumChatFormatting.DARK_GREEN + StatCollector
                .translateToLocalFormatted("libertas.chunkReshaper.state.running", modeName(selectedMode));
        }
        if (selectedMode != 0 && finished.getIntValue() != 0) {
            return EnumChatFormatting.DARK_AQUA
                + StatCollector.translateToLocalFormatted("libertas.chunkReshaper.state.done", modeName(selectedMode));
        }
        if (selectedMode != 0 && cursor.getIntValue() > 0 && cursor.getIntValue() < total.getIntValue()) {
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("libertas.chunkReshaper.state.paused");
        }
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("libertas.chunkReshaper.state.idle");
    }

    private static String modeLine(IntSyncValue selected) {
        return StatCollector
            .translateToLocalFormatted("libertas.chunkReshaper.info.mode", modeName(selected.getIntValue()));
    }

    private static String progressLine(IntSyncValue cursor, IntSyncValue total) {
        final int done = cursor.getIntValue();
        final int all = total.getIntValue();
        if (all <= 0) {
            return StatCollector.translateToLocalFormatted("libertas.chunkReshaper.info.progress.empty");
        }
        final double percent = done * 100d / all;
        return StatCollector.translateToLocalFormatted(
            "libertas.chunkReshaper.info.progress",
            formatNumber(done),
            formatNumber(all),
            String.format(Locale.ROOT, "%.1f", percent));
    }

    private static String changedLine(IntSyncValue selected, IntSyncValue changed, IntSyncValue currentY) {
        final int selectedMode = selected.getIntValue();
        if (selectedMode == Mode.EXCAVATE.ordinal()) {
            return StatCollector.translateToLocalFormatted(
                "libertas.chunkReshaper.info.excavated",
                formatNumber(changed.getIntValue()),
                currentY.getIntValue());
        }
        if (selectedMode == Mode.PAVE.ordinal()) {
            return StatCollector.translateToLocalFormatted(
                "libertas.chunkReshaper.info.paved",
                formatNumber(changed.getIntValue()),
                currentY.getIntValue());
        }
        return StatCollector.translateToLocal("libertas.chunkReshaper.info.idle");
    }

    private static String modeName(int ordinal) {
        if (ordinal == Mode.EXCAVATE.ordinal()) {
            return StatCollector.translateToLocal("libertas.chunkReshaper.mode.excavate");
        }
        if (ordinal == Mode.PAVE.ordinal()) {
            return StatCollector.translateToLocal("libertas.chunkReshaper.mode.pave");
        }
        return StatCollector.translateToLocal("libertas.chunkReshaper.mode.none");
    }

    private static String formatNumber(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
