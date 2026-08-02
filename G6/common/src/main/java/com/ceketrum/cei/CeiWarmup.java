package com.ceketrum.cei;

import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.LootTableSourceManager;
import com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prechauffage progressif des index, apres l'entree en jeu.
 *
 * Le probleme : meme construit une seule fois, l'index de recettes coute
 * ~240 ms -- et sans prechauffage c'est le joueur qui les paie, au moment
 * precis ou il ouvre sa premiere fiche. Un gel d'un quart de seconde sur un
 * clic est exactement ce qu'on reproche a JEI/REI/EMI.
 *
 * La solution retenue n'est PAS un fil d'execution separe. Les registres, les
 * recettes et les loot tables ne sont pas concus pour etre lus depuis un autre
 * fil pendant que la partie tourne, et une exploration reflexive de graphe
 * d'objets moddes est le pire endroit ou en faire le pari. On garde donc tout
 * sur le fil de rendu, mais on le decoupe : 2 ms par frame, ce qui tient
 * largement dans les 16,6 ms d'une frame a 60 images/s.
 *
 * Si le joueur est plus rapide que le prechauffage, rien n'est casse :
 * ensureBuilt() termine simplement le travail restant sur-le-champ.
 */
public final class CeiWarmup {

    private CeiWarmup() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("cei-warmup");

    /** Part d'une frame qu'on s'autorise. 2 ms sur 16,6 ms restent invisibles. */
    private static final long BUDGET_NANOS = 2_000_000L;

    /**
     * On laisse le monde se poser avant de commencer : juste apres l'entree en
     * jeu, le client charge encore les chunks et c'est lui qui a besoin du CPU.
     */
    private static final int DELAY_FRAMES = 60;

    private static int frames = 0;
    private static int buildFrames = 0;
    private static boolean recipesReady = false;
    private static boolean done = false;
    private static long startedAt = 0L;

    /** A appeler a chaque changement de monde. */
    public static synchronized void reset() {
        frames = 0;
        buildFrames = 0;
        recipesReady = false;
        done = false;
        startedAt = 0L;
    }

    /**
     * A appeler une fois par frame, depuis le HUD -- donc uniquement quand
     * aucun ecran n'est ouvert : c'est precisement la fenetre qu'on veut
     * exploiter.
     */
    public static void onClientFrame() {
        if (done) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;

        if (++frames < DELAY_FRAMES) return;

        if (!recipesReady) {
            if (startedAt == 0L) startedAt = System.currentTimeMillis();
            buildFrames++;
            try {
                recipesReady = CeiRecipeIndex.buildStep(client, BUDGET_NANOS);
            } catch (Exception | StackOverflowError | LinkageError e) {
                // Un echec de prechauffage ne doit jamais empecher de jouer :
                // le chemin paresseux reste disponible a l'ouverture d'une fiche.
                LOGGER.warn("[WARMUP] Prechauffage de l'index abandonne : {}", e.toString());
                recipesReady = true;
            }
            if (recipesReady) {
                LOGGER.info("[WARMUP] Index de recettes pret en {} ms ({} frames, {} items indexes).",
                        System.currentTimeMillis() - startedAt, buildFrames,
                        CeiRecipeIndex.indexedItems());
            }
            return;
        }

        // Le reste est court et ne se decoupe pas naturellement : une frame suffit.
        try {
            LootTableSourceManager.getInstance().ensureCacheBuilt();
        } catch (Exception | StackOverflowError | LinkageError e) {
            // deja journalise par le gestionnaire concerne
        }
        try {
            BrewingRecipeManager.getInstance().ensureCacheBuilt();
        } catch (Exception | StackOverflowError | LinkageError e) {
            // idem
        }
        done = true;
    }
}
