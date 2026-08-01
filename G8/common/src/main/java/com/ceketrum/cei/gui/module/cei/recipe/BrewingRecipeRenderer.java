package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Renderer spécialisé pour les recettes de brewing (alambic).
 */
public class BrewingRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(GuiGraphicsExtractor context, int startX, int startY,
                     Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                     RegistryAccess dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.gui.Font textRenderer) {
        // Les recettes de brewing n'ont pas de classe dédiée dans Minecraft 1.21.1
        // On utilise directement la Recipe générique
        
        // Afficher un header traduisible pour l'alambic
        String title = Component.translatable("recipe.cei.brewing.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20;
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.width(displayTitle);
        context.text(textRenderer, Component.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFFFF, false);
        
        int currentY = startY + 12;
        
        // Dessiner le bloc de brewing (input potion + ingredient → output potion)
        currentY = drawBrewingBlock(context, startX, currentY, recipe, dynamicRegistryManager, textRenderer);
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
     * Rend une recette de brewing depuis des données (ItemStack) plutôt que depuis un Recipe.
     */
    public int renderFromData(GuiGraphicsExtractor context, int startX, int startY,
                              ItemStack inputPotion, ItemStack ingredient, ItemStack outputPotion,
                              ItemStack hoveredStack, String itemDescription,
                              net.minecraft.client.gui.Font textRenderer) {
        // Afficher un header traduisible pour l'alambic
        String title = Component.translatable("recipe.cei.brewing.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20;
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.width(displayTitle);
        context.text(textRenderer, Component.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFFFF, false);
        
        int currentY = startY + 12;
        
        // Dessiner le bloc de brewing (input potion + ingredient → output potion)
        currentY = drawBrewingBlockFromData(context, startX, currentY, inputPotion, ingredient, outputPotion, textRenderer);
        currentY += GuiConstants.SLOT_SIZE + 5;
        
        // Barre de séparation entre la recette et la description
        if (!itemDescription.isEmpty()) {
            context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, 0xFF808080);
            currentY += 8;
        }
        
        if (!itemDescription.isEmpty()) {
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int indent = 4;
            TextRenderHelper.drawWrappedText(context, itemDescription, startX + 10, currentY, descMaxWidth, 0xCCCCCC, scale, indent, textRenderer);
        }
        
        return currentY;
    }
    
    /**
     * Dessine le bloc de brewing (input potion, ingredient, flèche, output potion) centré dans la popup.
     * Format : [Input Potion] + [Ingredient] → [Output Potion]
     * @return La position Y après le bloc
     */
    private int drawBrewingBlock(GuiGraphicsExtractor context, int startX, int startY,
                                 Recipe<?> recipe, RegistryAccess dynamicRegistryManager,
                                 net.minecraft.client.gui.Font textRenderer) {
        // Largeur avec espacement : input (18) + gap (2) + plus (4) + gap (2) + ingredient (18) + gap (6) + flèche (16) + tête (4) + gap (6) + output (18) = 100
        int blockWidth = 100;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        // Récupérer les ingrédients
        ItemStack inputStack = getInputIngredient(recipe);
        ItemStack ingredientStack = getIngredient(recipe);
        ItemStack outputStack = getOutput(recipe, dynamicRegistryManager);
        
        // Dessiner le slot d'input (potion de base)
        int inputX = blockStartX;
        drawSlot(context, inputX, startY);
        if (!inputStack.isEmpty()) {
            drawItemInSlot(context, inputStack, inputX, startY);
        }
        
        // Dessiner le signe "+" entre input et ingredient
        int plusX = inputX + GuiConstants.SLOT_SIZE + 2;
        int plusY = startY + GuiConstants.SLOT_SIZE / 2;
        context.fill(plusX, plusY - 1, plusX + 4, plusY + 1, 0xFFFFFFFF);
        context.fill(plusX + 1, plusY - 2, plusX + 3, plusY + 2, 0xFFFFFFFF);
        
        // Dessiner le slot d'ingredient
        int ingredientX = plusX + 6;
        drawSlot(context, ingredientX, startY);
        if (!ingredientStack.isEmpty()) {
            drawItemInSlot(context, ingredientStack, ingredientX, startY);
        }
        
        // Dessiner une vraie flèche vers le résultat
        int arrowStartX = ingredientX + GuiConstants.SLOT_SIZE + 6;
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
        
        // Dessiner le slot de résultat
        int resultX = arrowStartX + arrowLength + arrowHeadSize + 6;
        drawSlot(context, resultX, startY);
        if (!outputStack.isEmpty()) {
            drawItemInSlot(context, outputStack, resultX, startY);
        }
        
        return startY + GuiConstants.SLOT_SIZE;
    }
    
    /**
     * Dessine le bloc de brewing depuis des données (ItemStack) plutôt que depuis un Recipe.
     */
    private int drawBrewingBlockFromData(GuiGraphicsExtractor context, int startX, int startY,
                                         ItemStack inputPotion, ItemStack ingredient, ItemStack outputPotion,
                                         net.minecraft.client.gui.Font textRenderer) {
        // Largeur avec espacement : input (18) + gap (2) + plus (4) + gap (2) + ingredient (18) + gap (6) + flèche (16) + tête (4) + gap (6) + output (18) = 100
        int blockWidth = 100;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        // Dessiner le slot d'input (potion de base)
        int inputX = blockStartX;
        drawSlot(context, inputX, startY);
        if (!inputPotion.isEmpty()) {
            drawItemInSlot(context, inputPotion, inputX, startY);
        }
        
        // Dessiner le signe "+" entre input et ingredient
        int plusX = inputX + GuiConstants.SLOT_SIZE + 2;
        int plusY = startY + GuiConstants.SLOT_SIZE / 2;
        context.fill(plusX, plusY - 1, plusX + 4, plusY + 1, 0xFFFFFFFF);
        context.fill(plusX + 1, plusY - 2, plusX + 3, plusY + 2, 0xFFFFFFFF);
        
        // Dessiner le slot d'ingredient
        int ingredientX = plusX + 6;
        drawSlot(context, ingredientX, startY);
        if (!ingredient.isEmpty()) {
            drawItemInSlot(context, ingredient, ingredientX, startY);
        }
        
        // Dessiner une vraie flèche vers le résultat
        int arrowStartX = ingredientX + GuiConstants.SLOT_SIZE + 6;
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
        
        // Dessiner le slot de résultat
        int resultX = arrowStartX + arrowLength + arrowHeadSize + 6;
        drawSlot(context, resultX, startY);
        if (!outputPotion.isEmpty()) {
            drawItemInSlot(context, outputPotion, resultX, startY);
        }
        
        return startY + GuiConstants.SLOT_SIZE;
    }
    
    /**
     * Récupère l'ingrédient d'input (la potion de base).
     */
    private ItemStack getInputIngredient(Recipe<?> recipe) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);
            var displays = recipe.display();
            if (!displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                var ingredients = getRecipeIngredients(display);
                if (ingredients.size() > 0) {
                    ItemStack res = ingredients.get(0).resolveForFirstStack(contextMap);
                    if (res != null) return res;
                }
            }
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Récupère l'ingrédient (le matériau de brewing).
     */
    private ItemStack getIngredient(Recipe<?> recipe) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);
            var displays = recipe.display();
            if (!displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                var ingredients = getRecipeIngredients(display);
                if (ingredients.size() > 1) {
                    ItemStack res = ingredients.get(1).resolveForFirstStack(contextMap);
                    if (res != null) return res;
                }
            }
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Récupère le résultat de la recette de brewing.
     */
    private ItemStack getOutput(Recipe<?> recipe, RegistryAccess dynamicRegistryManager) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);
            var displays = recipe.display();
            if (!displays.isEmpty()) {
                ItemStack res = displays.get(0).result().resolveForFirstStack(contextMap);
                if (res != null) return res;
            }
        }
        return ItemStack.EMPTY;
    }

    private static java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> getRecipeIngredients(net.minecraft.world.item.crafting.display.RecipeDisplay display) {
        if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
            return shaped.ingredients();
        } else if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients();
        } else if (display instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace) {
            return java.util.List.of(furnace.ingredient());
        } else if (display instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay stonecutter) {
            return java.util.List.of(stonecutter.input());
        } else if (display instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay smithing) {
            return java.util.List.of(smithing.template(), smithing.base(), smithing.addition());
        }
        return java.util.List.of();
    }
    
    /**
     * Dessine un slot vide.
     */
    private void drawSlot(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF404040);
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + 1, 0x44CCCCCC);
        context.fill(x, y, x + 1, y + GuiConstants.SLOT_SIZE, 0x44CCCCCC);
    }
    
    /**
     * Dessine un item dans un slot.
     */
    private void drawItemInSlot(GuiGraphicsExtractor context, ItemStack stack, int slotX, int slotY) {
        int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
        context.item(stack, slotX + offset, slotY + offset);
    }
}
