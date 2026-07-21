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
                        this.minecraft.player.level().getRecipeManager(),
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        assert this.minecraft != null;
        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        
        if (ceiModule.handleMouseClick(mouseX, mouseY, button, screenWidth, screenHeight, this.minecraft.font)) {
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (ceiModule.handleCharTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        assert this.minecraft != null;
        
        if (ceiModule.handleKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Si la barre n'est pas focusée, comportement normal
        // Vérifier si c'est la touche d'inventaire pour fermer l'inventaire
        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}



