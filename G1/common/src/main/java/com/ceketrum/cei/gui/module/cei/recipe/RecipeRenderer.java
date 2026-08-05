package com.ceketrum.cei.gui.module.cei.recipe;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.registry.DynamicRegistryManager;

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
    public int renderCraftingRecipe(DrawContext context, int startX, int startY, 
                                    Recipe<?> recipe, RecipeEntry<?> recipeEntry, 
                                    DynamicRegistryManager dynamicRegistryManager, 
                                    RecipeEntry<?> smeltingRecipeEntry,
                                    ItemStack hoveredStack,
                                    String itemDescription,
                                    net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererCrafting()) return startY;

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
    public int renderSpecialRecipe(DrawContext context, int startX, int startY, 
                                  Recipe<?> recipe, RecipeEntry<?> recipeEntry, 
                                  DynamicRegistryManager dynamicRegistryManager,
                                  ItemStack hoveredStack,
                                  String itemDescription,
                                  net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererSmelting()) return startY;

        return smeltingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                      dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type smithing (smithing table).
     */
    public int renderSmithingRecipe(DrawContext context, int startX, int startY,
                                   Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                                   DynamicRegistryManager dynamicRegistryManager,
                                   ItemStack hoveredStack,
                                   String itemDescription,
                                   net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererSmithing()) return startY;

        return smithingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                      dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type brewing (alambic).
     */
    public int renderBrewingRecipe(DrawContext context, int startX, int startY,
                                  Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                                  DynamicRegistryManager dynamicRegistryManager,
                                  ItemStack hoveredStack,
                                  String itemDescription,
                                  net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererBrewing()) return startY;

        return brewingRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                     dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de brewing depuis des données (ItemStack) plutôt que depuis un Recipe.
     */
    public int renderBrewingRecipeFromData(DrawContext context, int startX, int startY,
                                           ItemStack inputPotion, ItemStack ingredient, ItemStack outputPotion,
                                           ItemStack hoveredStack, String itemDescription,
                                           net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererBrewing()) return startY;

        return brewingRenderer.renderFromData(context, startX, startY, inputPotion, ingredient, outputPotion,
                                             hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette de type stonecutter (tailleur de pierre).
     */
    public int renderStonecutterRecipe(DrawContext context, int startX, int startY,
                                      Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                                      DynamicRegistryManager dynamicRegistryManager,
                                      ItemStack hoveredStack,
                                      String itemDescription,
                                      net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererStonecutter()) return startY;

        return stonecutterRenderer.render(context, startX, startY, recipe, recipeEntry, 
                                         dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
    
    /**
     * Rend une recette personnalisée de machine moddée.
     */
    public int renderCustomMachineRecipe(DrawContext context, int startX, int startY,
                                         Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                                         DynamicRegistryManager dynamicRegistryManager,
                                         ItemStack hoveredStack,
                                         String itemDescription,
                                         net.minecraft.client.font.TextRenderer textRenderer) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isRendererCustomMachine()) return startY;

        return customMachineRenderer.render(context, startX, startY, recipe, recipeEntry,
                                           dynamicRegistryManager, hoveredStack, itemDescription, textRenderer);
    }
}



