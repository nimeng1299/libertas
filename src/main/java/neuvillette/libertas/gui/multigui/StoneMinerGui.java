package neuvillette.libertas.gui.multigui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import net.minecraft.util.StatCollector;
import neuvillette.libertas.multiblock.StoneMiner;

public class StoneMinerGui extends MTEMultiBlockBaseGui<StoneMiner> {

    private static final String HURT_SYNC_KEY = "stoneMinerHurt";

    private final StoneMiner stoneMiner;

    public StoneMinerGui(StoneMiner multiblock) {
        super(multiblock);
        this.stoneMiner = multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(HURT_SYNC_KEY, new LongSyncValue(stoneMiner::getHurtGui, stoneMiner::setHurtGui));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        LongSyncValue hurtSync = syncManager.findSyncHandler(HURT_SYNC_KEY, LongSyncValue.class);
        return super.createTerminalTextWidget(syncManager, parent)
            .child(IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted("Stone_Miner_Gui_Hurt", hurtSync.getLongValue()))
                .asWidget()
                .fullWidth()
            );
    }
}
