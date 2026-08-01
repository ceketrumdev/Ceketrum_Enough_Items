package com.ceketrum.cei.gui.util;

/**
 * Codes de touches, resolus a l'execution.
 *
 * Minecraft a change de backend d'entree EN COURS de serie 26.x :
 *
 *   26.1 / 26.2 -> GLFW   (glfw.dll)  : event.key(),         Escape=256, Backspace=259
 *   26.3+       -> SDL3   (SDL3.dll)  : event.shortcutKey(), Escape=27,  Backspace=8
 *
 * Les valeurs GLFW ne sont pas seulement fausses en 26.3, elles sont DANGEREUSES :
 * GLFW_KEY_R vaut 82, or 82 est le scancode SDL de la fleche HAUT. Le mod
 * repondait a la mauvaise touche au lieu de rester inerte.
 *
 * Valeurs relevees dans EditBox.keyPressed et InputWithModifiers des deux jars,
 * pas deduites : 26.2 teste event.key() avec des constantes GLFW, 26.3 teste
 * event.shortcutKey() avec des keycodes SDL.
 *
 * La detection s'appuie sur shortcutKey(), ajoute en 26.3 (cf. CeiInput).
 */
public final class CeiKeys {

    private CeiKeys() {}

    /** true a partir de 26.3 (SDL3), false en 26.1 / 26.2 (GLFW). */
    public static final boolean SDL = CeiInput.hasShortcutKey();

    // Lettres : ASCII minuscule en SDL, ASCII majuscule en GLFW
    public static final int R = SDL ? 114 : 82;
    public static final int U = SDL ? 117 : 85;

    public static final int ESCAPE    = SDL ? 27  : 256;
    public static final int BACKSPACE = SDL ? 8   : 259;
    public static final int DELETE    = SDL ? 127 : 261;

    public static final int LEFT  = SDL ? 0x40000050 : 263;
    public static final int RIGHT = SDL ? 0x4000004F : 262;
    public static final int HOME  = SDL ? 0x4000004A : 268;
    public static final int END   = SDL ? 0x4000004D : 269;
}
