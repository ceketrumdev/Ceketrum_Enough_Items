package com.ceketrum.cei.jei;

import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Porteur de l'identifiant de mod "jei".
 *
 * Le bloc [[mods]] de neoforge.mods.toml ne suffit pas : javafml exige une
 * classe annotee @Mod pour chaque identifiant declare. Cette classe ne fait
 * rien d'autre qu'exister -- toute la logique vit dans CeiJeiBridge.
 *
 * Elle est sans effet cote serveur dedie : le pont ne charge aucun plugin
 * hors du client. Sa seule raison d'etre la-bas est de satisfaire les
 * dependances declarees side="BOTH" par les addons.
 */
@Mod("jei")
public final class CeiJeiIdentity {

    private static final Logger LOGGER = LoggerFactory.getLogger("cei-jei");

    public CeiJeiIdentity() {
        LOGGER.info("[CEI-JEI] Identifiant 'jei' fourni par CEI (LABO G4-DEV). Le vrai JEI ne doit PAS etre installe.");
    }
}
