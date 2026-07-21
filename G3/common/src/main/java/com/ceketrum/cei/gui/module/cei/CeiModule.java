package com.ceketrum.cei.gui.module.cei;

import com.ceketrum.cei.data.FavoriteItemsManager;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.gui.module.cei.components.CeiItemListRenderer;
import com.ceketrum.cei.gui.module.cei.components.CeiPanelRenderer;
import com.ceketrum.cei.gui.module.cei.components.HelpPopupRenderer;
import com.ceketrum.cei.gui.module.cei.components.RecipePopupRenderer;
import com.ceketrum.cei.gui.screen.CeiConfigScreen;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.ItemFilter;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Module CEI (Ceke Enhanced Inventory) - Module réutilisable pour ajouter
 * un panneau d'items avec recherche et recettes à n'importe quel écran d'inventaire.
 */
public class CeiModule {
    private final CeiPanelRenderer panelRenderer;
    private final CeiItemListRenderer itemListRenderer;
    private final RecipePopupRenderer recipePopupRenderer;
    private final HelpPopupRenderer helpPopupRenderer;
    
    private boolean isRecipePopupVisible = false;
    private ItemStack hoveredStack = null;
    private List<ItemStack> allItemsCache = null;
    private List<ItemStack> filteredItemsCache = null;
    private String lastSearchText = "";
    private boolean hasCheckedHelpPopup = false;
    
    public CeiModule() {
        this.panelRenderer = new CeiPanelRenderer();
        this.itemListRenderer = new CeiItemListRenderer();
        this.recipePopupRenderer = new RecipePopupRenderer();
        this.helpPopupRenderer = new HelpPopupRenderer();
    }
    
    /**
     * Initialise le module (appelé quand l'écran s'ouvre).
     */
    public void init() {
        // S'assurer que les descriptions sont chargées
        ItemDescriptionManager.getInstance().reloadCurrentLanguageDescriptions();
        
        // Réinitialiser l'animation du panneau quand on ouvre l'inventaire
        panelRenderer.resetPanelOpenTime();
        
        // Vérifier si on doit afficher la popup d'aide (une seule fois par ouverture)
        hasCheckedHelpPopup = false;
    }
    
    /**
     * Rend le module CEI.
     * 
     * @param context Le contexte de rendu
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param screenWidth Largeur de l'écran
     * @param screenHeight Hauteur de l'écran
     * @param textRenderer Le TextRenderer
     * @param recipeManager Le RecipeManager pour afficher les recettes
     * @param registryManager Le DynamicRegistryManager pour les recettes
     */
    public void render(GuiGraphics context, int mouseX, int mouseY, 
                      int screenWidth, int screenHeight,
                      Font textRenderer,
                      net.minecraft.world.item.crafting.RecipeManager recipeManager,
                      net.minecraft.core.RegistryAccess registryManager) {
        // Rendre le panneau CEI (inclut la barre de recherche)
        panelRenderer.render(context, screenHeight, screenWidth, textRenderer, (double) mouseX, (double) mouseY);
        
        // Obtenir la position Y où commence la liste d'items
        int itemsListStartY = panelRenderer.getItemsListStartY(textRenderer);
        
        // Filtrer les items selon la recherche
        String currentSearchText = panelRenderer.getSearchBar().getSearchText();
        if (!currentSearchText.equals(lastSearchText)) {
            // La recherche a changé, réinitialiser le cache et le scroll
            filteredItemsCache = null;
            itemListRenderer.resetScroll();
            lastSearchText = currentSearchText;
        }
        
        List<ItemStack> itemsToDisplay = getFilteredItems();
        int ceiHeight = panelRenderer.getCeiHeight(screenHeight, screenWidth);
        int ceiWidth = panelRenderer.getCeiWidth(screenWidth, screenHeight);
        int ceiX = panelRenderer.getCeiX(screenWidth);
        int ceiY = panelRenderer.getCeiY();
        float animationSlideOffset = panelRenderer.getAnimationSlideOffset();
        float animationAlpha = panelRenderer.getAnimationAlpha();
        ItemStack hovered = itemListRenderer.render(context, mouseX, mouseY, itemsToDisplay, 
                                                    ceiHeight, itemsListStartY, ceiWidth, ceiX, ceiY, textRenderer,
                                                    animationSlideOffset, animationAlpha);
        
        if (hovered != null) {
            hoveredStack = hovered;
            isRecipePopupVisible = true;
        }
        
        // Vérifier si on doit afficher la popup d'aide (une seule fois)
        if (!hasCheckedHelpPopup) {
            helpPopupRenderer.checkShouldShow();
            hasCheckedHelpPopup = true;
        }
        
        // Rendre la popup APRÈS tout le reste
        if (isRecipePopupVisible && hoveredStack != null) {
            context.pose().pushPose();
            context.pose().translate(0, 0, 1000); // Z-index élevé pour être au-dessus
            
            recipePopupRenderer.render(context, mouseX, mouseY, hoveredStack, 
                                     screenWidth, screenHeight,
                                     recipeManager,
                                     registryManager,
                                     textRenderer,
                                     ceiX, ceiWidth);
            
            context.pose().popPose();
        }
        
        // Rendre la popup d'aide (z-index très élevé)
        if (helpPopupRenderer.isVisible()) {
            context.pose().pushPose();
            context.pose().translate(0, 0, 2000);
            helpPopupRenderer.render(context, screenWidth, screenHeight, textRenderer);
            context.pose().popPose();
        }
        
        // Réinitialisation de l'état si aucun item n'est survolé
        if (!itemListRenderer.isAnyItemHovered(mouseX, mouseY, itemsToDisplay, ceiHeight, itemsListStartY, ceiWidth, ceiX, ceiY, animationSlideOffset)) {
            isRecipePopupVisible = false;
            // Ne pas mettre hoveredStack à null immédiatement pour éviter que l'animation redémarre
            // Le hoveredStack sera mis à null au prochain render si toujours pas survolé
            if (hoveredStack != null) {
                // Vérifier une dernière fois si vraiment pas survolé avant de nettoyer
                hoveredStack = null;
            }
        }
    }
    
    /**
     * Gère le scroll de la souris.
     * 
     * @return true si le scroll a été géré, false sinon
     */
    public boolean handleMouseScroll(double mouseX, double mouseY, double verticalAmount, 
                                    int screenWidth, int screenHeight, Font textRenderer, float animationSlideOffset) {
        int ceiHeight = panelRenderer.getCeiHeight(screenHeight, screenWidth);
        int ceiWidth = panelRenderer.getCeiWidth(screenWidth, screenHeight);
        int ceiX = panelRenderer.getCeiX(screenWidth);
        int ceiY = panelRenderer.getCeiY();
        int itemsListStartY = panelRenderer.getItemsListStartY(textRenderer);
        List<ItemStack> itemsToDisplay = getFilteredItems();
        
        return itemListRenderer.handleScroll(mouseX, mouseY, verticalAmount, itemsToDisplay, 
                                            ceiHeight, itemsListStartY, ceiWidth, ceiX, ceiY, animationSlideOffset);
    }
    
    /**
     * Gère le clic de la souris.
     * 
     * @return true si le clic a été géré, false sinon
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button, 
                                   int screenWidth, int screenHeight, Font textRenderer) {
        int ceiWidth = panelRenderer.getCeiWidth(screenWidth, screenHeight);
        int ceiX = panelRenderer.getCeiX(screenWidth);
        int ceiY = panelRenderer.getCeiY();
        int ceiHeight = panelRenderer.getCeiHeight(screenHeight, screenWidth);
        int itemsListStartY = panelRenderer.getItemsListStartY(textRenderer);
        List<ItemStack> itemsToDisplay = getFilteredItems();
        
        // PRIORITÉ 1: Vérifier le clic sur le bouton fermer de la popup d'aide
        if (helpPopupRenderer.isCloseButtonClicked((int) mouseX, (int) mouseY, screenWidth, screenHeight)) {
            helpPopupRenderer.close();
            return true;
        }
        
        // PRIORITÉ 2: Vérifier Shift + Click sur un item pour ajouter/retirer des favoris
        // PRIORITÉ 3: Vérifier le clic sur un item pour placement automatique dans la table de craft
        // Chercher quel item est cliqué
        ItemStack clickedItem = null;
        int columns = (ceiWidth - 2 * GuiConstants.PADDING) / GuiConstants.SLOT_SIZE;
        int ceiBottom = ceiY + ceiHeight;
        int availableHeight = ceiBottom - itemsListStartY;
        if (availableHeight < 0) availableHeight = 0;
        int maxVisibleRows = availableHeight / GuiConstants.SLOT_SIZE;
        int maxVisibleItems = columns * maxVisibleRows;
        int startIndex = itemListRenderer.getScrollOffset() * columns;
        
        for (int i = startIndex; i < Math.min(itemsToDisplay.size(), startIndex + maxVisibleItems); i++) {
            ItemStack stack = itemsToDisplay.get(i);
            int relativeIndex = i - startIndex;
            int x = ceiX + GuiConstants.PADDING + (relativeIndex % columns) * GuiConstants.SLOT_SIZE;
            int y = itemsListStartY + (relativeIndex / columns) * GuiConstants.SLOT_SIZE;
            if (GuiRenderHelper.isMouseOver((int) mouseX, (int) mouseY, x, y, GuiConstants.SLOT_SIZE, GuiConstants.SLOT_SIZE)) {
                clickedItem = stack;
                break;
            }
        }
        
        if (clickedItem != null && (button == 0 || button == 1)) {
            Minecraft client = Minecraft.getInstance();
            
            // PRIORITÉ 2: Si Shift est maintenu, toggle le favori
            if (client != null && client.screen != null && 
                net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                FavoriteItemsManager favoriteManager = FavoriteItemsManager.getInstance();
                // Utiliser la méthode qui prend en compte les Data Components
                favoriteManager.toggleFavorite(clickedItem);
                itemListRenderer.triggerFavoriteToggleAnimation(clickedItem);
                filteredItemsCache = null; // Invalider le cache pour recalculer
                // Fermer la popup d'aide si elle est visible
                if (helpPopupRenderer.isVisible()) {
                    helpPopupRenderer.close();
                }
                return true;
            }
            
            // PRIORITÉ 3: Si on est dans une table de craft, placer automatiquement les ingrédients
            if (client != null && client.screen != null && 
                client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handledScreen) {
                
                if (handledScreen.getMenu() instanceof net.minecraft.world.inventory.CraftingMenu craftingHandler) {
                    if (client.player != null) {
                        // Trouver une recette de crafting pour cet item
                        var recipeManager = client.player.level().getRecipeManager();
                        var registryManager = client.player.level().registryAccess();
                        
                        // Chercher une recette de crafting qui produit cet item
                        var craftingRecipes = recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
                        Recipe<?> foundRecipe = null;
                        
                        for (var recipeEntry : craftingRecipes) {
                            Recipe<?> recipe = recipeEntry.value();
                            if (recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe craftingRecipe) {
                                ItemStack result = craftingRecipe.getResultItem(registryManager);
                                if (result.is(clickedItem.getItem())) {
                                    foundRecipe = recipe;
                                    break;
                                }
                            }
                        }
                        
                        if (foundRecipe != null) {
                            // Clic gauche = 1 item, clic droit = stack maximum
                            int quantity = (button == 1) ? -1 : 1; // -1 = maximum, 1 = un seul
                            
                            com.ceketrum.cei.gui.module.cei.util.CraftingHelper.placeRecipeIngredients(
                                craftingHandler,
                                foundRecipe,
                                registryManager,
                                client.player,
                                quantity
                            );
                            
                            return true;
                        }
                    }
                }
            }
        }
        
        // PRIORITÉ 4: Vérifier le clic sur le bouton paramètres
        if (panelRenderer.isSettingsButtonClicked((int) mouseX, (int) mouseY, ceiX, ceiY, ceiWidth)) {
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) {
                client.setScreen(new CeiConfigScreen(client.screen));
            }
            return true;
        }
        
        // PRIORITÉ 5: Vérifier le clic sur le bouton toggle favoris
        if (panelRenderer.isFavoritesButtonClicked((int) mouseX, (int) mouseY, ceiX, ceiY, ceiWidth, textRenderer)) {
            panelRenderer.toggleFavoritesFilter();
            filteredItemsCache = null; // Invalider le cache pour recalculer
            itemListRenderer.resetScroll();
            return true;
        }
        
        // Gérer le clic sur la barre de recherche
        int searchBarX = ceiX + 5;
        int searchBarY = ceiY + 5 + textRenderer.lineHeight + 3;
        int searchBarWidth = ceiWidth - 10;
        
        boolean clickedOnSearchBar = panelRenderer.getSearchBar().mouseClicked(mouseX, mouseY, button, searchBarX, searchBarY, searchBarWidth);
        
        // Si on clique en dehors de la barre de recherche et qu'elle est focusée, la défocuser
        if (!clickedOnSearchBar && panelRenderer.getSearchBar().isFocused()) {
            panelRenderer.getSearchBar().setFocused(false);
        }
        
        // Si on a cliqué sur un item avec Shift, on a déjà retourné true plus haut
        // Sinon, retourner si on a cliqué sur la barre de recherche
        return clickedOnSearchBar;
    }
    
    /**
     * Gère la saisie de caractères.
     * 
     * @return true si le caractère a été géré, false sinon
     */
    public boolean handleCharTyped(char chr, int modifiers) {
        // Si la barre de recherche est focusée, intercepter TOUS les caractères
        if (panelRenderer.getSearchBar().isFocused()) {
            if (panelRenderer.getSearchBar().charTyped(chr, modifiers)) {
                return true; // Caractère géré par la barre de recherche
            }
            // Même si le caractère n'est pas géré, on intercepte pour éviter qu'il soit traité par le jeu
            return true;
        }
        return false;
    }
    
    /**
     * Gère l'appui sur une touche.
     * 
     * @return true si la touche a été gérée, false sinon
     */
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Si la barre de recherche est focusée, intercepter TOUTES les touches (sauf Escape)
        if (panelRenderer.getSearchBar().isFocused()) {
            // Échap pour quitter le focus de la barre de recherche
            if (keyCode == 256) { // GLFW_KEY_ESCAPE
                panelRenderer.getSearchBar().setFocused(false);
                return true;
            }
            
            // Gérer les touches spéciales dans la barre de recherche
            if (panelRenderer.getSearchBar().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            
            // Intercepter toutes les autres touches pour éviter qu'elles soient traitées par le jeu
            return true;
        }
        return false;
    }
    
    /**
     * Récupère tous les items du jeu, y compris les variantes avec Data Components.
     * Utilise un cache pour éviter de recréer la liste à chaque frame.
     */
    private List<ItemStack> getAllItems() {
        if (allItemsCache == null) {
            allItemsCache = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                try {
                    // Utiliser ItemVariantGenerator pour générer toutes les variantes de l'item
                    // Cela inclut les variantes avec Data Components (ex: potions avec différents effets)
                    List<ItemStack> variants = com.ceketrum.cei.gui.module.cei.util.ItemVariantGenerator.generateItemVariants(item);
                    for (ItemStack stack : variants) {
                        // Vérifier si la stack est valide et non vide
                        if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                            allItemsCache.add(stack);
                        }
                    }
                } catch (Exception e) {
                    // Si la génération échoue, ignorer cet item (ne devrait pas arriver normalement)
                    // Cela peut arriver pour certains items avec des constructeurs spéciaux
                }
            }
        }
        return allItemsCache;
    }
    
    /**
     * Récupère la liste d'items filtrée selon le texte de recherche et les favoris.
     * Utilise un cache pour éviter de refiltrer à chaque frame si le texte n'a pas changé.
     */
    private List<ItemStack> getFilteredItems() {
        String currentSearchText = panelRenderer.getSearchBar().getSearchText();
        boolean showFavoritesOnly = panelRenderer.isShowFavoritesOnly();
        
        if (filteredItemsCache == null || !currentSearchText.equals(lastSearchText)) {
            List<ItemStack> allItems = getAllItems();
            filteredItemsCache = ItemFilter.filterItems(allItems, currentSearchText);
            
            // Filtrer par favoris si nécessaire
            if (showFavoritesOnly) {
                FavoriteItemsManager favoriteManager = FavoriteItemsManager.getInstance();
                filteredItemsCache = filteredItemsCache.stream()
                    .filter(stack -> favoriteManager.isFavorite(stack))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            lastSearchText = currentSearchText;
        }
        
        return filteredItemsCache;
    }
    
    /**
     * Ferme la popup d'aide.
     */
    public void closeHelpPopup() {
        helpPopupRenderer.close();
    }
    
    /**
     * Retourne le panneau CEI pour accéder à ses méthodes publiques.
     */
    public CeiPanelRenderer getPanelRenderer() {
        return panelRenderer;
    }

    /**
     * Retourne l'item actuellement survolé dans la liste CEI.
     */
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }
}



