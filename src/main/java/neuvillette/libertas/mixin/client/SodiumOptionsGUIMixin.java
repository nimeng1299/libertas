package neuvillette.libertas.mixin.client;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import neuvillette.libertas.client.gui.ClientConfigOptionPage;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI", remap = false)
public abstract class SodiumOptionsGUIMixin {

    @Shadow
    @Final
    protected List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void libertas$addConfigPage(GuiScreen previous, CallbackInfo ci) {
        pages.add(ClientConfigOptionPage.create());
    }
}
