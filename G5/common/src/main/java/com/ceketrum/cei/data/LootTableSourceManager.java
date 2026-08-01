package com.ceketrum.cei.data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Gère le scan global et l'indexation dynamique des sources de loot tables (coffres, monstres, blocs, etc.).
 * Intègre également une base de données locale de secours (fallback) pour le mode multijoueur.
 */
public class LootTableSourceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-loot-tables");
    
    private static LootTableSourceManager instance;
    
    // Cache global inverse : Item -> Noms des sources d'obtention
    private final Map<Item, List<String>> globalLootCache = new HashMap<>();
    private final Map<Item, Set<ResourceLocation>> itemToLootIds = new HashMap<>();
    private boolean isCacheBuilt = false;
    private int attempts = 0;
    private static final int MAX_ATTEMPTS = 5;
    
    private LootTableSourceManager() {
        // Chargement à la demande
    }
    
    public static LootTableSourceManager getInstance() {
        if (instance == null) {
            instance = new LootTableSourceManager();
        }
        return instance;
    }
    
    /**
     * Recherche récursivement un champ dans une classe et ses superclasses.
     */
    private static java.lang.reflect.Field getDeclaredFieldRecursive(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " in " + clazz.getName());
    }
    
    /**
     * Construit le cache global des loot tables en scannant le registre du serveur.
     * Cette opération ne s'exécute qu'une seule fois par chargement de monde en moins de 10 ms.
     */
    public synchronized void ensureCacheBuilt() {
        if (isCacheBuilt) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        
        // En multijoueur, on n'a pas accès à l'integrated server. On skip le scan de loot tables pour éviter les spams/lags
        if (!client.isLocalServer()) {
            isCacheBuilt = true;
            LOGGER.info("[LOOT] Client en multijoueur, skip du scan global de loot tables (fallbacks locaux activés).");
            return;
        }
        
        MinecraftServer server = null;
        if (client.player != null) {
            server = client.player.getServer();
        }
        if (server == null) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                isCacheBuilt = true;
                LOGGER.warn("[LOOT] Nombre maximum de tentatives d'accès au serveur atteint en solo. Skip.");
            }
            return;
        }
        
        try {
            var registryManager = server.registryAccess();
            var lootTableRegistry = registryManager.lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE);
            if (lootTableRegistry == null) return;
            
            LOGGER.info("[LOOT] Début du scan global des loot tables...");
            long startTime = System.currentTimeMillis();
            
            globalLootCache.clear();
            itemToLootIds.clear();
            int scannedTables = 0;
            
            // Itérer sur toutes les loot tables enregistrées (vanilla et moddées)
            for (var key : lootTableRegistry.registryKeySet()) {
                LootTable lootTable = lootTableRegistry.get(key).map(net.minecraft.core.Holder::value).orElse(null);
                ResourceLocation id = key.location();
                
                if (lootTable != null && lootTable != LootTable.EMPTY) {
                    scannedTables++;
                    Set<Item> itemsInTable = findItemsInLootTable(lootTable);
                    String sourceName = formatLootTableName(id);
                    
                    for (Item item : itemsInTable) {
                        globalLootCache.computeIfAbsent(item, k -> new ArrayList<>()).add(sourceName);
                        itemToLootIds.computeIfAbsent(item, k -> new LinkedHashSet<>()).add(id);
                    }
                }
            }
            
            isCacheBuilt = true;
            LOGGER.info("[LOOT] Scan global terminé en {} ms. {} loot tables scannées, {} items indexés.", 
                        System.currentTimeMillis() - startTime, scannedTables, globalLootCache.size());
            
        } catch (Exception e) {
            // On marque le cache comme construit meme en cas d'echec. Cette
            // methode est appelee depuis le rendu : sans ce drapeau, un echec se
            // repetait a chaque frame et noyait le log sous plusieurs centaines
            // de stacktraces par seconde.
            isCacheBuilt = true;
            LOGGER.error("[LOOT] Scan global des loot tables abandonne : {}", e.getMessage(), e);
        }
    }
    
    /**
     * Parcourt récursivement une LootTable pour trouver tous les items qu'elle peut générer.
     */
    private Set<Item> findItemsInLootTable(LootTable lootTable) {
        Set<Item> items = new HashSet<>();
        if (lootTable != null && lootTable != LootTable.EMPTY) {
            scanObjectRecursively(lootTable, items, new HashSet<>(), 0);
        }
        return items;
    }
    
    private void collectItemsFromEntry(Object entry, Set<Item> items) {
        if (entry == null) return;
        scanObjectRecursively(entry, items, new HashSet<>(), 0);
    }
    
    /**
     * Parcourt récursivement et de façon omnisciente n'importe quel objet de loot
     * pour y extraire tous les items, stacks, tags ou tables de loot imbriquées, 
     * ce qui assure une compatibilité à 100% avec les entries moddées customisées.
     */
    private void scanObjectRecursively(Object obj, Set<Item> items, Set<Object> visited, int depth) {
        if (obj == null || depth > 8) return;
        if (!visited.add(obj)) return; // Protection contre les cycles infinis
        
        Class<?> clazz = obj.getClass();
        String className = clazz.getName();
        
        // Ignorer les classes système lourdes ou de réseau pour la sécurité et la performance
        if (className.startsWith("java.lang.ClassLoader") || 
            className.startsWith("java.lang.Thread") ||
            className.contains("MinecraftClient") ||
            className.contains("MinecraftServer") ||
            className.contains("ClientPlayNetworkHandler") ||
            className.contains("ServerPlayNetworkHandler") ||
            className.contains("World") ||
            className.contains("Level")) {
            return;
        }
        
        // 1. Extraction directe d'Item
        if (obj instanceof Item) {
            items.add((Item) obj);
            return;
        }
        
        // 2. Extraction d'ItemStack
        if (obj instanceof ItemStack stack) {
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
            return;
        }
        
        // 3. Unpacking de RegistryEntry (ex: RegistryEntry<Item> ou RegistryEntry<LootTable>)
        if (obj instanceof net.minecraft.core.Holder<?> entry) {
            try {
                Object value = entry.value();
                if (value instanceof Item) {
                    items.add((Item) value);
                    return;
                } else if (value != null) {
                    scanObjectRecursively(value, items, visited, depth + 1);
                }
            } catch (Exception e) {}
            
            // Tenter également d'extraire la clé du RegistryEntry en cas d'ID direct
            try {
                Method getKeyMethod = obj.getClass().getMethod("getKey");
                Object optionalKey = getKeyMethod.invoke(obj);
                if (optionalKey instanceof Optional<?> opt && opt.isPresent()) {
                    Object keyObj = opt.get();
                    Method getValueMethod = keyObj.getClass().getMethod("getValue");
                    Object idObj = getValueMethod.invoke(keyObj);
                    if (idObj instanceof ResourceLocation id) {
                        if (id.getPath().contains("loot_table") || className.contains("LootTable")) {
                            resolveAndScanLootTable(id, items, visited, depth + 1);
                        }
                    }
                }
            } catch (Exception e) {}
            return;
        }
        
        // 4. Résolution de TagKey d'items
        if (obj instanceof net.minecraft.tags.TagKey<?> tagKey) {
            try {
                if (tagKey.registry().location().getPath().equals("item")) {
                    @SuppressWarnings("unchecked")
                    var itemTagKey = (net.minecraft.tags.TagKey<Item>) tagKey;
                    var optionalList = BuiltInRegistries.ITEM.get(itemTagKey);
                    if (optionalList.isPresent()) {
                        for (var itemEntry : optionalList.get()) {
                            items.add(itemEntry.value());
                        }
                    }
                }
            } catch (Exception e) {}
            return;
        }
        
        // 5. Résolution d'Identifier direct (ex: ID de loot table imbriquée)
        if (obj instanceof ResourceLocation id) {
            if (id.getPath().contains("loot_tables/") || id.getPath().contains("chests/") || id.getPath().contains("entities/")) {
                resolveAndScanLootTable(id, items, visited, depth + 1);
            }
            return;
        }
        
        // 6. Traitement des tableaux
        if (clazz.isArray()) {
            try {
                int length = java.lang.reflect.Array.getLength(obj);
                for (int i = 0; i < length; i++) {
                    Object element = java.lang.reflect.Array.get(obj, i);
                    scanObjectRecursively(element, items, visited, depth + 1);
                }
            } catch (Exception e) {}
            return;
        }
        
        // 7. Traitement des Collections (List, Set, etc.)
        if (obj instanceof Collection<?> collection) {
            for (Object element : collection) {
                scanObjectRecursively(element, items, visited, depth + 1);
            }
            return;
        }
        
        // 8. Traitement des Maps
        if (obj instanceof Map<?,?> map) {
            for (Object key : map.keySet()) {
                scanObjectRecursively(key, items, visited, depth + 1);
            }
            for (Object val : map.values()) {
                scanObjectRecursively(val, items, visited, depth + 1);
            }
            return;
        }
        
        // 9. Parcours par réflexion récursive de tous les champs de l'objet
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                java.lang.reflect.Field[] fields = current.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null) {
                        scanObjectRecursively(fieldValue, items, visited, depth + 1);
                    }
                }
            } catch (Exception e) {
                // Ignorer les échecs d'accès sécurité
            }
            current = current.getSuperclass();
        }
    }
    
    /**
     * Résout un Identifier de Loot Table en objet LootTable réel sur le serveur de jeu, 
     * puis scanne cet objet de façon récursive pour en extraire tous les items.
     */
    private void resolveAndScanLootTable(ResourceLocation id, Set<Item> items, Set<Object> visited, int depth) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                MinecraftServer server = client.player.getServer();
                if (server != null) {
                    var registryManager = server.registryAccess();
                    var lootTableRegistry = registryManager.lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE);
                    if (lootTableRegistry != null) {
                        LootTable table = lootTableRegistry.get(id).map(net.minecraft.core.Holder::value).orElse(null);
                        if (table != null && table != LootTable.EMPTY) {
                            scanObjectRecursively(table, items, visited, depth);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignorer
        }
    }
    
    /**
     * Trouve les sources de loot tables pour un item donné.
     */
    public List<String> getSourcesForItem(Item item) {
        ensureCacheBuilt();
        
        List<String> sources = globalLootCache.get(item);
        if (sources != null && !sources.isEmpty()) {
            return sources;
        }
        
        // Fallback pour le mode multijoueur (integrated server null) ou les items non indexés
        return getFallbackSources(item);
    }
    
    /**
     * Formate un ID de loot table en nom lisible et plus immersif.
     */
    private String formatLootTableName(ResourceLocation id) {
        String path = id.getPath();
        String[] parts = path.split("/");
        if (parts.length > 1) {
            String category = parts[0];
            String name = parts[parts.length - 1].replace("_", " ");
            
            if (!name.isEmpty()) {
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            
            // Localisation et embellissement
            String categoryName = switch (category) {
                case "chests" -> "Coffre";
                case "entities" -> "Monstre";
                case "gameplay" -> "Activité";
                case "blocks" -> "Bloc";
                case "archaeology" -> "Fouilles";
                default -> category;
            };
            
            return name + " (" + categoryName + ")";
        }
        
        return path.replace("_", " ");
    }
    
    /**
     * Fournit une base de données de secours locale et localisée pour les items vanilla importants.
     * Cette base de données prend le relais en multijoueur ou lorsque le serveur n'est pas indexé.
     */
    private List<String> getFallbackSources(Item item) {
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFrench = lang != null && lang.toLowerCase().startsWith("fr");
        
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();
        
        String path = id.getPath();
        List<String> sources = new ArrayList<>();
        
        switch (path) {
            case "diamond":
                sources.add(isFrench ? "Coffres de structures (Bastions, Forteresses, Temples du Désert, Mineshafts)" : "Structure chests (Bastions, Fortresses, Desert Temples, Mineshafts)");
                sources.add(isFrench ? "Minerais de Diamant (Abysses Y < 0)" : "Diamond Ore (Deepslate Y < 0)");
                break;
            case "netherite_scrap":
            case "ancient_debris":
                sources.add(isFrench ? "Minerais de Débris Antiques (Nether, Y=8 à Y=22)" : "Ancient Debris Ore (Nether, Y=8 to Y=22)");
                break;
            case "netherite_ingot":
                sources.add(isFrench ? "Table de Smithing (Amélioration d'un outil en diamant)" : "Smithing Table (Upgrading diamond equipment)");
                sources.add(isFrench ? "Coffres de Bastions" : "Bastion Remnant chests");
                break;
            case "blaze_rod":
                sources.add(isFrench ? "Looté par les Blazes (Forteresses du Nether)" : "Dropped by Blazes (Nether Fortresses)");
                break;
            case "ender_pearl":
                sources.add(isFrench ? "Looté par les Endermen" : "Dropped by Endermen");
                sources.add(isFrench ? "Troc avec les Piglins (Or)" : "Bartering with Piglins (Gold)");
                sources.add(isFrench ? "Échange avec les Clercs villageois" : "Trading with Clerc villagers");
                break;
            case "slime_ball":
                sources.add(isFrench ? "Looté par les Slimes (Marécages, Chunks à Slime)" : "Dropped by Slimes (Swamps, Slime Chunks)");
                sources.add(isFrench ? "Échange avec le Marchand Ambulant" : "Trading with Wandering Trader");
                break;
            case "saddle":
                sources.add(isFrench ? "Coffres de structures (Donjons, Bastions, Forteresses)" : "Structure chests (Dungeons, Bastions, Fortresses)");
                sources.add(isFrench ? "Obtenu par la Pêche" : "Obtained by Fishing");
                break;
            case "name_tag":
                sources.add(isFrench ? "Coffres de structures, Pêche" : "Structure chests, Fishing");
                sources.add(isFrench ? "Échange avec les Bibliothécaires" : "Trading with Librarians");
                break;
            case "nether_star":
                sources.add(isFrench ? "Looté par le Wither Boss" : "Dropped by the Wither Boss");
                break;
            case "elytra":
                sources.add(isFrench ? "Cadre dans les Bateaux de l'End (Cités de l'End)" : "Item Frame in End Ships (End Cities)");
                break;
            case "totem_of_undying":
                sources.add(isFrench ? "Looté par les Évocateurs (Evokers - Raids / Manoirs)" : "Dropped by Evokers (Raids / Woodland Mansions)");
                break;
            case "heart_of_the_sea":
                sources.add(isFrench ? "Trésors Enfouis (Buried Treasure)" : "Buried Treasure chests");
                break;
            case "nautilus_shell":
                sources.add(isFrench ? "Looté par les Noyés (Drowned), Pêche" : "Dropped by Drowned, Fishing");
                sources.add(isFrench ? "Échange avec le Marchand Ambulant" : "Trading with Wandering Trader");
                break;
            case "sponge":
                sources.add(isFrench ? "Monuments Océaniques (Gardien Ancien / Salles d'éponges)" : "Ocean Monuments (Elder Guardian / Sponge Rooms)");
                break;
            case "wither_skeleton_skull":
                sources.add(isFrench ? "Looté par les Squelettes Wither (Forteresses)" : "Dropped by Wither Skeletons (Fortresses)");
                break;
            case "dragon_egg":
                sources.add(isFrench ? "Vaincre l'Ender Dragon (Haut du portail de retour)" : "Defeating the Ender Dragon (On top of exit portal)");
                break;
            case "trident":
                sources.add(isFrench ? "Looté par les Noyés (Drowned)" : "Dropped by Drowned");
                break;
            case "string":
                sources.add(isFrench ? "Looté par les Araignées, Toiles d'araignées" : "Dropped by Spiders, Cobwebs");
                break;
            case "gunpowder":
                sources.add(isFrench ? "Looté par les Creepers, Sorcières, Ghasts" : "Dropped by Creepers, Witches, Ghasts");
                break;
            case "bone":
                sources.add(isFrench ? "Looté par les Squelettes" : "Dropped by Skeletons");
                break;
            case "rotten_flesh":
                sources.add(isFrench ? "Looté par les Zombies" : "Dropped by Zombies");
                break;
            case "leather":
                sources.add(isFrench ? "Looté par les Vaches, Chevaux, Lamas" : "Dropped by Cows, Horses, Llamas");
                break;
            case "feather":
                sources.add(isFrench ? "Looté par les Poulets" : "Dropped by Chickens");
                break;
            case "ink_sac":
                sources.add(isFrench ? "Looté par les Poulpes" : "Dropped by Squids");
                break;
            case "glow_ink_sac":
                sources.add(isFrench ? "Looté par les Poulpes Luisants" : "Dropped by Glow Squids");
                break;
            case "prismarine_shard":
            case "prismarine_crystals":
                sources.add(isFrench ? "Looté par les Gardiens (Monuments Océaniques)" : "Dropped by Guardians (Ocean Monuments)");
                break;
            case "emerald":
                sources.add(isFrench ? "Échanges villageois, Minerais d'Émeraude (Montagnes)" : "Villager trades, Emerald Ore (Mountains)");
                break;
            case "echo_shard":
                sources.add(isFrench ? "Coffres des Cités Abyssales (Ancient City)" : "Ancient City chests");
                break;
            case "disc_fragment_5":
                sources.add(isFrench ? "Coffres des Cités Abyssales" : "Ancient City chests");
                break;
            case "disc_fragment_11":
            case "music_disc_11":
                sources.add(isFrench ? "Un squelette doit tuer un creeper" : "A skeleton must shoot and kill a creeper");
                break;
            case "music_disc_otherside":
                sources.add(isFrench ? "Coffres de Strongholds, Cités Abyssales" : "Stronghold or Ancient City chests");
                break;
            case "music_disc_relic":
                sources.add(isFrench ? "Fouilles de Ruines du Sentier (Trail Ruins)" : "Brushing Trail Ruins suspicious gravel");
                break;
            case "snort_pottery_sherd":
            case "prize_pottery_sherd":
            case "skull_pottery_sherd":
            case "anger_pottery_sherd":
            case "archer_pottery_sherd":
            case "arms_up_pottery_sherd":
            case "blade_pottery_sherd":
            case "brewer_pottery_sherd":
            case "burn_pottery_sherd":
            case "danger_pottery_sherd":
            case "explorer_pottery_sherd":
            case "friend_pottery_sherd":
            case "heart_pottery_sherd":
            case "heartbreak_pottery_sherd":
            case "howl_pottery_sherd":
            case "miner_pottery_sherd":
            case "mourner_pottery_sherd":
            case "plenty_pottery_sherd":
            case "sheaf_pottery_sherd":
            case "flow_pottery_sherd":
            case "guster_pottery_sherd":
            case "scrape_pottery_sherd":
                sources.add(isFrench ? "Fouilles des Chambres des Épreuves (Pots Décorés / Vases)" : "Trial Chambers excavations (Decorated Pots / Vases)");
                break;
            case "shelter_pottery_sherd":
                sources.add(isFrench ? "Fouilles archéologiques (Gravel/Sable Suspect)" : "Archaeological excavations (Suspicious Gravel/Sand)");
                break;
            case "enchanted_book":
                sources.add(isFrench ? "Coffres de structures (Donjons, Strongholds, Temples, Cités)" : "Structure chests (Dungeons, Strongholds, Temples, Cities)");
                sources.add(isFrench ? "Échanges villageois (Bibliothécaires)" : "Villager trades (Librarians)");
                sources.add(isFrench ? "Pêche, Table d'enchantement" : "Fishing, Enchanting table");
                break;
            case "oak_sapling":
            case "spruce_sapling":
            case "birch_sapling":
            case "jungle_sapling":
            case "acacia_sapling":
            case "dark_oak_sapling":
            case "cherry_sapling":
            case "mangrove_propagule":
                sources.add(isFrench ? "Obtenu en cassant les feuilles de l'arbre correspondant" : "Obtained by breaking leaves of the corresponding tree");
                break;
            case "oak_log":
            case "spruce_log":
            case "birch_log":
            case "jungle_log":
            case "acacia_log":
            case "dark_oak_log":
            case "mangrove_log":
            case "cherry_log":
                sources.add(isFrench ? "Couper le tronc de l'arbre correspondant" : "Chop down the trunk of the corresponding tree");
                break;
            case "heavy_core":
                sources.add(isFrench ? "Récompense de Coffre-fort des Chambres des Épreuves (Trial Vault)" : "Ominous Trial Vault reward (Trial Chambers)");
                break;
            case "breeze_rod":
                sources.add(isFrench ? "Looté par les Breezes (Chambres des Épreuves)" : "Dropped by Breezes (Trial Chambers)");
                break;
        }
        
        return sources;
    }
    
    public List<String> getWorldLocationsForItem(Item item) {
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFrench = lang != null && lang.toLowerCase().startsWith("fr");
        
        Set<String> locations = new LinkedHashSet<>();
        
        // 1. Dynamic scan
        ensureCacheBuilt();
        Set<ResourceLocation> ids = itemToLootIds.get(item);
        if (ids != null) {
            for (ResourceLocation id : ids) {
                String loc = parseLocationFromLootId(id, isFrench);
                if (loc != null) {
                    locations.add(loc);
                }
            }
        }
        
        // 2. Fallbacks/Overrides
        List<String> fallbacks = getFallbackWorldLocations(item, isFrench);
        locations.addAll(fallbacks);
        
        // If still empty, return Everywhere / Not specified
        if (locations.isEmpty()) {
            locations.add(isFrench ? "Partout dans le monde / Non spécifié" : "Everywhere / Not specified");
        }
        
        return new ArrayList<>(locations);
    }
    
    private String parseLocationFromLootId(ResourceLocation id, boolean isFrench) {
        String path = id.getPath();
        if (path.contains("desert_pyramid")) {
            return isFrench ? "Temple du Désert / Desert Pyramid" : "Desert Pyramid / Temple du Désert";
        }
        if (path.contains("desert_well")) {
            return isFrench ? "Puits du Désert / Desert Well" : "Desert Well / Puits du Désert";
        }
        if (path.contains("mineshaft") || path.contains("abandoned_mineshaft")) {
            return isFrench ? "Mines Abandonnées / Abandoned Mineshaft" : "Abandoned Mineshaft / Mines Abandonnées";
        }
        if (path.contains("jungle_temple") || path.contains("jungle_pyramid")) {
            return isFrench ? "Temple de la Jungle / Jungle Temple" : "Jungle Temple / Temple de la Jungle";
        }
        if (path.contains("stronghold")) {
            return isFrench ? "Forteresse Souterraine / Stronghold" : "Stronghold / Forteresse Souterraine";
        }
        if (path.contains("bastion")) {
            return isFrench ? "Vestiges de Bastion / Bastion Remnant" : "Bastion Remnant / Vestiges de Bastion";
        }
        if (path.contains("ancient_city")) {
            return isFrench ? "Cité Abyssale / Ancient City" : "Ancient City / Cité Abyssale";
        }
        if (path.contains("end_city")) {
            return isFrench ? "Cité de l'End / End City" : "End City / Cité de l'End";
        }
        if (path.contains("buried_treasure")) {
            return isFrench ? "Trésor Enfoui / Buried Treasure" : "Buried Treasure / Trésor Enfoui";
        }
        if (path.contains("simple_dungeon") || path.contains("dungeon")) {
            return isFrench ? "Donjon / Dungeon" : "Dungeon / Donjon";
        }
        if (path.contains("woodland_mansion") || path.contains("mansion")) {
            return isFrench ? "Manoir en Forêt / Woodland Mansion" : "Woodland Mansion / Manoir en Forêt";
        }
        if (path.contains("shipwreck")) {
            return isFrench ? "Épave de Bateau / Shipwreck" : "Shipwreck / Épave de Bateau";
        }
        if (path.contains("pillager_outpost") || path.contains("outpost")) {
            return isFrench ? "Avant-poste de Pillards / Pillager Outpost" : "Pillager Outpost / Avant-poste de Pillards";
        }
        if (path.contains("ruined_portal")) {
            return isFrench ? "Portail Ruiné / Ruined Portal" : "Ruined Portal / Portail Ruiné";
        }
        if (path.contains("trail_ruins") || path.contains("trail_ruin")) {
            return isFrench ? "Ruines du Sentier / Trail Ruins" : "Trail Ruins / Ruines du Sentier";
        }
        if (path.contains("ocean_ruin")) {
            return isFrench ? "Ruines Océaniques / Ocean Ruins" : "Ocean Ruins / Ruines Océaniques";
        }
        if (path.contains("trial_chambers") || path.contains("trial_chamber") || path.contains("trial/")) {
            return isFrench ? "Chambres des Épreuves / Trial Chambers" : "Trial Chambers / Chambres des Épreuves";
        }
        if (path.contains("fortress") || path.contains("nether_bridge")) {
            return isFrench ? "Forteresse du Nether / Nether Fortress" : "Nether Fortress / Forteresse du Nether";
        }
        if (path.contains("monument") || path.contains("ocean_monument")) {
            return isFrench ? "Monument Océanique / Ocean Monument" : "Ocean Monument / Monument Océanique";
        }
        if (path.contains("village")) {
            return isFrench ? "Village" : "Village";
        }
        if (path.contains("swamp") || path.contains("witch")) {
            return isFrench ? "Marais / Swamp" : "Swamp / Marais";
        }
        if (path.contains("cherry_grove")) {
            return isFrench ? "Forêt de Cerisiers / Cherry Grove" : "Cherry Grove / Forêt de Cerisiers";
        }
        return null;
    }
    
    private List<String> getFallbackWorldLocations(Item item, boolean isFrench) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();
        String path = id.getPath();
        
        List<String> locs = new ArrayList<>();
        if (path.equals("diamond") || path.equals("diamond_ore") || path.equals("deepslate_diamond_ore")) {
            locs.add(isFrench ? "Sous-sol (Abysses Y < 0)" : "Underground (Deepslate Y < 0)");
            locs.add(isFrench ? "Temples, Mines, Bastions (Coffres)" : "Temples, Mineshafts, Bastions (Chests)");
        } else if (path.equals("ancient_debris") || path.equals("netherite_scrap")) {
            locs.add(isFrench ? "Nether (Y: 8 à 22)" : "The Nether (Y: 8 to 22)");
        } else if (path.equals("netherite_ingot")) {
            locs.add(isFrench ? "Vestiges de Bastion (Treasure chests)" : "Bastion Remnants (Treasure chests)");
        } else if (path.equals("blaze_rod")) {
            locs.add(isFrench ? "Forteresses du Nether (Blazes)" : "Nether Fortresses (Blazes)");
        } else if (path.equals("ender_pearl")) {
            locs.add(isFrench ? "Forêt Distordue (Nether), L'End" : "Warped Forest (Nether), The End");
        } else if (path.equals("elytra")) {
            locs.add(isFrench ? "Bateaux de l'End (Cités de l'End)" : "End Ships (End Cities)");
        } else if (path.equals("totem_of_undying")) {
            locs.add(isFrench ? "Manoirs en Forêt (Évocateurs), Raids" : "Woodland Mansions (Evokers), Raids");
        } else if (path.equals("heart_of_the_sea")) {
            locs.add(isFrench ? "Trésors Enfouis" : "Buried Treasures");
        } else if (path.equals("heavy_core") || path.equals("breeze_rod")) {
            locs.add(isFrench ? "Chambres des Épreuves" : "Trial Chambers");
        } else if (path.contains("pottery_sherd")) {
            locs.add(isFrench ? "Ruines du Sentier, Chambres des Épreuves, Temples (Archéologie)" : "Trail Ruins, Trial Chambers, Temples (Archaeology)");
        } else if (path.contains("template")) {
            if (path.contains("netherite")) {
                locs.add(isFrench ? "Bastions (Salles des Trésors)" : "Bastions (Treasure Rooms)");
            } else if (path.contains("tide")) {
                locs.add(isFrench ? "Monuments Océaniques" : "Ocean Monuments");
            } else if (path.contains("sentry")) {
                locs.add(isFrench ? "Avant-postes de Pillards" : "Pillager Outposts");
            } else if (path.contains("coast")) {
                locs.add(isFrench ? "Épaves de Bateaux" : "Shipwrecks");
            } else if (path.contains("wild")) {
                locs.add(isFrench ? "Temples de la Jungle" : "Jungle Temples");
            } else if (path.contains("ward") || path.contains("silence")) {
                locs.add(isFrench ? "Cités Abyssales" : "Ancient Cities");
            } else if (path.contains("spire")) {
                locs.add(isFrench ? "Cités de l'End" : "End Cities");
            } else {
                locs.add(isFrench ? "Temples, Forts, Chambres des Épreuves" : "Temples, Strongholds, Trial Chambers");
            }
        } else if (path.contains("cherry")) {
            locs.add(isFrench ? "Biomes Forêt de Cerisiers (Cherry Grove)" : "Cherry Grove biomes");
        }
        return locs;
    }
    
    /**
     * Vide le cache des sources de loot tables.
     * Appelé lors du changement de monde ou du déchargement.
     */
    public void clearCache() {
        globalLootCache.clear();
        itemToLootIds.clear();
        isCacheBuilt = false;
    }
}
