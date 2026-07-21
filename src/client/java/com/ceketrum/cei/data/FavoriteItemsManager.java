package com.ceketrum.cei.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Gère la sauvegarde et le chargement des items favoris.
 * Utilise un fichier JSON pour persister les favoris entre les sessions.
 */
public class FavoriteItemsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-favorites");
    private static final String FAVORITES_FILE = "favorites.json";
    
    private static FavoriteItemsManager instance;
    private final Set<Identifier> favorites = new HashSet<>();
    private final Path configFile;
    
    private FavoriteItemsManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("cei");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Impossible de créer le dossier de configuration", e);
        }
        this.configFile = configDir.resolve(FAVORITES_FILE);
        load();
    }
    
    public static FavoriteItemsManager getInstance() {
        if (instance == null) {
            instance = new FavoriteItemsManager();
        }
        return instance;
    }
    
    /**
     * Ajoute un item aux favoris.
     * @param itemId L'identifiant de l'item
     */
    public void addFavorite(Identifier itemId) {
        favorites.add(itemId);
        save();
    }
    
    /**
     * Retire un item des favoris.
     * @param itemId L'identifiant de l'item
     */
    public void removeFavorite(Identifier itemId) {
        favorites.remove(itemId);
        save();
    }
    
    /**
     * Vérifie si un item est dans les favoris.
     * @param itemId L'identifiant de l'item
     * @return true si l'item est favori, false sinon
     */
    public boolean isFavorite(Identifier itemId) {
        return favorites.contains(itemId);
    }
    
    /**
     * Vérifie si un ItemStack est dans les favoris (prend en compte les Data Components).
     * @param stack L'ItemStack à vérifier
     * @return true si l'item est favori, false sinon
     */
    public boolean isFavorite(ItemStack stack) {
        Identifier uniqueId = getUniqueItemId(stack);
        return favorites.contains(uniqueId);
    }
    
    /**
     * Génère un identifiant unique pour un ItemStack, incluant les Data Components.
     * Pour les potions, inclut l'ID de la potion. Pour les autres items, utilise seulement l'ID de l'item.
     * @param stack L'ItemStack
     * @return Un identifiant unique pour cet ItemStack avec ses Data Components
     */
    public static Identifier getUniqueItemId(ItemStack stack) {
        Identifier baseId = Registries.ITEM.getId(stack.getItem());
        
        // Pour les potions, ajouter l'ID de la potion à l'identifiant
        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null && potionContents.potion().isPresent()) {
            Identifier potionId = potionContents.potion().get().getKey().orElse(null).getValue();
            if (potionId != null) {
                // Créer un identifiant composé avec un séparateur valide (_) au lieu de :
                // Format: "namespace_item_path_potion_namespace_potion_path"
                String combinedPath = baseId.getPath() + "_" + potionId.getNamespace() + "_" + potionId.getPath().replace("/", "_");
                return Identifier.of(baseId.getNamespace(), combinedPath);
            }
        }
        
        // Pour les autres items, retourner seulement l'ID de l'item
        return baseId;
    }
    
    /**
     * Ajoute un ItemStack aux favoris (prend en compte les Data Components).
     * @param stack L'ItemStack à ajouter
     */
    public void addFavorite(ItemStack stack) {
        Identifier uniqueId = getUniqueItemId(stack);
        addFavorite(uniqueId);
    }
    
    /**
     * Retire un ItemStack des favoris (prend en compte les Data Components).
     * @param stack L'ItemStack à retirer
     */
    public void removeFavorite(ItemStack stack) {
        Identifier uniqueId = getUniqueItemId(stack);
        removeFavorite(uniqueId);
    }
    
    /**
     * Toggle le statut favori d'un item.
     * @param itemId L'identifiant de l'item
     * @return true si l'item est maintenant favori, false sinon
     */
    public boolean toggleFavorite(Identifier itemId) {
        if (isFavorite(itemId)) {
            removeFavorite(itemId);
            return false;
        } else {
            addFavorite(itemId);
            return true;
        }
    }
    
    /**
     * Toggle le statut favori d'un ItemStack (prend en compte les Data Components).
     * @param stack L'ItemStack à toggle
     * @return true si l'item est maintenant favori, false sinon
     */
    public boolean toggleFavorite(ItemStack stack) {
        Identifier uniqueId = getUniqueItemId(stack);
        return toggleFavorite(uniqueId);
    }
    
    /**
     * Récupère tous les favoris.
     * @return Un Set contenant tous les identifiants des items favoris
     */
    public Set<Identifier> getFavorites() {
        return new HashSet<>(favorites);
    }
    
    /**
     * Charge les favoris depuis le fichier JSON.
     */
    public void load() {
        if (!Files.exists(configFile)) {
            LOGGER.info("Fichier de favoris non trouvé, création d'un nouveau fichier");
            return;
        }
        
        try {
            String content = Files.readString(configFile);
            JsonArray jsonArray = JsonParser.parseString(content).getAsJsonArray();
            
            favorites.clear();
            for (JsonElement element : jsonArray) {
                String itemIdString = element.getAsString();
                Identifier itemId = Identifier.tryParse(itemIdString);
                if (itemId != null) {
                    favorites.add(itemId);
                } else {
                    LOGGER.warn("Identifiant invalide dans les favoris: {}", itemIdString);
                }
            }
            
            LOGGER.info("Chargé {} favoris depuis {}", favorites.size(), configFile);
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement des favoris depuis {}", configFile, e);
        }
    }
    
    /**
     * Sauvegarde les favoris dans le fichier JSON.
     */
    public void save() {
        try {
            JsonArray jsonArray = new JsonArray();
            for (Identifier itemId : favorites) {
                jsonArray.add(itemId.toString());
            }
            
            Files.writeString(configFile, jsonArray.toString());
            LOGGER.debug("Sauvegardé {} favoris dans {}", favorites.size(), configFile);
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la sauvegarde des favoris dans {}", configFile, e);
        }
    }
}


