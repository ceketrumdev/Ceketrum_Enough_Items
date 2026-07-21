package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Écran d'inventaire personnalisé avec un panneau CEI (Ceke Enhanced Inventory) 
 * affichant tous les items disponibles.
 */
public class CustomInventoryScreen extends InventoryScreen {
    private final CeiModule ceiModule = new CeiModule();
    
    public CustomInventoryScreen(ClientPlayerEntity player) {
        super(player);
        ceiModule.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Rendre d'abord l'inventaire (qui inclut le modèle du joueur)
        super.renderBackground(context, mouseX, mouseY, delta);
        
        assert this.client != null;
        int screenHeight = this.client.getWindow().getScaledHeight();
        int screenWidth = this.client.getWindow().getScaledWidth();
        
        // Rendre le module CEI
        ceiModule.render(context, mouseX, mouseY, screenWidth, screenHeight,
                        this.client.textRenderer,
                        this.client.player.getWorld().getRecipeManager(),
                        this.client.player.getWorld().getRegistryManager());
        
        // Rendre l'inventaire (items + modèle du joueur)
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        assert this.client != null;
        int screenHeight = this.client.getWindow().getScaledHeight();
        int screenWidth = this.client.getWindow().getScaledWidth();
        
        float animationSlideOffset = ceiModule.getPanelRenderer().getAnimationSlideOffset();
        if (ceiModule.handleMouseScroll(mouseX, mouseY, verticalAmount, screenWidth, screenHeight, this.client.textRenderer, animationSlideOffset)) {
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        assert this.client != null;
        int screenWidth = this.client.getWindow().getScaledWidth();
        int screenHeight = this.client.getWindow().getScaledHeight();
        
        if (ceiModule.handleMouseClick(mouseX, mouseY, button, screenWidth, screenHeight, this.client.textRenderer)) {
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
        assert this.client != null;
        
        if (ceiModule.handleKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Si la barre n'est pas focusée, comportement normal
        // Vérifier si c'est la touche d'inventaire pour fermer l'inventaire
        if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}


