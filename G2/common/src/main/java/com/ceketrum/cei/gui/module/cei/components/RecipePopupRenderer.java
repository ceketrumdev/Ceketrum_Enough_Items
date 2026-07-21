package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Gère le rendu simplifié de la popup de survol (description réelle de l'item + statistiques).
 */
public class RecipePopupRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-recipe-popup");
    private final Map<Identifier, Long> popupOpenTimes = new HashMap<>(); 
    private final Map<Identifier, Boolean> animationCompleted = new HashMap<>(); 
    private int currentPopupX = 0;
    private int currentPopupY = 0;
    private static final int POPUP_ANIMATION_DURATION = 180; // ms

    public static class ItemStats {
        public int durability = -1;
        public int maxDurability = -1;
        public boolean hasDurability = false;
        
        public int foodPoints = -1;
        public float saturation = -1;
        public boolean hasFood = false;
        
        public double attackDamage = 0;
        public boolean hasAttackDamage = false;
        
        public double attackSpeed = 0;
        public boolean hasAttackSpeed = false;
        
        public double armor = 0;
        public boolean hasArmor = false;
        
        public double toughness = 0;
        public boolean hasToughness = false;
    }

    public ItemStats getItemStats(ItemStack stack) {
        ItemStats stats = new ItemStats();
        
        // Durability
        if (stack.isDamageable()) {
            stats.hasDurability = true;
            stats.maxDurability = stack.getMaxDamage();
            stats.durability = stats.maxDurability - stack.getDamage();
        }
        
        // Food
        if (stack.getItem().isFood()) {
            net.minecraft.item.FoodComponent food = stack.getItem().getFoodComponent();
            if (food != null) {
                stats.hasFood = true;
                stats.foodPoints = food.getHunger();
                stats.saturation = food.getSaturationModifier();
            }
        }
        
        // Attributes
        var modifiers = stack.getAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND);
        if (modifiers != null) {
            for (var entry : modifiers.entries()) {
                var attribute = entry.getKey();
                double amount = entry.getValue().getValue();
                
                if (attribute.equals(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                    stats.hasAttackDamage = true;
                    stats.attackDamage = amount;
                } else if (attribute.equals(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED)) {
                    stats.hasAttackSpeed = true;
                    stats.attackSpeed = amount;
                } else if (attribute.equals(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR)) {
                    stats.hasArmor = true;
                    stats.armor = amount;
                } else if (attribute.equals(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS)) {
                    stats.hasToughness = true;
                    stats.toughness = amount;
                }
            }
        }
        
        return stats;
    }
    
    private int calculateWrappedTextLines(String text, int maxWidth, float scale, net.minecraft.client.font.TextRenderer textRenderer) {
        if (text == null || text.isEmpty()) return 0;
        
        String normalized = text.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n");
        String[] paragraphs = normalized.split("\n");
        int linesCount = 0;
        
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();
            
            for (String word : words) {
                String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
                int testWidth = (int) (textRenderer.getWidth(testLine) * scale);
                
                if (testWidth > maxWidth && currentLine.length() > 0) {
                    linesCount++;
                    currentLine = new StringBuilder(word);
                } else {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
            }
            
            if (currentLine.length() > 0) {
                linesCount++;
            }
        }
        
        return linesCount;
    }
    
    private int calculatePopupHeight(String itemDescription, ItemStats stats, net.minecraft.client.font.TextRenderer textRenderer) {
        int height = 25; // Header (Item name + space)
        
        // Description
        if (!itemDescription.isEmpty()) {
            height += 8; // Margin
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int lines = calculateWrappedTextLines(itemDescription, descMaxWidth, scale, textRenderer);
            height += lines * ((int)(textRenderer.fontHeight * scale) + 2);
        }
        
        // Stats
        boolean hasAnyStats = stats.hasDurability || stats.hasFood || stats.hasAttackDamage || stats.hasAttackSpeed || stats.hasArmor || stats.hasToughness;
        if (hasAnyStats) {
            height += 10; // Separator margin
            int statLinesCount = 0;
            if (stats.hasDurability) statLinesCount++;
            if (stats.hasFood) statLinesCount++;
            if (stats.hasAttackDamage) statLinesCount++;
            if (stats.hasAttackSpeed) statLinesCount++;
            if (stats.hasArmor) statLinesCount++;
            if (stats.hasToughness) statLinesCount++;
            
            float scale = 0.75F;
            height += statLinesCount * ((int)(textRenderer.fontHeight * scale) + 3);
        }
        
        return height + 15; // Safety margin
    }

    public void render(DrawContext context, int mouseX, int mouseY, ItemStack stack,
                       int screenWidth, int screenHeight,
                       net.minecraft.recipe.RecipeManager recipeManager,
                       DynamicRegistryManager dynamicRegistryManager,
                       net.minecraft.client.font.TextRenderer textRenderer,
                       int ceiX, int ceiWidth) {
        
        Identifier itemId = com.ceketrum.cei.data.FavoriteItemsManager.getUniqueItemId(stack);
        
        String itemDescription = ItemDescriptionManager.getInstance().getDescription(stack.getItem());
        ItemStats stats = getItemStats(stack);
        
        int popupHeight = calculatePopupHeight(itemDescription, stats, textRenderer);
        
        // Animation
        float alpha = 1.0f;
        float slideOffset = 0.0f;
        
        Boolean isCompleted = animationCompleted.get(itemId);
        if (isCompleted != null && isCompleted) {
            alpha = 1.0f;
            slideOffset = 0.0f;
        } else {
            Long existingTime = popupOpenTimes.get(itemId);
            long popupOpenTime;
            if (existingTime == null) {
                popupOpenTime = System.currentTimeMillis();
                popupOpenTimes.put(itemId, popupOpenTime);
            } else {
                popupOpenTime = existingTime;
            }
            
            float animationProgress = AnimationHelper.getAnimationProgress(popupOpenTime, POPUP_ANIMATION_DURATION);
            
            if (animationProgress >= 1.0f) {
                animationCompleted.put(itemId, true);
                alpha = 1.0f;
                slideOffset = 0.0f;
                popupOpenTimes.remove(itemId);
            } else {
                alpha = AnimationHelper.easeOut(0.0f, 1.0f, animationProgress);
                slideOffset = AnimationHelper.easeOut(20.0f, 0.0f, animationProgress);
            }
        }
        
        // Position
        int inventoryWidth = 176;
        int inventoryX = (screenWidth - inventoryWidth) / 2;
        int shadowOffset = 3;
        
        int preferredX;
        com.ceketrum.cei.config.CeiConfig config = com.ceketrum.cei.config.CeiConfig.getInstance();
        
        if (config.isPanelOnLeft()) {
            preferredX = (int) (ceiX + ceiWidth + 10 + slideOffset);
            if (preferredX + GuiConstants.POPUP_WIDTH > inventoryX - 10) {
                preferredX = mouseX - GuiConstants.POPUP_WIDTH - 10;
                if (preferredX < ceiX + ceiWidth + 10) {
                    preferredX = ceiX + ceiWidth + 10;
                }
            }
        } else {
            preferredX = (int) (ceiX - GuiConstants.POPUP_WIDTH - 10 - slideOffset);
            if (preferredX < inventoryX + inventoryWidth + 10) {
                preferredX = mouseX + 10;
                if (preferredX + GuiConstants.POPUP_WIDTH > ceiX - 10) {
                    preferredX = ceiX - GuiConstants.POPUP_WIDTH - 10;
                }
            }
        }
        
        if (preferredX + GuiConstants.POPUP_WIDTH > screenWidth - 10) {
            preferredX = screenWidth - GuiConstants.POPUP_WIDTH - 10;
        }
        if (preferredX < 10) {
            preferredX = 10;
        }
        int startX = preferredX;
        
        int preferredY = mouseY + 10;
        if (preferredY + popupHeight > screenHeight - 10) {
            preferredY = screenHeight - popupHeight - 10;
        }
        if (preferredY < 10) {
            preferredY = 10;
        }
        int startY = preferredY;
        
        currentPopupX = startX;
        currentPopupY = startY;
        
        int shadowAlpha = (int) (0x80 * alpha);
        int bgAlpha = (int) (0xC0 * alpha);
        int borderAlpha = (int) (0xFF * alpha);
        int textAlpha = (int) (0xFF * alpha);
        
        // Draw background and borders (sleek glassmorphism)
        context.fill(startX + shadowOffset, startY + shadowOffset, 
                     startX + GuiConstants.POPUP_WIDTH + shadowOffset, startY + popupHeight + shadowOffset, 
                     shadowAlpha << 24);
        
        int popupBgColor = bgAlpha << 24 | 0x121212; // sleek dark background
        GuiRenderHelper.drawRoundedBackground(context, startX, startY, GuiConstants.POPUP_WIDTH, popupHeight, 10, popupBgColor);
        int popupBorderColor = borderAlpha << 24 | 0x3a3a3a;
        context.drawBorder(startX, startY, GuiConstants.POPUP_WIDTH, popupHeight, popupBorderColor);
        
        // Draw Header (Item Name)
        String itemName = stack.getName().getString();
        int maxNameWidth = GuiConstants.POPUP_WIDTH - 20;
        String truncatedName = TextRenderHelper.truncateText(itemName, maxNameWidth, textRenderer);
        int itemNameWidth = textRenderer.getWidth(truncatedName);
        int itemNameColor = textAlpha << 24 | 0xFFFFFF;
        context.drawText(textRenderer, Text.literal(truncatedName).formatted(Formatting.GOLD), 
                        startX + (GuiConstants.POPUP_WIDTH - itemNameWidth) / 2, startY + 8, itemNameColor, false);
        int separatorColor = borderAlpha << 24 | 0x2e2e2e;
        context.fill(startX + 10, startY + 20, startX + GuiConstants.POPUP_WIDTH - 10, startY + 21, separatorColor);
        
        int currentY = startY + 25;
        
        // Draw Description
        if (!itemDescription.isEmpty()) {
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int descColor = (int) (0xDD * alpha) << 24 | 0xDDDDDD;
            currentY = TextRenderHelper.drawWrappedText(context, itemDescription, startX + 10, currentY, 
                                                        descMaxWidth, descColor, scale, 4, textRenderer);
        }
        
        // Draw Stats Separator
        boolean hasAnyStats = stats.hasDurability || stats.hasFood || stats.hasAttackDamage || stats.hasAttackSpeed || stats.hasArmor || stats.hasToughness;
        if (hasAnyStats) {
            if (!itemDescription.isEmpty()) {
                currentY += 4;
                context.fill(startX + 10, currentY, startX + GuiConstants.POPUP_WIDTH - 10, currentY + 1, separatorColor);
                currentY += 6;
            } else {
                currentY += 2;
            }
            
            String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
            boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
            
            // Labels
            String durabilityLabel = isFr ? "Durabilité" : "Durability";
            String foodLabel = isFr ? "Nourriture" : "Food";
            String saturationLabel = isFr ? "Saturation" : "Saturation";
            String damageLabel = isFr ? "Dégâts" : "Damage";
            String speedLabel = isFr ? "Vitesse d'attaque" : "Attack Speed";
            String armorLabel = isFr ? "Armure" : "Armor";
            String toughnessLabel = isFr ? "Robustesse" : "Toughness";
            
            int statColor = (int) (0xBB * alpha) << 24 | 0xAAAAAA;
            float scale = 0.75F;
            
            if (stats.hasAttackDamage) {
                String val = String.format("%s: +%.1f", damageLabel, stats.attackDamage);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
            if (stats.hasAttackSpeed) {
                double speed = 4.0 + stats.attackSpeed;
                String val = String.format("%s: %.1f", speedLabel, speed);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
            if (stats.hasArmor) {
                String val = String.format("%s: +%.0f", armorLabel, stats.armor);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
            if (stats.hasToughness) {
                String val = String.format("%s: +%.0f", toughnessLabel, stats.toughness);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
            if (stats.hasFood) {
                String val = String.format("%s: +%d (%s: +%.1f)", foodLabel, stats.foodPoints, saturationLabel, stats.saturation);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
            if (stats.hasDurability) {
                String val = String.format("%s: %d / %d", durabilityLabel, stats.durability, stats.maxDurability);
                currentY = TextRenderHelper.drawWrappedText(context, val, startX + 10, currentY, GuiConstants.POPUP_WIDTH - 20, statColor, scale, 3, textRenderer);
            }
        }
        
        long currentTime = System.currentTimeMillis();
        popupOpenTimes.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > POPUP_ANIMATION_DURATION * 2);
    }
    
    public boolean isRecipeAreaClicked(int mouseX, int mouseY) {
        return false;
    }
    
    public boolean isResultClicked(int mouseX, int mouseY) {
        return false;
    }
    
    public Object getCurrentRecipe() {
        return null;
    }
    
    public Object getCurrentRecipeEntry() {
        return null;
    }
    
    public int getCurrentResultX() {
        return 0;
    }
    
    public int getCurrentResultY() {
        return 0;
    }
}


