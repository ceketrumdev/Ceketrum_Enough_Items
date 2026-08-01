package com.ceketrum.cei.gui.module.cei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Index recette -> items produits / consommes, construit UNE fois, et par
 * petites tranches.
 *
 * Mesure a l'origine de cette classe (relevee sur G4, pack Create,
 * 2816 recettes) : ouvrir la fiche d'un item coutait 244 ms. La cause n'etait
 * pas le nombre de recettes mais ce qu'on faisait de chacune -- pour CHAQUE
 * recette et a CHAQUE ouverture, deux explorations reflexives du graphe
 * d'objets, juste pour decouvrir qu'elle ne concernait pas l'item regarde.
 *
 * Ici le travail est paye une seule fois, et hors du chemin du joueur :
 * buildStep() accepte un budget de temps et rend la main des qu'il est epuise,
 * en gardant sa position. CeiWarmup l'appelle 2 ms par frame apres l'entree en
 * jeu. Si le joueur ouvre une fiche avant la fin, ensureBuilt() termine le
 * travail restant immediatement -- meme resultat, seul le moment change.
 *
 * Memoire : on n'indexe que des REFERENCES. Aucune copie d'ItemStack n'est
 * conservee ; l'index ne retient quasiment rien de plus que ce que le jeu
 * detient deja.
 */
public final class CeiRecipeIndex {

    private CeiRecipeIndex() {}

    private static final Map<Item, List<net.minecraft.recipe.RecipeEntry<?>>> BY_OUTPUT = new HashMap<>();
    private static final Map<Item, List<net.minecraft.recipe.RecipeEntry<?>>> BY_INPUT = new HashMap<>();

    /**
     * Monde ayant servi a construire l'index, et nombre de recettes vues. Si
     * l'un des deux change -- changement de monde, rechargement de datapack --
     * l'index se reconstruit tout seul, sans dependre d'un appel d'invalidation
     * qu'on oublierait de placer.
     */
    private static Object builtFrom = null;
    private static int recipeCount = -1;

    // ------------------------------------------------ construction en tranches
    private static List<net.minecraft.recipe.RecipeEntry<?>> pending = null;
    private static Object pendingFrom = null;
    private static int cursor = 0;

    /** Vide l'index. La reconstruction est paresseuse. */
    public static synchronized void invalidate() {
        BY_OUTPUT.clear();
        BY_INPUT.clear();
        builtFrom = null;
        recipeCount = -1;
        pending = null;
        pendingFrom = null;
        cursor = 0;
    }

    public static synchronized boolean isBuilt() {
        return builtFrom != null;
    }

    /**
     * Termine la construction sur-le-champ. C'est le chemin emprunte quand le
     * joueur ouvre une fiche : il ne peut pas attendre.
     */
    public static synchronized void ensureBuilt(MinecraftClient client) {
        buildStep(client, Long.MAX_VALUE);
    }

    /**
     * Avance la construction pendant au plus budgetNanos, puis rend la main.
     *
     * @return true si l'index est complet.
     */
    public static synchronized boolean buildStep(MinecraftClient client, long budgetNanos) {
        if (client == null || client.world == null) return false;

        Object owner = client.world;
        if (builtFrom == owner) return true;

        if (pending == null || pendingFrom != owner) {
            // L'enumeration n'a lieu qu'au demarrage d'une construction : sur
            // les versions recentes elle peut reconstruire des enveloppes de
            // recettes, ce qu'on ne veut surtout pas faire a chaque frame.
            BY_OUTPUT.clear();
            BY_INPUT.clear();
            List<net.minecraft.recipe.RecipeEntry<?>> all = new ArrayList<>();
            all.addAll(client.world.getRecipeManager().values());
            pending = all;
            pendingFrom = owner;
            cursor = 0;
        }

        var registries = client.world.getRegistryManager();

        boolean bounded = budgetNanos != Long.MAX_VALUE;
        long start = bounded ? System.nanoTime() : 0L;

        while (cursor < pending.size()) {
            indexOne(pending.get(cursor++), registries);
            if (bounded && (System.nanoTime() - start) >= budgetNanos) {
                return false;
            }
        }

        builtFrom = owner;
        recipeCount = pending.size();
        pending = null;
        pendingFrom = null;
        cursor = 0;
        return true;
    }

    /** Recettes produisant cet item. Liste vide si aucune -- jamais null. */
    public static synchronized List<net.minecraft.recipe.RecipeEntry<?>> producedBy(Item item) {
        List<net.minecraft.recipe.RecipeEntry<?>> list = BY_OUTPUT.get(item);
        return list == null ? Collections.emptyList() : list;
    }

    /** Recettes consommant cet item. Liste vide si aucune -- jamais null. */
    public static synchronized List<net.minecraft.recipe.RecipeEntry<?>> usedIn(Item item) {
        List<net.minecraft.recipe.RecipeEntry<?>> list = BY_INPUT.get(item);
        return list == null ? Collections.emptyList() : list;
    }

    /** Nombre d'items distincts indexes, pour le journal. */
    public static synchronized int indexedItems() {
        Set<Item> all = new HashSet<>(BY_OUTPUT.keySet());
        all.addAll(BY_INPUT.keySet());
        return all.size();
    }

    private static void register(Set<Item> produced, Set<Item> consumed, net.minecraft.recipe.RecipeEntry<?> entry) {
        for (Item item : produced) {
            BY_OUTPUT.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
        }
        for (Item item : consumed) {
            BY_INPUT.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
        }
    }

    private static void indexOne(net.minecraft.recipe.RecipeEntry<?> entry, net.minecraft.registry.DynamicRegistryManager registries) {
        net.minecraft.recipe.Recipe<?> recipe = entry.value();

        Set<Item> produced = new HashSet<>();
        Set<Item> consumed = new HashSet<>();

        // Exactement les memes extracteurs que l'ancien balayage : le contenu
        // de l'index est identique a ce qu'il trouvait, seule la frequence
        // change.
        try {
            ItemStack result = recipe.getResult(registries);
            if (result != null && !result.isEmpty()) produced.add(result.getItem());
            for (ItemStack out : com.ceketrum.cei.gui.screen.CeiItemInfoScreen
                    .extractCustomOutputs(recipe, registries)) {
                if (out != null && !out.isEmpty()) produced.add(out.getItem());
            }
        } catch (Exception | LinkageError e) {
            // une recette moddee peut lever : elle est simplement absente de
            // l'index cote sorties, comme elle l'etait de l'ancien balayage
        }

        try {
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient == null) continue;
                for (ItemStack match : ingredient.getMatchingStacks()) {
                    if (match != null && !match.isEmpty()) consumed.add(match.getItem());
                }
            }
        } catch (Exception | LinkageError e) {
            // idem
        }
        try {
            for (ItemStack in : com.ceketrum.cei.gui.screen.CeiItemInfoScreen
                    .extractCustomInputs(recipe)) {
                if (in != null && !in.isEmpty()) consumed.add(in.getItem());
            }
        } catch (Exception | LinkageError e) {
            // idem
        }

        register(produced, consumed, entry);
    }
}
