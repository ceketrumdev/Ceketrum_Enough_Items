package com.ceketrum.cei.gui.module.cei.recipe;

import com.ceketrum.cei.i18n.CeiText;
import com.ceketrum.cei.gui.constants.GuiConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Renderer générique et universel pour les recettes de machines personnalisées de mods (Mekanism, Create, etc.).
 * Adapte dynamiquement son affichage en fonction du nombre d'ingrédients d'entrée.
 */
public class CustomMachineRecipeRenderer implements IRecipeRenderer {

    @Override
    public int render(GuiGraphics context, int startX, int startY,
                      Recipe<?> recipe, RecipeHolder<?> recipeEntry,
                      RegistryAccess dynamicRegistryManager,
                      ItemStack hoveredStack,
                      String itemDescription,
                      net.minecraft.client.gui.Font textRenderer) {
        
        // 1. Déterminer le nom de la machine et du mod
        String lang = com.ceketrum.cei.data.ItemDescriptionManager.getInstance().getCurrentLanguage();
        String title = getMachineName(recipe, lang);
        int titleWidth = textRenderer.width(title);
        
        // Centrer et dessiner le titre de la machine
        context.drawString(textRenderer, Component.literal(title), 
                        startX + (GuiConstants.POPUP_WIDTH - titleWidth) / 2, startY, 0xFFFFFF, false);
        
        int currentY = startY + 12;
        
        // 2. Extraire les ingrédients d'entrée
        List<ItemStack> inputs = new ArrayList<>();
        try {
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                ItemStack[] matching = ingredient.getItems();
                if (matching.length > 0) {
                    inputs.add(matching[0]);
                }
            }
        } catch (Exception e) {
            // Sécurité en cas d'API moddée instable
        }
        
        // Fallback si aucun ingrédient n'est trouvé
        if (inputs.isEmpty()) {
            inputs.add(ItemStack.EMPTY);
        }
        
        // 3. Récupérer l'item de sortie
        ItemStack output = ItemStack.EMPTY;
        try {
            output = recipe.getResultItem(dynamicRegistryManager);
        } catch (Exception e) {
            // Sécurité
        }
        if (output.isEmpty()) {
            output = hoveredStack; // Fallback sur l'item courant
        }
        
        // 4. Calculer la mise en page dynamique en fonction du nombre d'entrées
        int numInputs = Math.min(inputs.size(), 4); // Limiter à 4 entrées en ligne
        int inputsWidth = numInputs * GuiConstants.SLOT_SIZE + (numInputs - 1) * 2;
        
        // Largeur totale du bloc = inputsWidth + gap (6) + arrow (16) + head (4) + gap (6) + output (18) = inputsWidth + 50
        int blockWidth = inputsWidth + 50;
        int blockStartX = startX + (GuiConstants.POPUP_WIDTH - blockWidth) / 2;
        
        // 5. Dessiner les items d'entrée
        for (int i = 0; i < numInputs; i++) {
            int inputX = blockStartX + i * (GuiConstants.SLOT_SIZE + 2);
            context.renderItem(inputs.get(i), inputX, currentY);
        }
        
        // 6. Dessiner la flèche animée
        int arrowStartX = blockStartX + inputsWidth + 6;
        int arrowCenterY = currentY + GuiConstants.SLOT_SIZE / 2;
        int arrowLength = 16;
        int arrowHeight = 4;
        
        long currentTime = System.currentTimeMillis();
        double fillProgress = (currentTime % 2000) / 2000.0;
        
        int arrowBodyY = arrowCenterY - arrowHeight / 2;
        context.fill(arrowStartX, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF555555);
        
        int filledWidth = (int)(arrowLength * fillProgress);
        if (filledWidth > 0) {
            context.fill(arrowStartX, arrowBodyY, arrowStartX + filledWidth, arrowBodyY + arrowHeight, 0xFFFFFFFF);
        }
        
        // Bordures de la flèche
        context.fill(arrowStartX, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + 1, 0xFF000000);
        context.fill(arrowStartX, arrowBodyY + arrowHeight - 1, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF000000);
        context.fill(arrowStartX, arrowBodyY, arrowStartX + 1, arrowBodyY + arrowHeight, 0xFF000000);
        context.fill(arrowStartX + arrowLength - 1, arrowBodyY, arrowStartX + arrowLength, arrowBodyY + arrowHeight, 0xFF000000);
        
        // Tête de la flèche
        int arrowHeadX = arrowStartX + arrowLength;
        int arrowHeadSize = 4;
        int arrowTipX = arrowHeadX + arrowHeadSize;
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowCenterY - i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        for (int i = 0; i <= arrowHeadSize; i++) {
            int x = arrowTipX - i;
            int y = arrowCenterY + i;
            context.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        
        // 7. Dessiner le slot de sortie
        int outputX = arrowStartX + arrowLength + arrowHeadSize + 6;
        context.renderItem(output, outputX, currentY);
        
        return currentY + GuiConstants.SLOT_SIZE;
    }
    
    /**
     * Génère et formate proprement le nom de la machine et du mod d'origine.
     */
    public static String getMachineName(Recipe<?> recipe, String lang) {
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) {
            return CeiText.t("cei.machine.unknown");
        }
        
        String namespace = typeId.getNamespace();
        String path = typeId.getPath();
        
        String machineAction = path.toLowerCase().replace("_", " ");
        // Une cle par action, avec repli sur le chemin embelli. Le switch
        // francais qui etait ici ne laissait aux autres langues que le
        // chemin brut, et il fallait le rouvrir pour chaque nouveau mod.
        machineAction = CeiText.or("cei.action." + path, capitalizeString(machineAction));
        
        String modName = capitalizeString(namespace);
        
        return machineAction + " (" + modName + ")";
    }
    
    private static String capitalizeString(String str) {
        if (str == null || str.isEmpty()) return "";
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
