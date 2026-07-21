package com.ceketrum.cei.gui.module.cei.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Utilitaire pour détecter si un item peut être obtenu via loot tables.
 */
public class LootTableHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-loot-tables");
    
    /**
     * Trouve les sources de loot tables qui contiennent un item donné.
     * 
     * @param item L'item à chercher
     * @param lootManager Le gestionnaire de loot tables (peut être null)
     * @return Une liste de noms de sources de loot tables contenant cet item
     */
    public static List<String> getLootTableSources(Item item, Object lootManager) {
        List<String> sources = new ArrayList<>();
        
        if (lootManager == null) {
            return sources;
        }
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return sources;
        }
        
        // TODO: Implémenter une recherche complète dans toutes les loot tables
        // Pour l'instant, cette méthode retourne une liste vide
        // Une vraie implémentation devrait parcourir toutes les loot tables du jeu
        
        return sources;
    }
}



