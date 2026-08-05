package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

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

    /**
     * Le resultat, ou une pile vide.
     *
     * A partir de 1.21.5 une recette n'a plus de resultat unique : elle a des
     * PRESENTATIONS, dont chacune sait se resoudre en piles. La premiere non
     * vide fait foi -- les suivantes decrivent la meme recette autrement.
     */
    public static ItemStack outputOf(RecipeHolder<?> holder) {
        if (holder == null) return ItemStack.EMPTY;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return ItemStack.EMPTY;
            var context = net.minecraft.world.item.crafting.display
                    .SlotDisplayContext.fromLevel(mc.level);
            for (var display : holder.value().display()) {
                ItemStack out = display.result().resolveForFirstStack(context);
                if (out != null && !out.isEmpty()) return out;
            }
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** Un exemplaire par occurrence d'ingredient, dans l'ordre de la recette. */
    public static List<ItemStack> inputsOf(RecipeHolder<?> holder) {
        if (holder == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return List.of();
            var context = net.minecraft.world.item.crafting.display
                    .SlotDisplayContext.fromLevel(mc.level);
            for (var display : holder.value().display()) {
                for (var slot : com.ceketrum.cei.gui.screen.CeiItemInfoScreen
                        .getRecipeIngredients(display)) {
                    if (slot == null) continue;
                    for (ItemStack first : slot.resolveForStacks(context)) {
                        if (first == null || first.isEmpty()) break;
                        out.add(first);
                        break;   // une pile par emplacement, comme ailleurs
                    }
                }
                // Une seule presentation : les autres redisent la meme recette,
                // et les cumuler compterait chaque ingredient plusieurs fois.
                if (!out.isEmpty()) break;
            }
        } catch (Exception | LinkageError e) {
            return List.of();
        }
        return out;
    }
}
