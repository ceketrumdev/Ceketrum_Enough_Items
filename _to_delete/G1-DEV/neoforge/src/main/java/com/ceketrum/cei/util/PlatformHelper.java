package com.ceketrum.cei.util;

import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

/**
 * En 1.20.1, NeoForge 47.x est encore un fork direct de Forge et conserve les
 * packages net.minecraftforge.* -- FMLPaths s'y trouve donc dans
 * net.minecraftforge.fml.loading, et non net.neoforged.fml.loading comme a
 * partir de 1.20.2.
 *
 * Verifie dans fancymodloader loader-47.2.2.jar.
 */
public class PlatformHelper {
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
