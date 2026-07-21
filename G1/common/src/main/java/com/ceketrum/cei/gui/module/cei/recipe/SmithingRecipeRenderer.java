package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Renderer spécialisé pour les recettes de smithing table.
 */
public class SmithingRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(DrawContext context, int startX, int startY,
                     Recipe<?> recipe, RecipeEntry<?> recipeEntry,
                     DynamicRegistryManager dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.font.TextRenderer textRenderer) {
        if (!(recipe instanceof SmithingRecipe smithingRecipe)) {
            return startY;
        }
        
        // Afficher un header traduisible pour la smithing table (tronqué si nécessaire)
        String title = Text.translatable("recipe.cei.smithing.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20; // Largeur disponible avec marges
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.getWidth(displayTitle);
        context.drawText(textRenderer, Text.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFF, false);
        
        int currentY = startY + 12;
        
        // Dessiner le bloc de smithing (base + addition = résultat)
        currentY = drawSmithingBlock(context, startX, currentY, smithingRecipe, dynamicRegistryManager, textRenderer);
        currentY += GuiConstants.SLOT_SIZE + 5;
        
        // Barre de séparation entre la recette et la description
        if (!itemDescription.isEmpty() || !RecipeDisplayHelper.getRecipeDescription(recipeEntry).isEmpty()) {
            context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, 0xFF808080);
            currentY += 8; // Espace après la barre
        }
        
        // Affichage de la description de la recette
        String description = RecipeDisplayHelper.getRecipeDescription(recipeEntry);
        if (description.isEmpty()) {
            description = itemDescription;
        }
        
        if (!description.isEmpty()) {
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int indent = 4;
            TextRenderHelper.drawWrappedText(context, description, startX + 10, currentY, descMaxWidth, 0xCCCCCC, scale, indent, textRenderer);
        }
        
        return currentY;
    }
    
    /**
     * Dessine le bloc de smithing (base, addition, flèche, résultat) centré dans la popup.
     * Format : [Base] + [Addition] -> [Résultat]
     * @return La position Y après le bloc
     */
    private int drawSmithingBlock(DrawContext context, int startX, int startY,
                                 SmithingRecipe smithingRecipe, DynamicRegistryManager dynamicRegistryManager,
                                 net.minecraft.client.font.TextRenderer textRenderer) {
        // Largeur avec espacement : base (18) + gap (2) + plus (4) + gap (2) + addition (18) + gap (6) + flèche (16) + tête (4) + gap (6) + résultat (18) = 100
        int blockWidth = 100;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        // Récupérer les ingrédients
        ItemStack baseStack = getBaseIngredient(smithingRecipe);
        ItemStack additionStack = getAdditionIngredient(smithingRecipe);
        ItemStack resultStack = smithingRecipe.getOutput(dynamicRegistryManager);
        
        // Dessiner le slot de base
        int baseX = blockStartX;
        drawSlot(context, baseX, startY);
        if (!baseStack.isEmpty()) {
            drawItemInSlot(context, baseStack, baseX, startY);
        }
        
        // Dessiner le signe "+" entre base et addition
        int plusX = baseX + GuiConstants.SLOT_SIZE + 2;
        int plusY = startY + GuiConstants.SLOT_SIZE / 2;
        context.fill(plusX, plusY - 1, plusX + 4, plusY + 1, 0xFFFFFFFF);
        context.fill(plusX + 1, plusY - 2, plusX + 3, plusY + 2, 0xFFFFFFFF);
        
        // Dessiner le slot d'addition
        int additionX = plusX + 6;
        drawSlot(context, additionX, startY);
        if (!additionStack.isEmpty()) {
            drawItemInSlot(context, additionStack, additionX, startY);
        }
        
        // Dessiner une vraie flèche vers le résultat (avec espacement pour ne pas passer sous l'item)
        int arrowStartX = additionX + GuiConstants.SLOT_SIZE + 6; // Espacement augmenté
        int arrowCenterY = startY + GuiConstants.SLOT_SIZE / 2; // Centre vertical du slot
        int arrowLength = 16; // Longueur de la flèche
        
        // Corps de la flèche (ligne horizontale)
        int arrowBodyY = arrowCenterY;
        context.fill(arrowStartX, arrowBodyY - 1, arrowStartX + arrowLength, arrowBodyY + 1, 0xFFFFFFFF);
        
        // Tête de flèche (triangle pointant vers la droite)
        int arrowHeadX = arrowStartX + arrowLength;
        int arrowHeadSize = 4; // Taille de la tête de flèche
        // Dessiner un triangle pointant vers la droite
        // La pointe est à droite, les lignes partent de la pointe vers la gauche
        int arrowTipX = arrowHeadX + arrowHeadSize; // Pointe à droite
        // Ligne supérieure du triangle (de la pointe vers le haut-gauche)
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowBodyY - i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        // Ligne inférieure du triangle (de la pointe vers le bas-gauche)
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowBodyY + i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        
        // Dessiner le slot de résultat (avec espacement après la flèche)
        int resultX = arrowStartX + arrowLength + arrowHeadSize + 6; // Espacement de 6 pixels après la flèche
        drawSlot(context, resultX, startY);
        if (!resultStack.isEmpty()) {
            drawItemInSlot(context, resultStack, resultX, startY);
        }
        
        return startY + GuiConstants.SLOT_SIZE;
    }
    
    /**
     * Récupère l'ingrédient de base (l'item à améliorer).
     */
    private ItemStack getBaseIngredient(SmithingRecipe smithingRecipe) {
        // Dans Minecraft 1.21.1, les recettes de smithing peuvent avoir une structure différente
        // Essayer d'accéder aux champs directement via la réflexion
        
        // Essayer d'abord les méthodes
        try {
            Method getBaseMethod = smithingRecipe.getClass().getMethod("getBase");
            Ingredient baseIngredient = (Ingredient) getBaseMethod.invoke(smithingRecipe);
            if (baseIngredient != null) {
                ItemStack[] baseItems = baseIngredient.getMatchingStacks();
                if (baseItems != null && baseItems.length > 0 && !baseItems[0].isEmpty()) {
                    return baseItems[0].copy();
                }
            }
        } catch (Exception e) {
            // Méthode n'existe pas, continuer
        }
        
        // Essayer d'accéder aux champs directement
        try {
            // Essayer différents noms de champs possibles
            String[] possibleFieldNames = {"base", "baseIngredient", "input", "template", "item"};
            for (String fieldName : possibleFieldNames) {
                try {
                    Field field = smithingRecipe.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(smithingRecipe);
                    
                    if (value instanceof Ingredient ingredient) {
                        ItemStack[] items = ingredient.getMatchingStacks();
                        if (items != null && items.length > 0 && !items[0].isEmpty()) {
                            return items[0].copy();
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // Ce champ n'existe pas, essayer le suivant
                }
            }
        } catch (Exception e) {
            // Erreur silencieuse
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * Récupère l'ingrédient d'addition (le matériau d'amélioration).
     */
    private ItemStack getAdditionIngredient(SmithingRecipe smithingRecipe) {
        // Essayer d'abord les méthodes
        try {
            Method getAdditionMethod = smithingRecipe.getClass().getMethod("getAddition");
            Ingredient additionIngredient = (Ingredient) getAdditionMethod.invoke(smithingRecipe);
            if (additionIngredient != null) {
                ItemStack[] additionItems = additionIngredient.getMatchingStacks();
                if (additionItems != null && additionItems.length > 0 && !additionItems[0].isEmpty()) {
                    return additionItems[0].copy();
                }
            }
        } catch (Exception e) {
            // Méthode n'existe pas, continuer
        }
        
        // Essayer d'accéder aux champs directement
        try {
            // Essayer différents noms de champs possibles
            String[] possibleFieldNames = {"addition", "additionIngredient", "material", "upgrade"};
            for (String fieldName : possibleFieldNames) {
                try {
                    Field field = smithingRecipe.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(smithingRecipe);
                    
                    if (value instanceof Ingredient ingredient) {
                        ItemStack[] items = ingredient.getMatchingStacks();
                        if (items != null && items.length > 0 && !items[0].isEmpty()) {
                            return items[0].copy();
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // Ce champ n'existe pas, essayer le suivant
                }
            }
        } catch (Exception e) {
            // Erreur silencieuse
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * Dessine un slot vide.
     */
    private void drawSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF404040);
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + 1, 0x44CCCCCC);
        context.fill(x, y, x + 1, y + GuiConstants.SLOT_SIZE, 0x44CCCCCC);
    }
    
    /**
     * Dessine un item dans un slot.
     * Utilise les matrices pour s'assurer que l'item est rendu au-dessus des autres éléments.
     */
    private void drawItemInSlot(DrawContext context, ItemStack stack, int slotX, int slotY) {
        int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
        // Utiliser les matrices pour s'assurer que l'item est rendu au-dessus
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200); // Z-index élevé pour être au-dessus
        context.drawItem(stack, slotX + offset, slotY + offset);
        context.getMatrices().pop();
    }
}



