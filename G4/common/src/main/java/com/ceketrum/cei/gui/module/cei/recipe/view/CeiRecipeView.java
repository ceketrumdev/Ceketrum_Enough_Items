package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Representation d'une recette, independante de la version de Minecraft et du
 * type de recette.
 *
 * C'est le pivot de la refonte : les adaptateurs (un par famille de version)
 * produisent ce modele, et un renderer unique le dessine. La grille n'est plus
 * supposee 3x3 -- ses dimensions font partie de la donnee, ce qui permet
 * d'afficher correctement les recettes moddees qui sortent du format vanilla.
 */
public record CeiRecipeView(
        Kind kind,
        int gridWidth,
        int gridHeight,
        List<CeiSlot> inputs,
        List<ItemStack> outputs,
        ItemStack station,
        Component title
) {
    public enum Kind {
        SHAPED,
        SHAPELESS,
        COOKING,
        STONECUTTING,
        SMITHING,
        /** Type inconnu : rendu en grille libre avec l'icone de la station. */
        GENERIC
    }

    /** L'emplacement (col, row), ou EMPTY s'il est hors grille ou vide. */
    public CeiSlot slotAt(int col, int row) {
        if (col < 0 || row < 0 || col >= gridWidth || row >= gridHeight) return CeiSlot.EMPTY;
        int i = row * gridWidth + col;
        return i < inputs.size() ? inputs.get(i) : CeiSlot.EMPTY;
    }

    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
    }

    /**
     * Choisit une grille lisible pour n entrees sans disposition imposee :
     * au plus 3 colonnes tant que c'est possible, sinon on elargit.
     */
    public static int[] bestFitGrid(int n) {
        if (n <= 0) return new int[] {0, 0};
        if (n <= 3) return new int[] {n, 1};
        if (n <= 9) {
            int cols = 3;
            return new int[] {cols, (n + cols - 1) / cols};
        }
        int cols = (int) Math.ceil(Math.sqrt(n));
        return new int[] {cols, (n + cols - 1) / cols};
    }
}
