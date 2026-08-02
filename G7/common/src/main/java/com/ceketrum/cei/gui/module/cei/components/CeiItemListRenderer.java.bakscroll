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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Gère le rendu de la liste d'items dans le panneau CEI avec scroll.
 */
public class CeiItemListRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-hover-animation");
    private static final boolean DEBUG_ANIMATION = false; // Activer/désactiver les logs de debug
    
    private int scrollOffset = 0;
    private final Map<Identifier, Long> favoriteToggleTimes = new HashMap<>();
    private final Map<Identifier, Long> hoverStartTimes = new HashMap<>();
    private final Map<Identifier, Float> cachedHoverScale = new HashMap<>();
    private final Map<Identifier, Float> cachedGlowAlpha = new HashMap<>();
    private final Map<Identifier, Long> unhoverStartTimes = new HashMap<>(); // Quand l'item n'est plus survolé
    private final Map<Identifier, Boolean> lastHoverState = new HashMap<>(); // État précédent du survol pour détecter les changements
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
    public ItemStack render(GuiGraphicsExtractor context, int mouseX, int mouseY, 
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
        int maxVisibleItems = columns * maxVisibleRows;
        
        // Calculer le nombre total de lignes nécessaires
        int totalRows = (allItems.size() + columns - 1) / columns; // Arrondi vers le haut
        int maxScroll = Math.max(0, totalRows - maxVisibleRows);
        
        // Limiter le scroll
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // Calculer l'index de départ basé sur le scroll
        int startIndex = scrollOffset * columns;
        
        ItemStack hoveredStack = null;
        
        // Appliquer l'offset d'animation aux positions
        int animatedCeiX = (int) (ceiX + animationSlideOffset);
        
        // Afficher seulement les items visibles (pour les performances)
        for (int i = startIndex; i < Math.min(allItems.size(), startIndex + maxVisibleItems); i++) {
            ItemStack stack = allItems.get(i);
            int relativeIndex = i - startIndex;
            int x = animatedCeiX + GuiConstants.PADDING + (relativeIndex % columns) * GuiConstants.SLOT_SIZE;
            int y = itemsListStartY + (relativeIndex / columns) * GuiConstants.SLOT_SIZE;
            
            // Fond du slot
            context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, 0xFF202020);
            
            // Utiliser l'Identifier unique incluant les Data Components pour différencier les variantes
            Identifier itemId = FavoriteItemsManager.getUniqueItemId(stack);
            
            // Animation au survol (zoom + glow)
            boolean isHovered = GuiRenderHelper.isMouseOver(mouseX, mouseY, x, y, GuiConstants.SLOT_SIZE, GuiConstants.SLOT_SIZE);
            float hoverScale = 1.0f;
            float glowAlpha = 0.0f;
            
            // Détecter les changements d'état du survol pour les logs (seulement si l'item est actuellement survolé ou l'était récemment)
            Boolean lastHovered = lastHoverState.get(itemId);
            boolean hoverStateChanged = (lastHovered == null) || (lastHovered != isHovered);
            
            // Ne logger que si l'item est actuellement survolé, a un cache, ou était récemment survolé
            boolean shouldLog = isHovered || cachedHoverScale.containsKey(itemId) || hoverStartTimes.containsKey(itemId) || unhoverStartTimes.containsKey(itemId);
            
            if (hoverStateChanged && shouldLog && DEBUG_ANIMATION) {
                lastHoverState.put(itemId, isHovered);
                LOGGER.info("[HOVER STATE] Item: {} | isHovered: {} -> {} | Cache: scale={}, alpha={} | hoverStartTimes: {} | unhoverStartTimes: {}", 
                    itemId, lastHovered, isHovered, 
                    cachedHoverScale.get(itemId), cachedGlowAlpha.get(itemId),
                    hoverStartTimes.containsKey(itemId), unhoverStartTimes.containsKey(itemId));
            } else if (hoverStateChanged) {
                lastHoverState.put(itemId, isHovered);
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
                context.pose().pushMatrix();
                float centerX = x + GuiConstants.SLOT_SIZE / 2.0f;
                float centerY = y + GuiConstants.SLOT_SIZE / 2.0f;
                context.pose().translate(centerX, centerY);
                context.pose().scale(hoverScale, hoverScale);
                context.pose().translate(-centerX, -centerY);
            }
            
            // Calcul de l'offset pour centrer l'item (supposé 16x16)
            int offset = (GuiConstants.SLOT_SIZE - 16) / 2;
            context.item(stack, x + offset, y + offset);
            
            // Overlay de glow progressif si survolé
            if (glowAlpha > 0.0f) {
                int glowColor = ((int) (glowAlpha * 255) << 24) | 0x00FFFFFF;
                context.fill(x, y, x + GuiConstants.SLOT_SIZE, y + GuiConstants.SLOT_SIZE, glowColor);
            }
            
            if (hoverScale != 1.0f) {
                context.pose().popMatrix();
            }
            
            // Icône étoile pour les favoris (rendue APRÈS l'item pour être au-dessus)
            boolean isFavorite = favoriteManager.isFavorite(stack);
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
                
                // Rendre l'étoile au-dessus de l'item
                context.text(textRenderer, Component.literal(starIcon).withStyle(ChatFormatting.BOLD), 
                                starX, starY, starColor, false);
            }
            
            // Marquer comme survolé pour la popup de recette
            if (isHovered) {
                hoveredStack = stack;
            }
        }
        
        // Afficher une barre de scroll si nécessaire
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
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 3));
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
        int maxVisibleItems = columns * maxVisibleRows;
        int startIndex = scrollOffset * columns;
        
        for (int i = startIndex; i < Math.min(allItems.size(), startIndex + maxVisibleItems); i++) {
            int relativeIndex = i - startIndex;
            int x = animatedCeiX + GuiConstants.PADDING + (relativeIndex % columns) * GuiConstants.SLOT_SIZE;
            int y = itemsListStartY + (relativeIndex / columns) * GuiConstants.SLOT_SIZE;
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
        scrollOffset = 0;
    }
    
    /**
     * Déclenche l'animation de toggle de favori pour un item.
     * @param stack L'item qui a été togglé
     */
    public void triggerFavoriteToggleAnimation(ItemStack stack) {
        Identifier itemId = FavoriteItemsManager.getUniqueItemId(stack);
        favoriteToggleTimes.put(itemId, System.currentTimeMillis());
    }
    
    /**
     * Retourne l'offset de scroll actuel.
     */
    public int getScrollOffset() {
        return scrollOffset;
    }
    
    /**
     * Affiche une barre de scroll sur le côté droit du panneau.
     */
    private void renderScrollBar(GuiGraphicsExtractor context, int ceiHeight, int itemsListStartY, int maxScroll, int ceiWidth, int ceiX, int ceiY, float animationSlideOffset) {
        int scrollBarWidth = 4;
        int animatedCeiX = (int) (ceiX + animationSlideOffset);
        int scrollBarX = animatedCeiX + ceiWidth - scrollBarWidth - 2;
        int scrollBarY = itemsListStartY;
        
        // Calculer la hauteur de la barre de scroll : depuis itemsListStartY jusqu'à la fin du panneau
        int ceiBottom = ceiY + ceiHeight;
        int scrollBarHeight = ceiBottom - itemsListStartY;
        
        // S'assurer que la hauteur est positive
        if (scrollBarHeight < 0) {
            scrollBarHeight = 0;
        }
        
        // Fond de la barre de scroll
        context.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0x66000000);
        
        // Calculer la position et la taille du curseur de scroll
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0.0f;
        int cursorHeight = Math.max(10, (int) (scrollBarHeight * ((float) (scrollBarHeight / GuiConstants.SLOT_SIZE) / (maxScroll + scrollBarHeight / GuiConstants.SLOT_SIZE))));
        int cursorY = scrollBarY + (int) (scrollRatio * (scrollBarHeight - cursorHeight));
        
        // Curseur de scroll
        context.fill(scrollBarX, cursorY, scrollBarX + scrollBarWidth, cursorY + cursorHeight, 0xFFFFFFFF);
    }
}


