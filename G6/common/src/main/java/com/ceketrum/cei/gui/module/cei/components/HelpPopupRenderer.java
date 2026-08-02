package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.i18n.CeiText;
import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Gère le rendu de la popup d'aide qui explique comment utiliser les favoris.
 */
public class HelpPopupRenderer {
    private static final Random RANDOM = new Random();
    /** Largeur plancher : en dessous, la fenetre parait rachitique. */
    private static final int POPUP_MIN_WIDTH = 200;
    /** Largeur plafond : au-dela, la fenetre mange l'ecran. */
    private static final int POPUP_MAX_WIDTH = 360;
    private static final int POPUP_HEIGHT = 80;
    private static final int PADDING = 10;
    private static final int CLOSE_SIZE = 12;
    private static final int CLOSE_MARGIN = 5;
    private static final int ANIMATION_DURATION = 300;
    private static final double SHOW_CHANCE = 0.15; // 15% de chance d'apparaître

    private Long popupOpenTime = null;
    private boolean shouldShow = false;
    /**
     * Largeur reellement dessinee au dernier rendu.
     *
     * Elle sert aussi au test de clic : si celui-ci recalculait la position du
     * bouton avec une largeur fixe, le bouton deviendrait faux des que la
     * boite s'elargit pour une langue plus verbeuse.
     */
    private int popupWidth = POPUP_MIN_WIDTH;

    private int selectedTipIndex = 0; // Stocker le choix de l'astuce une seule fois
    private final CeiConfig config = CeiConfig.getInstance();

    /**
     * Détermine si la popup doit être affichée (aléatoirement).
     */
    public void checkShouldShow() {
        if (popupOpenTime == null && config.isShowHelpPopup()) {
            // 15% de chance d'afficher la popup
            shouldShow = RANDOM.nextDouble() < SHOW_CHANCE;
            if (shouldShow) {
                popupOpenTime = System.currentTimeMillis();
                // Choisir l'astuce au hasard parmi les 5 astuces disponibles
                selectedTipIndex = RANDOM.nextInt(5);
            }
        }
    }

    /**
     * Rend la popup d'aide si nécessaire.
     */
    public void render(GuiGraphics context, int screenWidth, int screenHeight,
                      net.minecraft.client.gui.Font textRenderer) {
        if (!shouldShow || popupOpenTime == null || !config.isShowHelpPopup()) {
            return;
        }

        float animationProgress = AnimationHelper.getAnimationProgress(popupOpenTime, ANIMATION_DURATION);
        if (animationProgress >= 1.0f) {
            animationProgress = 1.0f;
        }

        float alpha = AnimationHelper.easeOut(0.0f, 1.0f, animationProgress);
        float slideOffset = AnimationHelper.easeOut(30.0f, 0.0f, animationProgress);

        // L'astuce est choisie AVANT de positionner la fenetre : c'est son
        // texte qui en determine la largeur, et la largeur qui determine la
        // position.
        String[] tip = currentTip();
        String title = tip[0];
        String helpText = tip[1];
        String helpText2 = tip[2];

        // Le titre doit tenir A COTE du bouton de fermeture, pas dessous.
        int titleNeed = textRenderer.width(title) + CLOSE_SIZE + CLOSE_MARGIN * 2;
        int need = Math.max(titleNeed,
                   Math.max(textRenderer.width(helpText), textRenderer.width(helpText2)))
                   + PADDING * 2;
        int maxWidth = Math.min(POPUP_MAX_WIDTH, Math.max(POPUP_MIN_WIDTH, screenWidth - 40));
        popupWidth = Math.max(POPUP_MIN_WIDTH, Math.min(need, maxWidth));

        // Positionner la popup en fonction de la position du panneau CEI
        // Si le panneau est à gauche, la popup va à droite, et vice versa
        int popupX;
        if (config.isPanelOnLeft()) {
            // Panneau à gauche : popup en bas à droite
            popupX = (int) (screenWidth - popupWidth - 20 - slideOffset);
        } else {
            // Panneau à droite : popup en bas à gauche
            popupX = (int) (20 + slideOffset);
        }
        int popupY = (int) (screenHeight - POPUP_HEIGHT - 20);

        // Appliquer l'alpha
        int bgAlpha = (int) (0xE0 * alpha);
        int borderAlpha = (int) (0xFF * alpha);
        int textAlpha = (int) (0xFF * alpha);

        // Ombre
        int shadowAlpha = (int) (0x80 * alpha);
        context.fill(popupX + 3, popupY + 3, popupX + popupWidth + 3, popupY + POPUP_HEIGHT + 3,
                    shadowAlpha << 24);

        // Fond
        int bgColor = bgAlpha << 24 | 0x202020;
        GuiRenderHelper.drawRoundedBackground(context, popupX, popupY, popupWidth, POPUP_HEIGHT, 8, bgColor);
        int borderColor = (borderAlpha << 24) | 0x00FFFFFF;
        context.renderOutline(popupX, popupY, popupWidth, POPUP_HEIGHT, borderColor);


        int titleColor = textAlpha << 24 | 0xFFFF00;
        // Centre dans l'espace LIBRE, celui que le bouton de fermeture ne
        // prend pas. Centrer sur toute la largeur faisait passer les
        // titres longs sous le bouton.
        int titleZone = popupWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int titleX = popupX + Math.max(PADDING, (titleZone - textRenderer.width(title)) / 2);
        context.drawString(textRenderer, Component.literal(title).withStyle(ChatFormatting.BOLD),
                        titleX, popupY + 8, titleColor, false);

        // Texte d'aide
        int textColor = textAlpha << 24 | 0x00FFFFFF;
        int textY = popupY + 25;
        int textX = popupX + (popupWidth - textRenderer.width(helpText)) / 2;
        context.drawString(textRenderer, Component.literal(helpText), textX, textY, textColor, false);
        if (!helpText2.isEmpty()) {
            textX = popupX + (popupWidth - textRenderer.width(helpText2)) / 2;
            context.drawString(textRenderer, Component.literal(helpText2), textX, textY + 12, textColor, false);
        }

        // Bouton fermer (petit X en haut à droite)
        int closeButtonSize = CLOSE_SIZE;
        int closeButtonX = popupX + popupWidth - closeButtonSize - 5;
        int closeButtonY = popupY + 5;
        context.fill(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize,
                    (borderAlpha << 24) | 0x666666);

        // Centrer le texte "×" dans le bouton
        String closeSymbol = "×";
        int symbolWidth = textRenderer.width(closeSymbol);
        // Centrer horizontalement : position du bouton + (largeur du bouton - largeur du texte) / 2
        int symbolX = closeButtonX + (closeButtonSize - symbolWidth) / 2;
        // Centrer verticalement : position du bouton + (hauteur du bouton - hauteur de la police) / 2
        // Le Y de drawText est la position de la ligne de base, donc on doit ajuster
        int symbolY = closeButtonY + (closeButtonSize - textRenderer.lineHeight) / 2;
        context.drawString(textRenderer, Component.literal(closeSymbol), symbolX, symbolY, textColor, false);
    }

    /**
     * Les trois lignes de l'astuce courante : titre, ligne 1, ligne 2.
     *
     * Regroupees ici parce que le rendu en a besoin deux fois -- une fois pour
     * mesurer, une fois pour dessiner -- et qu'un texte mesure autrement que
     * dessine est la meilleure facon d'obtenir un cadre qui ne colle pas.
     */
    private String[] currentTip() {
        switch (selectedTipIndex) {
            case 0:
                return new String[] { CeiText.t("cei.tip.shortcuts.title"),
                                      CeiText.t("cei.tip.shortcuts.line1"),
                                      CeiText.t("cei.tip.shortcuts.line2") };
            case 1:
                return new String[] { CeiText.t("cei.tip.pin.title"),
                                      CeiText.t("cei.tip.pin.line1"),
                                      CeiText.t("cei.tip.pin.line2") };
            case 2:
                return new String[] { CeiText.t("cei.tip.drag.title"),
                                      CeiText.t("cei.tip.drag.line1"),
                                      CeiText.t("cei.tip.drag.line2") };
            case 3:
                return new String[] { CeiText.t("cei.tip.autofill.title"),
                                      CeiText.t("cei.tip.autofill.line1"),
                                      CeiText.t("cei.tip.autofill.line2") };
            default:
                return new String[] { CeiText.t("cei.tip.favorites.title"),
                                      CeiText.t("cei.tip.favorites.line1"),
                                      CeiText.t("cei.tip.favorites.line2") };
        }
    }

    /**
     * Vérifie si le clic est sur le bouton fermer.
     */
    public boolean isCloseButtonClicked(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!shouldShow || popupOpenTime == null) {
            return false;
        }

        // Calculer la position de la popup selon la position du panneau
        int popupX;
        if (config.isPanelOnLeft()) {
            popupX = screenWidth - popupWidth - 20;
        } else {
            popupX = 20;
        }
        int popupY = screenHeight - POPUP_HEIGHT - 20;
        int closeButtonSize = CLOSE_SIZE;
        int closeButtonX = popupX + popupWidth - closeButtonSize - 5;
        int closeButtonY = popupY + 5;

        return mouseX >= closeButtonX && mouseX < closeButtonX + closeButtonSize &&
               mouseY >= closeButtonY && mouseY < closeButtonY + closeButtonSize;
    }

    /**
     * Ferme la popup.
     */
    public void close() {
        shouldShow = false;
        popupOpenTime = null;
        selectedTipIndex = 0; // Réinitialiser pour la prochaine fois
    }

    /**
     * Vérifie si la popup est visible.
     */
    public boolean isVisible() {
        return shouldShow && popupOpenTime != null;
    }
}


