package com.ceketrum.cei.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/**
 * Universal reflection-backed graphics helper compatible with both:
 * - 26.1 / 1.21.11 (GuiGraphics)
 * - 26.2 (GuiGraphicsExtractor)
 */
public class CeiGraphics {

    public static void fill(Object context, int x1, int y1, int x2, int y2, int color) {
        if (context == null) return;
        try {
            Method m = context.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class);
            m.invoke(context, x1, y1, x2, y2, color);
        } catch (Exception e) {
            // Silence or log if needed
        }
    }

    public static void drawString(Object context, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (context == null) return;
        // Try 26.1 drawString first
        try {
            Method m = context.getClass().getMethod("drawString", Font.class, Component.class, int.class, int.class, int.class, boolean.class);
            m.invoke(context, font, text, x, y, color, shadow);
            return;
        } catch (Exception ignored) {}
        // Fallback to 26.2 text
        try {
            Method m = context.getClass().getMethod("text", Font.class, Component.class, int.class, int.class, int.class, boolean.class);
            m.invoke(context, font, text, x, y, color, shadow);
        } catch (Exception ignored) {}
    }

    public static void drawString(Object context, Font font, String text, int x, int y, int color, boolean shadow) {
        drawString(context, font, Component.literal(text), x, y, color, shadow);
    }

    public static void renderItem(Object context, ItemStack stack, int x, int y) {
        if (context == null || stack == null || stack.isEmpty()) return;
        // Try 26.1 renderItem first
        try {
            Method m = context.getClass().getMethod("renderItem", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
            return;
        } catch (Exception ignored) {}
        // Fallback to 26.2 item
        try {
            Method m = context.getClass().getMethod("item", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
        } catch (Exception ignored) {}
    }

    public static void renderFakeItem(Object context, ItemStack stack, int x, int y) {
        if (context == null || stack == null || stack.isEmpty()) return;
        // Try 26.1 renderFakeItem first
        try {
            Method m = context.getClass().getMethod("renderFakeItem", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
            return;
        } catch (Exception ignored) {}
        // Fallback to 26.2 fakeItem
        try {
            Method m = context.getClass().getMethod("fakeItem", ItemStack.class, int.class, int.class);
            m.invoke(context, stack, x, y);
        } catch (Exception ignored) {}
    }

    public static void renderOutline(Object context, int x, int y, int width, int height, int color) {
        fill(context, x, y, x + width, y + 1, color);
        fill(context, x, y + height - 1, x + width, y + height, color);
        fill(context, x, y + 1, x + 1, y + height - 1, color);
        fill(context, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void setTooltipForNextFrame(Object context, Font font, Component text, int mouseX, int mouseY) {
        if (context == null || font == null || text == null) return;
        // Try 26.1 setTooltipForNextFrame(Font, Component, int, int)
        try {
            Method m = context.getClass().getMethod("setTooltipForNextFrame", Font.class, Component.class, int.class, int.class);
            m.invoke(context, font, text, mouseX, mouseY);
            return;
        } catch (Exception ignored) {}
        // Try 26.2 setTooltipForNextFrame
        try {
            Method m = context.getClass().getMethod("setTooltipForNextFrame", Font.class, java.util.List.class, java.util.Optional.class, int.class, int.class);
            m.invoke(context, font, java.util.List.of(text.getVisualOrderText()), java.util.Optional.empty(), mouseX, mouseY);
            return;
        } catch (Exception ignored) {}
        try {
            Method m = context.getClass().getMethod("tooltipForNextFrame", Font.class, Component.class, int.class, int.class);
            m.invoke(context, font, text, mouseX, mouseY);
            return;
        } catch (Exception ignored) {}
        try {
            Method m = context.getClass().getMethod("tooltipForNextFrame", Font.class, java.util.List.class, java.util.Optional.class, int.class, int.class);
            m.invoke(context, font, java.util.List.of(text.getVisualOrderText()), java.util.Optional.empty(), mouseX, mouseY);
        } catch (Exception ignored) {}
    }

    public static void renderTooltip(Object context, Font font, Component text, int mouseX, int mouseY) {
        setTooltipForNextFrame(context, font, text, mouseX, mouseY);
    }

    public static void renderTooltip(Object context, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (context == null || font == null || stack == null || stack.isEmpty()) return;
        try {
            Method m = context.getClass().getMethod("setTooltipForNextFrame", Font.class, ItemStack.class, int.class, int.class);
            m.invoke(context, font, stack, mouseX, mouseY);
            return;
        } catch (Exception ignored) {}
        try {
            Method m = context.getClass().getMethod("setTooltipForNextFrame", Font.class, Component.class, int.class, int.class);
            m.invoke(context, font, stack.getHoverName(), mouseX, mouseY);
            return;
        } catch (Exception ignored) {}
        setTooltipForNextFrame(context, font, stack.getHoverName(), mouseX, mouseY);
    }
}
