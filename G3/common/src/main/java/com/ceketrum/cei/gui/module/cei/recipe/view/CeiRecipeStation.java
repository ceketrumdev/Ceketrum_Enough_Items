package com.ceketrum.cei.gui.module.cei.recipe.view;

import com.ceketrum.cei.i18n.CeiText;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Resout, pour un type de recette donne, l'item de la station de travail et le
 * libelle a afficher.
 *
 * Raison d'etre : jusqu'ici, toute recette non vanilla retombait sur une icone
 * de distributeur et le texte "Machine Speciale" / "Custom Machine". Une recette
 * Create s'affichait donc comme "Custom Machine" alors que son type -- par
 * exemple create:crushing -- porte l'information.
 *
 * Aucune connaissance specifique a un mod n'est codee ici. Trois strategies
 * generiques, dans l'ordre :
 *
 *   1. l'identifiant du type designe directement un item (ns:path, et quelques
 *      variantes usuelles : _block, _machine, la forme agent en -er) ;
 *   2. a defaut, on balaie les items du meme namespace en cherchant celui dont
 *      l'identifiant contient la racine du type -- create:crushing -> racine
 *      "crush" -> create:crushing_wheel ; create:mechanical_crafting ->
 *      "mechanical_craft" -> create:mechanical_crafter ;
 *   3. a defaut d'item, on tente les cles de traduction que les mods emploient
 *      par convention pour nommer leurs categories, puis on retombe sur le
 *      chemin du type mis en forme ("crushing" -> "Crushing"), ce qui reste
 *      infiniment plus informatif que "Custom Machine".
 *
 * Les resultats -- succes comme echecs -- sont memorises : le balayage du
 * registre ne doit pas se produire a chaque frame.
 */
public final class CeiRecipeStation {

    private CeiRecipeStation() {}

    private static final Map<ResourceLocation, ItemStack> ICONS  = new HashMap<>();
    private static final Map<ResourceLocation, String>    LABELS = new HashMap<>();

    /** A appeler lors d'un rechargement de datapack / changement de monde. */
    public static synchronized void clear() {
        ICONS.clear();
        LABELS.clear();
    }

    /** Icone de la station, ou EMPTY si rien de credible n'a ete trouve. */
    public static synchronized ItemStack iconFor(ResourceLocation typeId) {
        if (typeId == null) return ItemStack.EMPTY;
        ItemStack cached = ICONS.get(typeId);
        if (cached != null) return cached.copy();
        ItemStack found = resolveIcon(typeId);
        ICONS.put(typeId, found);
        // On rend une copie : l'appelant peut poser la pile dans une liste de
        // slots rendus, et l'entree de cache ne doit pas pouvoir etre modifiee.
        return found.copy();
    }

    /**
     * Les libelles memorises sont TRADUITS : ils viennent du nom affiche de
     * l'icone, ou d'une cle de langue du mod fournisseur. Ils ne valent donc
     * que pour la langue active, et changer de langue en jeu laissait tous
     * les onglets dans l'ancienne -- le cache n'etait vide qu'au changement
     * de monde.
     *
     * Les icones, elles, ne dependent pas de la langue : ICONS survit.
     */
    private static String labelLanguage = null;

    private static void dropLabelsIfLanguageChanged() {
        // "language.code" est fourni par les fichiers de langue du jeu lui-meme
        // et vaut "en_us", "fr_fr"... Il change exactement quand le joueur
        // change de langue, ce qu'aucun champ de ce fichier ne sait voir.
        String lang = CeiText.t("language.code");
        if (labelLanguage == null || !labelLanguage.equals(lang)) {
            labelLanguage = lang;
            LABELS.clear();
        }
    }

    /** Libelle de la categorie. Ne renvoie jamais null ni chaine vide. */
    public static synchronized String labelFor(ResourceLocation typeId) {
        dropLabelsIfLanguageChanged();
        if (typeId == null) return CeiText.t("cei.station.custom");
        String cached = LABELS.get(typeId);
        if (cached != null) return cached;
        String label = resolveLabel(typeId);
        LABELS.put(typeId, label);
        return label;
    }

    // ------------------------------------------------------------------ icone

    private static ItemStack resolveIcon(ResourceLocation typeId) {
        String ns   = typeId.getNamespace();
        String path = typeId.getPath();
        String root = rootOf(path);

        // 1. correspondances directes
        String[] direct = {
                path,
                root,
                root + "er",
                root + "or",
                path + "_block",
                path + "_machine",
                root + "_block",
                root + "_machine",
                root + "ing_machine"
        };
        for (String candidate : direct) {
            ItemStack stack = itemOrEmpty(ns, candidate);
            if (!stack.isEmpty()) return stack;
        }

        // 2. balayage du namespace sur la racine
        if (root.length() >= 3) {
            Item best = null;
            String bestPath = null;
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null || !id.getNamespace().equals(ns)) continue;
                String p = id.getPath();
                if (!p.contains(root)) continue;
                if (best == null || better(item, p, best, bestPath)) {
                    best = item;
                    bestPath = p;
                }
            }
            if (best != null) return new ItemStack(best);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Un item de bloc l'emporte sur un objet (on cherche une machine), puis
     * l'identifiant le plus court, puis l'ordre alphabetique pour que le choix
     * soit stable d'une session a l'autre.
     */
    private static boolean better(Item candidate, String candidatePath, Item best, String bestPath) {
        boolean candidateIsBlock = candidate instanceof BlockItem;
        boolean bestIsBlock      = best instanceof BlockItem;
        if (candidateIsBlock != bestIsBlock) return candidateIsBlock;
        if (candidatePath.length() != bestPath.length()) return candidatePath.length() < bestPath.length();
        return candidatePath.compareTo(bestPath) < 0;
    }

    private static ItemStack itemOrEmpty(String ns, String path) {
        if (path == null || path.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id;
        try {
            id = new ResourceLocation(ns, path);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    // ----------------------------------------------------------------- libelle

    private static String resolveLabel(ResourceLocation typeId) {
        ItemStack icon = iconFor(typeId);
        if (!icon.isEmpty()) {
            String name = icon.getHoverName().getString();
            if (name != null && !name.isBlank()) return name + variantSuffix(typeId, icon);
        }

        String ns   = typeId.getNamespace();
        String path = typeId.getPath();

        // Conventions de cle rencontrees chez les mods pour nommer une categorie.
        String[] keys = {
                ns + ".recipe." + path,
                ns + ".recipe_type." + path,
                "recipe." + ns + "." + path,
                "recipe_type." + ns + "." + path,
                "emi.category." + ns + "." + path,
                "gui." + ns + ".category." + path,
                "jei." + ns + "." + path
        };
        Language language = Language.getInstance();
        for (String key : keys) {
            try {
                if (language.has(key)) {
                    String value = I18n.get(key);
                    if (value != null && !value.isBlank() && !value.equals(key)) return value;
                }
            } catch (Exception e) {
                // une implementation de Language exotique ne doit pas casser l'ecran
            }
        }

        return prettify(path);
    }

    // ------------------------------------------------------------------ outils

    /**
     * Racine du type : "crushing" -> "crush", "mechanical_crafting" ->
     * "mechanical_craft", "alloying_recipe" -> "alloy".
     *
     * Seul le dernier mot est degrade, pour ne pas perdre le prefixe qui porte
     * l'essentiel du sens dans les identifiants composes.
     */
    private static String rootOf(String path) {
        String s = path.toLowerCase(Locale.ROOT);
        if (s.endsWith("_recipe")) s = s.substring(0, s.length() - 7);
        if (s.endsWith("ing") && s.length() > 5) {
            s = s.substring(0, s.length() - 3);
            if (s.length() > 3 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2)) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }


    /**
     * Ce qui distingue ce type de recette de la machine qui lui sert d'icone.
     *
     * Plusieurs types tournent souvent dans la meme machine -- chez Thermal,
     * "pulverizer" et "pulverizer_catalyst". Comme le libelle est tire du nom
     * de l'icone, ces types se retrouvaient avec le MEME nom : deux onglets
     * identiques, indiscernables meme au survol. On raccroche donc au libelle
     * la partie du chemin qui les separe.
     *
     * Le prolongement n'est teste que dans un sens : le chemin du type doit
     * prolonger celui de l'icone. Chez Create, "crushing" trouve son icone
     * dans "crushing_wheel" -- c'est l'icone qui prolonge le type, il n'y a
     * rien a preciser, et la condition inverse aurait colle un suffixe absurde
     * a une categorie pourtant unique.
     */
    private static String variantSuffix(ResourceLocation typeId, ItemStack icon) {
        try {
            ResourceLocation iconId = BuiltInRegistries.ITEM.getKey(icon.getItem());
            if (iconId == null) return "";
            String iconPath = iconId.getPath();
            String typePath = typeId.getPath();
            if (iconPath.isEmpty() || typePath.equals(iconPath)) return "";

            String extra = null;
            if (typePath.startsWith(iconPath + "_")) {
                extra = typePath.substring(iconPath.length() + 1);
            } else if (typePath.endsWith("_" + iconPath)) {
                extra = typePath.substring(0, typePath.length() - iconPath.length() - 1);
            }
            if (extra == null || extra.isEmpty()) return "";
            return " (" + prettify(extra) + ")";
        } catch (Exception e) {
            return "";
        }
    }

    private static String prettify(String path) {
        String[] words = path.replace('/', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1));
        }
        return out.length() == 0 ? path : out.toString();
    }
}
