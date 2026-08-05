package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;

/**
 * La forme d'une recette : ce qu'elle produit, ce qu'elle consomme.
 *
 * C'EST LE SEUL ENDROIT DU CALCULATEUR QUI CONNAISSE LA VERSION. Les sept
 * lignees posent ces deux questions de trois facons irreconciliables --
 * getIngredients() et getResultItem() n'existent meme plus a partir de 1.21.5,
 * remplaces par le modele des presentations (display). Toute la divergence
 * tient dans ce fichier ; CeiCraftTree, lui, n'en sait rien.
 *
 * Chaque appel ici est recopie du indexOne() de ce groupe : du code que la
 * construction a deja valide, et non un nom suppose.
 */
public final class CeiRecipeShape {

    private CeiRecipeShape() {}

    /** Le resultat, ou une pile vide si la recette refuse de se decrire. */
    public static ItemStack outputOf(RecipeEntry<?> holder) {
        if (holder == null) return ItemStack.EMPTY;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return ItemStack.EMPTY;
            ItemStack out = holder.value().getOutput(client.world.getRegistryManager());
            return out == null ? ItemStack.EMPTY : out;
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    /** Un exemplaire par occurrence d'ingredient, dans l'ordre de la recette. */
    public static List<ItemStack> inputsOf(RecipeEntry<?> holder) {
        if (holder == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        try {
            for (var ingredient : holder.value().getIngredients()) {
                if (ingredient == null) continue;
                ItemStack[] matching = ingredient.getMatchingStacks();
                // Une case vide de recette faconnee rend un tableau vide :
                // c'est ainsi qu'on distingue un trou d'un ingredient.
                if (matching.length == 0) continue;
                ItemStack first = matching[0];
                if (first == null || first.isEmpty()) continue;
                out.add(first);
            }
        } catch (Exception | LinkageError e) {
            return List.of();
        }
        return out;
    }
}
