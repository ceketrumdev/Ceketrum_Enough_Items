package com.ceketrum.cei.gui.util;

import net.minecraft.client.input.KeyEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Adaptateur d'entree pour la plage 26.1 -> 26.3.
 *
 * Deux ecarts, tous deux verifies jar par jar :
 *
 *   scancode : 26.1/26.2 -> KeyEvent.scancode()      26.3 -> KeyEvent.keycode()
 *   touche   : 26.1/26.2 -> KeyEvent.key()   (GLFW)  26.3 -> KeyEvent.shortcutKey() (SDL)
 *
 * Le module est compile contre 26.2, donc shortcutKey() et keycode() n'existent
 * pas a la compilation : tout passe par MethodHandle, resolu une fois au
 * chargement, hors boucle de rendu.
 */
public final class CeiInput {

    private static final MethodHandle SCANCODE;
    private static final MethodHandle SHORTCUT_KEY;
    private static final MethodHandle KEY;

    private static MethodHandle find(String... names) {
        MethodHandles.Lookup l = MethodHandles.lookup();
        for (String n : names) {
            try {
                return l.findVirtual(KeyEvent.class, n, MethodType.methodType(int.class));
            } catch (Throwable ignored) {
                // nom absent sur cette version
            }
        }
        return null;
    }

    static {
        SCANCODE     = find("scancode", "keycode");
        SHORTCUT_KEY = find("shortcutKey");
        KEY          = find("key");
    }

    private CeiInput() {}

    /** true si shortcutKey() existe, c'est-a-dire 26.3+ (backend SDL). */
    public static boolean hasShortcutKey() {
        return SHORTCUT_KEY != null;
    }

    private static int call(MethodHandle h, KeyEvent event) {
        if (h == null || event == null) return 0;
        try {
            return (int) h.invoke(event);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Le code de touche a comparer aux constantes de CeiKeys. */
    public static int key(KeyEvent event) {
        return SHORTCUT_KEY != null ? call(SHORTCUT_KEY, event) : call(KEY, event);
    }

    /** Le code materiel, quel que soit son nom selon la version. */
    public static int scancode(KeyEvent event) {
        return call(SCANCODE, event);
    }
}
