package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.config.CeiConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Écran de configuration pour le module CEI.
 */
public class CeiConfigScreen extends Screen {
    private final Screen parent;
    private final CeiConfig config;
    
    private ButtonWidget panelPositionButton;
    private ButtonWidget enableAnimationsButton;
    private ButtonWidget showFavoritesButton;
    private ButtonWidget showHelpPopupButton;
    private ButtonWidget applyButton;
    private ButtonWidget cancelButton;
    private ButtonWidget resetButton;
    
    public CeiConfigScreen(Screen parent) {
        super(Text.translatable("gui.cei.config.title"));
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
        panelPositionButton = ButtonWidget.builder(
            Text.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"),
            button -> {
                config.setPanelOnLeft(!config.isPanelOnLeft());
                button.setMessage(Text.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"));
            }
        ).dimensions(centerX - 100, startY, 200, 20).build();
        addDrawableChild(panelPositionButton);

        // Bouton toggle animations
        enableAnimationsButton = ButtonWidget.builder(
            Text.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"),
            button -> {
                config.setEnableAnimations(!config.isEnableAnimations());
                button.setMessage(Text.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"));
            }
        ).dimensions(centerX - 100, startY + spacing, 200, 20).build();
        addDrawableChild(enableAnimationsButton);

        // Bouton toggle favoris par défaut
        showFavoritesButton = ButtonWidget.builder(
            Text.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"),
            button -> {
                config.setShowFavoritesByDefault(!config.isShowFavoritesByDefault());
                button.setMessage(Text.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"));
            }
        ).dimensions(centerX - 100, startY + spacing * 2, 200, 20).build();
        addDrawableChild(showFavoritesButton);

        // Bouton toggle popup d'aide
        showHelpPopupButton = ButtonWidget.builder(
            Text.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"),
            button -> {
                config.setShowHelpPopup(!config.isShowHelpPopup());
                button.setMessage(Text.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"));
            }
        ).dimensions(centerX - 100, startY + spacing * 3, 200, 20).build();
        addDrawableChild(showHelpPopupButton);

        // Boutons d'action
        int buttonY = height - 40;
        applyButton = ButtonWidget.builder(
            Text.translatable("gui.cei.config.apply"),
            button -> applyChanges()
        ).dimensions(centerX - 150, buttonY, 80, 20).build();
        addDrawableChild(applyButton);

        cancelButton = ButtonWidget.builder(
            Text.translatable("gui.cei.config.cancel"),
            button -> client.setScreen(parent)
        ).dimensions(centerX - 40, buttonY, 80, 20).build();
        addDrawableChild(cancelButton);

        resetButton = ButtonWidget.builder(
            Text.translatable("gui.cei.config.reset"),
            button -> {
                config.reset();
                loadConfigValues();
            }
        ).dimensions(centerX + 70, buttonY, 80, 20).build();
        addDrawableChild(resetButton);
    }
    
    private void loadConfigValues() {
        panelPositionButton.setMessage(Text.translatable(config.isPanelOnLeft() ? "gui.cei.config.panel_position.left" : "gui.cei.config.panel_position.right"));
        enableAnimationsButton.setMessage(Text.translatable(config.isEnableAnimations() ? "gui.cei.config.animations.on" : "gui.cei.config.animations.off"));
        showFavoritesButton.setMessage(Text.translatable(config.isShowFavoritesByDefault() ? "gui.cei.config.favorites_default.on" : "gui.cei.config.favorites_default.off"));
        if (showHelpPopupButton != null) {
            showHelpPopupButton.setMessage(Text.translatable(config.isShowHelpPopup() ? "gui.cei.config.help_popup.on" : "gui.cei.config.help_popup.off"));
        }
    }
    
    private void applyChanges() {
        // Tous les changements sont déjà appliqués en temps réel via les boutons
        config.save();
        client.setScreen(parent);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        int centerX = width / 2;
        int titleY = 20;
        context.drawCenteredTextWithShadow(textRenderer, title, centerX, titleY, 0xFFFFFF);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void close() {
        client.setScreen(parent);
    }
}



