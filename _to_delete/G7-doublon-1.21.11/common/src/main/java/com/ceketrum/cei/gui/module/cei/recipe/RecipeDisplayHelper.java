package com.ceketrum.cei.gui.module.cei.recipe;

import java.util.List;
import net.minecraft.util.context.ContextMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Aide à la récupération et au formatage des informations de recettes.
 */
public class RecipeDisplayHelper {
    
    /**
     * Récupère la mise en page d'une recette.
     */
    public static RecipeLayout getRecipeLayout(Recipe<?> recipe, RecipeHolder<?> recipeEntry, ContextMap contextMap) {
        RecipeLayout layout = new RecipeLayout();
        List<RecipeDisplay> displays = recipe.display();
        
        if (!displays.isEmpty() && displays.get(0) instanceof ShapedCraftingRecipeDisplay shaped) {
            int width = shaped.width();
            int height = shaped.height();
            layout.width = width;
            layout.height = height;
            layout.ingredients = new ItemStack[height][width];
            List<SlotDisplay> ingredientsList = shaped.ingredients();
            
            for (int i = 0; i < ingredientsList.size(); i++) {
                SlotDisplay slot = ingredientsList.get(i);
                ItemStack match = slot.resolveForFirstStack(contextMap);
                layout.ingredients[i / width][i % width] = match != null ? match : ItemStack.EMPTY;
            }
        } else {
            // Disposition par défaut : grille 3x3
            layout.width = 3;
            layout.height = 3;
            layout.ingredients = new ItemStack[3][3];
            
            List<SlotDisplay> ingredientsList = List.of();
            if (!displays.isEmpty()) {
                if (displays.get(0) instanceof ShapelessCraftingRecipeDisplay shapeless) {
                    ingredientsList = shapeless.ingredients();
                }
            }
            
            for (int i = 0; i < 9; i++) {
                if (i < ingredientsList.size()) {
                    SlotDisplay slot = ingredientsList.get(i);
                    ItemStack match = slot.resolveForFirstStack(contextMap);
                    layout.ingredients[i / 3][i % 3] = match != null ? match : ItemStack.EMPTY;
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
    public static String getRecipeDescription(RecipeHolder<?> recipeEntry) {
        String translationKey = "recipe.description.cei." + recipeEntry.id().identifier().getPath();
        Component translated = Component.translatable(translationKey);
        
        // Si la traduction retourne la clé elle-même, c'est qu'elle n'existe pas
        if (translated.getString().equals(translationKey)) {
            return "";
        }
        
        return translated.getString();
    }
}
