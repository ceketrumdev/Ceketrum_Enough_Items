package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;

/**
 * Renderer spécialisé pour les recettes de stonecutter (tailleur de pierre).
 */
public class StonecutterRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(DrawContext context, int startX, int startY,
                     Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                     DynamicRegistryManager dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.font.TextRenderer textRenderer) {
        // Afficher un header traduisible pour le stonecutter
        String title = Text.translatable("recipe.cei.stonecutting.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20;
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.getWidth(displayTitle);
        context.drawText(textRenderer, Text.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFF, false);
        
        int currentY = startY + 12;
        currentY = drawStonecutterBlock(context, startX, currentY, recipe, dynamicRegistryManager);
        currentY += GuiConstants.SLOT_SIZE + 5;
        
        // Barre de séparation entre la recette et la description
        if (!itemDescription.isEmpty() || !RecipeDisplayHelper.getRecipeDescription(recipeEntry).isEmpty()) {
            context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, 0xFF808080);
            currentY += 8;
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
     * Dessine le bloc de stonecutter (input, flèche, output) centré dans la popup.
     * @return La position Y après le bloc
     */
    private int drawStonecutterBlock(DrawContext context, int startX, int startY, 
                                     Recipe<?> recipe, DynamicRegistryManager dynamicRegistryManager) {
        // Largeur avec espacement : input (18) + gap (6) + flèche (16) + tête (4) + gap (6) + output (18) = 74
        int blockWidth = 74;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        // Affichage de l'input
        var ingredients = recipe.getIngredients();
        if (ingredients != null && ingredients.size() > 0) {
            Ingredient ingredient = ingredients.get(0);
            ItemStack[] possibleItems = ingredient.getMatchingStacks();
            if (possibleItems.length > 0) {
                ItemStack inputStack = possibleItems[0];
                context.drawItem(inputStack, blockStartX, startY);
            }
        }
        
        // Affichage de la flèche (sans animation, contrairement à smelting)
        int arrowStartX = blockStartX + GuiConstants.SLOT_SIZE + 6;
        int arrowCenterY = startY + GuiConstants.SLOT_SIZE / 2;
        int arrowLength = 16;
        
        // Corps de la flèche (ligne horizontale)
        int arrowBodyY = arrowCenterY;
        context.fill(arrowStartX, arrowBodyY - 1, arrowStartX + arrowLength, arrowBodyY + 1, 0xFFFFFFFF);
        
        // Tête de flèche (triangle pointant vers la droite)
        int arrowHeadX = arrowStartX + arrowLength;
        int arrowHeadSize = 4;
        int arrowTipX = arrowHeadX + arrowHeadSize;
        // Ligne supérieure du triangle
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowBodyY - i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        // Ligne inférieure du triangle
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowBodyY + i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        
        // Affichage de l'output
        ItemStack output = recipe.getResult(dynamicRegistryManager);
        context.drawItem(output, arrowStartX + arrowLength + arrowHeadSize + 6, startY);
        
        return startY + GuiConstants.SLOT_SIZE;
    }
}




