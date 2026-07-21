package com.ceketrum.cei.gui.module.cei.recipe;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;

/**
 * Interface pour les renderers de recettes.
 * Chaque type de recette (crafting, smelting, smithing) a son propre renderer.
 */
public interface IRecipeRenderer {
    /**
     * Rend une recette.
     * 
     * @param context Le contexte de rendu
     * @param startX Position X de départ
     * @param startY Position Y de départ
     * @param recipe La recette à rendre
     * @param recipeEntry L'entrée de recette
     * @param dynamicRegistryManager Le gestionnaire de registres dynamiques
     * @param hoveredStack L'item survolé
     * @param itemDescription La description de l'item
     * @param textRenderer Le TextRenderer
     * @return La position Y après le rendu
     */
    int render(DrawContext context, int startX, int startY,
               Recipe<?> recipe, RecipeEntry<?> recipeEntry,
               DynamicRegistryManager dynamicRegistryManager,
               ItemStack hoveredStack,
               String itemDescription,
               net.minecraft.client.font.TextRenderer textRenderer);
}




