# G4-DEV — laboratoire d'integration JEI

Copie de **G4** (MC 1.21.1, NeoForge 21.1, mappings **officialMojangMappings**).
**Ce module n'est pas destine a etre publie.** Il sert a mesurer ce que coute
reellement l'hebergement de plugins JEI dans CEI, et ou ca casse.

Le labo etait initialement en G1 (1.20.1). Il a ete deplace en 1.21.1 parce
que c'est la seule version pour laquelle du materiel de test reel existe sur
la machine : JEI 19.27.0.340 et huit addons, dans un pack de 398 mods.
L'ancien G1-DEV a ete ecarte dans `_to_delete/`.

## Regle du labo

**Le core de CEI ne change pas.** Tout ce qui concerne JEI vit dans des
fichiers NOUVEAUX, sous `com.ceketrum.cei.jei`. Aucun fichier CEI existant
(`CeiSlot`, `CeiRecipeView`, `CeiRecipeIndex`, `CeiModule`, `CEINeoForge`,
les renderers...) n'est modifie.

Le pont s'accroche par `@EventBusSubscriber`, resolu par scan d'annotations :
il n'a donc pas besoin d'etre appele depuis `CEINeoForge`. C'est ce qui rend
la regle tenable.

Consequence assumee, et c'est precisement ce qu'on veut mesurer : `CeiSlot`
restant une liste d'`ItemStack`, tout ingredient JEI qui n'est pas un item
(fluides, gaz, energie) ne pourra pas entrer dans le modele.

Toute entorse doit apparaitre dans un rapport, avec sa raison.

### Entorses actees

- **Internes de JEI** : on s'autorise a fournir les internes triviaux et
  stables reclames par certains addons (`mezz.jei.common.platform.Services`,
  `IPlatformHelper`, `IPlatformFluidHelperInternal`,
  `mezz.jei.common.transfer.RecipeTransferErrorInternal`). Decision prise le
  2026-08-02 ; a comptabiliser comme dette. Les internes non triviaux
  (`mezz.jei.gui.recipes.*`, cibles de Mixin) restent hors perimetre.

## Ce qui a ete touche hors "fichiers nouveaux"

Uniquement de la plomberie de build et des metadonnees, jamais du code CEI :

- `settings.gradle` : build **autonome** (`rootProject.name = 'cei-G4DEV'`),
  volontairement absent du `settings.gradle` de la racine.
- `build.gradle` : version du plugin Loom declaree localement, version `-DEV`.
- `neoforge/build.gradle` : `archivesName` en `G4DEV`, dependance `compileOnly`
  vers `libs/jei-api-19.27.0.340.jar` et embarquement verbatim de cette API
  dans le jar final.
- `neoforge/src/main/resources/META-INF/neoforge.mods.toml` : second bloc
  `[[mods]]` declarant l'identifiant `jei` en 19.99.0.999.

## libs/

`jei-api-19.27.0.340.jar` : le paquet `mezz/jei/api/**` extrait **tel quel**
de `jei-1.21.1-neoforge-19.27.0.340.jar` (182 classes, licence MIT, avis
conserve dans le jar). Aucune reecriture : c'est ce qui garantit la
compatibilite binaire avec les plugins deja compiles.

## Construction

    gradlew.bat -p G4-DEV :neoforge:build

depuis la racine du depot. Jar produit dans `G4-DEV/neoforge/build/libs/`.

## Instance de test

Pack *All the Mons* (1.21.1 / NeoForge 21.1.229, 398 mods).
**Retirer `jei-1.21.1-neoforge-19.27.0.340.jar`** : meme identifiant de mod,
les deux ne peuvent pas coexister.

## labs/reports/

Un fichier par manip, date, jamais ecrase : le labo garde la trace des echecs
autant que des succes.
