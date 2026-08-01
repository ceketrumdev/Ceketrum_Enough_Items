package com.ceketrum.cei.gui.util;

/**
 * Utility class for GUI rendering operations.
 */
public class GuiRenderHelper {
    
    public static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    
    public static void drawRoundedBackground(Object context, int x, int y, int width, int height, int radius, int color) {
        CeiGraphics.fill(context, x, y, x + width, y + height, color);
    }
}
