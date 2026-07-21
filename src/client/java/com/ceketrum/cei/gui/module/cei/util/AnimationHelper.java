package com.ceketrum.cei.gui.module.cei.util;

/**
 * Classe utilitaire pour gérer les animations et les interpolations.
 */
public class AnimationHelper {
    
    /**
     * Calcule la progression d'une animation basée sur le temps.
     * @param startTime Le temps de début de l'animation (en millisecondes)
     * @param duration La durée totale de l'animation (en millisecondes)
     * @return Une valeur entre 0.0 (début) et 1.0 (fin)
     */
    public static float getAnimationProgress(long startTime, int duration) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed <= 0) {
            return 0.0f;
        }
        if (elapsed >= duration) {
            return 1.0f;
        }
        return (float) elapsed / duration;
    }
    
    /**
     * Interpolation linéaire entre deux valeurs.
     * @param start Valeur de départ
     * @param end Valeur de fin
     * @param progress Progression de l'animation (0.0 à 1.0)
     * @return La valeur interpolée
     */
    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
    
    /**
     * Interpolation ease-in-out (démarrage et fin lents, milieu rapide).
     * @param start Valeur de départ
     * @param end Valeur de fin
     * @param progress Progression de l'animation (0.0 à 1.0)
     * @return La valeur interpolée avec easing
     */
    public static float easeInOut(float start, float end, float progress) {
        // Fonction ease-in-out cubique
        float eased = progress < 0.5f 
            ? 4 * progress * progress * progress 
            : 1 - (float) Math.pow(-2 * progress + 2, 3) / 2;
        return lerp(start, end, eased);
    }
    
    /**
     * Interpolation ease-out (démarrage rapide, fin lente).
     * @param start Valeur de départ
     * @param end Valeur de fin
     * @param progress Progression de l'animation (0.0 à 1.0)
     * @return La valeur interpolée avec easing
     */
    public static float easeOut(float start, float end, float progress) {
        float eased = 1 - (float) Math.pow(1 - progress, 3);
        return lerp(start, end, eased);
    }
    
    /**
     * Interpolation ease-in (démarrage lent, fin rapide).
     * @param start Valeur de départ
     * @param end Valeur de fin
     * @param progress Progression de l'animation (0.0 à 1.0)
     * @return La valeur interpolée avec easing
     */
    public static float easeIn(float start, float end, float progress) {
        float eased = progress * progress * progress;
        return lerp(start, end, eased);
    }
    
    /**
     * Fonction pulse (utilisée pour les animations de toggle).
     * Retourne une valeur qui oscille entre 0.0 et 1.0.
     * @param progress Progression de l'animation (0.0 à 1.0)
     * @return Une valeur oscillante entre 0.0 et 1.0
     */
    public static float pulse(float progress) {
        return (float) (0.5 + 0.5 * Math.sin(progress * Math.PI));
    }
    
    /**
     * Vérifie si une animation est terminée.
     * @param startTime Le temps de début de l'animation
     * @param duration La durée totale de l'animation
     * @return true si l'animation est terminée, false sinon
     */
    public static boolean isAnimationFinished(long startTime, int duration) {
        return getAnimationProgress(startTime, duration) >= 1.0f;
    }
}


