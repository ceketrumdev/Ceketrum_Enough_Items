package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.config.CeiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran de configuration pour le module CEI.
 */
public class CeiConfigScreen extends Screen {
    private final Screen parent;
    private final CeiConfig config;
    
    private Button panelPositionButton;
    private Button enableAnimationsButton;
    private Button showFavoritesButton;
    private Button showHelpPopupButton;
    private Button devModeButton;
    private Button applyButton;
    private Button cancelButton;
    private Button resetButton;
    
    public CeiConfigScreen(Screen parent) {
        super(Component.translatable("gui.cei.config.title"));
        this.parent = parent;
        this.config = CeiConfig.getInstance();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int startY = 60;
        int spacing = 25;
        
        // Bouton toggle position du panneau (gauche/droite)
        panelPositionButton = Button.builder(
            Component.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"),
            button -> {
                config.setPanelOnLeft(!config.isPanelOnLeft());
                button.setMessage(Component.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"));
            }
        ).bounds(centerX - 100, startY, 200, 20).build();
        addRenderableWidget(panelPositionButton);

        // Bouton toggle animations
        enableAnimationsButton = Button.builder(
            Component.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"),
            button -> {
                config.setEnableAnimations(!config.isEnableAnimations());
                button.setMessage(Component.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"));
            }
        ).bounds(centerX - 100, startY + spacing, 200, 20).build();
        addRenderableWidget(enableAnimationsButton);

        // Bouton toggle favoris par défaut
        showFavoritesButton = Button.builder(
            Component.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"),
            button -> {
                config.setShowFavoritesByDefault(!config.isShowFavoritesByDefault());
                button.setMessage(Component.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"));
            }
        ).bounds(centerX - 100, startY + spacing * 2, 200, 20).build();
        addRenderableWidget(showFavoritesButton);

        // Bouton toggle popup d'aide
        showHelpPopupButton = Button.builder(
            Component.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"),
            button -> {
                config.setShowHelpPopup(!config.isShowHelpPopup());
                button.setMessage(Component.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"));
            }
        ).bounds(centerX - 100, startY + spacing * 3, 200, 20).build();
        addRenderableWidget(showHelpPopupButton);

        // Mode developpeur.
        //
        // Libelle en clair et non par cle de traduction : ajouter une cle
        // obligerait a toucher tous les fichiers de langue pour une option
        // que seuls les developpeurs de packs verront.
        devModeButton = Button.builder(
            Component.literal(config.isDevMode() ? "Dev mode : ON" : "Dev mode : OFF"),
            button -> {
                config.setDevMode(!config.isDevMode());
                button.setMessage(Component.literal(config.isDevMode() ? "Dev mode : ON" : "Dev mode : OFF"));
            }
        ).bounds(centerX - 100, startY + spacing * 4, 200, 20).build();
        addRenderableWidget(devModeButton);

        // Boutons d'action
        int buttonY = height - 40;
        applyButton = Button.builder(
            Component.translatable("gui.cei.config.apply"),
            button -> applyChanges()
        ).bounds(centerX - 150, buttonY, 80, 20).build();
        addRenderableWidget(applyButton);

        cancelButton = Button.builder(
            Component.translatable("gui.cei.config.cancel"),
            button -> minecraft.setScreen(parent)
        ).bounds(centerX - 40, buttonY, 80, 20).build();
        addRenderableWidget(cancelButton);

        resetButton = Button.builder(
            Component.translatable("gui.cei.config.reset"),
            button -> {
                config.reset();
                loadConfigValues();
            }
        ).bounds(centerX + 70, buttonY, 80, 20).build();
        addRenderableWidget(resetButton);
    }
    
    private void loadConfigValues() {
        panelPositionButton.setMessage(Component.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"));
        enableAnimationsButton.setMessage(Component.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"));
        showFavoritesButton.setMessage(Component.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"));
        if (showHelpPopupButton != null) {
            showHelpPopupButton.setMessage(Component.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"));
        }
    }
    
    private void applyChanges() {
        // Tous les changements sont déjà appliqués en temps réel via les boutons
        config.save();
        minecraft.setScreen(parent);
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int titleY = 20;
        context.drawCenteredString(font, title, centerX, titleY, 0xFFFFFF);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}



