package com.ceketrum.cei.i18n;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Language;

/**
 * Acces au systeme de traduction de Minecraft.
 *
 * Raison d'etre : c'est le seul endroit du mod qui nomme l'API de traduction.
 * Elle change de nom selon le jeu de mappings -- I18n.translate en Yarn --
 * et le mod couvre sept versions. Concentrer l'appel ici, c'est une ligne a
 * adapter par version au lieu d'une par site d'appel.
 *
 * Avant, le texte etait choisi dans le code par des "isFrench ? ... : ...".
 * Deux consequences qu'on paie encore : les langues autres que le francais et
 * l'anglais etaient impossibles, et il suffisait d'oublier le ternaire pour
 * qu'une chaine reste en francais pour tout le monde -- ce qui etait le cas
 * des astuces, de l'en-tete du panneau et des noms de machines moddees.
 *
 * Ici, on ne peut plus afficher de texte sans passer par une cle.
 */
public final class CeiText {

    private CeiText() {}

    /** Texte traduit dans la langue du jeu. Repli automatique sur l'anglais. */
    public static String t(String key) {
        return I18n.translate(key);
    }

    /** Idem, avec des parametres (%s, %d) resolus par le fichier de langue. */
    public static String t(String key, Object... args) {
        return I18n.translate(key, args);
    }

    /** La cle existe-t-elle ? Utile quand le texte depend d'un mod tiers. */
    public static boolean has(String key) {
        return Language.getInstance().hasTranslation(key);
    }

    /**
     * Traduction si la cle existe, valeur de repli sinon.
     *
     * Sans cela, une cle absente s'affiche telle quelle a l'ecran
     * ("cei.action.crushing"), ce qui est pire que le nom technique.
     */
    public static String or(String key, String fallback) {
        return has(key) ? t(key) : fallback;
    }
}
