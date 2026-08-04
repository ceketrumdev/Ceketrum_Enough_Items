package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.i18n.CeiText;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Bouton "?" a droite de la barre de recherche, et completion des prefixes.
 *
 * Les deux vivent ensemble parce qu'ils repondent a la meme question : "qu'est
 * ce que je peux taper ici ?". L'un le rappelle, l'autre le propose.
 *
 * Tout ce qui recouvre la liste est dessine par renderOverlay(), appele en fin
 * de frame. Le bouton, lui, reste avec la barre : rien ne passe devant lui.
 */
public class CeiSearchHelp {

    public static final int BUTTON_W = 14;
    private static final int BAR_H = 14;
    private static final int ROW_H = 11;
    private static final int MAX_ROWS = 6;

    private int btnX, btnY;
    private int popX, popY, popW;
    private final List<String> shown = new ArrayList<>();
    /** Le prefixe en cours, pour recomposer la ligne au clic. */
    private char prefix = '@';
    /** Bas de ce qui recouvre la liste ; zero si rien ne la recouvre. */
    /**
     * Les lignes de l'infobulle, construites une seule fois.
     *
     * Elles vivent ici et pas dans le dessin parce que leur NOMBRE decide
     * de la hauteur, et que cette hauteur sert a couper la liste d'items.
     * Les recompter au dessin, c'est se donner deux verites.
     */
    private final List<String> helpLines = new ArrayList<>();
    /** Haut de la liste d'items : les fenetres flottantes s'y posent. */
    private int listTop = 0;
    /** Bornes du panneau : l'infobulle se pose a cote, pas dedans. */
    private int panelX = 0, panelWidth = 0;
    private boolean helpVisible = false;
    private int popBottom = 0;

    /** Le bouton, dessine avec la barre de recherche. */
    public void renderButton(DrawContext context, int x, int y,
                             net.minecraft.client.font.TextRenderer textRenderer, double mouseX, double mouseY,
                             String searchText, java.util.List<String> mods,
                             java.util.List<String> tags, int listTop,
                             int panelX, int panelWidth) {
        btnX = x; btnY = y; this.listTop = listTop;
        this.panelX = panelX; this.panelWidth = panelWidth;
        // La geometrie de la fenetre est decidee ICI, avant que la liste
        // d'items ne se dessine : c'est elle qui doit savoir ou s'arreter.
        prepare(searchText, mods, tags, textRenderer, mouseX, mouseY);
        boolean hov = hovered(mouseX, mouseY);
        context.fill(x, y, x + BUTTON_W, y + BAR_H, hov ? 0xFF3A3A3A : 0xFF2C2C2C);
        context.drawBorder(x, y, BUTTON_W, BAR_H, GuiConstants.BORDER_COLOR);
        String q = "?";
        context.drawText(textRenderer, Text.literal(q),
                x + (BUTTON_W - textRenderer.getWidth(q)) / 2,
                y + (BAR_H - textRenderer.fontHeight) / 2 + 1,
                hov ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private boolean hovered(double mouseX, double mouseY) {
        return mouseX >= btnX && mouseX < btnX + BUTTON_W
                && mouseY >= btnY && mouseY < btnY + BAR_H;
    }

    /**
     * L'infobulle d'aide et la completion, dessinees en fin de frame.
     *
     * Les deux recouvrent la liste d'items : les dessiner avec la barre les
     * ferait passer dessous.
     */
    /**
     * Decide ce qui sera affiche, sans rien dessiner.
     *
     * Separer la decision du dessin est ce qui permet a la liste d'items,
     * rendue entre les deux, de savoir ou s'arreter. Tout calculer au
     * moment du dessin donnerait une coupe en retard d'une frame.
     */
    private void prepare(String searchText, java.util.List<String> mods,
                         java.util.List<String> tags, net.minecraft.client.font.TextRenderer textRenderer,
                         double mouseX, double mouseY) {
        shown.clear();
        popBottom = 0;
        helpLines.clear();
        helpVisible = false;
        if (hovered(mouseX, mouseY)) {
            List<String> lines = helpLines;
            lines.add(CeiText.t("gui.cei.search.help.title"));
            lines.add(CeiText.t("gui.cei.search.help.mod"));
            lines.add(CeiText.t("gui.cei.search.help.tag"));
            lines.add(CeiText.t("gui.cei.search.help.desc"));
            lines.add(CeiText.t("gui.cei.search.help.regex"));
            lines.add(CeiText.t("gui.cei.search.help.combine"));
            // La hauteur vient du NOMBRE de lignes, jamais d'un compte
            // ecrit a la main : c'est ce qui l'avait fait deborder quand
            // la ligne #tag s'est ajoutee.
            // Posee au niveau de la liste, pas du bouton : partir du bouton
            // masquait la barre de recherche et les deux boutons, c'est-a-dire
            // ce que le joueur est en train d'utiliser.
            // Aucune hauteur a retenir : l'infobulle se dessine HORS du
            // panneau, elle ne recouvre donc pas la liste et n'entre pas
            // dans le calcul de sa coupe.
            helpVisible = true;
            return;
        }
        if (searchText == null || searchText.indexOf(' ') >= 0) return;
        List<String> source;
        if (searchText.startsWith("@")) source = mods;
        else if (searchText.startsWith("#")) source = tags;
        else return;
        prefix = searchText.charAt(0);
        String typed = searchText.substring(1).toLowerCase();
        if (source == null) return;
        for (String id : source) {
            if (id.toLowerCase().contains(typed)) shown.add(id);
            if (shown.size() >= MAX_ROWS) break;
        }
        if (shown.isEmpty()) return;
        popW = 190;
        popX = Math.max(0, btnX + BUTTON_W - popW);
        popY = listTop + 2;
        popBottom = popY + shown.size() * ROW_H + 4;
    }

    /** Bas de ce qui recouvre la liste, pour qu'elle n'y dessine rien. */
    public int overlayBottom() {
        // Seule la completion recouvre encore la liste.
        return popBottom;
    }

    public void renderOverlay(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer,
                              double mouseX, double mouseY) {
        if (helpVisible) {
            // Exactement les lignes mesurees par prepare : les reconstruire
            // ici rouvrirait la porte a un ecart entre mesure et dessin.
            List<String> lines = helpLines;
            int w = 0;
            for (String l : lines) w = Math.max(w, textRenderer.getWidth(l));
            int h = lines.size() * (textRenderer.fontHeight + 1) + 6;
            // Le cote suit celui du panneau : colle a droite de l'ecran, une
            // infobulle posee a droite du bouton sort du cadre. La source est
            // la configuration du mod -- la meme que pour la fenetre d'astuces.
            // DEHORS, du cote libre : le panneau est colle a un bord, l'autre
            // cote de l'ecran ne sert a rien. Chercher une place a l'interieur
            // revenait a masquer soit l'en-tete, soit les items.
            boolean panelLeft = com.ceketrum.cei.config.CeiConfig.getInstance().isPanelOnLeft();
            int x = panelLeft ? panelX + panelWidth + 4 : panelX - 4 - (w + 10);
            if (x < 0) x = 0;
            int y = btnY;
            context.fill(x, y, x + w + 10, y + h, 0xF0141414);
            context.drawBorder(x, y, w + 10, h, GuiConstants.BORDER_COLOR);
            int ly = y + 4;
            for (int i = 0; i < lines.size(); i++) {
                context.drawText(textRenderer, Text.literal(lines.get(i)), x + 5, ly,
                        i == 0 ? 0xFFFFFF55 : 0xFFCCCCCC, false);
                ly += textRenderer.fontHeight + 1;
            }
            return;
        }

        if (shown.isEmpty()) return;
        int h = shown.size() * ROW_H + 4;
        context.fill(popX, popY, popX + popW, popY + h, 0xF0141414);
        context.drawBorder(popX, popY, popW, h, GuiConstants.BORDER_COLOR);
        int ly = popY + 2;
        for (String id : shown) {
            boolean hov = mouseX >= popX && mouseX < popX + popW
                    && mouseY >= ly && mouseY < ly + ROW_H;
            if (hov) context.fill(popX + 1, ly, popX + popW - 1, ly + ROW_H, 0x33FFFFFF);
            context.drawText(textRenderer, Text.literal(id), popX + 4, ly + 1,
                    hov ? 0xFFFFFFFF : 0xFFBBBBBB, false);
            ly += ROW_H;
        }
    }

    /**
     * @return la ligne completee si une proposition a ete cliquee, sinon null.
     */
    public String click(double mouseX, double mouseY) {
        if (shown.isEmpty()) return null;
        int ly = popY + 2;
        for (String id : shown) {
            if (mouseX >= popX && mouseX < popX + popW
                    && mouseY >= ly && mouseY < ly + ROW_H) {
                // L'espace final enchaine directement sur la recherche par nom.
                return prefix + id + " ";
            }
            ly += ROW_H;
        }
        return null;
    }
}
