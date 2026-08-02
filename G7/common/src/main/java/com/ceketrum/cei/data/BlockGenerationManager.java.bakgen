package com.ceketrum.cei.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Gère le scan dynamique et l'indexation de la génération naturelle de blocs
 * (minerais, roches, végétation) dans les biomes et structures de Minecraft.
 */
public class BlockGenerationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-block-gen");
    private static BlockGenerationManager instance;
    
    private final Map<Item, Set<String>> blockBiomesCache = new HashMap<>();
    private final Map<Item, String> blockHeightsCache = new HashMap<>();
    private boolean isCacheBuilt = false;
    
    private BlockGenerationManager() {}
    
    public static BlockGenerationManager getInstance() {
        if (instance == null) {
            instance = new BlockGenerationManager();
        }
        return instance;
    }
    
    /**
     * Assure que le cache de génération de blocs est construit en solo.
     * Explore récursivement tous les biomes et leurs PlacedFeatures.
     */
    /**
     * Passe a true si l'exploration echoue sur une API absente de la version
     * courante. Les classes de generation / loot / brassage bougent d'une
     * version a l'autre (ConfiguredFeature a par exemple disparu en 26.3) :
     * une fonctionnalite annexe ne doit pas faire tomber le client.
     */
    private volatile boolean cei$degraded = false;

    public synchronized void ensureCacheBuilt() {
        if (cei$degraded) {
            return;
        }
        try {
            cei$ensureCacheBuiltImpl();
        } catch (LinkageError | Exception e) {
            cei$degraded = true;
            org.slf4j.LoggerFactory.getLogger("cei").warn(
                "CEI: BlockGenerationManager desactive sur cette version de Minecraft ({}). "
                + "La fonctionnalite associee restera vide, le reste du mod fonctionne.",
                e.toString());
        }
    }

    private synchronized void cei$ensureCacheBuiltImpl() {
        if (isCacheBuilt) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        
        if (!client.isLocalServer()) {
            isCacheBuilt = true;
            LOGGER.info("[BLOCKGEN] Client en multijoueur, skip du scan dynamique (fallbacks locaux activés).");
            return;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            var registryManager = client.level.registryAccess();
            var biomeRegistry = registryManager.lookupOrThrow(Registries.BIOME);
            
            if (biomeRegistry == null) return;
            
            blockBiomesCache.clear();
            blockHeightsCache.clear();
            
            String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
            boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
            
            int scannedBiomes = 0;
            
            // Parcourir tous les biomes
            for (var key : biomeRegistry.registryKeySet()) {
                var biome = biomeRegistry.get(key).map(net.minecraft.core.Holder::value).orElse(null);
                if (biome == null) continue;
                Identifier biomeId = key.identifier();
                String biomeName = formatBiomeName(biomeId, isFr);
                scannedBiomes++;
                
                // Récupérer les paramètres de génération du biome
                var genSettings = biome.getGenerationSettings();
                
                // Les étapes intéressantes : 6 (UNDERGROUND_ORES) et 8 (VEGETAL_DECORATION)
                List<Integer> stepsOfInterest = List.of(6, 8);
                
                for (int step : stepsOfInterest) {
                    try {
                        var features = genSettings.features();
                        if (step < features.size()) {
                            var placedFeatureList = features.get(step);
                            for (var placedFeatureEntry : placedFeatureList) {
                                PlacedFeature placedFeature = placedFeatureEntry.value();
                                if (placedFeature != null) {
                                    scanPlacedFeature(placedFeature, biomeName, isFr);
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            }
            
            isCacheBuilt = true;
            LOGGER.info("[BLOCKGEN] Scan dynamique terminé en {} ms. {} biomes scannés, {} blocs indexés.", 
                        System.currentTimeMillis() - startTime, scannedBiomes, blockBiomesCache.size());
            
        } catch (Exception e) {
            LOGGER.error("[BLOCKGEN] Erreur lors du scan de génération de blocs: {}", e.getMessage(), e);
        }
    }
    
    private void scanPlacedFeature(PlacedFeature feature, String biomeName, boolean isFr) {
        try {
            // 1. Extraire la configuration configurée
            var configuredEntry = feature.feature();
            ConfiguredFeature<?, ?> configuredFeature = configuredEntry.value();
            if (configuredFeature == null) return;
            
            Set<Block> blocksInFeature = new HashSet<>();
            scanFeatureConfigRecursively(configuredFeature.config(), blocksInFeature, new HashSet<>(), 0);
            
            // 2. Extraire la hauteur si présente (ex: pour les minerais)
            String heightDetails = null;
            for (PlacementModifier modifier : feature.placement()) {
                if (modifier instanceof HeightRangePlacement heightModifier) {
                    heightDetails = formatHeightModifier(heightModifier, isFr);
                    break;
                }
            }
            
            // 3. Associer les blocs au biome et à la hauteur
            for (Block block : blocksInFeature) {
                Item item = block.asItem();
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    blockBiomesCache.computeIfAbsent(item, k -> new LinkedHashSet<>()).add(biomeName);
                    if (heightDetails != null) {
                        blockHeightsCache.put(item, heightDetails);
                    }
                }
            }
        } catch (Exception e) {}
    }
    
    private void scanFeatureConfigRecursively(Object config, Set<Block> blocks, Set<Object> visited, int depth) {
        if (config == null || depth > 6) return;
        if (!visited.add(config)) return;
        
        Class<?> clazz = config.getClass();
        
        // Extraction directe de Block ou BlockState
        if (config instanceof Block) {
            blocks.add((Block) config);
            return;
        }
        if (config instanceof BlockState) {
            blocks.add(((BlockState) config).getBlock());
            return;
        }
        
        // Extraction depuis OreFeatureConfig.Target
        if (config instanceof OreConfiguration.TargetBlockState target) {
            blocks.add(target.state.getBlock());
            return;
        }
        
        // Extraction depuis BlockStateProvider
        if (config instanceof net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider provider) {
            try {
                // Tenter d'accéder au champ 'state' de SimpleBlockStateProvider
                Field stateField = provider.getClass().getDeclaredField("state");
                stateField.setAccessible(true);
                Object stateObj = stateField.get(provider);
                if (stateObj instanceof BlockState) {
                    blocks.add(((BlockState) stateObj).getBlock());
                }
            } catch (Exception e) {}
            return;
        }
        
        // Traitement récursif des collections
        if (config instanceof Collection<?> collection) {
            for (Object element : collection) {
                scanFeatureConfigRecursively(element, blocks, visited, depth + 1);
            }
            return;
        }
        
        // Parcours par réflexion de tous les champs de configuration
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field[] fields = current.getDeclaredFields();
                for (Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(config);
                    if (value != null) {
                        scanFeatureConfigRecursively(value, blocks, visited, depth + 1);
                    }
                }
            } catch (Exception e) {}
            current = current.getSuperclass();
        }
    }
    
    private String formatHeightModifier(HeightRangePlacement modifier, boolean isFr) {
        try {
            Field heightProviderField = modifier.getClass().getDeclaredField("height");
            heightProviderField.setAccessible(true);
            HeightProvider heightProvider = (HeightProvider) heightProviderField.get(modifier);
            if (heightProvider != null) {
                String desc = heightProvider.toString(); // Souvent formaté de type Uniform / Trapezoid
                return isFr ? "Altitudes Y : " + desc : "Y Levels: " + desc;
            }
        } catch (Exception e) {}
        return null;
    }
    
    private String formatBiomeName(Identifier id, boolean isFr) {
        String path = id.getPath();
        String name = path.replace("_", " ");
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        
        if (isFr) {
            // Localisation soignée en français des biomes vanilla courants
            return switch (path) {
                case "plains" -> "Plaines";
                case "desert" -> "Désert";
                case "forest" -> "Forêt";
                case "taiga" -> "Taïga";
                case "swamp" -> "Marais";
                case "river" -> "Rivière";
                case "ocean" -> "Océan";
                case "beach" -> "Plage";
                case "savanna" -> "Savane";
                case "badlands" -> "Badlands (Terres arides)";
                case "snowy_slopes" -> "Pentes enneigées";
                case "meadow" -> "Prairie";
                case "grove" -> "Bosquet";
                case "cherry_grove" -> "Forêt de Cerisiers";
                case "deep_dark" -> "Abîmes obscures (Deep Dark)";
                case "mangrove_swamp" -> "Marais à Mangroves";
                case "lush_caves" -> "Grottes luxuriantes";
                case "dripstone_caves" -> "Grottes de spéléothèmes";
                case "basalt_deltas" -> "Deltas de Basalte";
                case "crimson_forest" -> "Forêt carmin";
                case "warped_forest" -> "Forêt distordue";
                case "soul_sand_valley" -> "Vallée des Âmes";
                case "nether_wastes" -> "Désolations du Nether";
                case "the_end" -> "L'End";
                default -> name;
            };
        }
        return name;
    }
    
    /**
     * Renvoie la liste de génération naturelle (biomes, structures, altitudes) pour un bloc donné.
     */
    public List<String> getBlockGenerationSources(Item item) {
        ensureCacheBuilt();
        
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
        
        List<String> results = new ArrayList<>();
        
        // 1. Essayer de récupérer le scan dynamique
        Set<String> scannedBiomes = blockBiomesCache.get(item);
        if (scannedBiomes != null && !scannedBiomes.isEmpty()) {
            String title = isFr ? "Génération Biomes (" + scannedBiomes.size() + ") :" : "Biome Generation (" + scannedBiomes.size() + "):";
            results.add(title);
            
            // Limiter à 5 biomes visibles et ajouter un "... et X autres"
            List<String> list = new ArrayList<>(scannedBiomes);
            int limit = Math.min(5, list.size());
            for (int i = 0; i < limit; i++) {
                results.add("  • " + list.get(i));
            }
            if (list.size() > 5) {
                results.add(isFr ? "  • ... et " + (list.size() - 5) + " autres biomes" : "  • ... and " + (list.size() - 5) + " other biomes");
            }
            
            String height = blockHeightsCache.get(item);
            if (height != null) {
                results.add("  • " + height);
            }
        }
        
        // 2. Récupérer les fallbacks locaux (biomes, structures, altitudes)
        List<String> fallbacks = getFallbackBlockLocations(item, isFr);
        results.addAll(fallbacks);
        
        return results;
    }
    
    private List<String> getFallbackBlockLocations(Item item, boolean isFr) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();
        String path = id.getPath();
        
        List<String> locs = new ArrayList<>();
        
        switch (path) {
            // Minerais Vanilla
            case "coal_ore":
            case "deepslate_coal_ore":
                locs.add(isFr ? "Génération : Tous les biomes montagneux et souterrains" : "Generation: All mountainous and underground biomes");
                locs.add(isFr ? "Altitudes Y : Y=0 à Y=256 (Pic à Y=96 et Y=256)" : "Y Levels: Y=0 to Y=256 (Peaks at Y=96 and Y=256)");
                break;
            case "iron_ore":
            case "deepslate_iron_ore":
                locs.add(isFr ? "Génération : Tous les biomes (Particulièrement les Montagnes et Grottes)" : "Generation: All biomes (Especially Mountains and Caves)");
                locs.add(isFr ? "Altitudes Y : Y=-64 à Y=320 (Pics à Y=16 et Y=256)" : "Y Levels: Y=-64 to Y=320 (Peaks at Y=16 and Y=256)");
                break;
            case "copper_ore":
            case "deepslate_copper_ore":
                locs.add(isFr ? "Génération : Tous les biomes (Abondant dans les grottes de spéléothèmes)" : "Generation: All biomes (Very abundant in Dripstone Caves)");
                locs.add(isFr ? "Altitudes Y : Y=-16 à Y=112 (Pic à Y=48)" : "Y Levels: Y=-16 to Y=112 (Peak at Y=48)");
                break;
            case "gold_ore":
            case "deepslate_gold_ore":
                locs.add(isFr ? "Génération : Badlands (Terres arides) et tous les souterrains" : "Generation: Badlands and all deep undergrounds");
                locs.add(isFr ? "Badlands : Y=32 à Y=256 / Souterrain : Y=-64 à Y=32" : "Badlands: Y=32 to Y=256 / Underground: Y=-64 to Y=32");
                break;
            case "redstone_ore":
            case "deepslate_redstone_ore":
                locs.add(isFr ? "Génération : Très profond sous terre (Tous les biomes)" : "Generation: Deep underground in all biomes");
                locs.add(isFr ? "Altitudes Y : Y=-64 à Y=16 (Abondance accrue vers Y=-64)" : "Y Levels: Y=-64 to Y=16 (Increasingly common towards Y=-64)");
                break;
            case "lapis_ore":
            case "deepslate_lapis_ore":
                locs.add(isFr ? "Génération : Profond sous terre (Tous les biomes)" : "Generation: Deep underground in all biomes");
                locs.add(isFr ? "Altitudes Y : Y=-64 à Y=64 (Pic à Y=0)" : "Y Levels: Y=-64 to Y=64 (Peak at Y=0)");
                break;
            case "diamond_ore":
            case "deepslate_diamond_ore":
                locs.add(isFr ? "Génération : Profondeurs extrêmes des Abysses (Deepslate Y < 0)" : "Generation: Extreme depths of the Deepslate (Y < 0)");
                locs.add(isFr ? "Altitudes Y : Y=-64 à Y=16 (Abondance maximale à Y=-64)" : "Y Levels: Y=-64 to Y=16 (Max abundance at Y=-64)");
                break;
            case "emerald_ore":
            case "deepslate_emerald_ore":
                locs.add(isFr ? "Génération : Uniquement sous les biomes de Montagne (Windswept / Peaks)" : "Generation: Only under Mountain biomes (Windswept / Peaks)");
                locs.add(isFr ? "Altitudes Y : Y=-16 à Y=320 (Pic à Y=256)" : "Y Levels: Y=-16 to Y=320 (Peak at Y=256)");
                break;
            case "nether_quartz_ore":
            case "nether_gold_ore":
                locs.add(isFr ? "Génération : Naturellement partout dans le Nether" : "Generation: Naturally everywhere in the Nether");
                locs.add(isFr ? "Altitudes Y : Y=10 à Y=117" : "Y Levels: Y=10 to Y=117");
                break;
            case "ancient_debris":
                locs.add(isFr ? "Génération : Sous-sol très profond du Nether (Masqué sous la lave)" : "Generation: Deep underground in the Nether (Hidden under lava)");
                locs.add(isFr ? "Altitudes Y : Y=8 à Y=119 (Pic absolu à Y=15)" : "Y Levels: Y=8 to Y=119 (Absolute peak at Y=15)");
                break;
                
            // Blocs de Structures & Trial Chambers
            case "tuff":
            case "tuff_bricks":
            case "chiseled_tuff":
            case "polished_tuff":
                locs.add(isFr ? "Structure : Chambres des Épreuves (Trial Chambers)" : "Structure: Trial Chambers");
                locs.add(isFr ? "Génération : Abysses souterraines abyssales (Y < 0)" : "Generation: Deepslate underground (Y < 0)");
                break;
            case "copper_bulb":
            case "chiseled_copper":
            case "copper_grate":
            case "waxed_copper_bulb":
                locs.add(isFr ? "Structure : Chambres des Épreuves (Éléments d'éclairage et murs)" : "Structure: Trial Chambers (Lighting and structural blocks)");
                break;
            case "nether_bricks":
            case "nether_brick_fence":
                locs.add(isFr ? "Structure : Forteresses du Nether" : "Structure: Nether Fortresses");
                break;
            case "red_nether_bricks":
                locs.add(isFr ? "Génération : Forêt Carmin (Ruines / Nether)" : "Generation: Crimson Forest (Nether)");
                break;
            case "basalt":
            case "polished_basalt":
                locs.add(isFr ? "Biome : Deltas de Basalte (Colonnes volcaniques massives)" : "Biome: Basalt Deltas (Massive volcanic columns)");
                break;
            case "blackstone":
            case "polished_blackstone":
            case "gilded_blackstone":
                locs.add(isFr ? "Biome : Vallée des Âmes, Vestiges de Bastion (Nether)" : "Biome: Soul Sand Valley, Bastion Remnants (Nether)");
                break;
            case "soul_sand":
            case "soul_soil":
                locs.add(isFr ? "Biome : Vallée des Âmes (Soul Sand Valley - Nether)" : "Biome: Soul Sand Valley (Nether)");
                break;
            case "glowstone":
                locs.add(isFr ? "Nether : Pousse sous le plafond rocheux de toutes les zones" : "Nether: Grows under the ceiling of all Nether biomes");
                break;
            case "end_stone":
                locs.add(isFr ? "Dimension : L'End (Constitue l'intégralité des îles)" : "Dimension: The End (Constitutes all islands)");
                break;
            case "purpur_block":
            case "purpur_pillar":
                locs.add(isFr ? "Structure : Cités de l'End (Tours et ponts)" : "Structure: End Cities (Towers and bridges)");
                break;
            case "prismarine":
            case "dark_prismarine":
            case "prismarine_bricks":
                locs.add(isFr ? "Structure : Monuments Océaniques (Ocean Monument)" : "Structure: Ocean Monuments");
                break;
            case "sea_lantern":
                locs.add(isFr ? "Structure : Monuments Océaniques (Éléments lumineux)" : "Structure: Ocean Monuments (Light sources)");
                break;
            case "sponge":
            case "wet_sponge":
                locs.add(isFr ? "Structure : Monuments Océaniques (Salles d'éponges secrètes)" : "Structure: Ocean Monuments (Secret sponge rooms)");
                break;
            case "moss_block":
            case "spore_blossom":
            case "cave_vines":
                locs.add(isFr ? "Biome : Grottes Luxuriantes (Lush Caves)" : "Biome: Lush Caves");
                break;
            case "dripstone_block":
            case "pointed_dripstone":
                locs.add(isFr ? "Biome : Grottes de spéléothèmes (Dripstone Caves)" : "Biome: Dripstone Caves");
                break;
            case "sculk":
            case "sculk_catalyst":
            case "sculk_shrieker":
            case "sculk_sensor":
                locs.add(isFr ? "Biome : Cités Abyssales (Ancient Cities / Deep Dark)" : "Biome: Ancient Cities (Deep Dark / Ancient City)");
                break;
            case "terracotta":
            case "red_terracotta":
            case "orange_terracotta":
            case "yellow_terracotta":
            case "white_terracotta":
            case "light_gray_terracotta":
            case "brown_terracotta":
                locs.add(isFr ? "Biome : Badlands (Terres arides - Stries géologiques colorées)" : "Biome: Badlands (Colorful geologic clay layers)");
                break;
            case "mud":
            case "mangrove_roots":
                locs.add(isFr ? "Biome : Marais à Mangroves (Mangrove Swamp)" : "Biome: Mangrove Swamp");
                break;
            case "sandstone":
            case "chiseled_sandstone":
            case "smooth_sandstone":
                locs.add(isFr ? "Génération : Sous-couche des déserts, Temples du désert" : "Generation: Sub-surface of Desert biomes, Desert Temples");
                break;
            case "red_sandstone":
                locs.add(isFr ? "Génération : Sous-couche des Badlands" : "Generation: Sub-surface of Badlands");
                break;
        }
        
        return locs;
    }
}
