package com.ceketrum.cei.gui.util;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;

/**
 * Associe chaque ecran a son CeiModule, et retient l'etat des modificateurs.
 *
 * L'ancienne implementation de hasShiftDown() appelait
 * InputConstants.isKeyDown(window, 340). Deux problemes : la surcharge a deux
 * arguments a disparu en 26.3 (NoSuchMethodError au premier Shift+clic), et
 * 340/341 sont des codes GLFW, faux des que le backend passe a SDL.
 *
 * On lit desormais l'etat sur les evenements d'entree que CEI intercepte deja :
 * InputWithModifiers.hasShiftDown() existe sur toute la plage 26.1 -> 26.3 et ne
 * depend d'aucune constante ni d'aucune API supprimee.
 */
public class CeiScreenHelper {
    private static final WeakHashMap<Screen, CeiModule> MODULES = new WeakHashMap<>();

    private static volatile boolean shiftDown = false;

    public static CeiModule getOrCreateModule(Screen screen) {
        return MODULES.computeIfAbsent(screen, s -> {
            CeiModule module = new CeiModule();
            module.init();
            return module;
        });
    }

    /** Alimente depuis les mixins d'entree, a chaque evenement clavier ou souris. */
    public static void setShiftDown(boolean down) {
        shiftDown = down;
    }

    public static boolean hasShiftDown() {
        return shiftDown;
    }
}
