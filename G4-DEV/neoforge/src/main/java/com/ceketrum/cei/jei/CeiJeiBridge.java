package com.ceketrum.cei.jei;

import java.lang.annotation.ElementType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import mezz.jei.api.IModPlugin;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manip 02 -- decouverte des plugins JEI presents dans le pack.
 *
 * PERIMETRE VOLONTAIREMENT MINIMAL. On ne construit rien, on n'affiche rien :
 * on se contente de trouver les classes annotees @JeiPlugin, de les
 * instancier, et de demander leur identifiant. C'est le plus petit pas qui
 * puisse echouer de facon instructive, et il valide d'un coup les quatre
 * choses les plus risquees du projet :
 *
 *   1. le chargeur accepte que CEI fournisse l'identifiant de mod "jei" ;
 *   2. les addons se chargent au lieu d'etre rejetes ;
 *   3. l'API embarquee verbatim se lie bien au bytecode des plugins, deja
 *      compile contre le vrai JEI -- si les signatures avaient bouge, c'est
 *      ici qu'on verrait NoSuchMethodError / NoClassDefFoundError ;
 *   4. le scan d'annotations de FML voit bien @JeiPlugin.
 *
 * Chaque plugin est isole : un plugin qui explose est journalise, pas fatal.
 * Le but du labo est de collecter les echecs, pas de les eviter.
 */
public final class CeiJeiBridge {

    private CeiJeiBridge() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("cei-jei");

    /** Nom binaire de l'annotation, compare en chaine : pas besoin d'ASM ici. */
    private static final String JEI_PLUGIN_ANNOTATION = "mezz.jei.api.JeiPlugin";

    private static boolean done = false;

    /** Une ligne de resultat par classe annotee. */
    private record Result(String className, String uid, String failure, long micros) {}

    public static synchronized void discoverOnce() {
        if (done) return;
        done = true;

        long start = System.nanoTime();
        Set<String> classNames = scanForPluginClasses();
        long scanMicros = (System.nanoTime() - start) / 1000L;

        LOGGER.info("[CEI-JEI] {} classe(s) @JeiPlugin trouvee(s) en {} us.", classNames.size(), scanMicros);

        List<Result> results = new ArrayList<>();
        int ok = 0;
        for (String className : classNames) {
            Result r = tryLoad(className);
            results.add(r);
            if (r.failure() == null) {
                ok++;
            } else {
                // Nominatif : sans le nom du plugin, un rapport d'echec est inutilisable.
                LOGGER.warn("[CEI-JEI] {} : {}", className, r.failure());
            }
        }

        long totalMicros = (System.nanoTime() - start) / 1000L;
        LOGGER.info("[CEI-JEI] {} plugin(s) instancie(s) sur {} en {} us.", ok, results.size(), totalMicros);

        writeReport(results, scanMicros, totalMicros);
    }

    /**
     * Parcourt les donnees de scan de FML. Elles portent les annotations
     * relevees a la lecture des jars, donc aucune classe n'est chargee ici --
     * c'est important : on veut mesurer separement le cout du chargement.
     */
    private static Set<String> scanForPluginClasses() {
        Set<String> out = new LinkedHashSet<>();
        try {
            for (ModFileScanData data : ModList.get().getAllScanData()) {
                for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                    if (annotation.targetType() != ElementType.TYPE) continue;
                    if (!JEI_PLUGIN_ANNOTATION.equals(annotation.annotationType().getClassName())) continue;
                    out.add(annotation.clazz().getClassName());
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[CEI-JEI] Scan d'annotations impossible : {}", t.toString());
        }
        return out;
    }

    /**
     * Charge, instancie, interroge. Chaque etape peut echouer differemment et
     * on veut savoir laquelle : c'est la difference entre "l'addon ne marche
     * pas" et "l'API que nous fournissons est incomplete".
     */
    private static Result tryLoad(String className) {
        long t0 = System.nanoTime();
        try {
            Class<?> type = Class.forName(className, false, CeiJeiBridge.class.getClassLoader());

            if (!IModPlugin.class.isAssignableFrom(type)) {
                return new Result(className, null, "annotee @JeiPlugin mais n'implemente pas IModPlugin", micros(t0));
            }

            // L'initialisation statique se declenche ici : c'est le premier
            // endroit ou une reference a un interne de JEI se voit.
            Object instance = type.getDeclaredConstructor().newInstance();
            IModPlugin plugin = (IModPlugin) instance;

            String uid = String.valueOf(plugin.getPluginUid());
            return new Result(className, uid, null, micros(t0));

        } catch (Throwable t) {
            // NoClassDefFoundError attendu pour les plugins qui touchent aux
            // internes (mezz.jei.common / library / gui) : on le veut nomme.
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            return new Result(className, null,
                    cause.getClass().getSimpleName() + " : " + String.valueOf(cause.getMessage()), micros(t0));
        }
    }

    private static long micros(long t0) {
        return (System.nanoTime() - t0) / 1000L;
    }

    private static void writeReport(List<Result> results, long scanMicros, long totalMicros) {
        StringBuilder sb = new StringBuilder();
        sb.append("CEI / labo G4-DEV -- manip 02 : decouverte des plugins JEI\n");
        sb.append("==========================================================\n\n");
        sb.append("scan d'annotations : ").append(scanMicros).append(" us\n");
        sb.append("total              : ").append(totalMicros).append(" us\n");
        sb.append("classes trouvees   : ").append(results.size()).append("\n\n");

        sb.append("-- INSTANCIES ------------------------------------------------\n");
        for (Result r : results) {
            if (r.failure() != null) continue;
            sb.append(String.format("%-8s %-70s %s%n", r.micros() + "us", r.className(), r.uid()));
        }
        sb.append("\n-- ECHECS ----------------------------------------------------\n");
        for (Result r : results) {
            if (r.failure() == null) continue;
            sb.append(String.format("%-8s %-70s %s%n", r.micros() + "us", r.className(), r.failure()));
        }

        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("cei-jei");
            Files.createDirectories(dir);
            Path file = dir.resolve("manip02-decouverte.txt");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            LOGGER.info("[CEI-JEI] Rapport ecrit dans {}", file);
        } catch (Throwable t) {
            LOGGER.error("[CEI-JEI] Ecriture du rapport impossible : {}", t.toString());
            LOGGER.info("\n{}", sb);
        }
    }
}
