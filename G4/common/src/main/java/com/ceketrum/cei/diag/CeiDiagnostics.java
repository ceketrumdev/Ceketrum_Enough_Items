package com.ceketrum.cei.diag;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation du mod, desactivee par defaut (option "diagnostics").
 *
 * Raison d'etre : optimiser contre des chiffres et non contre des intuitions.
 * On soupconne trois coupables -- la liste d'items reconstruite a chaque ecran,
 * le filtre qui refait le travail a chaque touche, le scan reflexif des loot
 * tables -- mais on ne sait pas lequel domine sur un vrai pack. Ces sondes le
 * disent.
 *
 * Cout quand c'est eteint : une lecture de champ statique par site d'appel.
 * begin() renvoie 0 et toutes les autres methodes sortent immediatement ; sur
 * les chemins par frame, System.nanoTime() n'est donc jamais invoque.
 *
 * Ce fichier est un outil de chantier. Il a vocation a disparaitre une fois la
 * 0.1.5 calibree, et ne doit rien changer au comportement du mod.
 */
public final class CeiDiagnostics {

    private CeiDiagnostics() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("cei-diag");

    /** Positionne une fois au chargement de la configuration. */
    public static volatile boolean ENABLED = false;

    /** Intervalle de synthese pour les mesures par frame. */
    private static final long REPORT_INTERVAL_MS = 5000L;

    // ---------------------------------------------------- mesures ponctuelles

    public static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void end(String label, long t0) {
        end(label, t0, -1, null);
    }

    /** @param count grandeur mesuree (items, recettes...), -1 si sans objet. */
    public static void end(String label, long t0, long count, String unit) {
        if (!ENABLED || t0 == 0L) return;
        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        if (count >= 0) {
            LOGGER.info("{} : {} ms  ({} {})", label, ms(ms), count, unit == null ? "" : unit);
        } else {
            LOGGER.info("{} : {} ms", label, ms(ms));
        }
    }

    // ---------------------------------------------------------------- memoire

    /**
     * Occupation du tas en octets.
     *
     * Volontairement SANS System.gc() : declencher une collecte en pleine partie
     * fausserait la mesure plus surement que le bruit qu'on chercherait a
     * eliminer. Un ecart avant/apres est donc une INDICATION d'ordre de
     * grandeur, jamais une taille retenue. Les compteurs d'elements, eux, sont
     * exacts -- ce sont eux qu'il faut croire.
     */
    public static long heap() {
        if (!ENABLED) return 0L;
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    public static void heapDelta(String label, long before) {
        if (!ENABLED || before == 0L) return;
        LOGGER.info("{} : {} sur le tas (approx., sans GC)", label, bytes(heap() - before));
    }

    // ----------------------------------------------------- mesures par frame

    private static final class Acc {
        long count;
        long totalNanos;
        long maxNanos;
        long lastReport;
    }

    private static final Map<String, Acc> FRAMES = new ConcurrentHashMap<>();

    /** Agrege une duree par frame et publie une synthese toutes les 5 secondes. */
    public static void frame(String label, long t0) {
        if (!ENABLED || t0 == 0L) return;
        long dt = System.nanoTime() - t0;
        Acc a = FRAMES.computeIfAbsent(label, k -> new Acc());
        synchronized (a) {
            a.count++;
            a.totalNanos += dt;
            if (dt > a.maxNanos) a.maxNanos = dt;

            long now = System.currentTimeMillis();
            if (a.lastReport == 0L) {
                a.lastReport = now;
                return;
            }
            if (now - a.lastReport < REPORT_INTERVAL_MS) return;

            LOGGER.info("{} : {} frames, moyenne {} ms, pic {} ms",
                    label, a.count,
                    ms(a.totalNanos / 1_000_000.0 / a.count),
                    ms(a.maxNanos / 1_000_000.0));
            a.count = 0;
            a.totalNanos = 0;
            a.maxNanos = 0;
            a.lastReport = now;
        }
    }

    // -------------------------------------------------------------- compteurs

    private static final Map<String, long[]> COUNTERS = new ConcurrentHashMap<>();

    /**
     * Compteur cumulatif journalise aux puissances de deux : 1, 2, 4, 8, 16...
     *
     * Assez pour voir qu'une chose arrive bien plus souvent que prevu, sans
     * inonder le log -- exactement ce qu'il faut pour compter les CeiModule
     * crees, dont on soupconne qu'il y en a un par ecran ouvert.
     */
    public static void tick(String label) {
        if (!ENABLED) return;
        long[] c = COUNTERS.computeIfAbsent(label, k -> new long[1]);
        long n;
        synchronized (c) {
            n = ++c[0];
        }
        if ((n & (n - 1)) == 0) {
            LOGGER.info("{} : {} fois depuis le lancement", label, n);
        }
    }

    // ----------------------------------------------------------------- format

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String bytes(long b) {
        long a = Math.abs(b);
        if (a < 1024L) return b + " o";
        if (a < 1024L * 1024L) return String.format(Locale.ROOT, "%+.1f Kio", b / 1024.0);
        return String.format(Locale.ROOT, "%+.1f Mio", b / (1024.0 * 1024.0));
    }
}
