package com.ceketrum.cei.data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
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
    
    // Cache des recettes trouvées pour éviter de recalculer
    private final Map<Identifier, List<BrewingRecipe>> cache = new HashMap<>();
    
    /**
     * Représente une recette de brewing.
     */
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
    
    private BrewingRecipeManager() {
        // Pas de chargement statique, les recettes sont récupérées dynamiquement depuis le serveur
    }
    
    public static BrewingRecipeManager getInstance() {
        if (instance == null) {
            instance = new BrewingRecipeManager();
        }
        return instance;
    }
    
    private boolean isCacheBuilt = false;
    private int attempts = 0;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Passe a true si l'exploration echoue sur une API absente de la version
     * courante. Les classes de generation / loot / brassage bougent d'une
     * version a l'autre (ConfiguredFeature a par exemple disparu en 26.3) :
     * une fonctionnalite annexe ne doit pas faire tomber le client.
     */
    private volatile boolean cei$degraded = false;

    /**
     * Construit le cache des recettes de brassage.
     *
     * L'API est appelee DIRECTEMENT, sans reflexion. L'ancien code passait par
     * Class.forName sur des noms Minecraft, ce qui ne pouvait pas aboutir : sous
     * Fabric le jeu tourne en mappings intermediary et Loom ne remappe pas le
     * contenu des chaines. L'onglet brassage etait donc vide depuis toujours.
     *
     * Attention a l'ordre des arguments, releve au bytecode et NON symetrique :
     *     hasMix(potion, ingredient)   --  teste isContainer() sur le 1er argument
     *     mix(ingredient, potion)      --  lit POTION_CONTENTS sur le 2e
     */
    public synchronized void ensureCacheBuilt() {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBrewing()) return;

        if (isCacheBuilt) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }

        // Les melanges de brassage vivent cote serveur : en multijoueur on ne
        // peut que constater l'absence, sans reessayer a chaque ouverture.
        if (!client.isLocalServer()) {
            isCacheBuilt = true;
            LOGGER.info("[BREWING] Client en multijoueur : recettes de brassage indisponibles.");
            return;
        }

        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                isCacheBuilt = true;
                LOGGER.warn("[BREWING] Serveur integre inaccessible apres {} tentatives.", MAX_ATTEMPTS);
            }
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            cache.clear();

            var brewing = server.potionBrewing();
            if (brewing == null) {
                isCacheBuilt = true;
                LOGGER.warn("[BREWING] Aucune table de brassage cote serveur.");
                return;
            }

            List<Item> potionItems = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);
            List<Item> brewingIngredients = List.of(
                    Items.NETHER_WART,
                    Items.GLISTERING_MELON_SLICE,
                    Items.MAGMA_CREAM,
                    Items.GOLDEN_CARROT,
                    Items.FERMENTED_SPIDER_EYE,
                    Items.REDSTONE,
                    Items.GLOWSTONE_DUST,
                    Items.GUNPOWDER,
                    Items.DRAGON_BREATH,
                    Items.SUGAR,
                    Items.RABBIT_FOOT,
                    Items.PUFFERFISH,
                    Items.TURTLE_HELMET,
                    Items.PHANTOM_MEMBRANE
            );

            var allPotions = new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>>();
            for (var key : BuiltInRegistries.POTION.registryKeySet()) {
                BuiltInRegistries.POTION.get(key).ifPresent(allPotions::add);
            }
            int totalRecipes = 0;

            for (var potionEntry : allPotions) {
                for (Item potionItem : potionItems) {
                    ItemStack inputPotion = new ItemStack(potionItem, 1);
                    inputPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(potionEntry));

                    for (Item ingredientItem : brewingIngredients) {
                        ItemStack ingredient = new ItemStack(ingredientItem, 1);
                        // hasMix prend (potion, ingredient), mix prend (ingredient, potion).
                        if (!brewing.hasMix(inputPotion, ingredient)) {
                            continue;
                        }
                        ItemStack result = brewing.mix(ingredient, inputPotion);
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
            // 26.3 a supprime PotionBrewing : le brassage y est devenu un vrai
            // type de recette (BrewingRecipe), deja couvert par le pipeline de
            // recettes. On degrade en une ligne au lieu de reessayer sans fin.
            isCacheBuilt = true;
            LOGGER.warn("[BREWING] API de brassage indisponible sur cette version ({}). "
                    + "En 26.3 les recettes de brassage passent par le pipeline de recettes.", e.toString());
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

    /**
     * Trouve les recettes de brewing qui utilisent l'ItemStack spécifié comme ingrédient ou potion d'entrée.
     */
    public List<BrewingRecipe> getRecipesForInput(ItemStack input) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBrewing()) return java.util.List.of();

        ensureCacheBuilt();
        List<BrewingRecipe> list = new ArrayList<>();
        
        for (List<BrewingRecipe> recipes : cache.values()) {
            for (BrewingRecipe recipe : recipes) {
                if (recipe.ingredient.is(input.getItem())) {
                    list.add(recipe);
                } else if (recipe.inputPotion.is(input.getItem())) {
                    PotionContents inputContents = input.get(DataComponents.POTION_CONTENTS);
                    PotionContents recipeContents = recipe.inputPotion.get(DataComponents.POTION_CONTENTS);
                    if (inputContents != null && recipeContents != null) {
                        if (inputContents.potion().equals(recipeContents.potion())) {
                            list.add(recipe);
                        }
                    } else if (inputContents == null && recipeContents == null) {
                        list.add(recipe);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Vide le cache des recettes.
     * Utile si les recettes changent (rechargement de ressources, etc.).
     */
    public void clearCache() {
        cache.clear();
        isCacheBuilt = false;
    }
}

