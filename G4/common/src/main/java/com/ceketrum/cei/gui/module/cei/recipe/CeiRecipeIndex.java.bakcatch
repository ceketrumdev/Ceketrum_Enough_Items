package com.ceketrum.cei.gui.module.cei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Index recette -> items produits / consommes, construit UNE fois, et par
 * petites tranches.
 *
 * Mesure a l'origine de cette classe : ouvrir la fiche d'un item coutait
 * 244 ms sur un pack Create de 2816 recettes. La cause n'etait pas le nombre de
 * recettes mais ce qu'on faisait de chacune : pour CHAQUE recette, et a CHAQUE
 * ouverture d'ecran, on lancait deux explorations reflexives du graphe d'objets
 * (extractCustomOutputs et extractCustomInputs) juste pour decouvrir qu'elle ne
 * concernait pas l'item regarde.
 *
 * Premiere etape : payer ce travail une seule fois. Le cout par ouverture est
 * alors passe de 244 ms a 0,5 ms -- mais les 244 ms restaient dues, en bloc, a
 * la premiere ouverture.
 *
 * Deuxieme etape, celle-ci : les payer en dehors du chemin de l'utilisateur.
 * buildStep() accepte un budget de temps et rend la main des qu'il est epuise,
 * en gardant sa position. CeiWarmup l'appelle 2 ms par frame apres l'entree en
 * jeu : l'index est pret en une seconde ou deux, sans qu'aucune frame ne soit
 * sacrifiee. Si le joueur ouvre une fiche avant la fin, ensureBuilt() termine
 * le travail restant immediatement -- le resultat est le meme, seul le moment
 * du paiement change.
 *
 * Sur la memoire : on n'indexe que des REFERENCES -- des Item comme cles, des
 * RecipeHolder deja detenus par le RecipeManager comme valeurs. Aucune copie
 * d'ItemStack n'est conservee. L'index ne retient donc quasiment rien de plus
 * que ce que le jeu detient deja.
 */
public final class CeiRecipeIndex {

    private CeiRecipeIndex() {}

    private static final Map<Item, List<RecipeHolder<?>>> BY_OUTPUT = new HashMap<>();
    private static final Map<Item, List<RecipeHolder<?>>> BY_INPUT = new HashMap<>();

    /**
     * Le RecipeManager ayant servi a construire l'index. S'il change -- changement
     * de monde, rechargement de datapack -- l'index se reconstruit tout seul,
     * sans dependre d'un appel d'invalidation qu'on oublierait de placer.
     */
    private static RecipeManager builtFrom = null;
    private static int recipeCount = -1;

    // ------------------------------------------------ construction en tranches
    /** Instantane des recettes restant a traiter, null quand rien n'est en cours. */
    private static List<RecipeHolder<?>> pending = null;
    private static RecipeManager pendingFrom = null;
    private static int cursor = 0;

    /** Vide l'index. A appeler a la deconnexion ; la reconstruction est paresseuse. */
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

    /** Recettes deja traitees / a traiter, pour le diagnostic. */
    public static synchronized int pendingDone() {
        return pending == null ? 0 : cursor;
    }

    public static synchronized int pendingTotal() {
        return pending == null ? 0 : pending.size();
    }

    /**
     * Construit l'index en totalite si necessaire. C'est le chemin emprunte
     * quand le joueur ouvre une fiche : il ne peut pas attendre.
     */
    public static synchronized void ensureBuilt(
            RecipeManager manager,
            RegistryAccess registries,
            BiFunction<Recipe<?>, RegistryAccess, List<ItemStack>> customOutputs,
            Function<Recipe<?>, List<ItemStack>> customInputs) {

        buildStep(manager, registries, customOutputs, customInputs, Long.MAX_VALUE);
    }

    /**
     * Avance la construction pendant au plus budgetNanos, puis rend la main.
     *
     * Les deux extracteurs sont passes en parametre plutot que reimplementes ici :
     * ce sont exactement ceux qu'utilisait l'ancien chemin, donc le contenu de
     * l'index est identique a ce que l'ancien balayage trouvait.
     *
     * @return true si l'index est complet.
     */
    public static synchronized boolean buildStep(
            RecipeManager manager,
            RegistryAccess registries,
            BiFunction<Recipe<?>, RegistryAccess, List<ItemStack>> customOutputs,
            Function<Recipe<?>, List<ItemStack>> customInputs,
            long budgetNanos) {

        if (manager == null) return false;
        int size = manager.getRecipes().size();
        if (builtFrom == manager && recipeCount == size) return true;

        // Nouveau manager, ou nombre de recettes different : on repart de zero.
        // L'instantane evite de dependre de l'ordre d'iteration du manager entre
        // deux tranches.
        if (pending == null || pendingFrom != manager || pending.size() != size) {
            BY_OUTPUT.clear();
            BY_INPUT.clear();
            pending = new ArrayList<>(manager.getRecipes());
            pendingFrom = manager;
            cursor = 0;
        }

        boolean bounded = budgetNanos != Long.MAX_VALUE;
        long start = bounded ? System.nanoTime() : 0L;

        while (cursor < pending.size()) {
            indexOne(pending.get(cursor++), registries, customOutputs, customInputs);
            if (bounded && (System.nanoTime() - start) >= budgetNanos) {
                return false;
            }
        }

        builtFrom = manager;
        recipeCount = size;
        pending = null;
        pendingFrom = null;
        cursor = 0;
        return true;
    }

    private static void indexOne(
            RecipeHolder<?> entry,
            RegistryAccess registries,
            BiFunction<Recipe<?>, RegistryAccess, List<ItemStack>> customOutputs,
            Function<Recipe<?>, List<ItemStack>> customInputs) {

        Recipe<?> recipe = entry.value();

        Set<Item> produced = new HashSet<>();
        Set<Item> consumed = new HashSet<>();

        try {
            ItemStack result = recipe.getResultItem(registries);
            if (result != null && !result.isEmpty()) produced.add(result.getItem());
            for (ItemStack out : customOutputs.apply(recipe, registries)) {
                if (out != null && !out.isEmpty()) produced.add(out.getItem());
            }
        } catch (Exception | LinkageError e) {
            // une recette moddee peut lever : elle est simplement absente de
            // l'index cote sorties, comme elle l'etait de l'ancien balayage
        }

        try {
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient == null) continue;
                for (ItemStack match : ingredient.getItems()) {
                    if (match != null && !match.isEmpty()) consumed.add(match.getItem());
                }
            }
        } catch (Exception | LinkageError e) {
            // idem
        }
        try {
            for (ItemStack in : customInputs.apply(recipe)) {
                if (in != null && !in.isEmpty()) consumed.add(in.getItem());
            }
        } catch (Exception | LinkageError e) {
            // idem
        }

        for (Item item : produced) {
            BY_OUTPUT.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
        }
        for (Item item : consumed) {
            BY_INPUT.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
        }
    }

    /** Recettes produisant cet item. Liste vide si aucune -- jamais null. */
    public static synchronized List<RecipeHolder<?>> producedBy(Item item) {
        List<RecipeHolder<?>> list = BY_OUTPUT.get(item);
        return list == null ? Collections.emptyList() : list;
    }

    /** Recettes consommant cet item. Liste vide si aucune -- jamais null. */
    public static synchronized List<RecipeHolder<?>> usedIn(Item item) {
        List<RecipeHolder<?>> list = BY_INPUT.get(item);
        return list == null ? Collections.emptyList() : list;
    }

    /** Nombre d'items distincts indexes, pour le diagnostic. */
    public static synchronized int indexedItems() {
        Set<Item> all = new HashSet<>(BY_OUTPUT.keySet());
        all.addAll(BY_INPUT.keySet());
        return all.size();
    }
}
