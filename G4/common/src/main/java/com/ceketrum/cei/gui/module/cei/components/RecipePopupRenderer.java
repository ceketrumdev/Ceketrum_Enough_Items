package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.i18n.CeiText;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Gère le rendu simplifié de la popup de survol (description réelle de l'item + statistiques).
 */
public class RecipePopupRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-recipe-popup");
    private final Map<ResourceLocation, Long> popupOpenTimes = new HashMap<>();
    private final Map<ResourceLocation, Boolean> animationCompleted = new HashMap<>();
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
        if (stack.isDamageableItem()) {
            stats.hasDurability = true;
            stats.maxDurability = stack.getMaxDamage();
            stats.durability = stats.maxDurability - stack.getDamageValue();
        }

        // Food
        net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            stats.hasFood = true;
            stats.foodPoints = food.nutrition();
            stats.saturation = food.saturation();
        }

        // Attributes
        net.minecraft.world.item.component.ItemAttributeModifiers attributeComponent = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributeComponent != null) {
            for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : attributeComponent.modifiers()) {
                double amount = entry.modifier().amount();
                var attribute = entry.attribute();

                if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                    stats.hasAttackDamage = true;
                    stats.attackDamage = amount;
                } else if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) {
                    stats.hasAttackSpeed = true;
                    stats.attackSpeed = amount;
                } else if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) {
                    stats.hasArmor = true;
                    stats.armor = amount;
                } else if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) {
                    stats.hasToughness = true;
                    stats.toughness = amount;
                }
            }
        }

        return stats;
    }

    private int calculateWrappedTextLines(String text, int maxWidth, float scale, net.minecraft.client.gui.Font textRenderer) {
        if (text == null || text.isEmpty()) return 0;

        String normalized = text.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n");
        String[] paragraphs = normalized.split("\n");
        int linesCount = 0;

        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
                int testWidth = (int) (textRenderer.width(testLine) * scale);

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

    private int calculatePopupHeight(String itemDescription, ItemStats stats, net.minecraft.client.gui.Font textRenderer) {
        int height = 25; // Header (Item name + space)

        // Description
        if (!itemDescription.isEmpty()) {
            height += 8; // Margin
            int descMaxWidth = GuiConstants.POPUP_WIDTH - 20;
            float scale = 0.75F;
            int lines = calculateWrappedTextLines(itemDescription, descMaxWidth, scale, textRenderer);
            height += lines * ((int)(textRenderer.lineHeight * scale) + 2);
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
            height += statLinesCount * ((int)(textRenderer.lineHeight * scale) + 3);
        }

        return height + 15; // Safety margin
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, ItemStack stack,
                       int screenWidth, int screenHeight,
                       net.minecraft.world.item.crafting.RecipeManager recipeManager,
                       RegistryAccess dynamicRegistryManager,
                       net.minecraft.client.gui.Font textRenderer,
                       int ceiX, int ceiWidth) {

        ResourceLocation itemId = com.ceketrum.cei.data.FavoriteItemsManager.getUniqueItemId(stack);

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
        context.renderOutline(startX, startY, GuiConstants.POPUP_WIDTH, popupHeight, popupBorderColor);

        // Draw Header (Item Name)
        String itemName = stack.getHoverName().getString();
        int maxNameWidth = GuiConstants.POPUP_WIDTH - 20;
        String truncatedName = TextRenderHelper.truncateText(itemName, maxNameWidth, textRenderer);
        int itemNameWidth = textRenderer.width(truncatedName);
        int itemNameColor = textAlpha << 24 | 0xFFFFFF;
        context.drawString(textRenderer, Component.literal(truncatedName).withStyle(ChatFormatting.GOLD),
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
            String durabilityLabel = CeiText.t("cei.stat.durability");
            String foodLabel = CeiText.t("cei.stat.food");
            String saturationLabel = CeiText.t("cei.stat.saturation");
            String damageLabel = CeiText.t("cei.stat.damage");
            String speedLabel = CeiText.t("cei.stat.attack_speed");
            String armorLabel = CeiText.t("cei.stat.armor");
            String toughnessLabel = CeiText.t("cei.stat.toughness");

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
