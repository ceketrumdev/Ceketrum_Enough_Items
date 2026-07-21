package com.ceketrum.cei.gui.module.cei.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

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
        for (ResourceKey<Potion> potionKey : BuiltInRegistries.POTION.registryKeySet()) {
            try {
                Holder<Potion> potionEntry = BuiltInRegistries.POTION.get(potionKey).orElse(null);
                if (potionEntry != null) {
                    // Créer un ItemStack avec cette potion
                    ItemStack stack = new ItemStack(potionItem, 1);
                    
                    // Ajouter le composant POTION_CONTENTS avec l'entry de potion
                    PotionContents potionContents = new PotionContents(potionEntry);
                    stack.set(DataComponents.POTION_CONTENTS, potionContents);
                    
                    if (!stack.isEmpty()) {
                        variants.add(stack);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Impossible de créer une variante de potion pour {}", potionKey.location(), e);
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


