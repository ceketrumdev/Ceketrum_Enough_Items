package com.ceketrum.cei.gui.module.cei.recipe.view;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Adaptateur "display" : Minecraft 1.21.5 et au-dela (groupes G5 a G7).
 *
 * Depuis 1.21.5, Recipe.getIngredients() n'existe plus ; le serveur envoie des
 * RecipeDisplay deja structures. L'ancien chemin de CEI les reconvertissait en
 * un Recipe anonyme dont le type etait DEVINE depuis la categorie du livre de
 * recettes, avec RecipeType.CRAFTING en defaut : toute recette moddee finissait
 * donc dessinee dans une grille 3x3 qui n'est pas la sienne.
 *
 * Ici on lit la donnee telle qu'elle arrive :
 *   - ShapedCraftingRecipeDisplay porte width x height REELS, quels qu'ils soient ;
 *   - craftingStation() donne le bloc de fabrication sans rien savoir du mod ;
 *   - pour un type de display inconnu, placementInfo().ingredients() reste un
 *     point d'accroche type ; en dernier recours seulement, on inspecte les
 *     accesseurs de type SlotDisplay du display -- une reflexion BORNEE a la
 *     profondeur 1 et a un type precis, sans commune mesure avec l'exploration
 *     recursive de graphe d'objets qui provoquait un StackOverflowError.
 */
public final class CeiRecipeAdapter {

    private CeiRecipeAdapter() {}

    public static CeiRecipeView from(Recipe<?> recipe, ContextMap ctx) {
        if (recipe == null || ctx == null) return null;
        try {
            RecipeDisplay display = firstDisplay(recipe);

            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                int w = Math.max(1, shaped.width());
                int h = Math.max(1, shaped.height());
                List<CeiSlot> slots = new ArrayList<>(w * h);
                List<SlotDisplay> ing = shaped.ingredients();
                for (int i = 0; i < w * h; i++) {
                    slots.add(i < ing.size() ? toSlot(ing.get(i), ctx) : CeiSlot.EMPTY);
                }
                return build(CeiRecipeView.Kind.SHAPED, w, h, slots, display, recipe, ctx);
            }

            if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                return flat(CeiRecipeView.Kind.SHAPELESS, shapeless.ingredients(), display, recipe, ctx);
            }

            if (display instanceof FurnaceRecipeDisplay furnace) {
                return flat(CeiRecipeView.Kind.COOKING, List.of(furnace.ingredient()), display, recipe, ctx);
            }

            if (display instanceof StonecutterRecipeDisplay cut) {
                return flat(CeiRecipeView.Kind.STONECUTTING, List.of(cut.input()), display, recipe, ctx);
            }

            if (display instanceof SmithingRecipeDisplay smith) {
                return flat(CeiRecipeView.Kind.SMITHING,
                        List.of(smith.template(), smith.base(), smith.addition()), display, recipe, ctx);
            }

            // Type de display inconnu -- c'est la branche qui absorbe Create et
            // tout le reste, sans une ligne de code par mod.
            List<CeiSlot> slots = fromPlacementInfo(recipe);
            if (slots.isEmpty() && display != null) slots = fromDisplayAccessors(display, ctx);
            int[] grid = CeiRecipeView.bestFitGrid(slots.size());
            return build(CeiRecipeView.Kind.GENERIC, grid[0], grid[1], slots, display, recipe, ctx);

        } catch (Exception | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("cei-recipes").debug(
                    "Adaptation impossible pour {} : {}",
                    recipe.getClass().getName(), e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------- assemblage

    private static CeiRecipeView flat(CeiRecipeView.Kind kind, List<SlotDisplay> ing,
                                      RecipeDisplay display, Recipe<?> recipe, ContextMap ctx) {
        List<CeiSlot> slots = new ArrayList<>();
        for (SlotDisplay sd : ing) {
            CeiSlot slot = toSlot(sd, ctx);
            if (!slot.isEmpty()) slots.add(slot);
        }
        int[] grid = CeiRecipeView.bestFitGrid(slots.size());
        return build(kind, grid[0], grid[1], slots, display, recipe, ctx);
    }

    private static CeiRecipeView build(CeiRecipeView.Kind kind, int w, int h, List<CeiSlot> slots,
                                       RecipeDisplay display, Recipe<?> recipe, ContextMap ctx) {
        List<ItemStack> outputs = new ArrayList<>();
        ItemStack station = ItemStack.EMPTY;
        if (display != null) {
            try {
                ItemStack out = display.result().resolveForFirstStack(ctx);
                if (out != null && !out.isEmpty()) outputs.add(out.copy());
            } catch (Exception e) {
                // resultat non resoluble cote client
            }
            try {
                ItemStack s = display.craftingStation().resolveForFirstStack(ctx);
                if (s != null && !s.isEmpty()) station = s.copy();
            } catch (Exception e) {
                // pas de station declaree
            }
        }
        Identifier typeId = typeIdOf(recipe);
        if (station.isEmpty()) station = CeiRecipeStation.iconFor(typeId);
        String title = typeId == null ? "Recette" : CeiRecipeStation.labelFor(typeId, false);
        return new CeiRecipeView(kind, w, h, slots, outputs, station, Component.literal(title));
    }

    // ---------------------------------------------------------------- entrees

    private static CeiSlot toSlot(SlotDisplay slotDisplay, ContextMap ctx) {
        if (slotDisplay == null) return CeiSlot.EMPTY;
        try {
            List<ItemStack> stacks = slotDisplay.resolveForStacks(ctx);
            if (stacks == null || stacks.isEmpty()) return CeiSlot.EMPTY;
            return new CeiSlot(stacks);
        } catch (Exception e) {
            return CeiSlot.EMPTY;
        }
    }

    /** Point d'accroche type, disponible sur toute recette depuis 1.21.5. */
    private static List<CeiSlot> fromPlacementInfo(Recipe<?> recipe) {
        List<CeiSlot> slots = new ArrayList<>();
        try {
            var info = recipe.placementInfo();
            if (info == null) return slots;
            for (Ingredient ingredient : info.ingredients()) {
                if (ingredient == null) continue;
                List<ItemStack> stacks = new ArrayList<>();
                for (Holder<Item> holder : ingredient.items().toList()) {
                    stacks.add(new ItemStack(holder));
                }
                if (!stacks.isEmpty()) slots.add(new CeiSlot(stacks));
            }
        } catch (Exception | LinkageError e) {
            // placementInfo() peut lever sur une recette moddee non placable
        }
        return slots;
    }

    /**
     * Dernier recours : accesseurs sans argument du display dont le type de
     * retour EST SlotDisplay. Profondeur 1, aucun appel recursif, aucun type
     * arbitraire -- contrairement a l'ancien unpackInputObject.
     */
    private static List<CeiSlot> fromDisplayAccessors(RecipeDisplay display, ContextMap ctx) {
        List<CeiSlot> slots = new ArrayList<>();
        try {
            for (Method m : display.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!SlotDisplay.class.isAssignableFrom(m.getReturnType())) continue;
                String name = m.getName();
                if (name.equals("result") || name.equals("craftingStation")) continue;
                m.setAccessible(true);
                CeiSlot slot = toSlot((SlotDisplay) m.invoke(display), ctx);
                if (!slot.isEmpty()) slots.add(slot);
            }
        } catch (Exception | LinkageError e) {
            // un display recalcitrant ne doit pas vider l'ecran
        }
        return slots;
    }

    // ----------------------------------------------------------------- outils

    private static RecipeDisplay firstDisplay(Recipe<?> recipe) {
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (displays != null && !displays.isEmpty()) return displays.get(0);
        } catch (Exception | LinkageError e) {
            // recette sans display
        }
        return null;
    }

    private static Identifier typeIdOf(Recipe<?> recipe) {
        try {
            return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (Exception e) {
            return null;
        }
    }
}
