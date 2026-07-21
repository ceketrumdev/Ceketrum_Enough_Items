package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

/**
 * Renderer spécialisé pour les recettes de smithing table.
 */
public class SmithingRecipeRenderer implements IRecipeRenderer {
    
    @Override
    public int render(GuiGraphics context, int startX, int startY,
                     Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                     RegistryAccess dynamicRegistryManager,
                     ItemStack hoveredStack,
                     String itemDescription,
                     net.minecraft.client.gui.Font textRenderer) {
        
        // Afficher un header traduisible pour la smithing table (tronqué si nécessaire)
        String title = Component.translatable("recipe.cei.smithing.header").getString();
        int maxTitleWidth = GuiConstants.POPUP_WIDTH - 20; // Largeur disponible avec marges
        String displayTitle = com.ceketrum.cei.gui.util.TextRenderHelper.truncateText(title, maxTitleWidth, textRenderer);
        int titleWidth = textRenderer.width(displayTitle);
        context.drawString(textRenderer, Component.literal(displayTitle), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFF, false);
        
        int currentY = startY + 12;
        
        // Dessiner le bloc de smithing (template + base + addition = résultat)
        currentY = drawSmithingBlock(context, startX, currentY, recipe, textRenderer);
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
     * Dessine le bloc de smithing (template, base, addition, flèche, résultat) centré dans la popup.
     * Format : [Template] [Base] + [Addition] -> [Résultat]
     * @return La position Y après le bloc
     */
    private int drawSmithingBlock(GuiGraphics context, int startX, int startY,
                                 Recipe<?> recipe, net.minecraft.client.gui.Font textRenderer) {
        // Largeur avec espacement : template (18) + gap (2) + base (18) + gap (2) + plus (4) + gap (2) + addition (18) + gap (6) + flèche (16) + tête (4) + gap (6) + résultat (18) = 120
        int blockWidth = 120;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        ItemStack templateStack = ItemStack.EMPTY;
        ItemStack baseStack = ItemStack.EMPTY;
        ItemStack additionStack = ItemStack.EMPTY;
        ItemStack resultStack = ItemStack.EMPTY;
        
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);
            var displays = recipe.display();
            if (!displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                resultStack = display.result().resolveForFirstStack(contextMap);
                if (display instanceof SmithingRecipeDisplay smithing) {
                    templateStack = smithing.template().resolveForFirstStack(contextMap);
                    baseStack = smithing.base().resolveForFirstStack(contextMap);
                    additionStack = smithing.addition().resolveForFirstStack(contextMap);
                }
            }
        }
        
        // Dessiner le slot de template
        int templateX = blockStartX;
        drawSlot(context, templateX, startY);
        if (templateStack != null && !templateStack.isEmpty()) {
            drawItemInSlot(context, templateStack, templateX, startY);
        }
        
        // Dessiner le slot de base
        int baseX = templateX + GuiConstants.SLOT_SIZE + 2;
        drawSlot(context, baseX, startY);
        if (baseStack != null && !baseStack.isEmpty()) {
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
        if (additionStack != null && !additionStack.isEmpty()) {
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
        if (resultStack != null && !resultStack.isEmpty()) {
            drawItemInSlot(context, resultStack, resultX, startY);
        }
        
        return startY + GuiConstants.SLOT_SIZE;
    }
    
    /**
     * Dessine un slot vide.
     */
    private void drawSlot(GuiGraphics context, int x, int y) {
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF404040);
        context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + 1, 0x44CCCCCC);
        context.fill(x, y, x + 1, y + GuiConstants.SLOT_SIZE, 0x44CCCCCC);
    }
    
    /**
     * Dessine un item dans un slot.
     * Utilise les matrices pour s'assurer que l'item est rendu au-dessus des autres éléments.
     */
    private void drawItemInSlot(GuiGraphics context, ItemStack stack, int slotX, int slotY) {
        int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
        // Utiliser les matrices pour s'assurer que l'item est rendu au-dessus
        context.pose().pushPose();
        context.pose().translate(0, 0, 200); // Z-index élevé pour être au-dessus
        context.renderItem(stack, slotX + offset, slotY + offset);
        context.pose().popPose();
    }
}
