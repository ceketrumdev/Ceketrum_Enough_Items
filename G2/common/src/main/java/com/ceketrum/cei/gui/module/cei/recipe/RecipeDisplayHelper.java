package com.ceketrum.cei.gui.module.cei.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Aide à la récupération et au formatage des informations de recettes.
 */
public class RecipeDisplayHelper {
    
    /**
     * Récupère la mise en page d'une recette.
     */
    public static RecipeLayout getRecipeLayout(Recipe<?> recipe, RecipeEntry<?> recipeEntry, DynamicRegistryManager dynamicRegistryManager) {
        RecipeLayout layout = new RecipeLayout();
        
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            layout.width = width;
            layout.height = height;
            layout.ingredients = new ItemStack[height][width];
            List<Ingredient> ingredientsList = shaped.getIngredients();
            
            for (int i = 0; i < ingredientsList.size(); i++) {
                Ingredient ingredient = ingredientsList.get(i);
                ItemStack[] matching = ingredient.getMatchingStacks();
                layout.ingredients[i / width][i % width] = matching.length > 0 ? matching[0] : ItemStack.EMPTY;
            }
        } else {
            // Disposition par défaut : grille 3x3
            layout.width = 3;
            layout.height = 3;
            layout.ingredients = new ItemStack[3][3];
            List<Ingredient> ingredientsList = recipe.getIngredients();
            
            for (int i = 0; i < 9; i++) {
                if (i < ingredientsList.size()) {
                    Ingredient ingredient = ingredientsList.get(i);
                    ItemStack[] matching = ingredient.getMatchingStacks();
                    layout.ingredients[i / 3][i % 3] = matching.length > 0 ? matching[0] : ItemStack.EMPTY;
                } else {
                    layout.ingredients[i / 3][i % 3] = ItemStack.EMPTY;
                }
            }
        }
        
        layout.description = getRecipeDescription(recipeEntry);
        return layout;
    }
    
    /**
     * Récupère la description d'une recette depuis les traductions.
     */
    public static String getRecipeDescription(RecipeEntry<?> recipeEntry) {
        String translationKey = "recipe.description.cei." + recipeEntry.id().getPath();
        Text translated = Text.translatable(translationKey);
        
        // Si la traduction retourne la clé elle-même, c'est qu'elle n'existe pas
        if (translated.getString().equals(translationKey)) {
            return "";
        }
        
        return translated.getString();
    }
}




