package com.ceketrum.cei.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

public class ClientRecipeHelper {

    public static Collection<RecipeHolder<?>> getRecipes(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            return client.getSingleplayerServer().getRecipeManager().getRecipes();
        }
        
        List<RecipeHolder<?>> list = new ArrayList<>();
        try {
            if (client.player != null) {
                var book = client.player.getRecipeBook();
                for (var collection : book.getCollections()) {
                    for (RecipeDisplayEntry entry : collection.getRecipes()) {
                        list.add(wrapDisplayEntry(entry));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static RecipeHolder<?> wrapDisplayEntry(RecipeDisplayEntry entry) {
        Recipe mockRecipe = new Recipe() {
            @Override
            public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) { return false; }
            @Override
            public ItemStack assemble(RecipeInput input, net.minecraft.core.HolderLookup.Provider provider) { return ItemStack.EMPTY; }
            @Override
            public boolean isSpecial() { return false; }
            @Override
            public String group() { return ""; }
            @Override
            public RecipeSerializer getSerializer() { return null; }
            @Override
            public RecipeType getType() {
                RecipeBookCategory cat = entry.category();
                if (cat == RecipeBookCategories.STONECUTTER) {
                    return RecipeType.STONECUTTING;
                } else if (cat == RecipeBookCategories.SMITHING) {
                    return RecipeType.SMITHING;
                } else if (cat == RecipeBookCategories.CAMPFIRE) {
                    return RecipeType.CAMPFIRE_COOKING;
                } else if (cat == RecipeBookCategories.CRAFTING_BUILDING_BLOCKS ||
                           cat == RecipeBookCategories.CRAFTING_REDSTONE ||
                           cat == RecipeBookCategories.CRAFTING_EQUIPMENT ||
                           cat == RecipeBookCategories.CRAFTING_MISC) {
                    return RecipeType.CRAFTING;
                } else if (cat == RecipeBookCategories.FURNACE_FOOD ||
                           cat == RecipeBookCategories.FURNACE_BLOCKS ||
                           cat == RecipeBookCategories.FURNACE_MISC) {
                    return RecipeType.SMELTING;
                } else if (cat == RecipeBookCategories.BLAST_FURNACE_BLOCKS ||
                           cat == RecipeBookCategories.BLAST_FURNACE_MISC) {
                    return RecipeType.BLASTING;
                } else if (cat == RecipeBookCategories.SMOKER_FOOD) {
                    return RecipeType.SMOKING;
                }
                return RecipeType.CRAFTING;
            }
            @Override
            public List<RecipeDisplay> display() {
                return List.of(entry.display());
            }
            @Override
            public boolean showNotification() { return false; }
            @Override
            public PlacementInfo placementInfo() { return null; }
            @Override
            public RecipeBookCategory recipeBookCategory() { return entry.category(); }
        };
        
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cei_client_recipe", "recipe_" + entry.id().index());
        ResourceKey<Recipe<?>> key = ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
        return new RecipeHolder<>(key, mockRecipe);
    }
}
