package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Écran d'inventaire personnalisé avec un panneau CEI (Ceke Enhanced Inventory) 
 * affichant tous les items disponibles.
 */
public class CustomInventoryScreen extends InventoryScreen {
    private final CeiModule ceiModule = new CeiModule();
    
    public CustomInventoryScreen(LocalPlayer player) {
        super(player);
        ceiModule.init();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Rendre d'abord l'inventaire (qui inclut le modèle du joueur)
        super.renderBackground(context, mouseX, mouseY, delta);
        
        assert this.minecraft != null;
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        
        // Rendre le module CEI
        ceiModule.render(context, mouseX, mouseY, screenWidth, screenHeight,
                        this.minecraft.font,
                        this.minecraft.getConnection().recipes(),
                        this.minecraft.player.level().registryAccess());
        
        // Rendre l'inventaire (items + modèle du joueur)
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        assert this.minecraft != null;
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        
        float animationSlideOffset = ceiModule.getPanelRenderer().getAnimationSlideOffset();
        if (ceiModule.handleMouseScroll(mouseX, mouseY, verticalAmount, screenWidth, screenHeight, this.minecraft.font, animationSlideOffset)) {
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        assert this.minecraft != null;
        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        
        if (ceiModule.handleMouseClick(event.x(), event.y(), event.button(), screenWidth, screenHeight, this.minecraft.font)) {
            return true;
        }
        
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (ceiModule.handleCharTyped((char) event.codepoint(), event.modifiers())) {
            return true;
        }
        return super.charTyped(event);
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        assert this.minecraft != null;
        
        if (ceiModule.handleKeyPress(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        
        // Si la barre n'est pas focusée, comportement normal
        // Vérifier si c'est la touche d'inventaire pour fermer l'inventaire
        if (this.minecraft.options.keyInventory.matches(event)) {
            return super.keyPressed(event);
        }
        
        return super.keyPressed(event);
    }
}


