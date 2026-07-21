package com.ceketrum.cei.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
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

    public synchronized void ensureCacheBuilt() {
        if (isCacheBuilt) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        
        // En multijoueur, pas de serveur intégré local. On skip le scan serveur de recettes pour éviter les spams/lags
        if (!client.isInSingleplayer()) {
            isCacheBuilt = true;
            LOGGER.info("[BREWING] Client en multijoueur, skip du scan serveur des recettes de brewing.");
            return;
        }
        
        // Essayer plusieurs méthodes pour accéder au serveur
        MinecraftServer server = null;
        if (client.player != null) {
            server = client.player.getServer();
        }
        if (server == null) {
            try {
                Method getServerMethod = client.getClass().getMethod("getServer");
                server = (MinecraftServer) getServerMethod.invoke(client);
            } catch (Exception e) {}
        }
        if (server == null && client.player != null && client.player.getWorld() != null) {
            try {
                Method getServerMethod = client.player.getWorld().getClass().getMethod("getServer");
                server = (MinecraftServer) getServerMethod.invoke(client.player.getWorld());
            } catch (Exception e) {}
        }
        if (server == null && client.world != null) {
            try {
                Method getServerMethod = client.world.getClass().getMethod("getServer");
                server = (MinecraftServer) getServerMethod.invoke(client.world);
            } catch (Exception e) {}
        }
        
        if (server == null) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                isCacheBuilt = true;
                LOGGER.warn("[BREWING] Nombre maximum de tentatives d'accès au serveur atteint en solo. Skip.");
            }
            return;
        }
        
        LOGGER.info("[BREWING] Construction du cache global des recettes de brewing...");
        long startTime = System.currentTimeMillis();
        
        try {
            Class<?> brewingRecipeRegistryClass = null;
            String[] possibleClassNames = {
                "net.minecraft.brewing.BrewingRecipeRegistry",
                "net.minecraft.item.BrewingRecipeRegistry",
                "net.minecraft.potion.BrewingRecipeRegistry",
                "net.minecraft.recipe.BrewingRecipeRegistry"
            };
            
            for (String className : possibleClassNames) {
                try {
                    brewingRecipeRegistryClass = Class.forName(className);
                    break;
                } catch (ClassNotFoundException e) {}
            }
            
            if (brewingRecipeRegistryClass == null) {
                LOGGER.error("[BREWING] BrewingRecipeRegistry non trouvé");
                return;
            }
            
            Method hasRecipeMethod = null;
            Method craftMethod = null;
            try {
                hasRecipeMethod = brewingRecipeRegistryClass.getMethod("hasRecipe", ItemStack.class, ItemStack.class);
                craftMethod = brewingRecipeRegistryClass.getMethod("craft", ItemStack.class, ItemStack.class);
            } catch (NoSuchMethodException e) {
                LOGGER.error("[BREWING] Méthodes d'instance non trouvées dans BrewingRecipeRegistry");
                return;
            }
            
            // Trouver la méthode create()
            Method createMethod = null;
            for (Method method : brewingRecipeRegistryClass.getDeclaredMethods()) {
                if (method.getName().equals("create")) {
                    createMethod = method;
                    createMethod.setAccessible(true);
                    break;
                }
            }
            if (createMethod == null) {
                for (Method method : brewingRecipeRegistryClass.getMethods()) {
                    if (method.getName().equals("create") && method.getDeclaringClass() != Object.class) {
                        createMethod = method;
                        break;
                    }
                }
            }
            if (createMethod == null) {
                LOGGER.error("[BREWING] Méthode create() non trouvée");
                return;
            }
            
            Object brewingRegistry = null;
            Object featureSet = null;
            try {
                if (createMethod.getParameterCount() == 0) {
                    brewingRegistry = createMethod.invoke(null);
                } else {
                    Class<?>[] paramTypes = createMethod.getParameterTypes();
                    Class<?> featureSetClass = paramTypes[0];
                    
                    try {
                        Method getSavePropertiesMethod = server.getClass().getMethod("getSaveProperties");
                        Object saveProperties = getSavePropertiesMethod.invoke(server);
                        if (saveProperties != null) {
                            Method getDataConfigurationMethod = saveProperties.getClass().getMethod("getDataConfiguration");
                            Object dataConfiguration = getDataConfigurationMethod.invoke(saveProperties);
                            if (dataConfiguration != null) {
                                Method getEnabledFeaturesMethod = dataConfiguration.getClass().getMethod("getEnabledFeatures");
                                Object result = getEnabledFeaturesMethod.invoke(dataConfiguration);
                                if (result != null && featureSetClass.isAssignableFrom(result.getClass())) {
                                    featureSet = result;
                                }
                            }
                        }
                    } catch (Exception e) {}
                    
                    if (featureSet == null) {
                        try {
                            var worlds = server.getWorlds();
                            var worldIterator = worlds.iterator();
                            if (worldIterator.hasNext()) {
                                var world = worldIterator.next();
                                Method getEnabledFeaturesMethod = world.getClass().getMethod("getEnabledFeatures");
                                Object result = getEnabledFeaturesMethod.invoke(world);
                                if (result != null && featureSetClass.isAssignableFrom(result.getClass())) {
                                    featureSet = result;
                                }
                            }
                        } catch (Exception e) {}
                    }
                    
                    if (featureSet != null) {
                        brewingRegistry = createMethod.invoke(null, featureSet);
                    } else {
                        try {
                            java.lang.reflect.Field[] featureSetFields = featureSetClass.getDeclaredFields();
                            for (java.lang.reflect.Field field : featureSetFields) {
                                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) 
                                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())
                                    && "EMPTY".equals(field.getName())
                                    && featureSetClass.isAssignableFrom(field.getType())) {
                                    field.setAccessible(true);
                                    Object emptyFeatureSet = field.get(null);
                                    if (emptyFeatureSet != null) {
                                        featureSet = emptyFeatureSet;
                                        brewingRegistry = createMethod.invoke(null, emptyFeatureSet);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
                
                if (brewingRegistry != null) {
                    try {
                        Method registerDefaultsMethod = null;
                        try {
                            registerDefaultsMethod = brewingRecipeRegistryClass.getMethod("registerDefaults");
                        } catch (NoSuchMethodException e) {
                            for (Method method : brewingRecipeRegistryClass.getMethods()) {
                                if (method.getName().equals("registerDefaults")) {
                                    registerDefaultsMethod = method;
                                    break;
                                }
                            }
                        }
                        
                        if (registerDefaultsMethod != null) {
                            registerDefaultsMethod.setAccessible(true);
                            if (registerDefaultsMethod.getParameterCount() == 0) {
                                registerDefaultsMethod.invoke(brewingRegistry);
                            } else if (featureSet != null) {
                                registerDefaultsMethod.invoke(brewingRegistry, featureSet);
                            }
                        }
                    } catch (Exception e) {}
                }
            } catch (Exception e) {
                LOGGER.error("[BREWING] Erreur de création de BrewingRecipeRegistry: {}", e.getMessage(), e);
                return;
            }
            
            if (brewingRegistry == null) {
                LOGGER.error("[BREWING] Aucune instance de BrewingRecipeRegistry");
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
            
            var allPotions = Registries.POTION.streamEntries().toList();
            int totalRecipes = 0;
            
            for (var potionEntry : allPotions) {
                for (Item potionItem : potionItems) {
                    ItemStack inputPotion = new ItemStack(potionItem, 1);
                    PotionContentsComponent potionContents = new PotionContentsComponent(potionEntry);
                    inputPotion.set(DataComponentTypes.POTION_CONTENTS, potionContents);
                    
                    for (Item ingredientItem : brewingIngredients) {
                        ItemStack ingredient = new ItemStack(ingredientItem, 1);
                        try {
                            Boolean hasRecipe = (Boolean) hasRecipeMethod.invoke(brewingRegistry, inputPotion, ingredient);
                            if (Boolean.TRUE.equals(hasRecipe)) {
                                ItemStack result = (ItemStack) craftMethod.invoke(brewingRegistry, inputPotion, ingredient);
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

    /**
     * Trouve les recettes de brewing qui produisent l'ItemStack spécifié.
     * Récupère les recettes depuis le serveur si disponible.
     */
    public List<BrewingRecipe> getRecipesForOutput(ItemStack output) {
        ensureCacheBuilt();
        Identifier outputId = FavoriteItemsManager.getUniqueItemId(output);
        return cache.getOrDefault(outputId, Collections.emptyList());
    }

    /**
     * Trouve les recettes de brewing qui utilisent l'ItemStack spécifié comme ingrédient ou potion d'entrée.
     */
    public List<BrewingRecipe> getRecipesForInput(ItemStack input) {
        ensureCacheBuilt();
        List<BrewingRecipe> list = new ArrayList<>();
        
        for (List<BrewingRecipe> recipes : cache.values()) {
            for (BrewingRecipe recipe : recipes) {
                if (recipe.ingredient.isOf(input.getItem())) {
                    list.add(recipe);
                } else if (recipe.inputPotion.isOf(input.getItem())) {
                    PotionContentsComponent inputContents = input.get(DataComponentTypes.POTION_CONTENTS);
                    PotionContentsComponent recipeContents = recipe.inputPotion.get(DataComponentTypes.POTION_CONTENTS);
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

