package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Filtrage des items par texte de recherche.
 *
 * La recherche porte sur le nom localise, l'id complet (modid:itemid), le nom
 * simple et le namespace. Ces chaines sont concatenees UNE FOIS a la
 * construction de la liste (voir CeiModule.ensureItemsBuilt) : les recalculer a
 * chaque frappe coutait un getHoverName() -- resolution de traduction et
 * allocation -- par item et par caractere tape.
 */
public class ItemFilter {

    private ItemFilter() {}

    /** Construit la chaine de recherche d'un item, deja en minuscules. */
    public static String buildSearchBlob(ItemStack stack) {
        StringBuilder sb = new StringBuilder(64);
        try {
            sb.append(stack.getHoverName().getString().toLowerCase());
        } catch (Exception e) {
            // nom non resolvable : on se rabat sur l'identifiant seul
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            sb.append(' ').append(id.toString().toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Filtre en s'appuyant sur l'index precalcule.
     *
     * @param items liste complete
     * @param blobs index de meme taille et de meme ordre que `items`
     */
    public static List<ItemStack> filterIndexed(List<ItemStack> items, List<String> blobs, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return items;
        }
        if (blobs == null || blobs.size() != items.size()) {
            // index absent ou desynchronise : repli sur le chemin lent
            return filterItems(items, searchText);
        }

        String needle = searchText.toLowerCase().trim();
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (blobs.get(i).contains(needle)) {
                out.add(items.get(i));
            }
        }
        return out;
    }

    /** Chemin lent, conserve comme repli et pour les appels externes. */
    public static List<ItemStack> filterItems(List<ItemStack> items, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return items;
        }
        String needle = searchText.toLowerCase().trim();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : items) {
            if (buildSearchBlob(stack).contains(needle)) {
                out.add(stack);
            }
        }
        return out;
    }
}
