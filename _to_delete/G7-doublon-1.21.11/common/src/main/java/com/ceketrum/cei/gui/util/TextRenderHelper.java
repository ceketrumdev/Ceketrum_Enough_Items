package com.ceketrum.cei.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Utility class for text rendering operations.
 */
public class TextRenderHelper {
    
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
    
    public static int drawWrappedText(Object context, String text, int x, int y, int maxWidth, int color, float scale, int indent, Font textRenderer) {
        if (text == null || text.isEmpty()) return y;
        
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
                    CeiGraphics.drawString(context, textRenderer, Component.literal(currentLine.toString()), currentX, currentY, color, false);
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
            
            if (currentLine.length() > 0) {
                CeiGraphics.drawString(context, textRenderer, Component.literal(currentLine.toString()), currentX, currentY, color, false);
                currentY += (int) (textRenderer.lineHeight * scale) + 2;
            }
        }
        
        return currentY;
    }
}
