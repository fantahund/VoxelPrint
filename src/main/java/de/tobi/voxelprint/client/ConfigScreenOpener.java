package de.tobi.voxelprint.client;

import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.config.VoxelPrintConfigRegistration;
import de.voxelmap.voxelconfig.ConfigScreen;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds and shows VoxelConfig's settings screen.
 *
 * <p>Client-only: {@link ConfigScreen} extends {@code Screen} and would not even
 * load on a dedicated server.
 *
 * <p>A command cannot open the screen directly. Minecraft closes the chat screen
 * right after a command runs, which would immediately discard anything the
 * command put on screen. The request is therefore parked and carried out on the
 * next client tick, once the chat screen is gone.
 */
public final class ConfigScreenOpener {

    private final VoxelPrintConfig config;
    private final VoxelPrintConfigRegistration provider;

    private volatile boolean openRequested;

    public ConfigScreenOpener(VoxelPrintConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.provider = new VoxelPrintConfigRegistration(config);
    }

    /**
     * Creates the screen without showing it.
     *
     * <p>Used by the Mod Menu integration, which supplies the screen to return
     * to and displays the result itself.
     */
    public ConfigScreen createScreen(Screen parent) {
        return ConfigScreen.create(
                Component.translatable("voxelprint.config.title"),
                provider,
                parent,
                config::save);
    }

    /** Asks for the screen to be shown on the next client tick. */
    public void requestOpen() {
        openRequested = true;
    }

    /** Hook for {@code ClientTickEvents.END_CLIENT_TICK}. */
    public void onEndClientTick(Minecraft client) {
        if (!openRequested) {
            return;
        }
        openRequested = false;
        // No parent: closing the screen returns straight to the game, which is
        // what a player who typed a command expects.
        client.setScreenAndShow(createScreen(null));
    }
}
