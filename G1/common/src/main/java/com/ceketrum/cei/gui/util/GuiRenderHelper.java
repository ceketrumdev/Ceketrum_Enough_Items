package com.ceketrum.cei.gui.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Utility class for GUI rendering operations.
 */
public class GuiRenderHelper {
    
    /**
     * Checks if the mouse is over a rectangular area.
     * 
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param x Rectangle X position
     * @param y Rectangle Y position
     * @param width Rectangle width
     * @param height Rectangle height
     * @return true if the mouse is over the rectangle
     */
    public static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    
    /**
     * Draws a rounded background rectangle.
     * 
     * @param context The DrawContext
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     * @param radius Corner radius (not currently implemented, draws as rectangle)
     * @param color Background color (ARGB format)
     */
    public static void drawRoundedBackground(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        // Simple rectangle implementation (rounded corners could be added later)
        context.fill(x, y, x + width, y + height, color);
    }
}


