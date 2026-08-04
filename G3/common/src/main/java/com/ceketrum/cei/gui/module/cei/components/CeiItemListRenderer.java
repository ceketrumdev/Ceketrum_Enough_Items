package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.data.FavoriteItemsManager;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.module.cei.util.AnimationHelper;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Gère le rendu de la liste d'items dans le panneau CEI avec scroll.
 */
public class CeiItemListRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-hover-animation");
    private static final boolean DEBUG_ANIMATION = false; // Activer/désactiver les logs de debug

    /**
     * Cible du defilement, EN PIXELS.
     *
     * L'ancien champ comptait des lignes, dans un entier : un cran de molette
     * deplacait les items de 54 pixels d'un coup. Les pixels permettent de
     * s'arreter entre deux lignes, ce qui est toute la difference entre un
     * defilement et une teleportation.
     */
    /**
     * Haut impose a la zone visible, quand quelque chose la recouvre.
     *
     * Un item ne se dessine pas comme un aplat : il passe par le pipeline
     * de modeles, avec son propre decalage de profondeur. Le peindre en
     * premier ne suffit donc pas a le faire passer derriere. On l'empeche
     * plutot d'etre dessine la, en descendant le haut de la coupe.
     */
    private int clipTop = 0;

    public void setClipTop(int y) {
        this.clipTop = y;
    }

    private float scrollTarget = 0f;

    /**
     * Position reellement DESSINEE, qui rejoint la cible.
     *
     * Les deux sont necessaires : la cible dit ou l'on va, celle-ci dit ou
     * l'on est. Les confondre, c'est revenir au saut d'un cran.
     */
    private float scrollShown = 0f;
    private long scrollNanos = 0L;
    private int lastMaxScrollPx = 0;

    /** Geometrie de la barre au dernier rendu, memorisee pour le clic. */
    private int barX = 0, barY = 0, barW = 4, barH = 0, thumbY = 0, thumbH = 0;
    private boolean barVisible = false;
    private boolean draggingBar = false;
    private float dragGrab = 0f;

    /**
     * Rapproche la position dessinee de la cible.
     *
     * Amortissement exponentiel cale sur le temps ecoule : un pas fixe par
     * image defilerait deux fois plus vite a 120 images par seconde qu'a 60.
     * Le delta est plafonne, sinon un chargement ferait tout rattraper d'un
     * coup -- ce qui revient a ne pas avoir d'animation du tout.
     *
     * Pendant qu'on tient l'ascenseur, aucun amortissement : la position EST
     * la cible. Une barre qui traine derriere la souris donne l'impression
     * d'un jeu qui rame.
     */
    private void stepScroll(int maxScrollPx) {
        lastMaxScrollPx = maxScrollPx;
        scrollTarget = Math.max(0f, Math.min(maxScrollPx, scrollTarget));

        long now = System.nanoTime();
        float dt = (scrollNanos == 0L) ? 0f
                : Math.min(0.1f, (now - scrollNanos) / 1_000_000_000f);
        scrollNanos = now;

        if (draggingBar) {
            scrollShown = scrollTarget;
            return;
        }
        float delta = scrollTarget - scrollShown;
        if (Math.abs(delta) < 0.3f) {
            scrollShown = scrollTarget;
            return;
        }
        scrollShown += delta * (1f - (float) Math.exp(-18.0 * dt));
    }

    /** Premiere ligne dessinee. */
    public int getScrollRow() {
        return (int) Math.floor(scrollShown / GuiConstants.SLOT_SIZE);
    }

    /** De combien de pixels cette premiere ligne est remontee hors de la bande. */
    public int getScrollShift() {
        return Math.round(scrollShown - getScrollRow() * GuiConstants.SLOT_SIZE);
    }

    /**
     * Clic sur la barre.
     *
     * Sur le curseur : on le saisit la ou on a clique, il ne saute pas sous la
     * souris. Ailleurs sur la barre : on l'amene d'un coup, centre sous la
     * souris, et le glisser s'enchaine. C'est ce que fait tout ascenseur, et
     * ca evite d'avoir a viser douze pixels de haut.
     */
    public boolean handleBarClick(double mouseX, double mouseY) {
        if (!barVisible) return false;
        // Deux pixels de tolerance de chaque cote : la barre fait quatre
        // pixels de large, la viser au pixel pres serait une punition.
        if (mouseX < barX - 2 || mouseX >= barX + barW + 2) return false;
        if (mouseY < barY || mouseY >= barY + barH) return false;

        if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
            dragGrab = (float) (mouseY - thumbY);
        } else {
            dragGrab = thumbH / 2f;
            applyBarPosition(mouseY);
        }
        draggingBar = true;
        return true;
    }

    /** Fin du glisser. Appele sans argument : la signature des evenements de
     *  souris a change en 26.x, et on n'a besoin d'aucun de leurs champs. */
    public void endBarDrag() {
        draggingBar = false;
    }

    public boolean isDraggingBar() {
        return draggingBar;
    }

    private void applyBarPosition(double mouseY) {
        int travel = barH - thumbH;
        if (travel <= 0) return;
        float ratio = (float) ((mouseY - dragGrab - barY) / travel);
        ratio = Math.max(0f, Math.min(1f, ratio));
        scrollTarget = ratio * lastMaxScrollPx;
        scrollShown = scrollTarget;
    }

    private final Map<ResourceLocation, Long> favoriteToggleTimes = new HashMap<>();
    private final Map<ResourceLocation, Long> hoverStartTimes = new HashMap<>();
    private final Map<ResourceLocation, Float> cachedHoverScale = new HashMap<>();
    private final Map<ResourceLocation, Float> cachedGlowAlpha = new HashMap<>();
    private final Map<ResourceLocation, Long> unhoverStartTimes = new HashMap<>(); // Quand l'item n'est plus survolé
    private final Map<ResourceLocation, Boolean> lastHoverState = new HashMap<>(); // État précédent du survol pour détecter les changements
    private final FavoriteItemsManager favoriteManager = FavoriteItemsManager.getInstance();
    private static final int HOVER_ANIMATION_DURATION = 200; // ms
    private static final long UNHOVER_DELAY = 100; // Délai avant de vider le cache après avoir quitté le survol (ms)
    private long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL = 5000; // Nettoyer toutes les 5 secondes

    /**
     * Rend la liste d'items dans le panneau CEI.
     *
     * @param context Le contexte de rendu
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param allItems Liste de tous les items à afficher
     * @param ceiHeight Hauteur disponible pour le panneau CEI
     * @param itemsListStartY Position Y où commence la liste d'items
     * @param textRenderer Le TextRenderer pour mesurer le texte
     * @return L'ItemStack survolé, ou null si aucun
     */
    public ItemStack render(GuiGraphics context, int mouseX, int mouseY,
                           List<ItemStack> allItems, int ceiHeight, int itemsListStartY,
                           int ceiWidth, int ceiX, int ceiY,
                           net.minecraft.client.gui.Font textRenderer,
                           float animationSlideOffset, float animationAlpha) {
        int columns = (ceiWidth - 2 * GuiConstants.PADDING) / GuiConstants.SLOT_SIZE;

        // Calculer la hauteur disponible : depuis itemsListStartY jusqu'à la fin du panneau CEI
        // ceiHeight est la hauteur totale du panneau depuis ceiY
        // Donc la fin du panneau est à : ceiY + ceiHeight
        // La hauteur disponible est donc : (ceiY + ceiHeight) - itemsListStartY
        int ceiBottom = ceiY + ceiHeight;
        int availableHeight = ceiBottom - itemsListStartY;

        // S'assurer que la hauteur disponible est positive
        if (availableHeight < 0) {
            availableHeight = 0;
        }

        int maxVisibleRows = availableHeight / GuiConstants.SLOT_SIZE;
        int maxVisibleItems = columns * (maxVisibleRows + 2);

        // Calculer le nombre total de lignes nécessaires
        int totalRows = (allItems.size() + columns - 1) / columns; // Arrondi vers le haut
        int maxScroll = Math.max(0, totalRows - maxVisibleRows);

        // Limiter le scroll
        int maxScrollPx = maxScroll * GuiConstants.SLOT_SIZE;
        lastMaxScrollPx = maxScrollPx;
        // Le glisser se met a jour ICI : render() recoit deja la
        // position de la souris a chaque image, il n'y a donc aucun
        // evenement de deplacement a intercepter.
        if (draggingBar) applyBarPosition(mouseY);
        stepScroll(maxScrollPx);

        int shift = getScrollShift();
        int startIndex = getScrollRow() * columns;

        ItemStack hoveredStack = null;

        // Appliquer l'offset d'animation aux positions
        int animatedCeiX = (int) (ceiX + animationSlideOffset);

        // Afficher seulement les items visibles (pour les performances)
        // Une ligne depasse en haut et une en bas pour que celle qui
        // entre glisse au lieu d'apparaitre. Ce qui depasse est coupe.
        // On coupe sur un nombre ENTIER de lignes, PAS sur ceiBottom : la
        // hauteur disponible n'est presque jamais un multiple de la hauteur
        // d'une case, et couper sur la bordure y laissait une demi-rangee
        // collee au cadre. La bande restante sert de marge.
        int bandBottom = itemsListStartY + maxVisibleRows * GuiConstants.SLOT_SIZE;
        // Les items gardent leurs coordonnees : ils sont seulement
        // invisibles sous ce qui les recouvre, rien ne se decale.
        int bandTop = Math.min(Math.max(itemsListStartY, clipTop), bandBottom);
        context.enableScissor(animatedCeiX, bandTop,
                animatedCeiX + ceiWidth, bandBottom);
        for (int i = startIndex; i < Math.min(allItems.size(), startIndex + maxVisibleItems); i++) {
            ItemStack stack = allItems.get(i);
            int relativeIndex = i - startIndex;
            int x = animatedCeiX + GuiConstants.PADDING + (relativeIndex % columns) * GuiConstants.SLOT_SIZE;
            int y = itemsListStartY - shift + (relativeIndex / columns) * GuiConstants.SLOT_SIZE;

            // Fond du slot
            context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF202020);

            // Utiliser l'Identifier unique incluant les Data Components pour différencier les variantes
            ResourceLocation itemId = FavoriteItemsManager.getUniqueItemId(stack);

            // Animation au survol (zoom + glow)
            boolean isHovered = GuiRenderHelper.isMouseOver(mouseX, mouseY, x, y, GuiConstants.SLOT_SIZE, GuiConstants.SLOT_SIZE);
            float hoverScale = 1.0f;
            float glowAlpha = 0.0f;

            // Détecter les changements d'état du survol pour les logs (seulement si l'item est actuellement survolé ou l'était récemment)
            // Tout ce bloc n'existe que pour la journalisation de mise au
            // point. hoverStateChanged n'est lu que dans des conditions
            // contenant DEBUG_ANIMATION ; lastHoverState et shouldLog n'ont
            // pas d'autre role que d'alimenter ce reperage.
            //
            // DEBUG_ANIMATION etant faux, on payait quatre recherches de table
            // -- et parfois une ecriture -- par case et par image, pour des
            // journaux qui ne sortent jamais. lastHoverState grossissait en
            // outre sans fin : cleanupFinishedAnimations ne purge que
            // favoriteToggleTimes.
            //
            // Remettre DEBUG_ANIMATION a vrai redonne l'ancien comportement.
            boolean shouldLog = false;
            boolean hoverStateChanged = false;
            if (DEBUG_ANIMATION) {
                Boolean lastHovered = lastHoverState.get(itemId);
                hoverStateChanged = (lastHovered == null) || (lastHovered != isHovered);
                shouldLog = isHovered
                        || cachedHoverScale.containsKey(itemId)
                        || hoverStartTimes.containsKey(itemId)
                        || unhoverStartTimes.containsKey(itemId);
                if (hoverStateChanged) {
                    lastHoverState.put(itemId, isHovered);
                    if (shouldLog) {
                        LOGGER.info("[HOVER STATE] Item: {} | isHovered: {} -> {} | Cache: scale={}, alpha={} | hoverStartTimes: {} | unhoverStartTimes: {}",
                            itemId, lastHovered, isHovered,
                            cachedHoverScale.get(itemId), cachedGlowAlpha.get(itemId),
                            hoverStartTimes.containsKey(itemId), unhoverStartTimes.containsKey(itemId));
                    }
                }
            }

            if (isHovered) {
                // Si on survole, retirer de la map "unhover" car on est de nouveau survolé
                if (unhoverStartTimes.containsKey(itemId)) {
                    if (shouldLog && DEBUG_ANIMATION) {
                        LOGGER.info("[UNHOVER CLEARED] Item: {} | Retiré de unhoverStartTimes (on survole à nouveau)", itemId);
                    }
                    unhoverStartTimes.remove(itemId);
                }

                // PRIORITÉ ABSOLUE : vérifier le cache en premier - si existe, utiliser directement
                Float cachedScale = cachedHoverScale.get(itemId);
                Float cachedAlpha = cachedGlowAlpha.get(itemId);

                if (cachedScale != null && cachedAlpha != null) {
                    // Cache existe - utiliser directement, AUCUN recalcul, AUCUNE vérification
                    hoverScale = cachedScale;
                    glowAlpha = cachedAlpha;
                    // IMPORTANT : ne PAS toucher à hoverStartTimes si le cache existe
                    if (shouldLog && hoverStateChanged && DEBUG_ANIMATION) {
                        LOGGER.info("[CACHE USED] Item: {} | Utilisation du cache: scale={}, alpha={}", itemId, hoverScale, glowAlpha);
                    }
                } else {
                    // Pas de cache, démarrer ou continuer l'animation
                    Long startTime = hoverStartTimes.get(itemId);
                    if (startTime == null) {
                        // Première fois qu'on survole cet item, démarrer l'animation
                        startTime = System.currentTimeMillis();
                        hoverStartTimes.put(itemId, startTime);
                        if (shouldLog && DEBUG_ANIMATION) {
                            LOGGER.info("[ANIMATION START] Item: {} | Démarrage de l'animation à {}", itemId, startTime);
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startTime;

                    // Si l'animation est terminée, mettre en cache les valeurs finales
                    if (elapsed >= HOVER_ANIMATION_DURATION) {
                        hoverScale = 1.1f;
                        glowAlpha = 0.3f;
                        // Mettre en cache UNE SEULE FOIS - ne jamais recalculer après
                        if (!cachedHoverScale.containsKey(itemId)) {
                            cachedHoverScale.put(itemId, hoverScale);
                            cachedGlowAlpha.put(itemId, glowAlpha);
                            if (shouldLog && DEBUG_ANIMATION) {
                                LOGGER.info("[ANIMATION COMPLETE] Item: {} | Animation terminée, mise en cache: scale={}, alpha={} | elapsed={}ms",
                                    itemId, hoverScale, glowAlpha, elapsed);
                            }
                        }
                        // Nettoyer le temps de départ (plus besoin)
                        hoverStartTimes.remove(itemId);
                    } else {
                        // Sinon, calculer avec l'easing
                        float progress = Math.min(1.0f, (float) elapsed / HOVER_ANIMATION_DURATION);
                        hoverScale = AnimationHelper.easeOut(1.0f, 1.1f, progress);
                        glowAlpha = AnimationHelper.easeOut(0.0f, 0.3f, progress);
                        if (shouldLog && hoverStateChanged && DEBUG_ANIMATION) {
                            LOGGER.info("[ANIMATION PROGRESS] Item: {} | Animation en cours: progress={}, scale={}, alpha={}, elapsed={}ms",
                                itemId, String.format("%.2f", progress), String.format("%.3f", hoverScale), String.format("%.3f", glowAlpha), elapsed);
                        }
                    }
                }
            } else {
                // Quand on ne survole plus, ne pas vider immédiatement le cache
                // Au lieu de ça, noter le moment où on a quitté le survol
                Long unhoverTime = unhoverStartTimes.get(itemId);
                if (unhoverTime == null) {
                    // Première fois qu'on quitte le survol, noter le temps
                    // Seulement si l'item avait un cache ou une animation en cours
                    if (cachedHoverScale.containsKey(itemId) || hoverStartTimes.containsKey(itemId)) {
                        unhoverTime = System.currentTimeMillis();
                        unhoverStartTimes.put(itemId, unhoverTime);
                        if (shouldLog && DEBUG_ANIMATION) {
                            LOGGER.info("[UNHOVER START] Item: {} | Début du délai avant vidage du cache à {}", itemId, unhoverTime);
                        }
                    }
                } else {
                    // Vérifier si on a quitté le survol depuis assez longtemps
                    long timeSinceUnhover = System.currentTimeMillis() - unhoverTime;
                    if (timeSinceUnhover >= UNHOVER_DELAY) {
                        // Assez de temps s'est écoulé, vider le cache
                        boolean hadCache = cachedHoverScale.containsKey(itemId) || cachedGlowAlpha.containsKey(itemId);
                        hoverStartTimes.remove(itemId);
                        cachedHoverScale.remove(itemId);
                        cachedGlowAlpha.remove(itemId);
                        unhoverStartTimes.remove(itemId);
                        if (shouldLog && hadCache && DEBUG_ANIMATION) {
                            LOGGER.info("[CACHE CLEARED] Item: {} | Cache vidé après {}ms (délai: {}ms)",
                                itemId, timeSinceUnhover, UNHOVER_DELAY);
                        }
                    } else {
                        // Pas encore assez de temps, utiliser le cache si disponible
                        Float cachedScale = cachedHoverScale.get(itemId);
                        Float cachedAlpha = cachedGlowAlpha.get(itemId);
                        if (cachedScale != null && cachedAlpha != null) {
                            // Utiliser le cache pendant le délai (pour une transition douce)
                            hoverScale = cachedScale;
                            glowAlpha = cachedAlpha;
                            if (shouldLog && hoverStateChanged && DEBUG_ANIMATION) {
                                LOGGER.info("[CACHE KEPT] Item: {} | Cache conservé pendant le délai: scale={}, alpha={}, timeSinceUnhover={}ms",
                                    itemId, hoverScale, glowAlpha, timeSinceUnhover);
                            }
                        }
                    }
                }
            }

            // Appliquer la transformation de scale si nécessaire
            if (hoverScale != 1.0f) {
                context.pose().pushPose();
                float centerX = x + GuiConstants.SLOT_SIZE / 2.0f;
                float centerY = y + GuiConstants.SLOT_SIZE / 2.0f;
                context.pose().translate(centerX, centerY, 0);
                context.pose().scale(hoverScale, hoverScale, 1.0f);
                context.pose().translate(-centerX, -centerY, 0);
            }

            // Calcul de l'offset pour centrer l'item (supposé 16x16)
            int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
            context.renderItem(stack, x + offset, y + offset);

            // Overlay de glow progressif si survolé
            if (glowAlpha > 0.0f) {
                int glowColor = (int) (glowAlpha * 255) << 24 | 0xFFFFFF;
                context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, glowColor);
            }

            if (hoverScale != 1.0f) {
                context.pose().popPose();
            }

            // Icône étoile pour les favoris (rendue APRÈS l'item pour être au-dessus)
            // itemId vient d'etre calcule pour cette case. Passer par
            // isFavorite(stack) refaisait getUniqueItemId() : une recherche de
            // registre et une lecture de composants de plus, par case et par
            // image, pour retrouver une valeur deja en main.
            boolean isFavorite = favoriteManager.isFavorite(itemId);
            if (isFavorite) {
                String starIcon = "★";
                int starX = x + GuiConstants.SLOT_SIZE - 10;
                int starY = y + 2;
                int starColor = 0xFFFFD700;

            // Animation pulse si toggle récent
            if (favoriteToggleTimes.containsKey(itemId)) {
                long toggleTime = favoriteToggleTimes.get(itemId);
                long elapsed = System.currentTimeMillis() - toggleTime;
                if (elapsed < 300) {
                    float progress = (float) elapsed / 300.0f;
                    float scale = 1.0f + 0.3f * (float) Math.sin(progress * Math.PI);
                    int starSize = (int) (8 * scale);
                    starX = x + GuiConstants.SLOT_SIZE - starSize - 2;
                    starY = y + 2;
                    starColor = 0xFFFFD700;
                } else {
                    favoriteToggleTimes.remove(itemId);
                }
            }

                // Rendre l'étoile avec un z-index élevé
                context.pose().pushPose();
                context.pose().translate(0, 0, 200); // Z-index élevé pour être au-dessus de l'item
                context.drawString(textRenderer, Component.literal(starIcon).withStyle(ChatFormatting.BOLD),
                                starX, starY, starColor, false);
                context.pose().popPose();
            }

            // Marquer comme survolé pour la popup de recette
            if (isHovered) {
                hoveredStack = stack;
            }
        }

        // Afficher une barre de scroll si nécessaire
        context.disableScissor();

        if (maxScroll > 0) {
            renderScrollBar(context, ceiHeight, itemsListStartY, maxScroll, ceiWidth, ceiX, ceiY, animationSlideOffset);
        }

        // Nettoyer périodiquement les animations terminées (pour éviter l'accumulation de mémoire)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupFinishedAnimations();
            lastCleanupTime = currentTime;
        }

        return hoveredStack;
    }

    /**
     * Nettoie les animations terminées depuis plus de 1 seconde.
     */
    private void cleanupFinishedAnimations() {
        long currentTime = System.currentTimeMillis();

        // Nettoyer les animations de favoris terminées
        favoriteToggleTimes.entrySet().removeIf(entry ->
            currentTime - entry.getValue() > 300); // 300ms = durée de l'animation

        // Les animations de survol sont déjà nettoyées quand on ne survole plus
        // On nettoie juste les caches qui pourraient rester (ne devrait pas arriver)
        // Mais on garde cette méthode pour la sécurité
    }

    /**
     * Gère le scroll de la liste d'items.
     *
     * @param mouseX Position X de la souris
     * @param mouseY Position Y de la souris
     * @param verticalAmount Quantité de scroll (positif = vers le bas, négatif = vers le haut)
     * @param allItems Liste de tous les items
     * @param ceiHeight Hauteur disponible pour le panneau CEI
     * @param itemsListStartY Position Y où commence la liste d'items
     * @return true si le scroll a été géré, false sinon
     */
    public boolean handleScroll(double mouseX, double mouseY, double verticalAmount,
                                List<ItemStack> allItems, int ceiHeight, int itemsListStartY, int ceiWidth, int ceiX, int ceiY, float animationSlideOffset) {

        // Appliquer l'offset d'animation pour la détection de la souris
        int animatedCeiX = (int) (ceiX + animationSlideOffset);
        // Vérifier si la souris est sur le panneau CEI (mais pas sur la barre de recherche)
        if (mouseX >= animatedCeiX && mouseX < animatedCeiX + ceiWidth &&
            mouseY >= itemsListStartY && mouseY < ceiY + ceiHeight) {

            int columns = (ceiWidth - 2 * GuiConstants.PADDING) / GuiConstants.SLOT_SIZE;
            int availableHeight = ceiHeight - (itemsListStartY - ceiY);
            int maxVisibleRows = availableHeight / GuiConstants.SLOT_SIZE;
            int totalRows = (allItems.size() + columns - 1) / columns; // Arrondi vers le haut
            int maxScroll = Math.max(0, totalRows - maxVisibleRows);

            // Ajuster le scroll
            // Trois lignes par cran, comme avant, mais sur la CIBLE : c'est
            // l'amortissement qui fait le trajet.
            scrollTarget = Math.max(0f, Math.min(maxScroll * GuiConstants.SLOT_SIZE,
                    scrollTarget - (float) verticalAmount * 3 * GuiConstants.SLOT_SIZE));
            return true;
        }
        return false;
    }

    /**
     * Vérifie si un item est survolé.
     */
    public boolean isAnyItemHovered(int mouseX, int mouseY, List<ItemStack> allItems, int ceiHeight, int itemsListStartY, int ceiWidth, int ceiX, int ceiY, float animationSlideOffset) {
        int columns = (ceiWidth - 2 * GuiConstants.PADDING) / GuiConstants.SLOT_SIZE;

        // Appliquer l'offset d'animation
        int animatedCeiX = (int) (ceiX + animationSlideOffset);

        // Calculer la hauteur disponible : depuis itemsListStartY jusqu'à la fin du panneau CEI
        int ceiBottom = ceiY + ceiHeight;
        int availableHeight = ceiBottom - itemsListStartY;

        // S'assurer que la hauteur disponible est positive
        if (availableHeight < 0) {
            availableHeight = 0;
        }

        int maxVisibleRows = availableHeight / GuiConstants.SLOT_SIZE;
        int maxVisibleItems = columns * (maxVisibleRows + 2);
        int shift = getScrollShift();
        int startIndex = getScrollRow() * columns;

        for (int i = startIndex; i < Math.min(allItems.size(), startIndex + maxVisibleItems); i++) {
            int relativeIndex = i - startIndex;
            int x = animatedCeiX + GuiConstants.PADDING + (relativeIndex % columns) * GuiConstants.SLOT_SIZE;
            int y = itemsListStartY - shift + (relativeIndex / columns) * GuiConstants.SLOT_SIZE;
            if (GuiRenderHelper.isMouseOver(mouseX, mouseY, x, y, GuiConstants.SLOT_SIZE, GuiConstants.SLOT_SIZE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Réinitialise le scroll (utile quand la liste change, par exemple après un filtre).
     */
    public void resetScroll() {
        scrollTarget = 0f;
        scrollShown = 0f;
        draggingBar = false;
    }

    /**
     * Déclenche l'animation de toggle de favori pour un item.
     * @param stack L'item qui a été togglé
     */
    public void triggerFavoriteToggleAnimation(ItemStack stack) {
        ResourceLocation itemId = FavoriteItemsManager.getUniqueItemId(stack);
        favoriteToggleTimes.put(itemId, System.currentTimeMillis());
    }

    /**
     * Retourne l'offset de scroll actuel.
     */
    public int getScrollOffset() {
        // Conserve pour les appelants existants : la premiere ligne
        // dessinee. Le decalage sous-ligne se lit par getScrollShift().
        return getScrollRow();
    }

    /**
     * Affiche une barre de scroll sur le côté droit du panneau.
     */
    /**
     * Barre de defilement.
     *
     * Sa geometrie est MEMORISEE plutot que recalculee au moment du clic : le
     * clic n'a pas les memes entrees que le rendu, et deux calculs paralleles
     * finissent toujours par diverger d'un pixel -- juste assez pour qu'on
     * attrape le curseur a cote.
     */
    private void renderScrollBar(GuiGraphics context, int ceiHeight, int itemsListStartY, int maxScroll, int ceiWidth, int ceiX, int ceiY, float animationSlideOffset) {
        int animatedCeiX = (int) (ceiX + animationSlideOffset);
        barW = 4;
        barX = animatedCeiX + ceiWidth - barW - 2;
        barY = itemsListStartY;
        barH = Math.max(0, (ceiY + ceiHeight) - itemsListStartY);
        barVisible = barH > 0 && maxScroll > 0;
        if (!barVisible) return;

        int maxScrollPx = maxScroll * GuiConstants.SLOT_SIZE;
        int contentPx = barH + maxScrollPx;

        // Le curseur occupe la meme part de la barre que la fenetre visible
        // occupe du contenu : sa longueur dit d'un coup d'oeil combien il
        // reste a parcourir. L'ancien calcul melangeait des lignes et des
        // pixels et donnait une taille sans rapport.
        thumbH = Math.max(12, (int) ((long) barH * barH / Math.max(1, contentPx)));
        if (thumbH > barH) thumbH = barH;
        float ratio = maxScrollPx > 0 ? scrollShown / maxScrollPx : 0f;
        thumbY = barY + Math.round(ratio * (barH - thumbH));

        context.fill(barX, barY, barX + barW, barY + barH, 0x66000000);
        context.fill(barX, thumbY, barX + barW, thumbY + thumbH,
                draggingBar ? 0xFFFFFFFF : 0xCCFFFFFF);
    }
}



