package com.ceketrum.cei.gui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Seule incompatibilite entre 26.1 et 26.2/26.3 pour CEI : la gestion de l'ecran courant.
 *
 *   26.1        -> Minecraft.setScreen(Screen)   et le champ public Minecraft.screen
 *   26.2 / 26.3 -> Minecraft.gui.setScreen(Screen)  et Minecraft.gui.screen()
 *
 * Tout le reste de l'API touchee par CEI est identique sur les trois versions :
 * GuiGraphicsExtractor, extractRenderState, extractContents, getHoveredSlot,
 * mouseClicked(MouseButtonEvent, boolean), AbstractRecipeBookScreen + widthTooNarrow,
 * pose() / nextStratum() / fill / text / item. Verifie jar par jar.
 *
 * On resout donc ces deux methodes UNE FOIS au chargement, et un seul module couvre
 * 26.1 -> 26.3. La reflexion est ici bornee a deux appels hors boucle de rendu --
 * a ne pas confondre avec l'ancien CeiGraphics, qui tentait de faire pareil sur des
 * types presents dans les signatures : ca, ca ne pouvait pas marcher.
 */
public final class CeiScreens {

    private static final MethodHandle SET;
    private static final MethodHandle GET;
    private static final boolean VIA_GUI;

    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        MethodHandle set = null, get = null;
        boolean viaGui = false;
        try {
            // 26.2+ : la gestion d'ecran vit sur Gui
            Class<?> guiClass = Class.forName("net.minecraft.client.gui.Gui");
            set = l.findVirtual(guiClass, "setScreen", MethodType.methodType(void.class, Screen.class));
            get = l.findVirtual(guiClass, "screen", MethodType.methodType(Screen.class));
            viaGui = true;
        } catch (Throwable ignored) {
            try {
                // 26.1 : elle vit encore sur Minecraft
                set = l.findVirtual(Minecraft.class, "setScreen", MethodType.methodType(void.class, Screen.class));
                get = l.findGetter(Minecraft.class, "screen", Screen.class);
            } catch (Throwable fatal) {
                throw new IllegalStateException(
                    "CEI: aucune API de gestion d'ecran reconnue (ni Gui.setScreen, ni Minecraft.setScreen)", fatal);
            }
        }
        SET = set; GET = get; VIA_GUI = viaGui;
    }

    private CeiScreens() {}

    private static Object target(Minecraft mc) {
        return VIA_GUI ? mc.gui : mc;
    }

    public static void set(Screen screen) {
        set(Minecraft.getInstance(), screen);
    }

    public static void set(Minecraft mc, Screen screen) {
        if (mc == null) return;
        try {
            SET.invoke(target(mc), screen);
        } catch (Throwable t) {
            throw new IllegalStateException("CEI: echec de setScreen", t);
        }
    }

    public static Screen current() {
        return current(Minecraft.getInstance());
    }

    public static Screen current(Minecraft mc) {
        if (mc == null) return null;
        try {
            return (Screen) GET.invoke(target(mc));
        } catch (Throwable t) {
            return null;
        }
    }
}
