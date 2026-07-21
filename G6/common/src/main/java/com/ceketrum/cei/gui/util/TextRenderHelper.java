package com.ceketrum.cei.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Utility class for text rendering operations.
 */
public class TextRenderHelper {
    
    /**
     * Truncates text to fit within a maximum width, adding ellipsis if necessary.
     * 
     * @param text The text to truncate
     * @param maxWidth The maximum width in pixels
     * @param textRenderer The TextRenderer to measure text width
     * @return The truncated text string
     */
    public static String truncateText(String text, int maxWidth, Font textRenderer) {
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }
        
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.width(ellipsis);
        int availableWidth = maxWidth - ellipsisWidth;
        
        if (availableWidth <= 0) {
            return ellipsis;
        }
        
        String truncated = text;
        while (textRenderer.width(truncated) > availableWidth && truncated.length() > 0) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        
        return truncated + ellipsis;
    }
    
    /**
     * Draws wrapped text within a specified width.
     * 
     * @param context The DrawContext
     * @param text The text to draw
     * @param x Starting X position
     * @param y Starting Y position
     * @param maxWidth Maximum width for wrapping
     * @param color Text color (ARGB format)
     * @param scale Text scale factor
     * @param indent Indentation in pixels for wrapped lines
     * @param textRenderer The TextRenderer
     * @return The Y position after the text
     */
    public static int drawWrappedText(GuiGraphics context, String text, int x, int y, int maxWidth, int color, float scale, int indent, Font textRenderer) {
        if (text == null || text.isEmpty()) return y;
        
        // Normalize any literal or real newlines (backslash-n, CRLF, CR) to standard LF
        String normalized = text.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n");
        String[] paragraphs = normalized.split("\n");
        int currentY = y;
        
        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p];
            String[] words = paragraph.split(" ");
            int currentX = x;
            StringBuilder currentLine = new StringBuilder();
            
            for (String word : words) {
                String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
                int testWidth = (int) (textRenderer.width(testLine) * scale);
                
                if (testWidth > maxWidth && currentLine.length() > 0) {
                    // Draw current line and start new line
                    context.pose().pushMatrix();
                    context.pose().scale(scale, scale);
                    float invScale = 1.0f / scale;
                    context.drawString(textRenderer, Component.literal(currentLine.toString()), (int)(currentX * invScale), (int)(currentY * invScale), color, false);
                    context.pose().popMatrix();
                    
                    currentLine = new StringBuilder(word);
                    currentY += (int) (textRenderer.lineHeight * scale) + 2;
                    currentX = x + indent;
                } else {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
            }
            
            // Draw the last line of this paragraph
            if (currentLine.length() > 0) {
                context.pose().pushMatrix();
                context.pose().scale(scale, scale);
                float invScale = 1.0f / scale;
                context.drawString(textRenderer, Component.literal(currentLine.toString()), (int)(currentX * invScale), (int)(currentY * invScale), color, false);
                context.pose().popMatrix();
                
                currentY += (int) (textRenderer.lineHeight * scale) + 2;
            }
        }
        
        return currentY;
    }
}

