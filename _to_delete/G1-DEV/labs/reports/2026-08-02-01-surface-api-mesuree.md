# Manip 01 — Surface d'API JEI réellement utilisée par les addons

**Date** : 2026-08-02 · **Matériel** : jars du pack *All the Mons* (MC 1.21.1 / NeoForge 21.1.229) et *Everything Create*
**Méthode** : extraction des classes, lecture du pool de constantes (`grep` sur les noms internes) puis désassemblage `javap -p -c` des 139 classes d'addons qui référencent JEI.
**Aucune compilation** — cette manip ne dépend d'aucun build.

> ⚠️ Ces mesures portent sur **JEI 19.27 (1.21.1)**, pas sur JEI 15.x (1.20.1) : aucun jar JEI 1.20.1 n'existe sur la machine. Voir « Limite » en fin de rapport.

---

## 1. Taille réelle de l'API

| Mesure | Valeur |
|---|---|
| Classes dans `jei-1.21.1-neoforge-19.27.0.340.jar` | **923** |
| dont `mezz/jei/api/**` | **182** (164 types de premier niveau) |
| dont internes (`library` 286, `gui` 197, `common` 174, `core` 37, `neoforge` 46) | **740** |
| Types d'API **référencés** par les 8 addons testés | **77 / 164 — 46 %** |
| Types jamais référencés | **87** |
| Membres distincts (méthodes + champs) réellement appelés | **170** |

**L'API n'est donc pas le mur qu'on croyait.** Pour couvrir ces huit addons il faut fournir 164 types (les signatures doivent exister) mais n'en faire vivre que 77, et implémenter **170 membres**. C'est un ordre de grandeur en dessous de ce que l'étude de faisabilité supposait par prudence.

## 2. Où se concentre le travail

Répartition des 77 types utilisés, par sous-paquet :

```
12  registration/        7  runtime/          6  gui/builder/
 5  recipe/              5  ingredients/      4  recipe/transfer/
 4  helpers/             4  gui/ingredient/   4  gui/drawable/
 3  gui/widgets/         3  gui/placement/    3  gui/handlers/
 2  recipe/vanilla/      2  recipe/category/  2  ingredients/subtypes/
```

Les 25 types les plus sollicités concentrent l'essentiel :

| Type | Membres appelés |
|---|---|
| `gui/builder/IRecipeSlotBuilder` | 15 |
| `helpers/IGuiHelper` | 11 |
| `gui/ingredient/IRecipeSlotView` | 10 |
| `gui/widgets/ITextWidget` | 7 |
| `recipe/RecipeType`, `recipe/RecipeIngredientRole` | 6 chacun |
| `runtime/IIngredientManager`, `registration/IRecipeRegistration`, `helpers/IJeiHelpers`, `gui/drawable/IDrawable`, `gui/builder/IRecipeLayoutBuilder` | 5 chacun |

**Conclusion opérationnelle** : le cœur du pont, c'est `IRecipeLayoutBuilder` + `IRecipeSlotBuilder` + `IGuiHelper` + `IDrawable`. Un plugin décrit sa recette en posant des slots dans un builder — et *ça*, ça se convertit vers `CeiSlot` sans toucher au core de CEI. C'est la bonne nouvelle de la manip.

## 3. Qui touche aux internes de JEI — les vrais bloqueurs

**6 addons sur 8 ne référencent QUE `mezz.jei.api`.** Ils sont, sur ce critère, hébergeables :

- `JustEnoughProfessions` (20 types) · `JustEnoughResources` (27) · `ae2jeiintegration` (65) · `ftb-jei-extras` (36) · `justenoughbreeding` (24) · `sophisticatedbackpacks` (23)

Les deux autres échouent, et pour deux raisons différentes :

### `JustEnoughMekanismMultiblocks` — échec par Mixin

```
giselle/jei_mekanism_multiblocks/client/mixin/jei/RecipesGuiMixin
giselle/jei_mekanism_multiblocks/client/mixin/jei/RecipeGuiLogicAccessor
→ mezz/jei/gui/recipes/{RecipesGui, RecipeGuiLogic, IRecipeGuiLogic, RecipeGuiLayouts}
→ mezz/jei/gui/recipes/lookups/ILookupState
```

Il ne se contente pas d'utiliser l'API : **il injecte dans l'écran de recettes de JEI**. Aucune couche de compat ne peut fournir ça — il faudrait réimplémenter l'écran interne de JEI à l'identique, classe par classe, pour que le Mixin s'applique. Mode d'échec attendu : erreur d'application du Mixin au démarrage, puis crash ou désactivation selon la configuration `required` du mod.

### `refinedstorage-jei-integration` — échec par référence directe

```
com/refinedmods/.../CraftingGridRecipeTransferHandler
com/refinedmods/.../JeiRecipeModIngredientConverter
→ mezz/jei/common/platform/{Services, IPlatformHelper, IPlatformFluidHelperInternal}
→ mezz/jei/common/transfer/RecipeTransferErrorInternal
```

Du code normal, pas un Mixin. Mode d'échec : `NoClassDefFoundError` au chargement de ces deux classes. **Contournement possible** ici, contrairement au précédent : ces quatre classes internes sont petites et stables ; on peut les fournir en compatibilité binaire. Ça viole la règle « API verbatim uniquement », et ça doit être une décision consciente — pas un réflexe. À noter comme dette.

## 4. Ce que ça change pour la conception

1. **La contrainte « core CEI intact » tient mieux que prévu.** Les plugins ne dessinent pas directement dans le cas courant : ils *décrivent* via `IRecipeLayoutBuilder`. On peut donc intercepter la description et la convertir en `CeiRecipeView`. Le plafond « ingrédients non-items » reste, mais il arrive plus tard qu'estimé.
2. **87 types d'API sur 164 peuvent être des coquilles** qui lèvent `UnsupportedOperationException`. À condition de les fournir quand même : leur seule absence suffit à casser la vérification de liens.
3. **Le taux d'échec dur mesuré est de 2 sur 8**, dont un seul irrécupérable. C'est le chiffre à garder : une couche de compat ne « supporte pas les plugins JEI », elle en supporte ~75 % et échoue proprement sur le reste — à condition d'échouer *proprement*, ce qui est un objectif de conception à part entière.
4. **`RecipeTypes` (constantes vanilla) est référencé 4 fois** : les addons s'accrochent aux catégories vanilla de JEI (`CRAFTING`, `SMELTING`…). Le pont doit donc exposer des `RecipeType` vanilla stables auxquels CEI sache faire correspondre ses propres `Kind`.

## 5. Limite de cette manip

Le module de labo est **G1-DEV = 1.20.1 / JEI 15.x**, or tout ce qui précède est mesuré sur **JEI 19.27 / 1.21.1** : aucun jar JEI 1.20.1 n'est présent sur la machine, et aucun des dix mods du log d'origine non plus.

Ce qui transfère quand même vers 1.20.1 : la structure de l'API, la proportion utilisée, le fait que le pivot soit `IRecipeLayoutBuilder`, et les deux modes d'échec (Mixin sur l'écran JEI, référence directe aux internes). Ce qui ne transfère pas : les signatures exactes, donc le code.

**Décision à prendre avant d'écrire une ligne** : porter le labo sur 1.21.1 (= module **G4**, matériel de test réel déjà disponible, pack de 398 mods) ou rester en 1.20.1 (= G1, matériel à télécharger).
