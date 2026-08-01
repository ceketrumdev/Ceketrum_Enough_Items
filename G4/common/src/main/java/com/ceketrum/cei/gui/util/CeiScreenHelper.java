package com.ceketrum.cei.gui.util;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;

/**
 * Helper class to safely map screen instances to their respective CeiModule.
 * Uses a WeakHashMap to ensure no memory leaks occur when screen instances are garbage collected.
 */
public class CeiScreenHelper {
    private static final WeakHashMap<Screen, CeiModule> MODULES = new WeakHashMap<>();
    
    public static CeiModule getOrCreateModule(Screen screen) {
        return MODULES.computeIfAbsent(screen, s -> {
            // Un module par instance d'ecran : c'est ce compteur qui dira si le
            // cache d'items est reconstruit a chaque ouverture de coffre.
            com.ceketrum.cei.diag.CeiDiagnostics.tick("CeiModule cree");
            CeiModule module = new CeiModule();
            module.init();
            return module;
        });
    }
}
