package com.ceketrum.cei.gui.module.cei.util;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Utilitaires pour filtrer les items selon un critère de recherche.
 */
public class ItemFilter {
    
    /**
     * Filtre une liste d'items selon un texte de recherche.
     * La recherche se fait sur :
     * - Le nom de l'item (localisé)
     * - L'ID de l'item (modid:itemid)
     * - Le nom simple de l'item (itemid)
     * 
     * @param items La liste d'items à filtrer
     * @param searchText Le texte de recherche (insensible à la casse)
     * @return La liste filtrée
     */
    public static List<ItemStack> filterItems(List<ItemStack> items, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return items;
        }
        
        String lowerSearch = searchText.toLowerCase().trim();
        
        return items.stream()
                .filter(stack -> {
                    // Recherche par nom localisé
                    String itemName = stack.getHoverName().getString().toLowerCase();
                    if (itemName.contains(lowerSearch)) {
                        return true;
                    }
                    
                    // Recherche par ID complet (ex: "minecraft:dirt")
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    String fullId = itemId.toString().toLowerCase();
                    if (fullId.contains(lowerSearch)) {
                        return true;
                    }
                    
                    // Recherche par nom simple (ex: "dirt")
                    String simpleId = itemId.getPath().toLowerCase();
                    if (simpleId.contains(lowerSearch)) {
                        return true;
                    }
                    
                    // Recherche par namespace (ex: "minecraft")
                    String namespace = itemId.getNamespace().toLowerCase();
                    if (namespace.contains(lowerSearch)) {
                        return true;
                    }
                    
                    return false;
                })
                .collect(Collectors.toList());
    }
}



