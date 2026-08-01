package com.ceketrum.cei.gui.module.cei.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.util.List;

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
    public static boolean placeRecipeIngredients(CraftingMenu handler, Recipe<?> recipe,
                                                 RegistryAccess registryManager,
                                                 LocalPlayer player, int quantity) {
        System.out.println("[CEI-DEBUG] placeRecipeIngredients appelé - quantity: " + quantity);
        
        var displays = recipe.display();
        if (displays.isEmpty()) {
            System.out.println("[CEI-DEBUG] Échec: recipe n'a pas de display");
            return false;
        }
        
        RecipeDisplay display = displays.get(0);
        ContextMap contextMap = SlotDisplayContext.fromLevel(player.level());
        
        boolean isShaped = display instanceof ShapedCraftingRecipeDisplay;
        boolean isShapeless = display instanceof ShapelessCraftingRecipeDisplay;
        System.out.println("[CEI-DEBUG] isShaped: " + isShaped + ", isShapeless: " + isShapeless);
        
        // Calculer la quantité à craft
        int craftQuantity = quantity;
        if (quantity == -1) {
            craftQuantity = calculateMaxCraftable(handler, recipe, contextMap, player);
            System.out.println("[CEI-DEBUG] Quantité maximale calculée: " + craftQuantity);
        }
        
        if (craftQuantity <= 0) {
            System.out.println("[CEI-DEBUG] Échec: craftQuantity <= 0");
            return false;
        }
        
        System.out.println("[CEI-DEBUG] Placement de " + craftQuantity + " items");
        
        // Vider la table de craft avant de placer les ingrédients
        clearCraftingGrid(handler, player);
        
        if (isShaped && display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
            System.out.println("[CEI-DEBUG] Placement d'une recette shaped");
            return placeShapedRecipe(handler, shapedDisplay, contextMap, player, craftQuantity);
        } else if (isShapeless && display instanceof ShapelessCraftingRecipeDisplay shapelessDisplay) {
            System.out.println("[CEI-DEBUG] Placement d'une recette shapeless");
            return placeShapelessRecipe(handler, shapelessDisplay, contextMap, player, craftQuantity);
        }
        
        System.out.println("[CEI-DEBUG] Échec: type de recette non reconnu");
        return false;
    }
    
    /**
     * Vide la grille de craft en remettant tous les items dans l'inventaire du joueur.
     */
    private static void clearCraftingGrid(CraftingMenu handler, LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameMode == null) {
            return;
        }
        
        for (int slot = 1; slot <= 9; slot++) {
            var stack = handler.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                client.gameMode.handleContainerInput(handler.containerId, slot, 0, ContainerInput.PICKUP, player);
                var cursorStack = handler.getCarried();
                if (cursorStack.isEmpty()) {
                    continue;
                }
                
                boolean placed = false;
                while (!cursorStack.isEmpty() && !placed) {
                    boolean foundCompatibleSlot = false;
                    
                    for (int invSlot = 10; invSlot <= 36 && !cursorStack.isEmpty(); invSlot++) {
                        var invStack = handler.getSlot(invSlot).getItem();
                        if (!invStack.isEmpty() && canStack(invStack, cursorStack) && 
                            invStack.getCount() < invStack.getMaxStackSize()) {
                            client.gameMode.handleContainerInput(handler.containerId, invSlot, 0, ContainerInput.PICKUP, player);
                            cursorStack = handler.getCarried();
                            foundCompatibleSlot = true;
                            if (cursorStack.isEmpty()) {
                                placed = true;
                                break;
                            }
                        }
                    }
                    
                    if (!cursorStack.isEmpty() && !foundCompatibleSlot) {
                        for (int invSlot = 37; invSlot <= 45 && !cursorStack.isEmpty(); invSlot++) {
                            var invStack = handler.getSlot(invSlot).getItem();
                            if (!invStack.isEmpty() && canStack(invStack, cursorStack) && 
                                invStack.getCount() < invStack.getMaxStackSize()) {
                                client.gameMode.handleContainerInput(handler.containerId, invSlot, 0, ContainerInput.PICKUP, player);
                                cursorStack = handler.getCarried();
                                foundCompatibleSlot = true;
                                if (cursorStack.isEmpty()) {
                                    placed = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (!foundCompatibleSlot || !cursorStack.isEmpty()) {
                        for (int invSlot = 10; invSlot <= 36 && !cursorStack.isEmpty(); invSlot++) {
                            if (handler.getSlot(invSlot).getItem().isEmpty()) {
                                client.gameMode.handleContainerInput(handler.containerId, invSlot, 0, ContainerInput.PICKUP, player);
                                cursorStack = handler.getCarried();
                                if (cursorStack.isEmpty()) {
                                    placed = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!cursorStack.isEmpty()) {
                            for (int invSlot = 37; invSlot <= 45 && !cursorStack.isEmpty(); invSlot++) {
                                if (handler.getSlot(invSlot).getItem().isEmpty()) {
                                    client.gameMode.handleContainerInput(handler.containerId, invSlot, 0, ContainerInput.PICKUP, player);
                                    cursorStack = handler.getCarried();
                                    if (cursorStack.isEmpty()) {
                                        placed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if (!cursorStack.isEmpty()) {
                            client.gameMode.handleContainerInput(handler.containerId, 10, 0, ContainerInput.PICKUP, player);
                            placed = true;
                        }
                    }
                }
            }
        }
    }
    
    private static boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItem(stack1, stack2);
    }
    
    /**
     * Calcule le nombre maximum d'items craftables avec les ressources disponibles.
     */
    private static int calculateMaxCraftable(CraftingMenu handler, Recipe<?> recipe,
                                             ContextMap contextMap,
                                             LocalPlayer player) {
        var inventory = player.getInventory();
        var displays = recipe.display();
        if (displays.isEmpty()) return 1;
        
        RecipeDisplay display = displays.get(0);
        ItemStack resultStack = display.result().resolveForFirstStack(contextMap);
        int maxStackSize = resultStack != null ? resultStack.getMaxStackSize() : 64;
        int maxCraftable = maxStackSize;
        
        List<SlotDisplay> ingredientsList = List.of();
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            ingredientsList = shaped.ingredients();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            ingredientsList = shapeless.ingredients();
        }
        
        java.util.List<java.util.Map.Entry<SlotDisplay, Integer>> ingredientCounts = new java.util.ArrayList<>();
        
        for (var ingredient : ingredientsList) {
            if (ingredient != null) {
                boolean found = false;
                for (var entry : ingredientCounts) {
                    if (slotDisplaysMatch(entry.getKey(), ingredient, contextMap)) {
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
        
        for (var entry : ingredientCounts) {
            var ingredient = entry.getKey();
            int countPerCraft = entry.getValue();
            
            int availableCount = 0;
            for (int i = 0; i < 36; i++) {
                var stack = inventory.getItem(i);
                if (!stack.isEmpty() && matchesSlotDisplay(stack, ingredient, contextMap)) {
                    availableCount += stack.getCount();
                }
            }
            
            int craftableWithThisIngredient = availableCount / countPerCraft;
            maxCraftable = Math.min(maxCraftable, craftableWithThisIngredient);
        }
        
        return Math.max(1, Math.min(maxCraftable, maxStackSize));
    }
    
    private static boolean slotDisplaysMatch(SlotDisplay disp1, SlotDisplay disp2, ContextMap contextMap) {
        if (disp1 == null || disp2 == null) return disp1 == disp2;
        List<ItemStack> stacks1 = disp1.resolveForStacks(contextMap);
        List<ItemStack> stacks2 = disp2.resolveForStacks(contextMap);
        if (stacks1.size() != stacks2.size()) return false;
        
        java.util.Set<net.minecraft.world.item.Item> items1 = new java.util.HashSet<>();
        java.util.Set<net.minecraft.world.item.Item> items2 = new java.util.HashSet<>();
        for (ItemStack s : stacks1) if (!s.isEmpty()) items1.add(s.getItem());
        for (ItemStack s : stacks2) if (!s.isEmpty()) items2.add(s.getItem());
        return items1.equals(items2);
    }
    
    private static boolean matchesSlotDisplay(ItemStack invStack, SlotDisplay display, ContextMap contextMap) {
        if (display == null) return false;
        List<ItemStack> possible = display.resolveForStacks(contextMap);
        for (ItemStack p : possible) {
            if (ItemStack.isSameItem(invStack, p)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean placeShapedRecipe(CraftingMenu handler, ShapedCraftingRecipeDisplay recipe,
                                             ContextMap contextMap,
                                             LocalPlayer player, int quantity) {
        var ingredients = recipe.ingredients();
        int width = recipe.width();
        int height = recipe.height();
        var inventory = player.getInventory();
        
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int ingredientIndex = row * width + col;
                if (ingredientIndex >= ingredients.size()) {
                    continue;
                }
                
                var ingredient = ingredients.get(ingredientIndex);
                if (ingredient == null) {
                    continue;
                }
                
                int craftSlot = row * 3 + col + 1;
                if (craftSlot < 1 || craftSlot > 9) {
                    continue;
                }
                
                int handlerSlot = craftSlot;
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.gameMode != null) {
                    try {
                        if (!handler.getSlot(handlerSlot).getItem().isEmpty()) {
                            client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 0, ContainerInput.PICKUP, player);
                        }
                        
                        boolean found = false;
                        for (int i = 0; i < 36 && !found; i++) {
                            var stack = inventory.getItem(i);
                            if (!stack.isEmpty() && matchesSlotDisplay(stack, ingredient, contextMap)) {
                                int inventoryHandlerSlot = (i >= 9) ? (i + 1) : (i + 37);
                                
                                if (quantity == 1) {
                                    client.gameMode.handleContainerInput(handler.containerId, inventoryHandlerSlot, 0, ContainerInput.PICKUP, player);
                                    client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 1, ContainerInput.PICKUP, player);
                                    client.gameMode.handleContainerInput(handler.containerId, inventoryHandlerSlot, 0, ContainerInput.PICKUP, player);
                                } else {
                                    int itemsToPlace = quantity;
                                    int maxStackSize = stack.getMaxStackSize();
                                    itemsToPlace = Math.min(itemsToPlace, maxStackSize);
                                    
                                    int availableCount = 0;
                                    for (int j = 0; j < 36; j++) {
                                        var invStack = inventory.getItem(j);
                                        if (!invStack.isEmpty() && matchesSlotDisplay(invStack, ingredient, contextMap)) {
                                            availableCount += invStack.getCount();
                                        }
                                    }
                                    
                                    itemsToPlace = Math.min(itemsToPlace, availableCount);
                                    int remainingToPlace = itemsToPlace;
                                    while (remainingToPlace > 0) {
                                        boolean foundStack = false;
                                        for (int j = 0; j < 36 && !foundStack && remainingToPlace > 0; j++) {
                                            var invStack = inventory.getItem(j);
                                            if (!invStack.isEmpty() && matchesSlotDisplay(invStack, ingredient, contextMap)) {
                                                int jHandlerSlot = (j >= 9) ? (j + 1) : (j + 37);
                                                client.gameMode.handleContainerInput(handler.containerId, jHandlerSlot, 0, ContainerInput.PICKUP, player);
                                                int currentSlotCount = handler.getSlot(handlerSlot).getItem().getCount();
                                                int canPlace = Math.min(remainingToPlace, maxStackSize - currentSlotCount);
                                                for (int k = 0; k < canPlace && remainingToPlace > 0; k++) {
                                                    client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 1, ContainerInput.PICKUP, player);
                                                    remainingToPlace--;
                                                }
                                                client.gameMode.handleContainerInput(handler.containerId, jHandlerSlot, 0, ContainerInput.PICKUP, player);
                                                foundStack = true;
                                                if (handler.getSlot(handlerSlot).getItem().getCount() >= maxStackSize) {
                                                    remainingToPlace = 0;
                                                }
                                            }
                                        }
                                        if (!foundStack) break;
                                    }
                                }
                                found = true;
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
        return true;
    }
    
    private static boolean placeShapelessRecipe(CraftingMenu handler, ShapelessCraftingRecipeDisplay recipe,
                                                ContextMap contextMap,
                                                LocalPlayer player, int quantity) {
        var ingredients = recipe.ingredients();
        var inventory = player.getInventory();
        boolean[] usedSlots = new boolean[9];
        
        for (var ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            
            int neededCount = quantity;
            int craftSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (!usedSlots[i]) {
                    craftSlot = i + 1;
                    usedSlots[i] = true;
                    break;
                }
            }
            
            if (craftSlot == -1) {
                return false;
            }
            
            int handlerSlot = craftSlot;
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.gameMode != null) {
                try {
                    if (!handler.getSlot(handlerSlot).getItem().isEmpty()) {
                        client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 0, ContainerInput.PICKUP, player);
                    }
                    
                    boolean found = false;
                    for (int i = 0; i < 36 && !found; i++) {
                        var stack = inventory.getItem(i);
                        if (!stack.isEmpty() && matchesSlotDisplay(stack, ingredient, contextMap)) {
                            int inventoryHandlerSlot = (i >= 9) ? (i + 1) : (i + 37);
                            
                            if (neededCount == 1) {
                                client.gameMode.handleContainerInput(handler.containerId, inventoryHandlerSlot, 0, ContainerInput.PICKUP, player);
                                client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 1, ContainerInput.PICKUP, player);
                                client.gameMode.handleContainerInput(handler.containerId, inventoryHandlerSlot, 0, ContainerInput.PICKUP, player);
                            } else {
                                int itemsToPlace = neededCount;
                                int maxStackSize = stack.getMaxStackSize();
                                itemsToPlace = Math.min(itemsToPlace, maxStackSize);
                                
                                int availableCount = 0;
                                for (int j = 0; j < 36; j++) {
                                    var invStack = inventory.getItem(j);
                                    if (!invStack.isEmpty() && matchesSlotDisplay(invStack, ingredient, contextMap)) {
                                        availableCount += invStack.getCount();
                                    }
                                }
                                
                                itemsToPlace = Math.min(itemsToPlace, availableCount);
                                int remainingToPlace = itemsToPlace;
                                while (remainingToPlace > 0) {
                                    boolean foundStack = false;
                                    for (int j = 0; j < 36 && !foundStack && remainingToPlace > 0; j++) {
                                        var invStack = inventory.getItem(j);
                                        if (!invStack.isEmpty() && matchesSlotDisplay(invStack, ingredient, contextMap)) {
                                            int jHandlerSlot = (j >= 9) ? (j + 1) : (j + 37);
                                            client.gameMode.handleContainerInput(handler.containerId, jHandlerSlot, 0, ContainerInput.PICKUP, player);
                                            int currentSlotCount = handler.getSlot(handlerSlot).getItem().getCount();
                                            int canPlace = Math.min(remainingToPlace, maxStackSize - currentSlotCount);
                                            for (int k = 0; k < canPlace && remainingToPlace > 0; k++) {
                                                client.gameMode.handleContainerInput(handler.containerId, handlerSlot, 1, ContainerInput.PICKUP, player);
                                                remainingToPlace--;
                                            }
                                            client.gameMode.handleContainerInput(handler.containerId, jHandlerSlot, 0, ContainerInput.PICKUP, player);
                                            foundStack = true;
                                            if (handler.getSlot(handlerSlot).getItem().getCount() >= maxStackSize) {
                                                remainingToPlace = 0;
                                            }
                                        }
                                    }
                                    if (!foundStack) break;
                                }
                            }
                            found = true;
                        }
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }
}
