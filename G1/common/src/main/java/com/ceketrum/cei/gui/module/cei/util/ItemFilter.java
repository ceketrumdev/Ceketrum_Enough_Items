package com.ceketrum.cei.gui.module.cei.util;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilitaires pour filtrer les items selon un critère de recherche.
 */
public class ItemFilter {

    /**
     * Filtre une liste d'items selon un texte de recherche.
     * La recherche se fait sur :
     * - Le nom de l'item (localisé)
     * - L'ID de l'item (modid:itemid)
     * - Le nom simple de l'item (itemid)
     *
     * @param items La liste d'items à filtrer
     * @param searchText Le texte de recherche (insensible à la casse)
     * @return La liste filtrée
     */
    public static List<ItemStack> filterItems(List<ItemStack> items, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return items;
        }

        String query = searchText.trim();

        // "@modid" : on cherche le MOD, plus les items. Sans ce prefixe, taper
        // "create" ramene aussi bien le mod Create que tout ce qui contient
        // "creative" -- le namespace etait deja consulte, mais melange aux
        // trois autres criteres, donc impossible a viser seul.
        String namespaceQuery = null;
        if (query.startsWith("@")) {
            int space = query.indexOf(' ');
            namespaceQuery = squash(space < 0 ? query.substring(1) : query.substring(1, space));
            query = (space < 0) ? "" : query.substring(space + 1).trim();
        }

        if (namespaceQuery != null && !namespaceQuery.isEmpty()) {
            final String wanted = namespaceQuery;
            items = items.stream()
                    .filter(stack -> squash(Registries.ITEM.getId(stack.getItem()).getNamespace()).contains(wanted))
                    .collect(Collectors.toList());
        }

        // "@create" seul : le mod entier. "@create shaft" : le mod, puis la
        // recherche habituelle par-dessus. Les deux se composent.
        if (query.isEmpty()) {
            return items;
        }

        String lowerSearch = query.toLowerCase();

        return items.stream()
                .filter(stack -> {
                    // Recherche par nom localisé
                    String itemName = stack.getName().getString().toLowerCase();
                    if (itemName.contains(lowerSearch)) {
                        return true;
                    }

                    // Recherche par ID complet (ex: "minecraft:dirt")
                    Identifier itemId = Registries.ITEM.getId(stack.getItem());
                    String fullId = itemId.toString().toLowerCase();
                    if (fullId.contains(lowerSearch)) {
                        return true;
                    }

                    // Recherche par nom simple (ex: "dirt")
                    String simpleId = itemId.getPath().toLowerCase();
                    if (simpleId.contains(lowerSearch)) {
                        return true;
                    }

                    // Recherche par namespace (ex: "minecraft")
                    String namespace = itemId.getNamespace().toLowerCase();
                    if (namespace.contains(lowerSearch)) {
                        return true;
                    }

                    return false;
                })
                .collect(Collectors.toList());
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



