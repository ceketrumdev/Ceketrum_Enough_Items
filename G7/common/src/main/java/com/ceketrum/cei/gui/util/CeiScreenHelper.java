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
            CeiModule module = new CeiModule();
            module.init();
            return module;
        });
    }
    
    public static Screen getCurrentScreen(net.minecraft.client.Minecraft mc) {
        if (mc == null) return null;
        // Try Gui.screen() or Gui.getScreen() first (Minecraft 26.2+)
        if (mc.gui != null) {
            try {
                java.lang.reflect.Method m = mc.gui.getClass().getMethod("screen");
                return (Screen) m.invoke(mc.gui);
            } catch (Exception ignored) {
                try {
                    java.lang.reflect.Method m = mc.gui.getClass().getMethod("getScreen");
                    return (Screen) m.invoke(mc.gui);
                } catch (Exception ignored2) {}
            }
        }
        // Fallback to direct field reflection on mc ("screen")
        try {
            java.lang.reflect.Field f = mc.getClass().getField("screen");
            return (Screen) f.get(mc);
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field f = mc.getClass().getDeclaredField("screen");
                f.setAccessible(true);
                return (Screen) f.get(mc);
            } catch (Exception ignored2) {}
        }
        return null;
    }

    public static void setScreen(net.minecraft.client.Minecraft mc, Screen screen) {
        if (mc == null) return;
        // Try Gui.setScreen(screen) first (Minecraft 26.2+)
        if (mc.gui != null) {
            try {
                java.lang.reflect.Method m = mc.gui.getClass().getMethod("setScreen", Screen.class);
                m.invoke(mc.gui, screen);
                return;
            } catch (Exception ignored) {}
        }
        // Fallback to mc.setScreenAndShow(screen) or mc.setScreen(screen)
        mc.setScreenAndShow(screen);
    }

    public static void initScreen(Screen screen, net.minecraft.client.Minecraft mc, int width, int height) {
        if (screen == null) return;
        try {
            java.lang.reflect.Method m = Screen.class.getMethod("init", net.minecraft.client.Minecraft.class, int.class, int.class);
            m.invoke(screen, mc, width, height);
            return;
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method m = Screen.class.getMethod("init", int.class, int.class);
            m.invoke(screen, width, height);
            return;
        } catch (Exception ignored) {}
        screen.init(width, height);
    }

    public static boolean keyMatches(net.minecraft.client.KeyMapping keyMapping, int key, int scancode) {
        if (keyMapping == null) return false;
        try {
            java.lang.reflect.Method m = keyMapping.getClass().getMethod("matches", int.class, int.class);
            return (boolean) m.invoke(keyMapping, key, scancode);
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method m = keyMapping.getClass().getMethod("matchesKey", int.class, int.class);
            return (boolean) m.invoke(keyMapping, key, scancode);
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean hasShiftDown() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), 340)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), 341);
    }
}
