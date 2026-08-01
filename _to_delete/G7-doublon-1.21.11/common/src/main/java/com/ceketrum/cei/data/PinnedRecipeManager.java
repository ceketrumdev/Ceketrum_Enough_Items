package com.ceketrum.cei.data;

import com.ceketrum.cei.gui.screen.CeiItemInfoScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Manages the global state of multiple pinned recipe cards.
 * Shared across CeiItemInfoScreen, screen mixins, and HUD overlays.
 */
public class PinnedRecipeManager {
    private static PinnedRecipeManager instance;
    
    private final List<PinnedCard> pinnedCards = new ArrayList<>();
    private PinnedCard activeDraggingCard = null;
    
    public static class PinnedCard {
        private final ItemStack targetStack;
        private CeiItemInfoScreen.TabType activeTab;
        private CeiItemInfoScreen.RecipeCategory activeCategory;
        private int currentPage;
        
        private double xOffset;
        private double yOffset;
        private float opacity = 1.0f;
        private boolean showInHud = false;
        
        private CeiItemInfoScreen screenInstance;
        
        public PinnedCard(ItemStack targetStack, CeiItemInfoScreen.TabType activeTab, CeiItemInfoScreen.RecipeCategory activeCategory, int currentPage, double xOffset, double yOffset) {
            this.targetStack = targetStack.copy();
            this.activeTab = activeTab;
            this.activeCategory = activeCategory;
            this.currentPage = currentPage;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
        
        public ItemStack getTargetStack() { return targetStack; }
        public CeiItemInfoScreen.TabType getActiveTab() { return activeTab; }
        public void setActiveTab(CeiItemInfoScreen.TabType activeTab) { this.activeTab = activeTab; }
        public CeiItemInfoScreen.RecipeCategory getActiveCategory() { return activeCategory; }
        public void setActiveCategory(CeiItemInfoScreen.RecipeCategory activeCategory) { this.activeCategory = activeCategory; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
        
        public double getxOffset() { return xOffset; }
        public void setxOffset(double xOffset) { this.xOffset = xOffset; }
        public double getyOffset() { return yOffset; }
        public void setyOffset(double yOffset) { this.yOffset = yOffset; }
        
        public float getOpacity() { return opacity; }
        public void cycleOpacity() {
            if (opacity == 1.0f) {
                opacity = 0.75f;
            } else if (opacity == 0.75f) {
                opacity = 0.5f;
            } else if (opacity == 0.5f) {
                opacity = 0.25f;
            } else {
                opacity = 1.0f;
            }
        }
        
        public boolean isShowInHud() { return showInHud; }
        public void setShowInHud(boolean showInHud) { this.showInHud = showInHud; }
        
        public CeiItemInfoScreen getScreenInstance() { return screenInstance; }
        public void setScreenInstance(CeiItemInfoScreen screenInstance) { this.screenInstance = screenInstance; }

        public boolean isDragging() {
            return PinnedRecipeManager.getInstance().getActiveDraggingCard() == this;
        }

        public void setDragging(boolean dragging) {
            if (dragging) {
                PinnedRecipeManager.getInstance().setActiveDraggingCard(this);
            } else {
                if (PinnedRecipeManager.getInstance().getActiveDraggingCard() == this) {
                    PinnedRecipeManager.getInstance().setActiveDraggingCard(null);
                }
            }
        }
    }
    
    private PinnedRecipeManager() {}
    
    public static synchronized PinnedRecipeManager getInstance() {
        if (instance == null) {
            instance = new PinnedRecipeManager();
        }
        return instance;
    }
    
    public List<PinnedCard> getPinnedCards() {
        return pinnedCards;
    }
    
    public PinnedCard getActiveDraggingCard() {
        return activeDraggingCard;
    }
    
    public void setActiveDraggingCard(PinnedCard card) {
        this.activeDraggingCard = card;
    }
    
    public boolean isPinned(ItemStack stack) {
        if (stack == null) return false;
        for (PinnedCard card : pinnedCards) {
            if (card.getTargetStack().getItem() == stack.getItem()) {
                return true;
            }
        }
        return false;
    }
    
    public PinnedCard getPinnedCard(ItemStack stack) {
        if (stack == null) return null;
        for (PinnedCard card : pinnedCards) {
            if (card.getTargetStack().getItem() == stack.getItem()) {
                return card;
            }
        }
        return null;
    }
    
    public void pinRecipe(ItemStack stack, CeiItemInfoScreen.TabType tab, CeiItemInfoScreen.RecipeCategory cat, int page, CeiItemInfoScreen screen) {
        if (stack == null) return;
        
        // Remove if already pinned to toggle it off
        PinnedCard existing = getPinnedCard(stack);
        if (existing != null) {
            pinnedCards.remove(existing);
            return;
        }
        
        // Offset each new card slightly so they don't stack directly on top of each other
        double offset = pinnedCards.size() * 15.0;
        PinnedCard card = new PinnedCard(stack, tab, cat, page, offset, offset);
        card.setScreenInstance(screen);
        pinnedCards.add(card);
    }
    
    public void unpinRecipe(ItemStack stack) {
        PinnedCard existing = getPinnedCard(stack);
        if (existing != null) {
            pinnedCards.remove(existing);
            if (activeDraggingCard == existing) {
                activeDraggingCard = null;
            }
        }
    }
    
    public void bringToFront(PinnedCard card) {
        if (pinnedCards.contains(card)) {
            pinnedCards.remove(card);
            pinnedCards.add(card); // Adds to end of list (topmost)
        }
    }
}
