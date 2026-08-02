package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Gère le rendu de la popup d'aide qui explique comment utiliser les favoris.
 */
public class HelpPopupRenderer {
    private static final Random RANDOM = new Random();
    private static final int POPUP_WIDTH = 200;
    private static final int POPUP_HEIGHT = 80;
    private static final int ANIMATION_DURATION = 300;
    private static final double SHOW_CHANCE = 0.15; // 15% de chance d'apparaître
    
    private Long popupOpenTime = null;
    private boolean shouldShow = false;
    private int selectedTipIndex = 0; // Stocker le choix de l'astuce une seule fois
    private final CeiConfig config = CeiConfig.getInstance();
    
    /**
     * Détermine si la popup doit être affichée (aléatoirement).
     */
    public void checkShouldShow() {
        if (popupOpenTime == null && config.isShowHelpPopup()) {
            // 15% de chance d'afficher la popup
            shouldShow = RANDOM.nextDouble() < SHOW_CHANCE;
            if (shouldShow) {
                popupOpenTime = System.currentTimeMillis();
                // Choisir l'astuce au hasard parmi les 5 astuces disponibles
                selectedTipIndex = RANDOM.nextInt(5);
            }
        }
    }
    
    /**
     * Rend la popup d'aide si nécessaire.
     */
    public void render(GuiGraphics context, int screenWidth, int screenHeight, 
                      net.minecraft.client.gui.Font textRenderer) {
        if (!shouldShow || popupOpenTime == null || !config.isShowHelpPopup()) {
            return;
        }
        
        float animationProgress = AnimationHelper.getAnimationProgress(popupOpenTime, ANIMATION_DURATION);
        if (animationProgress >= 1.0f) {
            animationProgress = 1.0f;
        }
        
        float alpha = AnimationHelper.easeOut(0.0f, 1.0f, animationProgress);
        float slideOffset = AnimationHelper.easeOut(30.0f, 0.0f, animationProgress);
        
        // Positionner la popup en fonction de la position du panneau CEI
        // Si le panneau est à gauche, la popup va à droite, et vice versa
        int popupX;
        if (config.isPanelOnLeft()) {
            // Panneau à gauche : popup en bas à droite
            popupX = (int) (screenWidth - POPUP_WIDTH - 20 - slideOffset);
        } else {
            // Panneau à droite : popup en bas à gauche
            popupX = (int) (20 + slideOffset);
        }
        int popupY = (int) (screenHeight - POPUP_HEIGHT - 20);
        
        // Appliquer l'alpha
        int bgAlpha = (int) (0xE0 * alpha);
        int borderAlpha = (int) (0xFF * alpha);
        int textAlpha = (int) (0xFF * alpha);
        
        // Ombre
        int shadowAlpha = (int) (0x80 * alpha);
        context.fill(popupX + 3, popupY + 3, popupX + POPUP_WIDTH + 3, popupY + POPUP_HEIGHT + 3, 
                    shadowAlpha << 24);
        
        // Fond
        int bgColor = bgAlpha << 24 | 0x202020;
        GuiRenderHelper.drawRoundedBackground(context, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, 8, bgColor);
        int borderColor = borderAlpha << 24 | 0xFFFFFF;
        context.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, borderColor);
        
        // Titre - utiliser l'astuce choisie au moment de l'ouverture
        String title;
        String helpText;
        String helpText2 = "";
        
        switch (selectedTipIndex) {
            case 0:
                title = "Astuce : Raccourcis R / U";
                helpText = "Touche R : Voir la Recette";
                helpText2 = "Touche U : Voir ses Usages";
                break;
            case 1:
                title = "Astuce : Ancrer la Fiche";
                helpText = "Bouton Épingle : Ancrer la fiche";
                helpText2 = "Reste affichée hors inventaire";
                break;
            case 2:
                title = "Astuce : Glisser & Calques";
                helpText = "Glisser le titre pour déplacer";
                helpText2 = "Clic n'importe où : Premier plan";
                break;
            case 3:
                title = "Astuce : Remplissage Auto";
                helpText = "Clic + : Remplir l'établi (+1)";
                helpText2 = "Shift ou Clic droit : Remplir max";
                break;
            default:
                title = "Astuce : Favoris";
                helpText = "Shift + Clic pour mettre en favori";
                helpText2 = "Permet un accès rapide à l'item";
                break;
        }
        
        int titleColor = textAlpha << 24 | 0xFFFF00;
        int titleX = popupX + (POPUP_WIDTH - textRenderer.width(title)) / 2;
        context.drawString(textRenderer, Component.literal(title).withStyle(ChatFormatting.BOLD), 
                        titleX, popupY + 8, titleColor, false);
        
        // Texte d'aide
        int textColor = textAlpha << 24 | 0xFFFFFF;
        int textY = popupY + 25;
        int textX = popupX + (POPUP_WIDTH - textRenderer.width(helpText)) / 2;
        context.drawString(textRenderer, Component.literal(helpText), textX, textY, textColor, false);
        if (!helpText2.isEmpty()) {
            textX = popupX + (POPUP_WIDTH - textRenderer.width(helpText2)) / 2;
            context.drawString(textRenderer, Component.literal(helpText2), textX, textY + 12, textColor, false);
        }
        
        // Bouton fermer (petit X en haut à droite)
        int closeButtonSize = 12;
        int closeButtonX = popupX + POPUP_WIDTH - closeButtonSize - 5;
        int closeButtonY = popupY + 5;
        context.fill(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize, 
                    (borderAlpha << 24) | 0x666666);
        
        // Centrer le texte "×" dans le bouton
        String closeSymbol = "×";
        int symbolWidth = textRenderer.width(closeSymbol);
        // Centrer horizontalement : position du bouton + (largeur du bouton - largeur du texte) / 2
        int symbolX = closeButtonX + (closeButtonSize - symbolWidth) / 2;
        // Centrer verticalement : position du bouton + (hauteur du bouton - hauteur de la police) / 2
        // Le Y de drawText est la position de la ligne de base, donc on doit ajuster
        int symbolY = closeButtonY + (closeButtonSize - textRenderer.lineHeight) / 2;
        context.drawString(textRenderer, Component.literal(closeSymbol), symbolX, symbolY, textColor, false);
    }
    
    /**
     * Vérifie si le clic est sur le bouton fermer.
     */
    public boolean isCloseButtonClicked(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!shouldShow || popupOpenTime == null) {
            return false;
        }
        
        // Calculer la position de la popup selon la position du panneau
        int popupX;
        if (config.isPanelOnLeft()) {
            popupX = screenWidth - POPUP_WIDTH - 20;
        } else {
            popupX = 20;
        }
        int popupY = screenHeight - POPUP_HEIGHT - 20;
        int closeButtonSize = 12;
        int closeButtonX = popupX + POPUP_WIDTH - closeButtonSize - 5;
        int closeButtonY = popupY + 5;
        
        return mouseX >= closeButtonX && mouseX < closeButtonX + closeButtonSize &&
               mouseY >= closeButtonY && mouseY < closeButtonY + closeButtonSize;
    }
    
    /**
     * Ferme la popup.
     */
    public void close() {
        shouldShow = false;
        popupOpenTime = null;
        selectedTipIndex = 0; // Réinitialiser pour la prochaine fois
    }
    
    /**
     * Vérifie si la popup est visible.
     */
    public boolean isVisible() {
        return shouldShow && popupOpenTime != null;
    }
}


