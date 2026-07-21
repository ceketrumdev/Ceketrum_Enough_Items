package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;

/**
 * Renderer spécialisé pour les recettes de smelting (cuisson).
 */
public class SmeltingRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(DrawContext context, int startX, int startY,
                     Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                     DynamicRegistryManager dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.font.TextRenderer textRenderer) {
        // Afficher un header traduisible pour la cuisson
        String title = Text.translatable("recipe.cei.cooking.header").getString();
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, Text.translatable("recipe.cei.cooking.header"), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFF, false);
        
        int currentY = startY + 12;
        currentY = drawCookingArrowBlock(context, startX, currentY, recipeEntry, dynamicRegistryManager);
        currentY += GuiConstants.SLOT_SIZE + 5;
        
        // Barre de séparation entre la recette et la description
        if (!itemDescription.isEmpty() || !RecipeDisplayHelper.getRecipeDescription(recipeEntry).isEmpty()) {
            context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, 0xFF808080);
            currentY += 8; // Espace après la barre
        }
        
        // Affichage de la description de la recette
        String description = RecipeDisplayHelper.getRecipeDescription(recipeEntry);
        if (description.isEmpty()) {
            description = itemDescription;
        }
        
        if (!description.isEmpty()) {
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int indent = 4;
            TextRenderHelper.drawWrappedText(context, description, startX + 10, currentY, descMaxWidth, 0xCCCCCC, scale, indent, textRenderer);
        }
        
        return currentY;
    }
    
    /**
     * Dessine le bloc de cuisson (input, flèche animée, output) centré dans la popup.
     * @return La position Y après le bloc
     */
    private int drawCookingArrowBlock(DrawContext context, int startX, int startY, 
                                     RecipeEntry<?> smeltingRecipeEntry, DynamicRegistryManager dynamicRegistryManager) {
        // Largeur avec espacement : input (18) + gap (6) + flèche (16) + tête (4) + gap (6) + output (18) = 74
        int blockWidth = 74;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        Recipe<?> smeltingRecipe = smeltingRecipeEntry.value();
        
        // Affichage de l'input de cuisson
        Ingredient ingredient = smeltingRecipe.getIngredients().get(0);
        ItemStack[] possibleItems = ingredient.getMatchingStacks();
        if (possibleItems.length > 0) {
            ItemStack rawStack = possibleItems[0];
            context.drawItem(rawStack, blockStartX, startY);
        }
        
        // Affichage de la flèche animée de cuisson avec remplissage progressif (vraie flèche)
        int arrowStartX = blockStartX + GuiConstants.SLOT_SIZE + 6; // Espacement augmenté
        int arrowCenterY = startY + GuiConstants.SLOT_SIZE / 2; // Centre vertical du slot
        int arrowLength = 16; // Longueur de la flèche
        int arrowHeight = 4; // Hauteur pour l'animation de remplissage
        
        // Animation de remplissage (cycle de 0 à 100%)
        long currentTime = System.currentTimeMillis();
        double fillProgress = (currentTime % 2000) / 2000.0; // Cycle de 2 secondes
        
        // Fond de la flèche (gris foncé) - corps rectangulaire
        int arrowBodyY = arrowCenterY - arrowHeight / 2;
        context.fill(arrowStartX, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF555555);
        
        // Partie remplie de la flèche (blanc/orange qui se remplit progressivement)
        int filledWidth = (int)(arrowLength * fillProgress);
        if (filledWidth > 0) {
            // Couleur qui change du blanc au orange selon le progrès
            int fillColor = 0xFFFFFFFF; // Blanc
            if (fillProgress > 0.5) {
                // Mélange blanc-orange pour la deuxième moitié
                float orangeFactor = (float)((fillProgress - 0.5) * 2);
                int r = (int)(255 * (1 - orangeFactor * 0.3));
                int g = (int)(255 * (1 - orangeFactor * 0.5));
                int b = (int)(255 * (1 - orangeFactor * 0.7));
                fillColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
            context.fill(arrowStartX, arrowBodyY, arrowStartX + filledWidth, arrowBodyY + arrowHeight, fillColor);
        }
        
        // Bordure de la flèche (corps)
        context.fill(arrowStartX, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + 1, 0xFF000000);
        context.fill(arrowStartX, arrowBodyY + arrowHeight - 1, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF000000);
        context.fill(arrowStartX, arrowBodyY, arrowStartX + 1, arrowBodyY + arrowHeight, 0xFF000000);
        context.fill(arrowStartX + arrowLength - 1, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF000000);
        
        // Tête de flèche (triangle pointant vers la droite) - vraie flèche
        int arrowHeadX = arrowStartX + arrowLength;
        int arrowHeadSize = 4; // Taille de la tête de flèche
        // Dessiner un triangle pointant vers la droite
        // La pointe est à droite, les lignes partent de la pointe vers la gauche
        int arrowTipX = arrowHeadX + arrowHeadSize; // Pointe à droite
        // Ligne supérieure du triangle (de la pointe vers le haut-gauche)
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowCenterY - i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        // Ligne inférieure du triangle (de la pointe vers le bas-gauche)
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowCenterY + i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        
        // Affichage de l'output cuisiné (avec espacement après la flèche)
        ItemStack output;
        if (smeltingRecipe instanceof SmeltingRecipe smelting) {
            output = smelting.getResult(dynamicRegistryManager);
        } else {
            output = ItemStack.EMPTY;
        }
        context.drawItem(output, arrowStartX + arrowLength + arrowHeadSize + 6, startY); // Espacement de 6 pixels après la flèche
        
        return startY + GuiConstants.SLOT_SIZE;
    }
}




