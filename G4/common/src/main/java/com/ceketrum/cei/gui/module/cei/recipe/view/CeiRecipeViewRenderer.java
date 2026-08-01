package com.ceketrum.cei.gui.module.cei.recipe.view;

import com.ceketrum.cei.gui.constants.GuiConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer unique pour un CeiRecipeView.
 *
 * Remplace les huit renderers specialises : la disposition ne depend plus du
 * type de recette mais des dimensions portees par le modele, donc une grille
 * 5x5 ou 2x7 se dessine aussi bien qu'une 3x3.
 */
public final class CeiRecipeViewRenderer {

    private static final int SLOT = GuiConstants.SLOT_SIZE;
    private static final int GAP = 2;
    private static final int SLOT_BG = 0xFF2A2A2A;
    private static final int SLOT_BORDER = 0x44FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTLE = 0xFFAAAAAA;

    private CeiRecipeViewRenderer() {}

    /** Hauteur totale que le rendu occupera, pour reserver la place en amont. */
    public static int measure(CeiRecipeView view, Font font) {
        int rows = Math.max(1, view.gridHeight());
        return font.lineHeight + 4 + rows * (SLOT + GAP) + 4;
    }

    /**
     * Dessine la recette et renvoie la position Y juste apres.
     *
     * @param timeMs horloge utilisee pour faire defiler les variantes d'un tag
     */
    public static int render(GuiGraphics context, int startX, int startY,
                             CeiRecipeView view, Font font, long timeMs) {
        if (view == null) return startY;

        int y = startY;

        // Titre : nom du type de recette, seule information honnete pour un mod
        if (view.title() != null) {
            context.drawString(font, view.title(), startX, y, SUBTLE, false);
            y += font.lineHeight + 4;
        }

        int cols = Math.max(1, view.gridWidth());
        int rows = Math.max(1, view.gridHeight());
        int gridPixelWidth = cols * (SLOT + GAP);

        // Grille d'entrees, aux dimensions reelles de la recette
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * (SLOT + GAP);
                int sy = y + row * (SLOT + GAP);
                drawSlot(context, x, sy, view.slotAt(col, row).current(timeMs), font);
            }
        }

        // Fleche puis resultat, verticalement centres sur la grille
        int arrowX = startX + gridPixelWidth + 4;
        int midY = y + (rows * (SLOT + GAP)) / 2 - SLOT / 2;
        context.drawString(font, "->", arrowX, midY + (SLOT - font.lineHeight) / 2, TEXT, false);

        int outX = arrowX + font.width("->") + 6;
        drawSlot(context, outX, midY, view.primaryOutput(), font);

        // Station de travail : c'est ce qui permet d'afficher "fabrique dans X"
        // sans rien connaitre du mod qui fournit la recette.
        ItemStack station = view.station();
        if (station != null && !station.isEmpty()) {
            int stX = outX + SLOT + 6;
            drawSlot(context, stX, midY, station, font);
        }

        return y + rows * (SLOT + GAP) + 4;
    }

    private static void drawSlot(GuiGraphics context, int x, int y, ItemStack stack, Font font) {
        context.fill(x, y, x + SLOT, y + SLOT, SLOT_BG);
        context.renderOutline(x, y, SLOT, SLOT, SLOT_BORDER);
        if (stack != null && !stack.isEmpty()) {
            context.renderItem(stack, x + 1, y + 1);
            context.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }

    /** L'emplacement survole, ou EMPTY. Sert pour les infobulles et la navigation. */
    public static ItemStack hoveredStack(CeiRecipeView view, int startX, int startY,
                                         int mouseX, int mouseY, Font font, long timeMs) {
        if (view == null) return ItemStack.EMPTY;
        int y = startY + (view.title() != null ? font.lineHeight + 4 : 0);
        int cols = Math.max(1, view.gridWidth());
        int rows = Math.max(1, view.gridHeight());

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * (SLOT + GAP);
                int sy = y + row * (SLOT + GAP);
                if (mouseX >= x && mouseX < x + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                    return view.slotAt(col, row).current(timeMs);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
