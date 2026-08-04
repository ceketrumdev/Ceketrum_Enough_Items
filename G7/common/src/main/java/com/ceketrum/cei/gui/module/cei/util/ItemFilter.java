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
        // Tout prefixe repasse par le chemin lent : l'index melange nom
        // et identifiant, on ne peut pas y viser un critere seul.
        if (parse(searchText).hasPrefix()) {
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
        Query q = parse(searchText);
        // L'index des tags est bati au premier # tape, pas au demarrage :
        // c'est un passage complet sur la liste d'items, et la plupart des
        // joueurs ne s'en serviront jamais.
        if (!q.tag.isEmpty()) CeiTagIndex.build(items);
        List<ItemStack> narrowed = new ArrayList<>();
        for (ItemStack stack : items) {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;
            String full = id.toString().toLowerCase();
            if (!q.namespace.isEmpty() && !squash(id.getNamespace()).contains(q.namespace)) continue;
            String nm;
            try { nm = stack.getHoverName().getString().toLowerCase(); }
            catch (Exception e) { nm = full; }
            if (!matchesExtras(stack, q, full, nm)) continue;
            narrowed.add(stack);
        }
        items = narrowed;
        if (q.text.isEmpty()) return items;

        String needle = q.text;
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
