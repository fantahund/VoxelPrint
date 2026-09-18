package de.tobi.voxelprint.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.tobi.voxelprint.VoxelPrint;
import de.voxelmap.voxelconfig.ConfigScreen;

/**
 * Optional Mod Menu integration.
 *
 * <p>Mod Menu is a {@code recommends}, never a {@code depends}: it is compiled
 * against but not required. Fabric only loads an entry point when some mod asks
 * for that entry point name, so without Mod Menu installed this class is never
 * touched and its missing classes never matter.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        ConfigScreenFactory<ConfigScreen> factory =
                parent -> VoxelPrint.configScreens().createScreen(parent);
        return factory;
    }
}
