# Format des Fichiers de Descriptions

Ce dossier contient les fichiers JSON pour les descriptions d'items du mod.

## Structure des Fichiers

Chaque langue a son propre fichier JSON nommé selon le format :
```
descriptions_[code_langue].json
```

Exemples :
- `descriptions_fr_fr.json` - Français
- `descriptions_en_us.json` - Anglais (États-Unis)
- `descriptions_es_es.json` - Espagnol
- `descriptions_de_de.json` - Allemand

## Format JSON

Le fichier JSON est un objet simple où :
- **Clé** : L'ID complet de l'item Minecraft (ex: `"minecraft:dirt"`) ou juste le nom (ex: `"dirt"`)
- **Valeur** : La description de l'item en texte brut

### Exemple de Structure

```json
{
  "minecraft:dirt": "La terre, base fondamentale de Minecraft. Utilisée pour l'agriculture et la construction.",
  "minecraft:stone": "La pierre, matériau de construction robuste. Peut être cuite pour obtenir de la pierre lisse.",
  "dirt": "Description générique pour tous les items nommés 'dirt'"
}
```

## Règles Importantes

1. **ID Complet vs Nom Simple** :
   - Utilisez `"minecraft:dirt"` pour cibler un item spécifique
   - Utilisez `"dirt"` pour une description générique qui s'appliquera à tous les items avec ce nom (utile pour les mods)

2. **Priorité** :
   - L'ID complet (`minecraft:dirt`) a la priorité sur le nom simple (`dirt`)
   - Si les deux existent, l'ID complet sera utilisé

3. **Texte Multi-lignes** :
   - Pour des descriptions sur plusieurs lignes, utilisez `\n` dans le JSON :
   ```json
   {
     "minecraft:diamond": "Le diamant, gemme la plus précieuse.\nNécessaire pour créer les meilleurs outils."
   }
   ```

4. **Caractères Spéciaux** :
   - Échappez les guillemets avec `\"`
   - Les caractères Unicode sont supportés directement

## Ajouter une Nouvelle Langue

1. Créez un nouveau fichier `descriptions_[code_langue].json`
2. Copiez la structure d'un fichier existant
3. Traduisez les descriptions
4. Le mod chargera automatiquement la langue correspondant à la langue du jeu

## Codes de Langue Supportés

Les codes de langue suivent le format standard Minecraft :
- `fr_fr` - Français
- `en_us` - Anglais (États-Unis)
- `en_gb` - Anglais (Royaume-Uni)
- `es_es` - Espagnol
- `de_de` - Allemand
- `it_it` - Italien
- `pt_br` - Portugais (Brésil)
- `ru_ru` - Russe
- `zh_cn` - Chinois (Simplifié)
- `ja_jp` - Japonais
- Et tous les autres codes de langue supportés par Minecraft

## Fallback

Si un fichier de langue n'existe pas, le mod utilisera automatiquement `descriptions_en_us.json` comme fallback.

