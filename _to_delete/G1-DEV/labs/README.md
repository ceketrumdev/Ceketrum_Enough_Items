# G1-DEV — laboratoire d'integration JEI

Copie de G1 (MC 1.20.1, Forge/NeoForge 47.x, mappings Yarn via Architectury).
**Ce module n'est pas destine a etre publie.** Il sert a mesurer ce que coute
reellement l'hebergement de plugins JEI dans CEI, et ou ca casse.

## Regle du labo

**Le core de CEI ne change pas.** Tout ce qui concerne JEI vit dans des
fichiers NOUVEAUX. Aucun fichier CEI existant (`CeiSlot`, `CeiRecipeView`,
`CeiRecipeIndex`, `CeiModule`, les renderers...) n'est modifie.

Consequence assumee, et c'est precisement ce qu'on veut mesurer : `CeiSlot`
restant une liste d'`ItemStack`, tout ingredient JEI qui n'est pas un item
(fluides, gaz Mekanism, energie) ne peut pas entrer dans le modele. Le rapport
listera ce qui est perdu et ce que couterait de lever la contrainte.

Toute entorse a cette regle doit apparaitre dans le rapport, avec sa raison.

## Ce qui a deja ete touche (hors "fichiers nouveaux")

Uniquement de la plomberie de build, jamais du code CEI :

- `settings.gradle` : build rendu **autonome** (`rootProject.name = 'cei-G1DEV'`).
  Le labo est volontairement absent du `settings.gradle` de la racine, pour
  qu'il ne puisse pas casser la chaine de production.
- `build.gradle` : version du plugin Loom declaree localement (elle venait de
  la racine du depot), version du mod suffixee `-DEV`.
- `fabric/build.gradle`, `neoforge/build.gradle` : `archivesName` en `G1DEV`,
  pour ne pas ecraser les jars de G1 dans `dist/`.

## Construction

    gradlew.bat -p G1-DEV build

depuis la racine du depot. Le wrapper de la racine convient (Java 21, Loom 1.14).
Jars produits dans `G1-DEV/fabric/build/libs/` et `G1-DEV/neoforge/build/libs/`.

## labs/jars/

Jars a analyser (JEI 15.20.x + addons). Servent a extraire les signatures
exactes de `mezz/jei/api/**` et a scanner ce que chaque addon reference
reellement, y compris les internes `mezz.jei.library` / `mezz.jei.common`
qui, eux, ne pourront jamais etre fournis.

## labs/reports/

Rapports de manip, dates. Un fichier par manip, pas d'ecrasement : le but du
labo est de garder la trace des echecs autant que des succes.
