package com.ceketrum.cei.gui.module.cei.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire pour générer toutes les variantes d'items avec Data Components.
 * Principalement utilisé pour générer les variantes de potions.
 */
public class ItemVariantGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-item-variants");
    
    /**
     * Génère toutes les variantes d'un item avec ses Data Components.
     * 
     * @param item L'item pour lequel générer les variantes
     * @return Une liste d'ItemStack représentant toutes les variantes possibles
     */
    public static List<ItemStack> generateItemVariants(Item item) {
        List<ItemStack> variants = new ArrayList<>();
        
        // Gérer les potions (potion, splash_potion, lingering_potion, tipped_arrow)
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
            variants.addAll(generatePotionVariants(item));
            return variants;
        }
        
        // Pour tous les autres items, créer un ItemStack de base
        try {
            ItemStack baseStack = new ItemStack(item, 1);
            if (baseStack != null && !baseStack.isEmpty()) {
                variants.add(baseStack);
            }
        } catch (Exception e) {
            LOGGER.debug("Impossible de créer un ItemStack pour l'item {}", item, e);
        }
        
        return variants;
    }
    
    /**
     * Génère toutes les variantes de potions possibles.
     * 
     * @param potionItem Le type d'item de potion (POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW)
     * @return Une liste de toutes les variantes de potions
     */
    private static List<ItemStack> generatePotionVariants(Item potionItem) {
        List<ItemStack> variants = new ArrayList<>();
        
        // Parcourir toutes les potions enregistrées en utilisant getIds() pour obtenir toutes les clés
        for (RegistryKey<Potion> potionKey : Registries.POTION.getKeys()) {
            try {
                RegistryEntry<Potion> potionEntry = Registries.POTION.getEntry(potionKey).orElse(null);
                if (potionEntry != null) {
                    // Créer un ItemStack avec cette potion
                    ItemStack stack = new ItemStack(potionItem, 1);
                    
                    // Ajouter le composant POTION_CONTENTS avec l'entry de potion
                    PotionContentsComponent potionContents = new PotionContentsComponent(potionEntry);
                    stack.set(DataComponentTypes.POTION_CONTENTS, potionContents);
                    
                    if (!stack.isEmpty()) {
                        variants.add(stack);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Impossible de créer une variante de potion pour {}", potionKey.getValue(), e);
            }
        }
        
        // Si aucune variante n'a été créée, créer au moins une potion vide
        if (variants.isEmpty()) {
            try {
                ItemStack emptyStack = new ItemStack(potionItem, 1);
                variants.add(emptyStack);
            } catch (Exception e) {
                LOGGER.warn("Impossible de créer même une potion vide pour {}", potionItem, e);
            }
        }
        
        return variants;
    }
}


