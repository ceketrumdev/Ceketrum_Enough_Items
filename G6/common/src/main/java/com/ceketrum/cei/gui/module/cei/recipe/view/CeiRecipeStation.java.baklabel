package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
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

    private static final Map<Identifier, ItemStack> ICONS  = new HashMap<>();
    private static final Map<Identifier, String>    LABELS = new HashMap<>();

    /** A appeler lors d'un rechargement de datapack / changement de monde. */
    public static synchronized void clear() {
        ICONS.clear();
        LABELS.clear();
    }

    /** Icone de la station, ou EMPTY si rien de credible n'a ete trouve. */
    public static synchronized ItemStack iconFor(Identifier typeId) {
        if (typeId == null) return ItemStack.EMPTY;
        ItemStack cached = ICONS.get(typeId);
        if (cached != null) return cached.copy();
        ItemStack found = resolveIcon(typeId);
        ICONS.put(typeId, found);
        // On rend une copie : l'appelant peut poser la pile dans une liste de
        // slots rendus, et l'entree de cache ne doit pas pouvoir etre modifiee.
        return found.copy();
    }

    /** Libelle de la categorie. Ne renvoie jamais null ni chaine vide. */
    public static synchronized String labelFor(Identifier typeId, boolean isFr) {
        if (typeId == null) return isFr ? "Machine Speciale" : "Custom Machine";
        String cached = LABELS.get(typeId);
        if (cached != null) return cached;
        String label = resolveLabel(typeId, isFr);
        LABELS.put(typeId, label);
        return label;
    }

    /**
     * 1.21.5+ : RecipeDisplay.craftingStation() est la reponse officielle a la
     * question "dans quoi ca se fabrique ?", et elle ne suppose rien du mod.
     * Elle n'existe pas avant 1.21.5, d'ou la separation par groupe.
     */
    public static ItemStack fromDisplay(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe == null) return ItemStack.EMPTY;
        try {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return ItemStack.EMPTY;
            var displays = recipe.display();
            if (displays == null || displays.isEmpty()) return ItemStack.EMPTY;
            var ctx = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
            ItemStack stack = displays.get(0).craftingStation().resolveForFirstStack(ctx);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ icone

    private static ItemStack resolveIcon(Identifier typeId) {
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
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
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
        Identifier id;
        try {
            id = Identifier.fromNamespaceAndPath(ns, path);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    // ----------------------------------------------------------------- libelle

    private static String resolveLabel(Identifier typeId, boolean isFr) {
        ItemStack icon = iconFor(typeId);
        if (!icon.isEmpty()) {
            String name = icon.getHoverName().getString();
            if (name != null && !name.isBlank()) return name;
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
