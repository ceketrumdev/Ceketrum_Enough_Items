package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.gui.constants.GuiConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Gère la barre de recherche pour filtrer les items.
 */
public class SearchBar {
    private String searchText = "";
    private boolean isFocused = false;
    private int cursorPosition = 0;
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;
    
    private static final int SEARCH_BAR_HEIGHT = 14;
    private static final int SEARCH_BAR_PADDING = 3;
    private static final int CURSOR_BLINK_INTERVAL = 500; // ms
    
    /**
     * Rend la barre de recherche.
     */
    public void render(GuiGraphicsExtractor context, int x, int y, int width, net.minecraft.client.gui.Font textRenderer) {
        // Fond de la barre de recherche
        int backgroundColor = isFocused ? 0xFF2C2C2C : 0xFF1E1E1E;
        context.fill(x, y, x + width, y + SEARCH_BAR_HEIGHT, backgroundColor);
        context.outline(x, y, width, SEARCH_BAR_HEIGHT, isFocused ? 0xFFFFFFFF : 0xFF808080);
        
        // Texte de recherche
        int textX = x + SEARCH_BAR_PADDING;
        int textY = y + (SEARCH_BAR_HEIGHT - textRenderer.lineHeight) / 2;
        String displayText = searchText;
        
        // Tronquer le texte si nécessaire
        int maxTextWidth = width - SEARCH_BAR_PADDING * 2 - (isFocused ? 6 : 0); // Réserver de l'espace pour le curseur
        if (textRenderer.width(displayText) > maxTextWidth) {
            // Tronquer depuis le début pour montrer la fin du texte
            while (textRenderer.width(displayText) > maxTextWidth && displayText.length() > 0) {
                displayText = displayText.substring(1);
            }
        }
        
        context.text(textRenderer, Component.literal(displayText), textX, textY, 0xFFFFFFFF, false);
        
        // Curseur si la barre est focusée
        if (isFocused) {
            updateCursorBlink();
            if (cursorVisible) {
                int cursorX = textX + textRenderer.width(displayText);
                context.fill(cursorX, textY, cursorX + 1, textY + textRenderer.lineHeight, 0xFFFFFFFF);
            }
        }
    }
    
    /**
     * Gère le clic sur la barre de recherche.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int searchBarX, int searchBarY, int searchBarWidth) {
        if (mouseX >= searchBarX && mouseX < searchBarX + searchBarWidth &&
            mouseY >= searchBarY && mouseY < searchBarY + SEARCH_BAR_HEIGHT) {
            setFocused(true);
            return true;
        }
        return false;
    }
    
    /**
     * Gère la saisie de caractères.
     */
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused) {
            return false;
        }
        
        if (isValidChar(chr)) {
            // Insérer le caractère à la position du curseur
            searchText = searchText.substring(0, cursorPosition) + chr + searchText.substring(cursorPosition);
            cursorPosition++;
            resetCursorBlink();
            return true;
        }
        
        return false;
    }
    
    /**
     * Gère l'appui sur une touche.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) {
            return false;
        }
        
        // Backspace
        if (keyCode == 259) { // GLFW_KEY_BACKSPACE
            if (cursorPosition > 0) {
                searchText = searchText.substring(0, cursorPosition - 1) + searchText.substring(cursorPosition);
                cursorPosition--;
                resetCursorBlink();
                return true;
            }
        }
        // Delete
        else if (keyCode == 261) { // GLFW_KEY_DELETE
            if (cursorPosition < searchText.length()) {
                searchText = searchText.substring(0, cursorPosition) + searchText.substring(cursorPosition + 1);
                resetCursorBlink();
                return true;
            }
        }
        // Flèche gauche
        else if (keyCode == 263) { // GLFW_KEY_LEFT
            if (cursorPosition > 0) {
                cursorPosition--;
                resetCursorBlink();
                return true;
            }
        }
        // Flèche droite
        else if (keyCode == 262) { // GLFW_KEY_RIGHT
            if (cursorPosition < searchText.length()) {
                cursorPosition++;
                resetCursorBlink();
                return true;
            }
        }
        // Début de ligne
        else if (keyCode == 268) { // GLFW_KEY_HOME
            cursorPosition = 0;
            resetCursorBlink();
            return true;
        }
        // Fin de ligne
        else if (keyCode == 269) { // GLFW_KEY_END
            cursorPosition = searchText.length();
            resetCursorBlink();
            return true;
        }
        
        return false;
    }
    
    /**
     * Vérifie si un caractère est valide pour la recherche.
     */
    private boolean isValidChar(char chr) {
        // Accepter tous les caractères imprimables sauf les caractères de contrôle
        return chr >= 32 && chr != 127;
    }
    
    /**
     * Met à jour l'état du clignotement du curseur.
     */
    private void updateCursorBlink() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorBlink >= CURSOR_BLINK_INTERVAL) {
            cursorVisible = !cursorVisible;
            lastCursorBlink = currentTime;
        }
    }
    
    /**
     * Réinitialise le clignotement du curseur (appelé après une action).
     */
    private void resetCursorBlink() {
        cursorVisible = true;
        lastCursorBlink = System.currentTimeMillis();
    }
    
    /**
     * Obtient le texte de recherche.
     */
    public String getSearchText() {
        return searchText;
    }
    
    /**
     * Définit le texte de recherche.
     */
    public void setSearchText(String text) {
        this.searchText = text;
        this.cursorPosition = Math.min(cursorPosition, searchText.length());
    }
    
    /**
     * Vérifie si la barre de recherche est focusée.
     */
    public boolean isFocused() {
        return isFocused;
    }
    
    /**
     * Définit si la barre de recherche est focusée.
     */
    public void setFocused(boolean focused) {
        this.isFocused = focused;
        if (focused) {
            resetCursorBlink();
        }
    }
}


