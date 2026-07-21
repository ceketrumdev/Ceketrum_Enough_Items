package com.ceketrum.cei.gui.util;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import net.minecraft.client.gui.screen.Screen;
import java.util.WeakHashMap;

/**
 * Helper class to safely map screen instances to their respective CeiModule.
 * Uses a WeakHashMap to ensure no memory leaks occur when screen instances are garbage collected.
 */
public class CeiScreenHelper {
    private static final WeakHashMap<Screen, CeiModule> MODULES = new WeakHashMap<>();
    
    public static CeiModule getOrCreateModule(Screen screen) {
        return MODULES.computeIfAbsent(screen, s -> {
            CeiModule module = new CeiModule();
            module.init();
            return module;
        });
    }
}


