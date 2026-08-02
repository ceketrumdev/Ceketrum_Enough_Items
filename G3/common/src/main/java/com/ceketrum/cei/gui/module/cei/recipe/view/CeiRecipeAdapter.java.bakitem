package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * Adaptateur "legacy" : Minecraft 1.20.1 -> 1.21.1 (groupes G1 a G4).
 *
 * Ces versions n'ont pas l'API RecipeDisplay, mais Recipe.getIngredients() y est
 * une methode PAR DEFAUT de l'interface : tout type de recette moddee l'expose,
 * meme sans support explicite. C'est le point d'accroche generique.
 *
 * Difference avec RecipeDisplayHelper, qu'il remplace : ce dernier retombait sur
 * une grille 3x3 EN DUR pour tout ce qui n'etait pas un ShapedRecipe. Les
 * recettes de Create, qui ne sont pas des ShapedRecipe, etaient donc ecrasees
 * dans un format qui n'est pas le leur. Ici, un type inconnu produit un
 * CeiRecipeView.Kind.GENERIC dont la grille est dimensionnee sur le nombre reel
 * d'ingredients.
 */
public final class CeiRecipeAdapter {

    private CeiRecipeAdapter() {}

    /**
     * Vues deja construites.
     *
     * from() est appelee DEPUIS LE RENDU, donc a chaque frame : sans cache,
     * chaque image refaisait getMatchingStacks() sur tous les ingredients, et
     * maintenant le repechage reflexif par-dessus. Cle par identite : une
     * recette est un objet unique detenu par le RecipeManager.
     */
    private static final java.util.Map<Recipe<?>, CeiRecipeView> CACHE =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** A appeler au changement de monde, en meme temps que l'index. */
    public static void invalidate() {
        CACHE.clear();
    }

    // ------------------------------------------------------------ repechage

    /** Au-dela, la grille devient illisible ; mieux vaut tronquer que noyer. */
    private static final int MAX_SALVAGED_SLOTS = 9;
    /** Variantes affichees en alternance dans une meme case. */
    private static final int MAX_ALTERNATIVES = 12;

    /**
     * Noms de champs qui designent une sortie.
     *
     * C'est une heuristique, mais une heuristique solide : la convention est
     * universelle dans le modding Minecraft. Elle sert a ne pas afficher le
     * resultat parmi les ingredients.
     */
    private static final java.util.Set<String> OUTPUT_NAMES = java.util.Set.of(
            "result", "results", "output", "outputs", "out",
            "resultitem", "outputitem", "outputstack", "resultstack");

    private static final java.util.Map<Class<?>, java.lang.reflect.Field[]> FIELDS =
            new java.util.HashMap<>();

    /** Champs d'instance de la classe et de ses parents, accessibles, mis en cache. */
    private static synchronized java.lang.reflect.Field[] fieldsOf(Class<?> clazz) {
        java.lang.reflect.Field[] cached = FIELDS.get(clazz);
        if (cached != null) return cached;
        List<java.lang.reflect.Field> keep = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("java.")) break;
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType().isPrimitive() || f.getType() == String.class) continue;
                    try { f.setAccessible(true); } catch (Exception e) { continue; }
                    keep.add(f);
                }
            } catch (Exception e) {
                // module ferme : on se contente de ce qu'on a
            }
        }
        java.lang.reflect.Field[] arr = keep.toArray(new java.lang.reflect.Field[0]);
        FIELDS.put(clazz, arr);
        return arr;
    }

    /** Une collection ou un tableau donne une case par element ; le reste, une case. */
    private static List<Object> spread(Object value) {
        List<Object> parts = new ArrayList<>();
        if (value instanceof java.util.Collection<?> col) {
            parts.addAll(col);
        } else if (value.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < n; i++) parts.add(java.lang.reflect.Array.get(value, i));
        } else {
            parts.add(value);
        }
        return parts;
    }

    private static List<ItemStack> dedupe(List<ItemStack> stacks, int max) {
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.item.Item> seen = new java.util.LinkedHashSet<>();
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            if (!seen.add(s.getItem())) continue;
            out.add(s);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Entrees repechees dans les champs de la recette, une case par source. */
    private static List<CeiSlot> salvageInputs(Recipe<?> recipe) {
        List<CeiSlot> slots = new ArrayList<>();
        for (java.lang.reflect.Field f : fieldsOf(recipe.getClass())) {
            if (OUTPUT_NAMES.contains(f.getName().toLowerCase(java.util.Locale.ROOT))) continue;
            Object value;
            try { value = f.get(recipe); } catch (Exception e) { continue; }
            if (value == null) continue;
            for (Object part : spread(value)) {
                List<ItemStack> found = new ArrayList<>();
                try {
                    com.ceketrum.cei.gui.screen.CeiItemInfoScreen.unpackInputObject(part, found);
                } catch (Exception | StackOverflowError | LinkageError e) {
                    continue;
                }
                CeiSlot slot = new CeiSlot(dedupe(found, MAX_ALTERNATIVES));
                if (!slot.isEmpty()) slots.add(slot);
                if (slots.size() >= MAX_SALVAGED_SLOTS) return slots;
            }
        }
        return slots;
    }

    /** Sorties repechees : d'abord les champs qui le disent, sinon l'extracteur. */
    private static List<ItemStack> salvageOutputs(Recipe<?> recipe, RegistryAccess registries) {
        for (java.lang.reflect.Field f : fieldsOf(recipe.getClass())) {
            if (!OUTPUT_NAMES.contains(f.getName().toLowerCase(java.util.Locale.ROOT))) continue;
            Object value;
            try { value = f.get(recipe); } catch (Exception e) { continue; }
            if (value == null) continue;
            List<ItemStack> found = new ArrayList<>();
            try {
                com.ceketrum.cei.gui.screen.CeiItemInfoScreen.unpackInputObject(value, found);
            } catch (Exception | StackOverflowError | LinkageError e) {
                continue;
            }
            List<ItemStack> clean = dedupe(found, 4);
            if (!clean.isEmpty()) return clean;
        }
        try {
            return dedupe(com.ceketrum.cei.gui.screen.CeiItemInfoScreen
                    .extractCustomOutputs(recipe, registries), 4);
        } catch (Exception | StackOverflowError | LinkageError e) {
            return List.of();
        }
    }


    public static CeiRecipeView from(Recipe<?> recipe, RegistryAccess registries) {
        if (recipe == null) return null;
        CeiRecipeView cached = CACHE.get(recipe);
        if (cached != null) return cached;
        CeiRecipeView built = build(recipe, registries);
        if (built != null) CACHE.put(recipe, built);
        return built;
    }

    private static CeiRecipeView build(Recipe<?> recipe, RegistryAccess registries) {
        try {
            if (recipe instanceof ShapedRecipe shaped)          return fromShaped(shaped, registries);
            if (recipe instanceof ShapelessRecipe shapeless)    return fromFlat(shapeless, registries, CeiRecipeView.Kind.SHAPELESS);
            if (recipe instanceof AbstractCookingRecipe cook)   return fromFlat(cook, registries, CeiRecipeView.Kind.COOKING);
            if (recipe instanceof StonecutterRecipe cut)        return fromFlat(cut, registries, CeiRecipeView.Kind.STONECUTTING);
            if (recipe instanceof SmithingRecipe smith)         return fromFlat(smith, registries, CeiRecipeView.Kind.SMITHING);
            return fromFlat(recipe, registries, CeiRecipeView.Kind.GENERIC);
        } catch (Exception | LinkageError e) {
            // Une recette moddee peut lever depuis getIngredients() ou depuis la
            // resolution du resultat. On n'interrompt pas l'ecran, mais on TRACE :
            // un retour null silencieux rend le diagnostic impossible.
            org.slf4j.LoggerFactory.getLogger("cei-recipes").debug(
                    "Adaptation impossible pour {} : {}",
                    recipe.getClass().getName(), e.toString());
            return null;
        }
    }

    private static CeiRecipeView fromShaped(ShapedRecipe shaped, RegistryAccess registries) {
        int w = Math.max(1, shaped.getWidth());
        int h = Math.max(1, shaped.getHeight());
        List<Ingredient> ing = new ArrayList<>(shaped.getIngredients());

        List<CeiSlot> slots = new ArrayList<>(w * h);
        for (int i = 0; i < w * h; i++) {
            slots.add(i < ing.size() ? toSlot(ing.get(i)) : CeiSlot.EMPTY);
        }
        return new CeiRecipeView(CeiRecipeView.Kind.SHAPED, w, h, slots,
                outputsOf(shaped, registries), stationOf(shaped), titleOf(shaped));
    }

    /** Recettes sans disposition imposee : la grille est deduite du nombre d'entrees. */
    private static CeiRecipeView fromFlat(Recipe<?> recipe, RegistryAccess registries, CeiRecipeView.Kind kind) {
        List<CeiSlot> slots = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            CeiSlot slot = toSlot(ingredient);
            if (!slot.isEmpty()) slots.add(slot);
        }
        // La recette n'a rien repondu : on va chercher dans ses champs, comme
        // le fait l'index. Sinon l'ecran affiche une case vide pour une
        // recette que la recherche a su remplir.
        if (slots.isEmpty()) slots.addAll(salvageInputs(recipe));
        int[] grid = CeiRecipeView.bestFitGrid(slots.size());
        return new CeiRecipeView(kind, grid[0], grid[1], slots,
                outputsOf(recipe, registries), stationOf(recipe), titleOf(recipe));
    }

    private static CeiSlot toSlot(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return CeiSlot.EMPTY;
        ItemStack[] items = ingredient.getItems();
        if (items == null || items.length == 0) return CeiSlot.EMPTY;
        return new CeiSlot(List.of(items));
    }

    private static List<ItemStack> outputsOf(Recipe<?> recipe, RegistryAccess registries) {
        try {
            ItemStack out = recipe.getResultItem(registries);
            if (out != null && !out.isEmpty()) return List.of(out.copy());
        } catch (Exception e) {
            // certaines recettes speciales n'ont pas de resultat statique
        }
        return salvageOutputs(recipe, registries);
    }

    /**
     * Icone de la station de travail.
     *
     * Les versions < 1.21.5 n'ont pas RecipeDisplay.craftingStation(). On delegue
     * donc a CeiRecipeStation, qui derive l'item du seul identifiant de type,
     * sans aucune connaissance specifique a un mod.
     */
    private static ItemStack stationOf(Recipe<?> recipe) {
        return CeiRecipeStation.iconFor(typeIdOf(recipe));
    }

    private static ResourceLocation typeIdOf(Recipe<?> recipe) {
        try {
            return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (Exception e) {
            return null;
        }
    }

    private static Component titleOf(Recipe<?> recipe) {
        ResourceLocation typeId = typeIdOf(recipe);
        if (typeId == null) return Component.literal("Recette");
        return Component.literal(CeiRecipeStation.labelFor(typeId, false));
    }
}
