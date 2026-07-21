package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Renderer spécialisé pour les recettes de crafting.
 */
public class CraftingRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(GuiGraphics context, int startX, int startY,
                     Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                     RegistryAccess dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.gui.Font textRenderer) {
        RecipeLayout layout = RecipeDisplayHelper.getRecipeLayout(recipe, recipeEntry, dynamicRegistryManager);
        int gridWidthPx = layout.width * GuiConstants.SLOT_SIZE;
        int gridHeightPx = layout.height * GuiConstants.SLOT_SIZE;
        
        // Centre la grille dans la popup
        int gridStartX = startX + (GuiConstants.POPUP_WIDTH - gridWidthPx) / 2;
        
        // Affichage de la grille d'ingrédients
        for (int row = 0; row < layout.height; row++) {
            for (int col = 0; col < layout.width; col++) {
                int x = gridStartX + col * GuiConstants.SLOT_SIZE;
                int y = startY + row * GuiConstants.SLOT_SIZE;
                
                // Fond du slot
                context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF404040);
                context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + 1, 0x44CCCCCC);
                context.fill(x, y, x + 1, y + GuiConstants.SLOT_SIZE, 0x44CCCCCC);
                
                int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
                ItemStack ingredientStack = layout.ingredients[row][col];
                if (!ingredientStack.isEmpty()) {
                    context.renderItem(ingredientStack, x + offset, y + offset);
                }
            }
        }
        
        int currentY = startY + gridHeightPx + 5;
        
        // Barre de séparation entre la recette et la description
        String description = layout.description;
        if (description == null || description.isEmpty()) {
            description = itemDescription;
        }
        if (!description.isEmpty()) {
            context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, 0xFF808080);
            currentY += 8; // Espace après la barre
        }
        
        // Affichage de la zone de description de la recette
        if (!description.isEmpty()) {
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int indent = 4;
            TextRenderHelper.drawWrappedText(context, description, startX + 10, currentY, descMaxWidth, 0xCCCCCC, scale, indent, textRenderer);
        }
        
        return currentY;
    }
    
    /**
     * Vérifie si un clic est sur la grille de recette.
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param startX Position X de départ de la recette
     * @param startY Position Y de départ de la recette
     * @param layout Le layout de la recette
     * @return true si le clic est sur la grille, false sinon
     */
    public static boolean isRecipeClicked(int mouseX, int mouseY, int startX, int startY, RecipeLayout layout) {
        int gridWidthPx = layout.width * GuiConstants.SLOT_SIZE;
        int gridHeightPx = layout.height * GuiConstants.SLOT_SIZE;
        int gridStartX = startX + (GuiConstants.POPUP_WIDTH - gridWidthPx) / 2;
        
        return mouseX >= gridStartX && mouseX < gridStartX + gridWidthPx &&
               mouseY >= startY && mouseY < startY + gridHeightPx;
    }
    
    /**
     * Vérifie si un clic est sur l'item de résultat.
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param startX Position X de départ de la recette
     * @param startY Position Y de départ de la recette
     * @param layout Le layout de la recette
     * @return true si le clic est sur l'item de résultat, false sinon
     */
    public static boolean isResultClicked(int mouseX, int mouseY, int startX, int startY, RecipeLayout layout) {
        int gridWidthPx = layout.width * GuiConstants.SLOT_SIZE;
        int gridStartX = startX + (GuiConstants.POPUP_WIDTH - gridWidthPx) / 2;
        int arrowStartX = gridStartX + gridWidthPx + 5;
        int arrowLength = 15;
        int arrowHeadSize = 5;
        int resultX = arrowStartX + arrowLength + arrowHeadSize + 6;
        
        return mouseX >= resultX && mouseX < resultX + GuiConstants.SLOT_SIZE &&
               mouseY >= startY && mouseY < startY + GuiConstants.SLOT_SIZE;
    }
    
}



