package com.ceketrum.cei.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    private void ensureCacheBuilt() {
        if (isCacheBuilt) return;
        
        MinecraftServer server = null;
        try {
            // Utiliser la réflexion pour récupérer le serveur sans dépendances directes
            var client = MinecraftClient.getInstance();
            if (client != null) {
                server = client.getServer();
            }
        } catch (Exception e) {}
        
        if (server == null) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                isCacheBuilt = true;
                LOGGER.warn("[BREWING] Nombre maximum de tentatives d'accès au serveur atteint en solo. Skip.");
            }
            return;
        }
        
        try {
            LOGGER.info("[BREWING] Début de la construction du cache des recettes de brewing...");
            long startTime = System.currentTimeMillis();
            
            cache.clear();
            
            // Récupérer le BrewingRecipeRegistry du serveur via réflexion
            // En 1.20.1/1.20.4, le BrewingRecipeRegistry a des méthodes statiques et des instances
            // On peut interroger le registre de potions vanilla ou utiliser les méthodes du jeu.
            // On utilise la classe BrewingRecipeRegistry directement.
            Class<?> brewingRegistryClass = Class.forName("net.minecraft.recipe.BrewingRecipeRegistry");
            
            // Méthode de vérification et de craft
            // En Yarn 1.20.1/1.20.4 :
            // boolean hasRecipe(ItemStack input, ItemStack ingredient)
            // ItemStack craft(ItemStack input, ItemStack ingredient)
            // On cherche ces méthodes via réflexion
            Method hasRecipeMethod = null;
            Method craftMethod = null;
            Object brewingRegistry = null;
            
            // Essayer d'abord la méthode statique directe ou l'instance
            for (Method m : brewingRegistryClass.getDeclaredMethods()) {
                if (m.getName().equals("hasRecipe") || m.getName().equals("method_8076")) {
                    m.setAccessible(true);
                    hasRecipeMethod = m;
                } else if (m.getName().equals("craft") || m.getName().equals("method_8071")) {
                    m.setAccessible(true);
                    craftMethod = m;
                }
            }
            
            if (hasRecipeMethod == null || craftMethod == null) {
                LOGGER.error("[BREWING] Impossible de trouver les méthodes hasRecipe ou craft sur BrewingRecipeRegistry.");
                return;
            }

            // Générer la liste des entrées possibles
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
            
            // Ajouter d'autres ingrédients communs trouvés dans le registre si nécessaire
            var allPotions = Registries.POTION.streamEntries().toList();
            int totalRecipes = 0;
            
            for (var potionEntry : allPotions) {
                for (Item potionItem : potionItems) {
                    ItemStack inputPotion = new ItemStack(potionItem, 1);
                    PotionUtil.setPotion(inputPotion, potionEntry.value());
                    
                    for (Item ingredientItem : brewingIngredients) {
                        ItemStack ingredient = new ItemStack(ingredientItem, 1);
                        try {
                            Boolean hasRecipe = (Boolean) hasRecipeMethod.invoke(null, inputPotion, ingredient);
                            if (Boolean.TRUE.equals(hasRecipe)) {
                                ItemStack result = (ItemStack) craftMethod.invoke(null, inputPotion, ingredient);
                                if (!result.isEmpty()) {
                                    Identifier resultId = FavoriteItemsManager.getUniqueItemId(result);
                                    cache.computeIfAbsent(resultId, k -> new ArrayList<>()).add(
                                        new BrewingRecipe(inputPotion.copy(), ingredient.copy(), result.copy())
                                    );
                                    totalRecipes++;
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            }
            
            isCacheBuilt = true;
            LOGGER.info("[BREWING] Cache des recettes de brewing construit avec succès en {} ms. {} recettes indexées.", 
                        System.currentTimeMillis() - startTime, totalRecipes);
            
        } catch (Exception e) {
            LOGGER.error("[BREWING] Erreur inattendue pendant la construction du cache", e);
        }
    }

    public List<BrewingRecipe> getRecipesForOutput(ItemStack output) {
        ensureCacheBuilt();
        Identifier outputId = FavoriteItemsManager.getUniqueItemId(output);
        return cache.getOrDefault(outputId, Collections.emptyList());
    }

    public List<BrewingRecipe> getRecipesForInput(ItemStack input) {
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

