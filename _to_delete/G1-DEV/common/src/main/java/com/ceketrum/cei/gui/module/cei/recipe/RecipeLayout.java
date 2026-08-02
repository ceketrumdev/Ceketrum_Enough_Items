package com.ceketrum.cei.gui.module.cei.recipe;

import net.minecraft.item.ItemStack;

/**
 * Représente la mise en page d'une recette (dimensions, grille d'ingrédients, description).
 */
public class RecipeLayout {
    public int width;
    public int height;
    public ItemStack[][] ingredients;
    public String description;
}



