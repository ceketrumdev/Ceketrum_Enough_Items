package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

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
        Query q = parse(searchText);
        // L'index des tags est bati au premier # tape, pas au demarrage :
        // c'est un passage complet sur la liste d'items, et la plupart des
        // joueurs ne s'en serviront jamais.
        if (!q.tag.isEmpty()) CeiTagIndex.build(items);

        // Les prefixes se composent : "@create ^cog" est un mod ET une
        // expression. Chacun retire des candidats, aucun ne remplace
        // les autres.
        if (q.hasPrefix()) {
            List<ItemStack> narrowed = new ArrayList<>();
            for (ItemStack stack : items) {
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) continue;
                if (!q.namespace.isEmpty()
                        && !squash(id.getNamespace()).contains(q.namespace)) continue;
                String full = id.toString().toLowerCase();
                String nm;
                try { nm = stack.getHoverName().getString().toLowerCase(); }
                catch (Exception e) { nm = full; }
                if (!matchesExtras(stack, q, full, nm)) continue;
                narrowed.add(stack);
            }
            items = narrowed;
        }

        if (q.text.isEmpty()) {
            return items;
        }

        String lowerSearch = q.text;

        return items.stream()
                .filter(stack -> {
                    // Recherche par nom localisé
                    String itemName = stack.getHoverName().getString().toLowerCase();
                    if (itemName.contains(lowerSearch)) {
                        return true;
                    }

                    // Recherche par ID complet (ex: "minecraft:dirt")
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    /**
     * Ce qu'une ligne de recherche demande vraiment.
     *
     * Un seul endroit lit les prefixes, pour que les deux chemins de filtrage
     * -- l'indexe et le lent -- ne puissent pas les interpreter differemment.
     */
    public static final class Query {
        public String namespace = "";   // @mod
        public String regex = "";       // ^expr
        public String description = ""; // $texte
        public String tag = "";         // #tag
        public String text = "";        // le reste

        public boolean hasPrefix() {
            return !namespace.isEmpty() || !regex.isEmpty()
                    || !description.isEmpty() || !tag.isEmpty();
        }
    }

    public static Query parse(String searchText) {
        Query q = new Query();
        if (searchText == null) return q;
        String s = searchText.trim();
        if (s.isEmpty()) return q;

        char c = s.charAt(0);
        if (c == '@' || c == '$' || c == '^' || c == '#') {
            int space = s.indexOf(' ');
            String head = (space < 0) ? s.substring(1) : s.substring(1, space);
            String rest = (space < 0) ? "" : s.substring(space + 1).trim();
            if (c == '@') q.namespace = squash(head);
            else if (c == '$') q.description = head.toLowerCase();
            else if (c == '#') q.tag = head.toLowerCase();
            else q.regex = head;
            q.text = rest.toLowerCase();
            return q;
        }
        q.text = s.toLowerCase();
        return q;
    }

    /**
     * La regex compilee, ou null si elle ne tient pas debout.
     *
     * Une expression est incomplete a peu pres a chaque touche pendant qu'on
     * la tape. La refuser en vidant la liste rendrait la frappe illisible :
     * on retombe sur "commence par", qui est ce que l'utilisateur voulait
     * neuf fois sur dix de toute facon.
     */
    private static java.util.regex.Pattern compile(String expr) {
        try {
            return java.util.regex.Pattern.compile(expr, java.util.regex.Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchesExtras(ItemStack stack, Query q, String id, String name) {
        if (!q.regex.isEmpty()) {
            java.util.regex.Pattern p = compile(q.regex);
            if (p == null) {
                String low = q.regex.toLowerCase();
                if (!name.startsWith(low) && !id.startsWith(low)
                        && !shortId(id).startsWith(low)) return false;
            } else if (!p.matcher(name).find() && !p.matcher(id).find()) {
                return false;
            }
        }
        if (!q.tag.isEmpty() && !CeiTagIndex.matches(stack, q.tag)) return false;
        if (!q.description.isEmpty()) {
            // La description vient du mod lui-meme : pas d'infobulle du jeu,
            // dont la signature a change trois fois et dont le calcul peut
            // declencher du rendu.
            String d = com.ceketrum.cei.data.ItemDescriptionManager.getInstance()
                    .getDescription(stack.getItem());
            if (d == null || !d.toLowerCase().contains(q.description)) return false;
        }
        return true;
    }

    private static String shortId(String fullId) {
        int i = fullId.indexOf(':');
        return i < 0 ? fullId : fullId.substring(i + 1);
    }
}


