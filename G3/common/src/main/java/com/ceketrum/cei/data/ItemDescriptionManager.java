package com.ceketrum.cei.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Gère le chargement et l'accès aux descriptions d'items depuis des fichiers JSON.
 * Supporte plusieurs langues et charge automatiquement la langue actuelle du jeu.
 */
public class ItemDescriptionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-descriptions");
    private static final String DESCRIPTIONS_PATH = "assets/cei/descriptions/descriptions_%s.json";
    
    private static ItemDescriptionManager instance;
    private final Map<String, String> descriptions = new HashMap<>();
    private String currentLanguage = "en_us";
    
    private ItemDescriptionManager() {
        // Constructeur privé pour singleton
    }
    
    public static ItemDescriptionManager getInstance() {
        if (instance == null) {
            instance = new ItemDescriptionManager();
        }
        return instance;
    }
    
    /**
     * Charge les descriptions pour la langue spécifiée.
     * @param languageCode Code de langue (ex: "fr_fr", "en_us")
     */
    public void loadDescriptions(String languageCode) {
        if (currentLanguage.equals(languageCode) && !descriptions.isEmpty()) {
            // Déjà chargé pour cette langue
            return;
        }
        
        descriptions.clear();
        currentLanguage = languageCode;
        
        String resourcePath = String.format(DESCRIPTIONS_PATH, languageCode);
        LOGGER.debug("Tentative de chargement depuis: {}", resourcePath);
        
        try {
            // Essayer plusieurs chemins possibles et plusieurs classloaders
            InputStream inputStream = null;
            ClassLoader classLoader = ItemDescriptionManager.class.getClassLoader();
            
            // Essayer d'abord avec le chemin complet
            inputStream = classLoader.getResourceAsStream(resourcePath);
            if (inputStream != null) {
                LOGGER.info("Fichier trouvé avec le chemin: {}", resourcePath);
            }
            
            // Si le chemin avec "assets/" ne fonctionne pas, essayer sans le préfixe
            if (inputStream == null) {
                String altPath1 = String.format("cei/descriptions/descriptions_%s.json", languageCode);
                inputStream = classLoader.getResourceAsStream(altPath1);
                if (inputStream != null) {
                    LOGGER.info("Fichier trouvé avec le chemin alternatif 1: {}", altPath1);
                }
            }
            
            // Essayer un autre chemin alternatif
            if (inputStream == null) {
                String altPath2 = String.format("descriptions/descriptions_%s.json", languageCode);
                inputStream = classLoader.getResourceAsStream(altPath2);
                if (inputStream != null) {
                    LOGGER.info("Fichier trouvé avec le chemin alternatif 2: {}", altPath2);
                }
            }
            
            // Essayer avec Thread.currentThread().getContextClassLoader()
            if (inputStream == null) {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                if (contextClassLoader != null) {
                    inputStream = contextClassLoader.getResourceAsStream(resourcePath);
                    if (inputStream != null) {
                        LOGGER.info("Fichier trouvé avec ContextClassLoader: {}", resourcePath);
                    }
                }
            }
            
            if (inputStream == null) {
                LOGGER.warn("Fichier de descriptions non trouvé: {} (et chemin alternatif). Utilisation de la langue par défaut (en_us).", resourcePath);
                // Essayer de charger en_us en fallback
                if (!languageCode.equals("en_us")) {
                    loadDescriptions("en_us");
                }
                return;
            }
            
            JsonObject json = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                String itemId = entry.getKey();
                String description = entry.getValue().getAsString();
                descriptions.put(itemId, description);
            }
            
            LOGGER.info("Chargé {} descriptions depuis {}", descriptions.size(), resourcePath);
            inputStream.close();
            
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement des descriptions depuis {}", resourcePath, e);
            // Essayer de charger en_us en fallback
            if (!languageCode.equals("en_us")) {
                loadDescriptions("en_us");
            }
        }
    }
    
    /**
     * Charge les descriptions pour la langue actuelle du jeu.
     */
    public void loadCurrentLanguageDescriptions() {
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getLanguageManager() != null) {
                String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
                LOGGER.info("Chargement des descriptions pour la langue: {}", languageCode);
                loadDescriptions(languageCode);
            } else {
                // Fallback vers en_us si le client n'est pas encore initialisé
                LOGGER.info("Client non initialisé, chargement de la langue par défaut: en_us");
                loadDescriptions("en_us");
            }
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement de la langue actuelle, utilisation de en_us", e);
            loadDescriptions("en_us");
        }
    }
    
    /**
     * Force le rechargement des descriptions pour la langue actuelle.
     * Utile quand la langue change ou quand l'écran s'ouvre.
     */
    public void reloadCurrentLanguageDescriptions() {
        currentLanguage = ""; // Force le rechargement
        loadCurrentLanguageDescriptions();
    }
    
    /**
     * Récupère la description d'un item.
     * @param item L'item dont on veut la description
     * @return La description de l'item, ou une chaîne vide si non trouvée
     */
    public String getDescription(Item item) {
        // S'assurer que les descriptions sont chargées
        if (descriptions.isEmpty()) {
            LOGGER.warn("Aucune description chargée, tentative de rechargement...");
            loadCurrentLanguageDescriptions();
        }
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String itemIdString = itemId.toString();
        
        // Chercher d'abord avec l'ID complet (ex: "minecraft:dirt")
        String description = descriptions.get(itemIdString);
        if (description != null && !description.isEmpty()) {
            return description;
        }
        
        // Si non trouvé, chercher avec seulement le path (ex: "dirt")
        // Cela permet d'avoir des descriptions génériques
        String path = itemId.getPath();
        description = descriptions.get(path);
        if (description != null && !description.isEmpty()) {
            return description;
        }
        
        return "";
    }
    
    /**
     * Retourne le nombre de descriptions chargées (pour débogage).
     */
    public int getLoadedDescriptionsCount() {
        return descriptions.size();
    }
    
    /**
     * Retourne la langue actuellement chargée (pour débogage).
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Récupère la description d'un item par son ID.
     * @param itemId L'ID de l'item (ex: "minecraft:dirt" ou "dirt")
     * @return La description de l'item, ou une chaîne vide si non trouvée
     */
    public String getDescriptionById(String itemId) {
        String description = descriptions.get(itemId);
        return description != null ? description : "";
    }
    
    /**
     * Vérifie si une description existe pour un item.
     */
    public boolean hasDescription(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return descriptions.containsKey(itemId.toString()) || descriptions.containsKey(itemId.getPath());
    }
}



