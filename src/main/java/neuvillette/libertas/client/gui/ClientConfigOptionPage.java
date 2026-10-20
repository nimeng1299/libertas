package neuvillette.libertas.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import com.google.common.collect.ImmutableList;

import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.SubScreenOption;

public final class ClientConfigOptionPage {

    private ClientConfigOptionPage() {}

    public static OptionPage create() {
        return new OptionPage(
            "Libertas",
            ImmutableList.of(
                OptionGroup.createBuilder()
                    .add(
                        new SubScreenOption(
                            I18n.format("libertas.config.title"),
                            I18n.format("libertas.config.tooltip"),
                            GuiClientConfig::new))
                    .add(
                        new SubScreenOption(
                            I18n.format("libertas.config.server.title"),
                            I18n.format("libertas.config.server.tooltip"),
                            parent -> new GuiClientConfig(parent, true)),
                        Minecraft.getMinecraft().theWorld == null)
                    .build()));
    }
}
