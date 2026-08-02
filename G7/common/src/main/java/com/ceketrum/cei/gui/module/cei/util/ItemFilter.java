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
        // "@modid" : l'index melange le nom et l'identifiant, on ne peut pas y
        // viser le namespace seul. Et filtrer par mod casserait la
        // correspondance de rang entre `items` et `blobs`, sur laquelle repose
        // tout ce chemin. On repasse donc par le chemin lent : c'est une
        // frappe manuelle, pas une boucle de rendu.
        if (searchText.trim().startsWith("@")) {
            return filterItems(items, searchText);
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
        String query = searchText.trim();

        // "@modid" : on cherche le MOD, plus les items. Sans ce prefixe, taper
        // "create" ramene aussi bien le mod Create que tout ce qui contient
        // "creative", ce qui noie exactement ce qu'on cherchait.
        if (query.startsWith("@")) {
            int space = query.indexOf(' ');
            String wanted = squash(space < 0 ? query.substring(1) : query.substring(1, space));
            query = (space < 0) ? "" : query.substring(space + 1).trim();
            if (!wanted.isEmpty()) {
                List<ItemStack> kept = new ArrayList<>();
                for (ItemStack stack : items) {
                    // var : le type de l'identifiant n'a pas le meme nom d'une
                    // lignee a l'autre, et on n'a aucun besoin de le nommer.
                    var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && squash(id.getNamespace()).contains(wanted)) {
                        kept.add(stack);
                    }
                }
                items = kept;
            }
            // "@create" seul : le mod entier. "@create shaft" : le mod, puis la
            // recherche habituelle par-dessus.
            if (query.isEmpty()) {
                return items;
            }
        }

        String needle = query.toLowerCase();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : items) {
            if (buildSearchBlob(stack).contains(needle)) {
                out.add(stack);
            }
        }
        return out;
    }

    /**
     * Enleve d'un identifiant ce qui ne fait que separer les mots.
     *
     * Les identifiants de mods s'ecrivent tantot "refinedstorage", tantot
     * "refined_storage", parfois les deux chez le meme auteur. Comparer les
     * formes nues evite d'exiger du joueur qu'il sache laquelle a ete retenue.
     */
    private static String squash(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c != '_' && c != '-' && c != ' ' && c != '.') out.append(c);
        }
        return out.toString();
    }
}
