package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.StonecuttingRecipe;

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

    public static CeiRecipeView from(Recipe<?> recipe, DynamicRegistryManager registries) {
        if (recipe == null) return null;
        try {
            if (recipe instanceof ShapedRecipe shaped)          return fromShaped(shaped, registries);
            if (recipe instanceof ShapelessRecipe shapeless)    return fromFlat(shapeless, registries, CeiRecipeView.Kind.SHAPELESS);
            if (recipe instanceof AbstractCookingRecipe cook)   return fromFlat(cook, registries, CeiRecipeView.Kind.COOKING);
            if (recipe instanceof StonecuttingRecipe cut)        return fromFlat(cut, registries, CeiRecipeView.Kind.STONECUTTING);
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

    private static CeiRecipeView fromShaped(ShapedRecipe shaped, DynamicRegistryManager registries) {
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
    private static CeiRecipeView fromFlat(Recipe<?> recipe, DynamicRegistryManager registries, CeiRecipeView.Kind kind) {
        List<CeiSlot> slots = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            CeiSlot slot = toSlot(ingredient);
            if (!slot.isEmpty()) slots.add(slot);
        }
        int[] grid = CeiRecipeView.bestFitGrid(slots.size());
        return new CeiRecipeView(kind, grid[0], grid[1], slots,
                outputsOf(recipe, registries), stationOf(recipe), titleOf(recipe));
    }

    private static CeiSlot toSlot(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return CeiSlot.EMPTY;
        ItemStack[] items = ingredient.getMatchingStacks();
        if (items == null || items.length == 0) return CeiSlot.EMPTY;
        return new CeiSlot(List.of(items));
    }

    private static List<ItemStack> outputsOf(Recipe<?> recipe, DynamicRegistryManager registries) {
        try {
            ItemStack out = recipe.getResult(registries);
            if (out != null && !out.isEmpty()) return List.of(out.copy());
        } catch (Exception e) {
            // certaines recettes speciales n'ont pas de resultat statique
        }
        return List.of();
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

    private static Identifier typeIdOf(Recipe<?> recipe) {
        try {
            return Registries.RECIPE_TYPE.getId(recipe.getType());
        } catch (Exception e) {
            return null;
        }
    }

    private static Text titleOf(Recipe<?> recipe) {
        Identifier typeId = typeIdOf(recipe);
        if (typeId == null) return Text.literal("Recette");
        return Text.literal(CeiRecipeStation.labelFor(typeId, false));
    }
}
