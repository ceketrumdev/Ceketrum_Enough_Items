package com.ceketrum.cei.data;

import com.ceketrum.cei.i18n.CeiText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
    public synchronized void ensureCacheBuilt() {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBlockGeneration()) return;

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
            var biomeRegistry = registryManager.registryOrThrow(Registries.BIOME);
            
            if (biomeRegistry == null) return;
            
            blockBiomesCache.clear();
            blockHeightsCache.clear();
            
            
            int scannedBiomes = 0;
            
            // Parcourir tous les biomes
            for (var biomeEntry : biomeRegistry.holders().toList()) {
                var biome = biomeEntry.value();
                ResourceLocation biomeId = biomeEntry.key().location();
                String biomeName = biomeId.getPath();   // brut : traduit a l'affichage
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
                                    scanPlacedFeature(placedFeature, biomeName);
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
    
    private void scanPlacedFeature(PlacedFeature feature, String biomeName) {
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
                    heightDetails = formatHeightModifier(heightModifier);
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
    
    private String formatHeightModifier(HeightRangePlacement modifier) {
        try {
            Field heightProviderField = modifier.getClass().getDeclaredField("height");
            heightProviderField.setAccessible(true);
            HeightProvider heightProvider = (HeightProvider) heightProviderField.get(modifier);
            if (heightProvider != null) {
                String desc = heightProvider.toString(); // Souvent formaté de type Uniform / Trapezoid
                return desc;   // prefixe ajoute a l'affichage, pas au scan
            }
        } catch (Exception e) {}
        return null;
    }
    
    private String formatBiomeName(String path) {
        String name = path.replace("_", " ");
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        
        // Une cle par biome, avec repli sur le chemin embelli : un biome
        // de mod suit exactement le meme chemin qu'un biome vanilla.
        return CeiText.or("cei.biome." + path, name);
    }
    
    /**
     * Renvoie la liste de génération naturelle (biomes, structures, altitudes) pour un bloc donné.
     */
    public List<String> getBlockGenerationSources(Item item) {
        // Module coupe dans la configuration : le travail n'a pas lieu.
        if (!com.ceketrum.cei.config.CeiConfig.getInstance().isFeatureBlockGeneration()) return java.util.List.of();

        ensureCacheBuilt();
        
        
        List<String> results = new ArrayList<>();
        
        // 1. Essayer de récupérer le scan dynamique
        Set<String> scannedBiomes = blockBiomesCache.get(item);
        if (scannedBiomes != null && !scannedBiomes.isEmpty()) {
            String title = CeiText.t("cei.gen.biomes.title", scannedBiomes.size());
            results.add(title);
            
            // Limiter à 5 biomes visibles et ajouter un "... et X autres"
            List<String> list = new ArrayList<>(scannedBiomes);
            int limit = Math.min(5, list.size());
            for (int i = 0; i < limit; i++) {
                results.add("  • " + formatBiomeName(list.get(i)));
            }
            if (list.size() > 5) {
                results.add("  • ... et " + CeiText.t("cei.gen.more", list.size() - 5));
            }
            
            String height = blockHeightsCache.get(item);
            if (height != null) {
                results.add("  • " + CeiText.t("cei.gen.ylevels", height));
            }
        }
        
        // 2. Récupérer les fallbacks locaux (biomes, structures, altitudes)
        List<String> fallbacks = getFallbackBlockLocations(item);
        results.addAll(fallbacks);
        
        return results;
    }
    
    private List<String> getFallbackBlockLocations(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();
        String path = id.getPath();
        
        List<String> locs = new ArrayList<>();
        
        switch (path) {
            // Minerais Vanilla
            case "coal_ore":
            case "deepslate_coal_ore":
                locs.add(CeiText.t("cei.gen.all_mountain_underground"));
                locs.add(CeiText.t("cei.gen.y_0_256_peaks_96_256"));
                break;
            case "iron_ore":
            case "deepslate_iron_ore":
                locs.add(CeiText.t("cei.gen.all_biomes_mountains_caves"));
                locs.add(CeiText.t("cei.gen.y_m64_320_peaks_16_256"));
                break;
            case "copper_ore":
            case "deepslate_copper_ore":
                locs.add(CeiText.t("cei.gen.all_biomes_dripstone"));
                locs.add(CeiText.t("cei.gen.y_m16_112_peak_48"));
                break;
            case "gold_ore":
            case "deepslate_gold_ore":
                locs.add(CeiText.t("cei.gen.badlands_and_underground"));
                locs.add(CeiText.t("cei.gen.y_badlands_underground"));
                break;
            case "redstone_ore":
            case "deepslate_redstone_ore":
                locs.add(CeiText.t("cei.gen.very_deep_all_biomes"));
                locs.add(CeiText.t("cei.gen.y_m64_16_toward_m64"));
                break;
            case "lapis_ore":
            case "deepslate_lapis_ore":
                locs.add(CeiText.t("cei.gen.deep_all_biomes"));
                locs.add(CeiText.t("cei.gen.y_m64_64_peak_0"));
                break;
            case "diamond_ore":
            case "deepslate_diamond_ore":
                locs.add(CeiText.t("cei.gen.extreme_deepslate"));
                locs.add(CeiText.t("cei.gen.y_m64_16_max_m64"));
                break;
            case "emerald_ore":
            case "deepslate_emerald_ore":
                locs.add(CeiText.t("cei.gen.only_under_mountains"));
                locs.add(CeiText.t("cei.gen.y_m16_320_peak_256"));
                break;
            case "nether_quartz_ore":
            case "nether_gold_ore":
                locs.add(CeiText.t("cei.gen.nether_everywhere"));
                locs.add(CeiText.t("cei.gen.y_10_117"));
                break;
            case "ancient_debris":
                locs.add(CeiText.t("cei.gen.nether_deep_under_lava"));
                locs.add(CeiText.t("cei.gen.y_8_119_peak_15"));
                break;
                
            // Blocs de Structures & Trial Chambers
            case "tuff":
            case "tuff_bricks":
            case "chiseled_tuff":
            case "polished_tuff":
                locs.add(CeiText.t("cei.gen.struct_trial_chambers"));
                locs.add(CeiText.t("cei.gen.deepslate_underground"));
                break;
            case "copper_bulb":
            case "chiseled_copper":
            case "copper_grate":
            case "waxed_copper_bulb":
                locs.add(CeiText.t("cei.gen.struct_trial_lighting"));
                break;
            case "nether_bricks":
            case "nether_brick_fence":
                locs.add(CeiText.t("cei.gen.struct_nether_fortress"));
                break;
            case "red_nether_bricks":
                locs.add(CeiText.t("cei.gen.crimson_forest"));
                break;
            case "basalt":
            case "polished_basalt":
                locs.add(CeiText.t("cei.gen.basalt_deltas"));
                break;
            case "blackstone":
            case "polished_blackstone":
            case "gilded_blackstone":
                locs.add(CeiText.t("cei.gen.soul_valley_bastion"));
                break;
            case "soul_sand":
            case "soul_soil":
                locs.add(CeiText.t("cei.gen.soul_sand_valley"));
                break;
            case "glowstone":
                locs.add(CeiText.t("cei.gen.nether_ceiling"));
                break;
            case "end_stone":
                locs.add(CeiText.t("cei.gen.the_end_islands"));
                break;
            case "purpur_block":
            case "purpur_pillar":
                locs.add(CeiText.t("cei.gen.struct_end_cities"));
                break;
            case "prismarine":
            case "dark_prismarine":
            case "prismarine_bricks":
                locs.add(CeiText.t("cei.gen.struct_ocean_monument"));
                break;
            case "sea_lantern":
                locs.add(CeiText.t("cei.gen.struct_monument_light"));
                break;
            case "sponge":
            case "wet_sponge":
                locs.add(CeiText.t("cei.gen.struct_monument_sponge"));
                break;
            case "moss_block":
            case "spore_blossom":
            case "cave_vines":
                locs.add(CeiText.t("cei.gen.lush_caves"));
                break;
            case "dripstone_block":
            case "pointed_dripstone":
                locs.add(CeiText.t("cei.gen.dripstone_caves"));
                break;
            case "sculk":
            case "sculk_catalyst":
            case "sculk_shrieker":
            case "sculk_sensor":
                locs.add(CeiText.t("cei.gen.ancient_cities"));
                break;
            case "terracotta":
            case "red_terracotta":
            case "orange_terracotta":
            case "yellow_terracotta":
            case "white_terracotta":
            case "light_gray_terracotta":
            case "brown_terracotta":
                locs.add(CeiText.t("cei.gen.badlands_clay"));
                break;
            case "mud":
            case "mangrove_roots":
                locs.add(CeiText.t("cei.gen.mangrove_swamp"));
                break;
            case "sandstone":
            case "chiseled_sandstone":
            case "smooth_sandstone":
                locs.add(CeiText.t("cei.gen.desert_subsurface"));
                break;
            case "red_sandstone":
                locs.add(CeiText.t("cei.gen.badlands_subsurface"));
                break;
        }
        
        return locs;
    }
}

