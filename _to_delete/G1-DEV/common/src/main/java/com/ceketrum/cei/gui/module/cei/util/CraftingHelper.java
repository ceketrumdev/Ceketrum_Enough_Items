package com.ceketrum.cei.gui.module.cei.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Classe utilitaire pour placer automatiquement les ingrédients d'une recette dans la table de craft.
 */
public class CraftingHelper {
    
    /**
     * Place les ingrédients d'une recette dans la grille de craft.
     * 
     * @param handler Le CraftingScreenHandler
     * @param recipe La recette à placer
     * @param registryManager Le DynamicRegistryManager
     * @param player Le joueur client
     * @param quantity La quantité à craft (1 pour un seul item, -1 pour le maximum possible)
     * @return true si le placement a réussi, false sinon
     */
    public static boolean placeRecipeIngredients(CraftingScreenHandler handler, Recipe<?> recipe,
                                                 DynamicRegistryManager registryManager,
                                                 ClientPlayerEntity player, int quantity) {
        System.out.println("[CEI-DEBUG] placeRecipeIngredients appelé - quantity: " + quantity);
        
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            System.out.println("[CEI-DEBUG] Échec: recipe n'est pas une CraftingRecipe");
            return false;
        }
        
        // Récupérer les ingrédients de la recette
        var ingredients = craftingRecipe.getIngredients();
        System.out.println("[CEI-DEBUG] Nombre d'ingrédients: " + ingredients.size());
        
        // Déterminer si c'est une recette shaped ou shapeless
        boolean isShaped = recipe instanceof ShapedRecipe;
        boolean isShapeless = recipe instanceof ShapelessRecipe;
        System.out.println("[CEI-DEBUG] isShaped: " + isShaped + ", isShapeless: " + isShapeless);
        
        // Pour les recettes shaped, on doit placer les ingrédients aux positions exactes
        // Pour les recettes shapeless, on peut les placer dans n'importe quel slot libre
        
        // Les slots de craft vont de 1 à 9 (slot 0 = résultat)
        // Slot 1 = position (0,0), Slot 2 = (0,1), Slot 3 = (0,2)
        // Slot 4 = position (1,0), Slot 5 = (1,1), Slot 6 = (1,2)
        // Slot 7 = position (2,0), Slot 8 = (2,1), Slot 9 = (2,2)
        
        // Calculer la quantité à craft
        int craftQuantity = quantity;
        if (quantity == -1) {
            // Calculer le maximum possible
            craftQuantity = calculateMaxCraftable(handler, craftingRecipe, registryManager, player);
            System.out.println("[CEI-DEBUG] Quantité maximale calculée: " + craftQuantity);
        }
        
        if (craftQuantity <= 0) {
            System.out.println("[CEI-DEBUG] Échec: craftQuantity <= 0");
            return false;
        }
        
        System.out.println("[CEI-DEBUG] Placement de " + craftQuantity + " items");
        
        // Vider la table de craft avant de placer les ingrédients
        clearCraftingGrid(handler, player);
        
        if (isShaped && recipe instanceof ShapedRecipe shapedRecipe) {
            System.out.println("[CEI-DEBUG] Placement d'une recette shaped");
            return placeShapedRecipe(handler, shapedRecipe, registryManager, player, craftQuantity);
        } else if (isShapeless) {
            System.out.println("[CEI-DEBUG] Placement d'une recette shapeless");
            return placeShapelessRecipe(handler, craftingRecipe, registryManager, player, craftQuantity);
        }
        
        System.out.println("[CEI-DEBUG] Échec: type de recette non reconnu");
        return false;
    }
    
    /**
     * Vide la grille de craft en remettant tous les items dans l'inventaire du joueur.
     * Essaie de restacker avec les items existants dans l'inventaire.
     */
    private static void clearCraftingGrid(CraftingScreenHandler handler, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) {
            return;
        }
        
        // Vider tous les slots de craft (1-9)
        for (int slot = 1; slot <= 9; slot++) {
            var stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty()) {
                // Prendre l'item du slot de craft (va dans le curseur)
                client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
                
                // Vérifier ce qui est dans le curseur maintenant
                var cursorStack = handler.getCursorStack();
                if (cursorStack.isEmpty()) {
                    continue; // Déjà placé quelque part
                }
                
                // Chercher un slot avec le même item pour restacker
                boolean placed = false;
                
                // D'abord, chercher tous les slots avec le même item qui ne sont pas pleins
                // Essayer de restacker progressivement jusqu'à ce que le curseur soit vide
                while (!cursorStack.isEmpty() && !placed) {
                    boolean foundCompatibleSlot = false;
                    
                    // Chercher un slot compatible dans l'inventaire principal
                    for (int invSlot = 10; invSlot <= 36 && !cursorStack.isEmpty(); invSlot++) {
                        var invStack = handler.getSlot(invSlot).getStack();
                        if (!invStack.isEmpty() && canStack(invStack, cursorStack) && 
                            invStack.getCount() < invStack.getMaxCount()) {
                            // Placer dans ce slot (va automatiquement restacker)
                            client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, player);
                            cursorStack = handler.getCursorStack();
                            foundCompatibleSlot = true;
                            if (cursorStack.isEmpty()) {
                                placed = true;
                                break;
                            }
                        }
                    }
                    
                    // Si toujours pas vide, chercher dans la hotbar
                    if (!cursorStack.isEmpty() && !foundCompatibleSlot) {
                        for (int invSlot = 37; invSlot <= 45 && !cursorStack.isEmpty(); invSlot++) {
                            var invStack = handler.getSlot(invSlot).getStack();
                            if (!invStack.isEmpty() && canStack(invStack, cursorStack) && 
                                invStack.getCount() < invStack.getMaxCount()) {
                                client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, player);
                                cursorStack = handler.getCursorStack();
                                foundCompatibleSlot = true;
                                if (cursorStack.isEmpty()) {
                                    placed = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Si on n'a pas trouvé de slot compatible ou si le curseur n'est toujours pas vide
                    if (!foundCompatibleSlot || !cursorStack.isEmpty()) {
                        // Chercher un slot vide
                        for (int invSlot = 10; invSlot <= 36 && !cursorStack.isEmpty(); invSlot++) {
                            if (handler.getSlot(invSlot).getStack().isEmpty()) {
                                client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, player);
                                cursorStack = handler.getCursorStack();
                                if (cursorStack.isEmpty()) {
                                    placed = true;
                                    break;
                                }
                            }
                        }
                        
                        // Si toujours pas vide, chercher dans la hotbar
                        if (!cursorStack.isEmpty()) {
                            for (int invSlot = 37; invSlot <= 45 && !cursorStack.isEmpty(); invSlot++) {
                                if (handler.getSlot(invSlot).getStack().isEmpty()) {
                                    client.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.PICKUP, player);
                                    cursorStack = handler.getCursorStack();
                                    if (cursorStack.isEmpty()) {
                                        placed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // Si toujours pas vide, forcer dans le premier slot disponible
                        if (!cursorStack.isEmpty()) {
                            client.interactionManager.clickSlot(handler.syncId, 10, 0, SlotActionType.PICKUP, player);
                            placed = true;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Vérifie si deux ItemStack peuvent être empilés ensemble.
     */
    private static boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }
        // Vérifier que c'est le même item
        if (!stack1.isOf(stack2.getItem())) {
            return false;
        }
        // Vérifier que les composants de données sont compatibles (pour le restacking)
        // Utiliser areItemsEqual() qui vérifie item + composants de données (sans la quantité)
        return ItemStack.areItemsEqual(stack1, stack2);
    }
    
    /**
     * Calcule le nombre maximum d'items craftables avec les ressources disponibles.
     * Limite à 1 stack (64 items) maximum.
     */
    private static int calculateMaxCraftable(CraftingScreenHandler handler, CraftingRecipe recipe,
                                             DynamicRegistryManager registryManager,
                                             ClientPlayerEntity player) {
        var ingredients = recipe.getIngredients();
        var inventory = player.getInventory();
        ItemStack resultStack = recipe.getOutput(registryManager);
        int maxStackSize = resultStack.getMaxCount();
        int maxCraftable = maxStackSize; // Limite à 1 stack
        
        // Liste pour stocker les ingrédients avec leur nombre d'occurrences
        // Utiliser une liste de paires (ingrédient, count) car les Ingredient ne sont pas comparables
        java.util.List<java.util.Map.Entry<net.minecraft.recipe.Ingredient, Integer>> ingredientCounts = new java.util.ArrayList<>();
        
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            // Pour les recettes shaped, compter les occurrences dans la grille
            var recipeIngredients = shapedRecipe.getIngredients();
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();
            
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int index = row * width + col;
                    if (index < recipeIngredients.size()) {
                        var ingredient = recipeIngredients.get(index);
                        if (!ingredient.isEmpty()) {
                            // Chercher si cet ingrédient existe déjà dans la liste
                            boolean found = false;
                            for (var entry : ingredientCounts) {
                                // Comparer en testant avec les mêmes items
                                if (ingredientsMatch(entry.getKey(), ingredient)) {
                                    entry.setValue(entry.getValue() + 1);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                ingredientCounts.add(new java.util.AbstractMap.SimpleEntry<>(ingredient, 1));
                            }
                        }
                    }
                }
            }
        } else {
            // Pour les recettes shapeless, chaque ingrédient apparaît une fois
            for (var ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    ingredientCounts.add(new java.util.AbstractMap.SimpleEntry<>(ingredient, 1));
                }
            }
        }
        
        // Pour chaque ingrédient unique, calculer combien de crafts on peut faire
        for (var entry : ingredientCounts) {
            var ingredient = entry.getKey();
            int countPerCraft = entry.getValue(); // Nombre de fois que cet ingrédient est utilisé par craft
            
            // Compter combien d'items correspondants sont disponibles dans l'inventaire
            int availableCount = 0;
            for (int i = 0; i < 36; i++) {
                var stack = inventory.getStack(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    availableCount += stack.getCount();
                }
            }
            
            // Calculer combien de crafts on peut faire avec cet ingrédient
            // Diviser par le nombre d'occurrences par craft
            int craftableWithThisIngredient = availableCount / countPerCraft;
            
            System.out.println("[CEI-DEBUG] Ingredient, disponible: " + availableCount + 
                             ", par craft: " + countPerCraft + ", craftable: " + craftableWithThisIngredient);
            
            // Prendre le minimum parmi tous les ingrédients
            maxCraftable = Math.min(maxCraftable, craftableWithThisIngredient);
        }
        
        System.out.println("[CEI-DEBUG] maxCraftable final: " + maxCraftable);
        return Math.max(1, Math.min(maxCraftable, maxStackSize));
    }
    
    /**
     * Vérifie si deux ingrédients correspondent aux mêmes items.
     */
    private static boolean ingredientsMatch(net.minecraft.recipe.Ingredient ing1, net.minecraft.recipe.Ingredient ing2) {
        if (ing1.isEmpty() != ing2.isEmpty()) {
            return false;
        }
        if (ing1.isEmpty()) {
            return true;
        }
        
        // Comparer en testant les items correspondants
        ItemStack[] stacks1 = ing1.getMatchingStacks();
        ItemStack[] stacks2 = ing2.getMatchingStacks();
        
        // Si les deux ont les mêmes items correspondants, ils matchent
        if (stacks1.length != stacks2.length) {
            return false;
        }
        
        java.util.Set<net.minecraft.item.Item> items1 = new java.util.HashSet<>();
        java.util.Set<net.minecraft.item.Item> items2 = new java.util.HashSet<>();
        
        for (ItemStack stack : stacks1) {
            if (!stack.isEmpty()) {
                items1.add(stack.getItem());
            }
        }
        for (ItemStack stack : stacks2) {
            if (!stack.isEmpty()) {
                items2.add(stack.getItem());
            }
        }
        
        return items1.equals(items2);
    }
    
    /**
     * Place les ingrédients d'une recette shaped.
     */
    private static boolean placeShapedRecipe(CraftingScreenHandler handler, ShapedRecipe recipe,
                                            DynamicRegistryManager registryManager,
                                            ClientPlayerEntity player, int quantity) {
        var ingredients = recipe.getIngredients();
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        
        // Trouver les ingrédients dans l'inventaire du joueur
        var inventory = player.getInventory();
        
        // Pour chaque position dans la grille de la recette
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int ingredientIndex = row * width + col;
                if (ingredientIndex >= ingredients.size()) {
                    continue;
                }
                
                var ingredient = ingredients.get(ingredientIndex);
                if (ingredient.isEmpty()) {
                    continue;
                }
                
                // Calculer le slot de craft (1-9)
                int craftSlot = row * 3 + col + 1; // +1 car slot 0 = résultat
                if (craftSlot < 1 || craftSlot > 9) {
                    continue;
                }
                
                // Placer l'item dans le slot de craft (les slots 1-9 sont directement les slots de craft)
                int handlerSlot = craftSlot;
                
                // Utiliser l'interaction manager pour placer l'item
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.interactionManager != null) {
                    try {
                        // Vérifier si le slot de craft est vide, sinon le vider d'abord
                        if (!handler.getSlot(handlerSlot).getStack().isEmpty()) {
                            client.interactionManager.clickSlot(handler.syncId, handlerSlot, 0, SlotActionType.PICKUP, player);
                        }
                        
                        // Trouver l'item dans l'inventaire et le placer dans le slot de craft
                        boolean found = false;
                        for (int i = 0; i < 36 && !found; i++) {
                            var stack = inventory.getStack(i);
                            if (!stack.isEmpty() && ingredient.test(stack)) {
                                // Mapper l'index de l'inventaire du joueur au slot du handler
                                // Dans CraftingScreenHandler :
                                // - Slots 0 = résultat
                                // - Slots 1-9 = grille de craft
                                // - Slots 10-36 = inventaire principal (slots 9-35 de l'inventaire du joueur)
                                // - Slots 37-45 = hotbar (slots 0-8 de l'inventaire du joueur)
                                int inventoryHandlerSlot;
                                if (i >= 9) {
                                    // Inventaire principal : slots 9-35 -> handler slots 10-36
                                    inventoryHandlerSlot = i + 1;
                                } else {
                                    // Hotbar : slots 0-8 -> handler slots 37-45
                                    inventoryHandlerSlot = i + 37;
                                }
                                
                                if (quantity == 1) {
                                    // Pour placer un seul item :
                                    // 1. Prendre tout le stack avec un clic gauche (va dans le curseur)
                                    client.interactionManager.clickSlot(handler.syncId, inventoryHandlerSlot, 0, SlotActionType.PICKUP, player);
                                    // 2. Placer un seul item dans le slot de craft avec un clic droit
                                    client.interactionManager.clickSlot(handler.syncId, handlerSlot, 1, SlotActionType.PICKUP, player);
                                    // 3. Remettre le reste dans l'inventaire avec un clic gauche
                                    client.interactionManager.clickSlot(handler.syncId, inventoryHandlerSlot, 0, SlotActionType.PICKUP, player);
                                } else {
                                    // Pour placer le maximum possible (quantity > 1 ou -1)
                                    // quantity représente le nombre de crafts qu'on veut faire
                                    // Donc on doit placer 'quantity' items dans ce slot (un par craft)
                                    int itemsToPlace = quantity;
                                    int maxStackSize = stack.getMaxCount();
                                    
                                    // Limiter par la taille maximale du stack
                                    itemsToPlace = Math.min(itemsToPlace, maxStackSize);
                                    
                                    // Compter combien d'items de ce type sont disponibles dans l'inventaire
                                    int availableCount = 0;
                                    for (int j = 0; j < 36; j++) {
                                        var invStack = inventory.getStack(j);
                                        if (!invStack.isEmpty() && ingredient.test(invStack)) {
                                            availableCount += invStack.getCount();
                                        }
                                    }
                                    
                                    // Ne pas placer plus que ce qui est disponible
                                    itemsToPlace = Math.min(itemsToPlace, availableCount);
                                    
                                    // Placer les items progressivement
                                    int remainingToPlace = itemsToPlace;
                                    while (remainingToPlace > 0) {
                                        // Trouver un stack avec des items disponibles
                                        boolean foundStack = false;
                                        for (int j = 0; j < 36 && !foundStack && remainingToPlace > 0; j++) {
                                            var invStack = inventory.getStack(j);
                                            if (!invStack.isEmpty() && ingredient.test(invStack)) {
                                                int jHandlerSlot = (j >= 9) ? (j + 1) : (j + 37);
                                                
                                                // Prendre le stack
                                                client.interactionManager.clickSlot(handler.syncId, jHandlerSlot, 0, SlotActionType.PICKUP, player);
                                                
                                                // Placer autant d'items que possible dans le slot de craft
                                                int currentSlotCount = handler.getSlot(handlerSlot).getStack().getCount();
                                                int canPlace = Math.min(remainingToPlace, maxStackSize - currentSlotCount);
                                                
                                                for (int k = 0; k < canPlace && remainingToPlace > 0; k++) {
                                                    client.interactionManager.clickSlot(handler.syncId, handlerSlot, 1, SlotActionType.PICKUP, player);
                                                    remainingToPlace--;
                                                }
                                                
                                                // Remettre le reste dans l'inventaire
                                                client.interactionManager.clickSlot(handler.syncId, jHandlerSlot, 0, SlotActionType.PICKUP, player);
                                                
                                                foundStack = true;
                                                
                                                // Si le slot est plein, arrêter
                                                if (handler.getSlot(handlerSlot).getStack().getCount() >= maxStackSize) {
                                                    remainingToPlace = 0;
                                                }
                                            }
                                        }
                                        
                                        if (!foundStack) {
                                            break; // Plus d'items disponibles
                                        }
                                    }
                                }
                                
                                found = true;
                            }
                        }
                        
                        // Si on n'a pas assez d'items, on place quand même ce qu'on a
                        // (comportement demandé par l'utilisateur)
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Place les ingrédients d'une recette shapeless.
     */
    private static boolean placeShapelessRecipe(CraftingScreenHandler handler, CraftingRecipe recipe,
                                               DynamicRegistryManager registryManager,
                                               ClientPlayerEntity player, int quantity) {
        var ingredients = recipe.getIngredients();
        var inventory = player.getInventory();
        
        // Liste des slots de craft disponibles (1-9)
        boolean[] usedSlots = new boolean[9];
        
        // Pour chaque ingrédient
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            
            // Pour les recettes shapeless, chaque ingrédient doit être placé une fois par craft
            // On doit donc placer quantity fois chaque ingrédient
            int neededCount = quantity;
            
            // Trouver un slot de craft libre
            int craftSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (!usedSlots[i]) {
                    craftSlot = i + 1; // Slots 1-9
                    usedSlots[i] = true;
                    break;
                }
            }
            
            if (craftSlot == -1) {
                return false; // Plus de slots disponibles
            }
            
            // Placer l'item (les slots 1-9 sont directement les slots de craft)
            int handlerSlot = craftSlot;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.interactionManager != null) {
                try {
                    // Vérifier si le slot de craft est vide, sinon le vider d'abord
                    if (!handler.getSlot(handlerSlot).getStack().isEmpty()) {
                        client.interactionManager.clickSlot(handler.syncId, handlerSlot, 0, SlotActionType.PICKUP, player);
                    }
                    
                    // Trouver l'item dans l'inventaire et le placer dans le slot de craft
                    boolean found = false;
                    for (int i = 0; i < 36 && !found; i++) {
                        var stack = inventory.getStack(i);
                        if (!stack.isEmpty() && ingredient.test(stack)) {
                            // Mapper l'index de l'inventaire du joueur au slot du handler
                            // Dans CraftingScreenHandler :
                            // - Slots 0 = résultat
                            // - Slots 1-9 = grille de craft
                            // - Slots 10-36 = inventaire principal (slots 9-35 de l'inventaire du joueur)
                            // - Slots 37-45 = hotbar (slots 0-8 de l'inventaire du joueur)
                            int inventoryHandlerSlot;
                            if (i >= 9) {
                                // Inventaire principal : slots 9-35 -> handler slots 10-36
                                inventoryHandlerSlot = i + 1;
                            } else {
                                // Hotbar : slots 0-8 -> handler slots 37-45
                                inventoryHandlerSlot = i + 37;
                            }
                            
                            if (neededCount == 1) {
                                // Pour placer un seul item :
                                // 1. Prendre tout le stack avec un clic gauche (va dans le curseur)
                                client.interactionManager.clickSlot(handler.syncId, inventoryHandlerSlot, 0, SlotActionType.PICKUP, player);
                                // 2. Placer un seul item dans le slot de craft avec un clic droit
                                client.interactionManager.clickSlot(handler.syncId, handlerSlot, 1, SlotActionType.PICKUP, player);
                                // 3. Remettre le reste dans l'inventaire avec un clic gauche
                                client.interactionManager.clickSlot(handler.syncId, inventoryHandlerSlot, 0, SlotActionType.PICKUP, player);
                            } else {
                                // Pour placer le maximum possible (neededCount > 1)
                                // neededCount représente le nombre de crafts qu'on veut faire
                                // Donc on doit placer 'neededCount' items dans ce slot (un par craft)
                                int itemsToPlace = neededCount;
                                int maxStackSize = stack.getMaxCount();
                                
                                // Limiter par la taille maximale du stack
                                itemsToPlace = Math.min(itemsToPlace, maxStackSize);
                                
                                // Compter combien d'items de ce type sont disponibles dans l'inventaire
                                int availableCount = 0;
                                for (int j = 0; j < 36; j++) {
                                    var invStack = inventory.getStack(j);
                                    if (!invStack.isEmpty() && ingredient.test(invStack)) {
                                        availableCount += invStack.getCount();
                                    }
                                }
                                
                                // Ne pas placer plus que ce qui est disponible
                                itemsToPlace = Math.min(itemsToPlace, availableCount);
                                
                                // Placer les items progressivement
                                int remainingToPlace = itemsToPlace;
                                while (remainingToPlace > 0) {
                                    // Trouver un stack avec des items disponibles
                                    boolean foundStack = false;
                                    for (int j = 0; j < 36 && !foundStack && remainingToPlace > 0; j++) {
                                        var invStack = inventory.getStack(j);
                                        if (!invStack.isEmpty() && ingredient.test(invStack)) {
                                            int jHandlerSlot = (j >= 9) ? (j + 1) : (j + 37);
                                            
                                            // Prendre le stack
                                            client.interactionManager.clickSlot(handler.syncId, jHandlerSlot, 0, SlotActionType.PICKUP, player);
                                            
                                            // Placer autant d'items que possible dans le slot de craft
                                            int currentSlotCount = handler.getSlot(handlerSlot).getStack().getCount();
                                            int canPlace = Math.min(remainingToPlace, maxStackSize - currentSlotCount);
                                            
                                            for (int k = 0; k < canPlace && remainingToPlace > 0; k++) {
                                                client.interactionManager.clickSlot(handler.syncId, handlerSlot, 1, SlotActionType.PICKUP, player);
                                                remainingToPlace--;
                                            }
                                            
                                            // Remettre le reste dans l'inventaire
                                            client.interactionManager.clickSlot(handler.syncId, jHandlerSlot, 0, SlotActionType.PICKUP, player);
                                            
                                            foundStack = true;
                                            
                                            // Si le slot est plein, arrêter
                                            if (handler.getSlot(handlerSlot).getStack().getCount() >= maxStackSize) {
                                                remainingToPlace = 0;
                                            }
                                        }
                                    }
                                    
                                    if (!foundStack) {
                                        break; // Plus d'items disponibles
                                    }
                                }
                            }
                            
                            found = true;
                        }
                    }
                    
                    // Si on n'a pas assez d'items, on place quand même ce qu'on a
                } catch (Exception e) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Trouve un item correspondant à un ingrédient dans l'inventaire du joueur.
     * @return L'index du slot dans l'inventaire, ou -1 si non trouvé
     */
    private static int findItemInInventory(net.minecraft.entity.player.PlayerInventory inventory,
                                            net.minecraft.recipe.Ingredient ingredient) {
        // Chercher dans l'inventaire principal (slots 9-35)
        for (int i = 9; i < 36; i++) {
            var stack = inventory.getStack(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                return i;
            }
        }
        
        // Chercher dans la barre d'action (slots 0-8)
        for (int i = 0; i < 9; i++) {
            var stack = inventory.getStack(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                return i;
            }
        }
        
        return -1;
    }
}



