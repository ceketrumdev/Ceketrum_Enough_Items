package com.ceketrum.cei.util;

import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

public class PlatformHelper {
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
