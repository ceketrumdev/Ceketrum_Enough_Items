package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Renderer spécialisé pour les recettes de stonecutter (tailleur de pierre).
 */
public class StonecutterRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(GuiGraphicsExtractor context, int startX, int startY,
                     Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                     RegistryAccess dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.gui.Font textRenderer) {
        // Afficher un header traduisible pour le stonecutter
        String title = Component.translatable("recipe.cei.stonecutting.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20;
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.width(displayTitle);
        context.text(textRenderer, Component.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFFFF, false);
        
        int currentY = startY + 12;
        currentY = drawStonecutterBlock(context, startX, currentY, recipe);
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
    private int drawStonecutterBlock(GuiGraphicsExtractor context, int startX, int startY, 
                                     Recipe<?> recipe) {
        // Largeur avec espacement : input (18) + gap (6) + flèche (16) + tête (4) + gap (6) + output (18) = 74
        int blockWidth = 74;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        ItemStack inputStack = ItemStack.EMPTY;
        ItemStack outputStack = ItemStack.EMPTY;
        
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);
            var displays = recipe.display();
            if (!displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                outputStack = display.result().resolveForFirstStack(contextMap);
                if (display instanceof StonecutterRecipeDisplay stonecutter) {
                    inputStack = stonecutter.input().resolveForFirstStack(contextMap);
                }
            }
        }
        
        // Affichage de l'input
        if (inputStack != null && !inputStack.isEmpty()) {
            context.item(inputStack, blockStartX, startY);
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
        context.item(outputStack != null ? outputStack : ItemStack.EMPTY, arrowStartX + arrowLength + arrowHeadSize + 6, startY);
        
        return startY + GuiConstants.SLOT_SIZE;
    }
}
