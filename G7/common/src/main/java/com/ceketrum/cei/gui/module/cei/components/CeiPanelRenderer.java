package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Gère le rendu du fond du panneau CEI avec la barre de recherche.
 */
public class CeiPanelRenderer {
    private final SearchBar searchBar = new SearchBar();
    private boolean showFavoritesOnly = false;
    private long panelOpenTime = System.currentTimeMillis();
    private static final int PANEL_ANIMATION_DURATION = 250; // ms
    private final CeiConfig config = CeiConfig.getInstance();
    
    // Animation de rotation de l'engrenage
    private Long settingsButtonHoverStartTime = null;
    private static final int ROTATION_ANIMATION_DURATION = 300; // ms
    private static final float ROTATION_ANGLE = 90.0f; // degrés
    
    // Valeurs calculées lors du dernier render (pour réutilisation par les getters)
    // Initialisées à -1 pour détecter si elles ont été calculées
    private int cachedCeiX = -1;
    private int cachedCeiY = -1;
    private int cachedCeiWidth = -1;
    private int cachedCeiHeight = -1;
    
    /**
     * Rend le fond du panneau CEI avec titre, barre de recherche et séparateur.
     * 
     * @param context Le contexte de rendu
     * @param screenHeight Hauteur de l'écran
     * @param screenWidth Largeur de l'écran
     * @param textRenderer Le TextRenderer pour dessiner le texte
     * @param mouseX Position X de la souris (pour détecter le survol du bouton paramètres)
     * @param mouseY Position Y de la souris (pour détecter le survol du bouton paramètres)
     */
    public void render(Object context, int screenHeight, int screenWidth, net.minecraft.client.gui.Font textRenderer, 
                      double mouseX, double mouseY) {
        // Animation d'ouverture (fade + slide depuis le côté approprié)
        float animationProgress = AnimationHelper.getAnimationProgress(panelOpenTime, PANEL_ANIMATION_DURATION);
        float alpha = AnimationHelper.easeOut(0.0f, 1.0f, animationProgress);
        // Si le panneau est à gauche, slide depuis la gauche (-50), sinon depuis la droite (+50)
        float slideStart = config.isPanelOnLeft() ? -50.0f : 50.0f;
        float slideOffset = AnimationHelper.easeOut(slideStart, 0.0f, animationProgress);
        
        // Calculer la position de l'inventaire pour éviter les chevauchements
        // L'inventaire standard fait 176 pixels de large et est centré
        int inventoryWidth = 176;
        int inventoryX = (screenWidth - inventoryWidth) / 2;
        
        // Détecter le mode portrait (largeur < hauteur)
        boolean isPortrait = screenWidth < screenHeight;
        
        // Calculer la position X selon le choix gauche/droite
        int inventoryRight = inventoryX + inventoryWidth;
        int minWidth = config.getPanelWidthMin();
        int maxWidth = config.getPanelWidthMax();
        int ceiWidth = config.getPanelWidth();
        
        if (isPortrait) {
            int maxAvailableWidth;
            if (config.isPanelOnLeft()) {
                maxAvailableWidth = Math.max(minWidth, inventoryX - config.getPanelMargin() * 2);
            } else {
                maxAvailableWidth = Math.max(minWidth, screenWidth - inventoryRight - GuiConstants.CEI_MARGIN - config.getPanelMargin());
            }
            ceiWidth = Math.min(ceiWidth, maxAvailableWidth);
        } else {
            int maxAvailableWidth;
            if (config.isPanelOnLeft()) {
                maxAvailableWidth = inventoryX - config.getPanelMargin() - GuiConstants.CEI_MARGIN;
            } else {
                maxAvailableWidth = screenWidth - inventoryRight - GuiConstants.CEI_MARGIN - config.getPanelMargin();
            }
            
            if (maxAvailableWidth > GuiConstants.CEI_WIDTH) {
                ceiWidth = Math.min(maxWidth, maxAvailableWidth);
            } else if (maxAvailableWidth < ceiWidth && maxAvailableWidth > 0) {
                ceiWidth = Math.max(minWidth, maxAvailableWidth);
            }
        }
        
        int baseCeiX;
        if (config.isPanelOnLeft()) {
            baseCeiX = config.getPanelMargin();
        } else {
            baseCeiX = screenWidth - ceiWidth - config.getPanelMargin();
        }
        int ceiX = (int) (baseCeiX + slideOffset);
        
        if (config.isPanelOnLeft()) {
            if (ceiX + ceiWidth > inventoryX - GuiConstants.CEI_MARGIN) {
                ceiWidth = Math.max(minWidth, inventoryX - ceiX - GuiConstants.CEI_MARGIN);
            }
        } else {
            if (ceiX < inventoryRight + GuiConstants.CEI_MARGIN) {
                baseCeiX = inventoryRight + GuiConstants.CEI_MARGIN;
                ceiX = (int) (baseCeiX + slideOffset);
                int maxWidthAfterInventory = screenWidth - baseCeiX - config.getPanelMargin();
                if (ceiWidth > maxWidthAfterInventory) {
                    ceiWidth = Math.max(minWidth, maxWidthAfterInventory);
                }
            }
        }
        
        if (ceiWidth < minWidth) {
            ceiWidth = Math.min(minWidth, screenWidth - config.getPanelMargin() * 2);
        }
        
        int ceiY = config.getPanelY();
        int maxHeightFromTop = screenHeight - ceiY - GuiConstants.CEI_MARGIN;
        int ceiHeight = maxHeightFromTop;
        
        if (ceiHeight < 100) {
            ceiHeight = Math.max(100, screenHeight - ceiY - GuiConstants.CEI_MARGIN);
        }
        
        if (ceiX + ceiWidth > screenWidth - config.getPanelMargin()) {
            ceiWidth = Math.max(minWidth, screenWidth - ceiX - config.getPanelMargin());
        }
        if (ceiX < config.getPanelMargin()) {
            ceiX = (int) (config.getPanelMargin() + slideOffset);
        }
        
        cachedCeiX = ceiX;
        cachedCeiY = ceiY;
        cachedCeiWidth = ceiWidth;
        cachedCeiHeight = ceiHeight;
        
        float effectiveAlpha = config.isEnableAnimations() ? alpha : 1.0f;
        int shadowAlpha = (int) (0x60 * effectiveAlpha);
        int bgAlpha = (int) ((config.getBackgroundColor() >>> 24) * effectiveAlpha);
        int borderAlpha = (int) ((config.getBorderColor() >>> 24) * effectiveAlpha);
        
        int shadowOffset = 4;
        com.ceketrum.cei.gui.util.CeiGraphics.fill(context, ceiX + shadowOffset, ceiY + shadowOffset, 
                     ceiX + ceiWidth + shadowOffset, ceiY + ceiHeight + shadowOffset, 
                     shadowAlpha << 24);
        com.ceketrum.cei.gui.util.CeiGraphics.fill(context, ceiX, ceiY, ceiX + ceiWidth, ceiY + ceiHeight, (bgAlpha << 24) | (config.getBackgroundColor() & 0xFFFFFFFF));
        com.ceketrum.cei.gui.util.CeiGraphics.renderOutline(context, ceiX, ceiY, ceiWidth, ceiHeight, (borderAlpha << 24) | (config.getBorderColor() & 0xFFFFFFFF));
        
        String title = "Items Disponibles";
        int titleWidth = textRenderer.width(title);
        int titleY = ceiY + 5;
        float titleAlpha = config.isEnableAnimations() ? alpha : 1.0f;
        int titleColor = (int) ((config.getTextColor() >>> 24) * titleAlpha) << 24 | (config.getTextColor() & 0xFFFFFFFF);
        com.ceketrum.cei.gui.util.CeiGraphics.drawString(context, textRenderer, Component.literal(title), 
                        ceiX + (ceiWidth - titleWidth) / 2, titleY, titleColor, false);
        
        int settingsButtonSize = 16;
        int settingsButtonX = ceiX + ceiWidth - settingsButtonSize - 5;
        int settingsButtonY = ceiY + 5;
        String settingsIcon = "⚙";
        int settingsColor = titleColor;
        
        com.ceketrum.cei.gui.util.CeiGraphics.drawString(context, textRenderer, Component.literal(settingsIcon), settingsButtonX, settingsButtonY, settingsColor, false);
        
        int searchBarY = titleY + textRenderer.lineHeight + 3;
        int searchBarWidth = ceiWidth - 10;
        int searchBarX = ceiX + 5;
        searchBar.render(context, searchBarX, searchBarY, searchBarWidth, textRenderer);
        
        int separatorY = searchBarY + 14 + 3;
        int separatorColor = (borderAlpha << 24) | 0x00FFFFFF;
        com.ceketrum.cei.gui.util.CeiGraphics.fill(context, ceiX + 5, separatorY, ceiX + ceiWidth - 5, separatorY + 1, separatorColor);
        
        int favoritesButtonY = separatorY + 3;
        int favoritesButtonHeight = 16;
        String favoritesButtonText = showFavoritesOnly ? 
            "★ " + net.minecraft.network.chat.Component.translatable("gui.cei.favorites").getString() : 
            "☆ " + net.minecraft.network.chat.Component.translatable("gui.cei.favorites.all").getString();
        int padding = 12;
        int textWidth = textRenderer.width(Component.literal(favoritesButtonText).withStyle(ChatFormatting.BOLD));
        int maxButtonWidth = ceiWidth - 10;
        int favoritesButtonWidth = Math.min(textWidth + padding, maxButtonWidth);
        int favoritesButtonX = ceiX + (ceiWidth - favoritesButtonWidth) / 2;
        
        int buttonBgColor = 0xFF2C2C2C;
        com.ceketrum.cei.gui.util.CeiGraphics.fill(context, favoritesButtonX, favoritesButtonY, favoritesButtonX + favoritesButtonWidth, 
                    favoritesButtonY + favoritesButtonHeight, buttonBgColor);
        com.ceketrum.cei.gui.util.CeiGraphics.renderOutline(context, favoritesButtonX, favoritesButtonY, favoritesButtonWidth, favoritesButtonHeight, 
                          GuiConstants.BORDER_COLOR);
        
        int textX = favoritesButtonX + (favoritesButtonWidth - textWidth) / 2;
        int textY = favoritesButtonY + (favoritesButtonHeight - textRenderer.lineHeight) / 2;
        int buttonTextColor = titleColor;
        com.ceketrum.cei.gui.util.CeiGraphics.drawString(context, textRenderer, Component.literal(favoritesButtonText).withStyle(ChatFormatting.BOLD), 
                        textX, textY, buttonTextColor, false);
    }
    
    /**
     * Réinitialise le temps d'ouverture du panneau (pour l'animation).
     */
    public void resetPanelOpenTime() {
        panelOpenTime = System.currentTimeMillis();
    }
    
    /**
     * Retourne la position X actuelle du panneau CEI (avec animation).
     * Utilise les valeurs mises en cache lors du dernier render.
     */
    public int getCeiX(int screenWidth) {
        // Retourner directement la valeur mise en cache (calculée dans render())
        return cachedCeiX != -1 ? cachedCeiX : config.getPanelMargin();
    }
    
    /**
     * Retourne l'offset d'animation actuel (pour appliquer aux items et scroll).
     */
    public float getAnimationSlideOffset() {
        if (!config.isEnableAnimations()) {
            return 0.0f;
        }
        float animationProgress = AnimationHelper.getAnimationProgress(panelOpenTime, PANEL_ANIMATION_DURATION);
        // Si le panneau est à gauche, slide depuis la gauche (-50), sinon depuis la droite (+50)
        float slideStart = config.isPanelOnLeft() ? -50.0f : 50.0f;
        return AnimationHelper.easeOut(slideStart, 0.0f, animationProgress);
    }
    
    /**
     * Retourne l'alpha d'animation actuel (pour appliquer aux items et scroll).
     */
    public float getAnimationAlpha() {
        if (!config.isEnableAnimations()) {
            return 1.0f;
        }
        float animationProgress = AnimationHelper.getAnimationProgress(panelOpenTime, PANEL_ANIMATION_DURATION);
        return AnimationHelper.easeOut(0.0f, 1.0f, animationProgress);
    }
    
    /**
     * Retourne la position Y actuelle du panneau CEI.
     * Utilise la valeur mise en cache lors du dernier render.
     */
    public int getCeiY() {
        // Retourner directement la valeur mise en cache (calculée dans render())
        return cachedCeiY != -1 ? cachedCeiY : config.getPanelY();
    }
    
    /**
     * Retourne la largeur actuelle du panneau CEI (peut être réduite sur petits écrans).
     */
    public int getCeiWidth(int screenWidth, int screenHeight) {
        // Retourner directement la valeur mise en cache (calculée dans render())
        return cachedCeiWidth != -1 ? cachedCeiWidth : config.getPanelWidth();
    }
    
    /**
     * Retourne la hauteur actuelle du panneau CEI (peut être réduite sur petits écrans).
     * Utilise la valeur mise en cache lors du dernier render.
     */
    public int getCeiHeight(int screenHeight, int screenWidth) {
        // Retourner directement la valeur mise en cache (calculée dans render())
        if (cachedCeiHeight != -1) {
            return cachedCeiHeight;
        }
        // Fallback si pas encore calculé
        int ceiY = config.getPanelY();
        int maxHeightFromTop = screenHeight - ceiY - 15;
        return Math.max(100, maxHeightFromTop);
    }
    
    /**
     * Retourne la barre de recherche pour gérer les interactions.
     */
    public SearchBar getSearchBar() {
        return searchBar;
    }
    
    /**
     * Retourne la position Y où commence la liste d'items (après le header et la barre de recherche).
     */
    public int getItemsListStartY(net.minecraft.client.gui.Font textRenderer) {
        int ceiY = config.getPanelY();
        int titleY = ceiY + 5;
        int searchBarY = titleY + textRenderer.lineHeight + 3;
        int separatorY = searchBarY + 14 + 3; // 14 = hauteur de la barre de recherche
        int favoritesButtonY = separatorY + 3;
        int favoritesButtonHeight = 16;
        return favoritesButtonY + favoritesButtonHeight + 3; // +3 pour un petit espace après le bouton
    }
    
    /**
     * Vérifie si le clic est sur le bouton paramètres.
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param ceiX Position X actuelle du panneau CEI (avec animation)
     * @param ceiY Position Y du panneau CEI
     * @param ceiWidth Largeur actuelle du panneau CEI (peut être ajustée)
     * @return true si le clic est sur le bouton, false sinon
     */
    public boolean isSettingsButtonClicked(int mouseX, int mouseY, int ceiX, int ceiY, int ceiWidth) {
        int settingsButtonSize = 16;
        int settingsButtonX = ceiX + ceiWidth - settingsButtonSize - 5;
        int settingsButtonY = ceiY + 5;
        return mouseX >= settingsButtonX && mouseX < settingsButtonX + settingsButtonSize &&
               mouseY >= settingsButtonY && mouseY < settingsButtonY + settingsButtonSize;
    }
    
    /**
     * Vérifie si le clic est sur le bouton toggle favoris.
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param ceiX Position X du panneau CEI
     * @param ceiY Position Y du panneau CEI
     * @param ceiWidth Largeur du panneau CEI
     * @param textRenderer Le TextRenderer pour mesurer le texte
     * @return true si le clic est sur le bouton, false sinon
     */
    public boolean isFavoritesButtonClicked(int mouseX, int mouseY, int ceiX, int ceiY, 
                                            int ceiWidth, net.minecraft.client.gui.Font textRenderer) {
        int titleY = ceiY + 5;
        int searchBarY = titleY + textRenderer.lineHeight + 3;
        int separatorY = searchBarY + 14 + 3;
        int favoritesButtonY = separatorY + 3;
        int favoritesButtonHeight = 16;
        String favoritesButtonText = showFavoritesOnly ? 
            "★ " + net.minecraft.network.chat.Component.translatable("gui.cei.favorites").getString() : 
            "☆ " + net.minecraft.network.chat.Component.translatable("gui.cei.favorites.all").getString();
        int padding = 12;
        // Mesurer le texte avec le formatage BOLD
        int textWidth = textRenderer.width(Component.literal(favoritesButtonText).withStyle(ChatFormatting.BOLD));
        int maxButtonWidth = ceiWidth - 10;
        int favoritesButtonWidth = Math.min(textWidth + padding, maxButtonWidth);
        int favoritesButtonX = ceiX + (ceiWidth - favoritesButtonWidth) / 2;
        
        return GuiRenderHelper.isMouseOver(mouseX, mouseY, favoritesButtonX, favoritesButtonY, 
                                          favoritesButtonWidth, favoritesButtonHeight);
    }
    
    /**
     * Toggle l'état du filtre favoris.
     */
    public void toggleFavoritesFilter() {
        showFavoritesOnly = !showFavoritesOnly;
        
        // Jouer le son de tourner une page de livre
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            client.player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
        }
    }
    
    /**
     * Retourne si le filtre favoris est activé.
     * @return true si seuls les favoris sont affichés, false sinon
     */
    public boolean isShowFavoritesOnly() {
        return showFavoritesOnly;
    }
}


