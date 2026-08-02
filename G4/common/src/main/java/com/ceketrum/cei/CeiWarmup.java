package com.ceketrum.cei;

import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.LootTableSourceManager;
import com.ceketrum.cei.diag.CeiDiagnostics;
import com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex;
import com.ceketrum.cei.gui.screen.CeiItemInfoScreen;

import net.minecraft.client.Minecraft;

/**
 * Prechauffage progressif des index, apres l'entree en jeu.
 *
 * Le probleme qu'il resout : meme une fois l'index de recettes construit une
 * seule fois au lieu de l'etre a chaque ecran, quelqu'un devait toujours payer
 * les ~240 ms de construction -- et c'etait le joueur, au moment precis ou il
 * ouvrait sa premiere fiche. Un gel d'un quart de seconde sur un clic est
 * exactement ce qu'on reproche a JEI/REI/EMI.
 *
 * La solution retenue n'est PAS un fil d'execution separe. Les registres, les
 * recettes et les loot tables ne sont pas concus pour etre lus depuis un autre
 * fil pendant que la partie tourne, et une exploration reflexive de graphe
 * d'objets moddes est le pire endroit ou en faire le pari. On garde donc tout
 * sur le fil de rendu, mais on le decoupe : 2 ms par frame, ce qui tient
 * largement dans les 16,6 ms d'une frame a 60 images/s. L'index est pret en une
 * a deux secondes, sans qu'aucune frame ne soit sacrifiee.
 *
 * Si le joueur est plus rapide que le prechauffage, rien n'est casse :
 * ensureBuilt() termine simplement le travail restant sur-le-champ.
 */
public final class CeiWarmup {

    private CeiWarmup() {}

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
    private static long startedAt = -1L;

    /** A appeler a chaque changement de monde. */
    public static synchronized void reset() {
        frames = 0;
        buildFrames = 0;
        recipesReady = false;
        done = false;
        startedAt = -1L;
    }

    /**
     * A appeler une fois par frame, depuis le HUD (donc uniquement quand aucun
     * ecran n'est ouvert : c'est precisement la fenetre qu'on veut exploiter).
     */
    public static void onClientFrame() {
        if (done) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;

        if (++frames < DELAY_FRAMES) return;

        if (!recipesReady) {
            if (startedAt < 0L) {
                startedAt = CeiDiagnostics.begin();
            }
            buildFrames++;
            try {
                recipesReady = CeiRecipeIndex.buildStep(
                        client.level.getRecipeManager(),
                        client.level.registryAccess(),
                        CeiItemInfoScreen::extractCustomOutputs,
                        CeiItemInfoScreen::extractCustomInputs,
                        BUDGET_NANOS);
            } catch (Exception | StackOverflowError | LinkageError e) {
                // Un echec de prechauffage ne doit jamais empecher de jouer :
                // le chemin paresseux reste disponible a l'ouverture d'une fiche.
                recipesReady = true;
            }
            if (recipesReady) {
                CeiDiagnostics.end("Prechauffage de l'index de recettes", startedAt,
                        buildFrames, "frames");
            }
            return;
        }

        // Le reste est court -- 26 ms pour les loot tables, 4 ms pour les
        // potions -- et ne se decoupe pas naturellement : une frame suffit.
        long t0 = CeiDiagnostics.begin();
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
        CeiDiagnostics.end("Prechauffage des tables et potions", t0);
        done = true;
    }
}
