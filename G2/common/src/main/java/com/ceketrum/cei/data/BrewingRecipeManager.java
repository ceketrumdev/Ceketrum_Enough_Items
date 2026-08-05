package com.ceketrum.cei.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Gère l'accès aux recettes de brewing depuis le serveur.
 * Les recettes de brewing utilisent BrewingRecipeRegistry qui n'est accessible que côté serveur.
 */
public class BrewingRecipeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-brewing");

    private static BrewingRecipeManager instance;
    private final Map<Identifier, List<BrewingRecipe>> cache = new HashMap<>();
    private boolean isCacheBuilt = false;
    private int attempts = 0;
    private static final int MAX_ATTEMPTS = 5;

    public static class BrewingRecipe {
        public final ItemStack inputPotion;
        public final ItemStack ingredient;
        public final ItemStack outputPotion;

        public BrewingRecipe(ItemStack inputPotion, ItemStack ingredient, ItemStack outputPotion) {
            this.inputPotion = inputPotion;
            this.ingredient = ingredient;
            this.outputPotion = outputPotion;
        }
    }

    private BrewingRecipeManager() {}

    public static BrewingRecipeManager getInstance() {
        if (instance == null) {
            instance = new BrewingRecipeManager();
        }
        return instance;
    }

    /**
     * Construit le cache des recettes de brassage.
     *
     * L'API est appelee DIRECTEMENT, sans reflexion. L'ancien code passait par
     * Class.forName sur des noms Minecraft, ce qui ne pouvait pas aboutir : sous
     * Fabric le jeu tourne en mappings intermediary et Loom ne remappe pas le
     * contenu des chaines. L'onglet brassage etait donc vide depuis toujours.
     *
     * Attention a l'ordre des arguments, releve au bytecode et NON symetrique :
     *     hasRecipe(potion, ingredient)  --  teste POTION_TYPE_PREDICATE sur le 1er argument
     *     craft(ingredient, potion)      --  lit PotionUtil.getPotion() sur le 2e
     */
    public synchronized void ensureCacheBuilt() {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBrewing()) return;

        if (isCacheBuilt) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            cache.clear();

            // Contrairement aux versions Mojmap, ou les melanges vivent cote
            // serveur, BrewingRecipeRegistry est ici statique et renseigne au
            // demarrage : pas besoin du serveur integre, et ca marche donc aussi
            // en multijoueur.
            List<Item> potionItems = Arrays.asList(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
            List<Item> brewingIngredients = Arrays.asList(
                    Items.NETHER_WART,
                    Items.REDSTONE,
                    Items.GLOWSTONE_DUST,
                    Items.FERMENTED_SPIDER_EYE,
                    Items.GUNPOWDER,
                    Items.DRAGON_BREATH,
                    Items.GHAST_TEAR,
                    Items.GOLDEN_CARROT,
                    Items.BLAZE_POWDER,
                    Items.SPIDER_EYE,
                    Items.PUFFERFISH,
                    Items.MAGMA_CREAM,
                    Items.RABBIT_FOOT,
                    Items.GLISTERING_MELON_SLICE,
                    Items.SUGAR,
                    Items.PHANTOM_MEMBRANE,
                    Items.TURTLE_HELMET
            );

            var allPotions = Registries.POTION.streamEntries().toList();
            int totalRecipes = 0;

            for (var potionEntry : allPotions) {
                for (Item potionItem : potionItems) {
                    ItemStack inputPotion = new ItemStack(potionItem, 1);
                    PotionUtil.setPotion(inputPotion, potionEntry.value());

                    for (Item ingredientItem : brewingIngredients) {
                        ItemStack ingredient = new ItemStack(ingredientItem, 1);
                        // hasRecipe prend (potion, ingredient), craft prend (ingredient, potion).
                        if (!BrewingRecipeRegistry.hasRecipe(inputPotion, ingredient)) {
                            continue;
                        }
                        ItemStack result = BrewingRecipeRegistry.craft(ingredient, inputPotion);
                        if (result == null || result.isEmpty()) {
                            continue;
                        }
                        Identifier resultId = FavoriteItemsManager.getUniqueItemId(result);
                        cache.computeIfAbsent(resultId, k -> new ArrayList<>()).add(
                                new BrewingRecipe(inputPotion.copy(), ingredient.copy(), result.copy()));
                        totalRecipes++;
                    }
                }
            }

            isCacheBuilt = true;
            LOGGER.info("[BREWING] Cache construit en {} ms, {} recettes indexees.",
                    System.currentTimeMillis() - startTime, totalRecipes);

        } catch (Exception | LinkageError e) {
            isCacheBuilt = true;
            LOGGER.warn("[BREWING] API de brassage indisponible ({}).", e.toString());
        }
    }

    /**
     * Trouve les recettes de brassage qui produisent l'ItemStack specifie.
     */
    public List<BrewingRecipe> getRecipesForOutput(ItemStack output) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBrewing()) return java.util.List.of();

        ensureCacheBuilt();
        Identifier outputId = FavoriteItemsManager.getUniqueItemId(output);
        return cache.getOrDefault(outputId, Collections.emptyList());
    }

    public List<BrewingRecipe> getRecipesForInput(ItemStack input) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBrewing()) return java.util.List.of();

        ensureCacheBuilt();
        List<BrewingRecipe> list = new ArrayList<>();

        for (List<BrewingRecipe> recipes : cache.values()) {
            for (BrewingRecipe recipe : recipes) {
                if (recipe.ingredient.isOf(input.getItem())) {
                    list.add(recipe);
                } else if (recipe.inputPotion.isOf(input.getItem())) {
                    Potion inputPotionType = PotionUtil.getPotion(input);
                    Potion recipePotionType = PotionUtil.getPotion(recipe.inputPotion);
                    if (inputPotionType.equals(recipePotionType)) {
                        list.add(recipe);
                    }
                }
            }
        }
        return list;
    }

    public void clearCache() {
        cache.clear();
        isCacheBuilt = false;
    }
}


