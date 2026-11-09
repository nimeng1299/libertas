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

/**
 * Adds the Libertas config entry as a tab in Angelica's video settings screen.
 * <p>
 * Angelica 2.2.28 and earlier could open Sodium's own {@code SodiumOptionsGUI}; 2.2.43 removed that screen (and
 * rewrote the video settings button to always open Reese's Sodium Options screen), which is why both targets are
 * listed here. {@code @Pseudo} lets whichever class is missing at runtime be skipped silently.
 */
@Pseudo
@Mixin(
    targets = { "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI",
        "me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen" },
    remap = false)
public abstract class VideoOptionsScreenMixin {

    @Shadow
    @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void libertas$addConfigPage(GuiScreen previous, CallbackInfo ci) {
        pages.add(ClientConfigOptionPage.create());
    }
}
