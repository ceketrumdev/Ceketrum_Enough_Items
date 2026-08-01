package com.ceketrum.cei.gui.module.cei.recipe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Gère le rendu des recettes en utilisant les renderers spécialisés.
 * Cette classe délègue le rendu aux classes spécialisées pour chaque type de recette.
 */
public class RecipeRenderer {
    private final CraftingRecipeRenderer craftingRenderer = new CraftingRecipeRenderer();
    private final SmeltingRecipeRenderer smeltingRenderer = new SmeltingRecipeRenderer();
    private final SmithingRecipeRenderer smithingRenderer = new SmithingRecipeRenderer();
    private final BrewingRecipeRenderer brewingRenderer = new BrewingRecipeRenderer();
    private final StonecutterRecipeRenderer stonecutterRenderer = new StonecutterRecipeRenderer();
    private final CustomMachineRecipeRenderer customMachineRenderer = new CustomMachineRecipeRenderer();
    
    /**
     * Rend une recette de type crafting.
     * Si une recette de cuisson est associée, on l'affiche en dessous.
     */
    public int renderCraftingRecipe(GuiGraphicsExtractor context, int startX, int startY, 
                                    Recipe<?> recipe, RecipeHolder<?> recipeEntry, 
                                    RegistryAccess dynamicRegistryManager, 
                                    RecipeHolder<?> smeltingRecipeEntry,
                                    ItemStack hoveredStack,
                                    String itemDescription,
                                    net.minecraft.client.gui.Font textRenderer) {
        int currentY = craftingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                              dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
        
        // Si une recette de cuisson est associée, l'afficher en dessous
        if (smeltingRecipeEntry != null) {
            currentY += 5;
            currentY = smeltingRenderer.render(context, startX, currentY, smeltingRecipeEntry.value(), 
                                               smeltingRecipeEntry, dynamicRegistryManager, 
                                               hoveredStack, "", textRenderer);
        }
        
        return currentY;
    }
    
    /**
     * Rend une recette spéciale (de type cuisson seule).
     */
    public int renderSpecialRecipe(GuiGraphicsExtractor context, int startX, int startY, 
                                  Recipe<?> recipe, RecipeHolder<?> recipeEntry, 
                                  RegistryAccess dynamicRegistryManager,
                                  ItemStack hoveredStack,
                                  String itemDescription,
                                  net.minecraft.client.gui.Font textRenderer) {
        return smeltingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                      dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type smithing (smithing table).
     */
    public int renderSmithingRecipe(GuiGraphicsExtractor context, int startX, int startY,
                                   Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                                   RegistryAccess dynamicRegistryManager,
                                   ItemStack hoveredStack,
                                   String itemDescription,
                                   net.minecraft.client.gui.Font textRenderer) {
        return smithingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                      dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type brewing (alambic).
     */
    public int renderBrewingRecipe(GuiGraphicsExtractor context, int startX, int startY,
                                  Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                                  RegistryAccess dynamicRegistryManager,
                                  ItemStack hoveredStack,
                                  String itemDescription,
                                  net.minecraft.client.gui.Font textRenderer) {
        return brewingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                     dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de brewing depuis des données (ItemStack) plutôt que depuis un Recipe.
     */
    public int renderBrewingRecipeFromData(GuiGraphicsExtractor context, int startX, int startY,
                                           ItemStack inputPotion, ItemStack ingredient, ItemStack outputPotion,
                                           ItemStack hoveredStack, String itemDescription,
                                           net.minecraft.client.gui.Font textRenderer) {
        return brewingRenderer.renderFromData(context, startX, startY, inputPotion, ingredient, outputPotion,
                                             hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type stonecutter (tailleur de pierre).
     */
    public int renderStonecutterRecipe(GuiGraphicsExtractor context, int startX, int startY,
                                      Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                                      RegistryAccess dynamicRegistryManager,
                                      ItemStack hoveredStack,
                                      String itemDescription,
                                      net.minecraft.client.gui.Font textRenderer) {
        return stonecutterRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                         dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette personnalisée de machine moddée.
     */
    public int renderCustomMachineRecipe(GuiGraphicsExtractor context, int startX, int startY,
                                         Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                                         RegistryAccess dynamicRegistryManager,
                                         ItemStack hoveredStack,
                                         String itemDescription,
                                         net.minecraft.client.gui.Font textRenderer) {
        return customMachineRenderer.render(context, startX, startY, recipe, recipeEntry,
                                           dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
}


