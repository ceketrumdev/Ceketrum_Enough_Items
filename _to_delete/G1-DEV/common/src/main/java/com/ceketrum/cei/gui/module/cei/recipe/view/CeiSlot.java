package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

/**
 * Un emplacement d'une recette : une ou plusieurs piles possibles.
 *
 * Un ingredient defini par un tag (par exemple #minecraft:planks) accepte
 * plusieurs items. L'ancien code n'en gardait que le premier -- on les fait
 * defiler, comme JEI et EMI.
 */
public final class CeiSlot {

    /** Duree d'affichage de chaque variante, en millisecondes. */
    private static final long CYCLE_MS = 1000L;

    public static final CeiSlot EMPTY = new CeiSlot(List.of());

    private final List<ItemStack> stacks;

    public CeiSlot(List<ItemStack> stacks) {
        List<ItemStack> clean = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack s : stacks) {
                if (s != null && !s.isEmpty()) clean.add(s);
            }
        }
        this.stacks = List.copyOf(clean);
    }

    public static CeiSlot of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new CeiSlot(List.of(stack));
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public List<ItemStack> all() {
        return stacks;
    }

    public int size() {
        return stacks.size();
    }

    /** La pile a afficher maintenant ; fait defiler les variantes s'il y en a plusieurs. */
    public ItemStack current(long timeMs) {
        if (stacks.isEmpty()) return ItemStack.EMPTY;
        if (stacks.size() == 1) return stacks.get(0);
        int i = (int) ((timeMs / CYCLE_MS) % stacks.size());
        return stacks.get(i);
    }
}
