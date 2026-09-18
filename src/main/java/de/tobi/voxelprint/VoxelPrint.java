package de.tobi.voxelprint;

import de.tobi.voxelprint.client.ConfigScreenOpener;
import de.tobi.voxelprint.command.VoxelPrintCommands;
import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.export.ExportFileManager;
import de.tobi.voxelprint.export.ExportService;
import de.tobi.voxelprint.selection.SelectionManager;
import de.tobi.voxelprint.selection.SelectionOutline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of VoxelPrint.
 *
 * <p>VoxelPrint is purely client side. Commands are registered in the client
 * dispatcher and never leave the machine, blocks are read from the client's own
 * copy of the world, and the selection outline is drawn with local particles.
 * Nothing is asked of the server, so the mod works on any server -- including
 * one running plain vanilla, and one where the player has no permissions.
 *
 * <p>The price of that is what the client knows: only chunks within render
 * distance exist, which is why an export refuses to run over unloaded ones
 * rather than quietly writing holes.
 *
 * <p>This class stays small on purpose. It holds the mod's identity and wires up
 * subsystems; all behaviour belongs in a dedicated service.
 */
public final class VoxelPrint implements ClientModInitializer {

    /** Mod id as declared in {@code fabric.mod.json}. */
    public static final String MOD_ID = "voxelprint";

    /** Human readable mod name, also used as the logger name. */
    public static final String MOD_NAME = "VoxelPrint";

    /** Shared logger. SLF4J is what Fabric Loader and VoxelConfig already use. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    /**
     * Static because Fabric instantiates every entry point itself, so the Mod
     * Menu integration has no other way to reach what was built here.
     */
    private static volatile ConfigScreenOpener configScreens;

    /** The settings screen. Available once the mod has initialised. */
    public static ConfigScreenOpener configScreens() {
        ConfigScreenOpener opener = configScreens;
        if (opener == null) {
            throw new IllegalStateException("VoxelPrint is not initialized yet");
        }
        return opener;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {} {}", MOD_NAME, version());

        VoxelPrintConfig config = new VoxelPrintConfig(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties"));
        config.load();

        ConfigScreenOpener opener = new ConfigScreenOpener(config);
        configScreens = opener;

        SelectionManager selections = new SelectionManager();
        ExportService exports = new ExportService(config,
                new ExportFileManager(FabricLoader.getInstance().getGameDir(), config));

        VoxelPrintCommands commands =
                new VoxelPrintCommands(selections, config, exports, opener::requestOpen);
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) -> commands.register(dispatcher));

        SelectionOutline outline = new SelectionOutline(selections, config);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            opener.onEndClientTick(client);
            outline.onEndClientTick(client);
        });

        // A selection belongs to the world it was made in, so it does not travel
        // to the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> selections.clear());
    }

    /** Returns the mod version declared in the metadata, or {@code "unknown"}. */
    public static String version() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
