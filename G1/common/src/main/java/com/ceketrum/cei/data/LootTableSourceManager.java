package com.ceketrum.cei.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

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

    // Index inverse : Item -> identifiants des tables qui le produisent.
    // On ne retient QUE des Identifier, deja crees par le registre et donc
    // partages : l'index ne fait qu'y renvoyer. Les libelles lisibles etaient
    // auparavant stockes pour les ~1700 items indexes alors que le joueur n'en
    // consulte qu'une poignee ; ils sont maintenant formates a la demande et
    // memorises dans les deux caches ci-dessous.
    private final Map<Item, List<Identifier>> itemToLootIds = new HashMap<>();
    private final Map<Item, List<String>> sourceNameCache = new HashMap<>();
    private final Map<Item, List<String>> worldLocationCache = new HashMap<>();

    // Les libelles dependent de la langue : on les jette si elle change.
    private String memoLanguage;

    /**
     * Cache des champs a explorer, par classe.
     *
     * getDeclaredFields() ne renvoie pas le tableau interne de la classe : il
     * en RECOPIE le tableau et chaque objet Field, a chaque appel. L'ancien
     * scan l'appelait pour chaque objet rencontre et pour chaque classe de sa
     * hierarchie -- de l'ordre du million de Field jetables. Une classe donnee
     * n'est desormais analysee qu'une seule fois.
     */
    private static final Map<Class<?>, java.lang.reflect.Field[]> FIELD_CACHE = new HashMap<>();
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        // En multijoueur, on n'a pas accès à l'integrated server. On skip le scan de loot tables pour éviter les spams/lags
        if (!client.isInSingleplayer()) {
            isCacheBuilt = true;
            LOGGER.info("[LOOT] Client en multijoueur, skip du scan global de loot tables (fallbacks locaux activés).");
            return;
        }

        // client.player.getServer() delegue a ClientWorld.getServer(), qui
        // renvoie null cote client : le scan echouait donc systematiquement et
        // se desactivait au bout de MAX_ATTEMPTS.
        // En Yarn le serveur integre s'obtient par MinecraftClient.getServer()
        // -- getSingleplayerServer() est le nom Mojang, valable a partir de G3.
        MinecraftServer server = client.getServer();
        if (server == null) {
            attempts++;
            if (attempts >= MAX_ATTEMPTS) {
                isCacheBuilt = true;
                LOGGER.warn("[LOOT] Nombre maximum de tentatives d'accès au serveur atteint en solo. Skip.");
            }
            return;
        }

        try {
            var lootManager = server.getLootManager();
            if (lootManager == null) return;

            LOGGER.info("[LOOT] Début du scan global des loot tables...");
            long startTime = System.currentTimeMillis();

            itemToLootIds.clear();
            sourceNameCache.clear();
            worldLocationCache.clear();
            int scannedTables = 0;

            // Deux tampons pour tout le scan au lieu de deux par table.
            // L'ensemble anti-cycle est un ensemble d'IDENTITE : il ne declenche
            // plus hashCode()/equals() sur des objets Minecraft (couteux, et
            // faux ici -- deux entrees egales mais distinctes s'eliminaient).
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Item> itemsInTable = new HashSet<>();

            Collection<Identifier> tableIds = lootManager.getIds(net.minecraft.loot.LootDataType.LOOT_TABLES);
            for (Identifier id : tableIds) {
                LootTable lootTable = (LootTable) lootManager.getElement(new net.minecraft.loot.LootDataKey<>(net.minecraft.loot.LootDataType.LOOT_TABLES, id));
                if (lootTable != null && lootTable != LootTable.EMPTY) {
                    scannedTables++;
                    visited.clear();
                    itemsInTable.clear();
                    scanObjectRecursively(lootTable, itemsInTable, visited, 0);

                    for (Item item : itemsInTable) {
                        // ArrayList et non LinkedHashSet : quelques entrees par
                        // item, un contains() lineaire coute moins cher qu'une
                        // table de hachage -- et bien moins en memoire.
                        List<Identifier> ids =
                                itemToLootIds.computeIfAbsent(item, k -> new ArrayList<>(2));
                        if (!ids.contains(id)) {
                            ids.add(id);
                        }
                    }
                }
            }

            isCacheBuilt = true;
            LOGGER.info("[LOOT] Scan global terminé en {} ms. {} loot tables scannées, {} items indexés.",
                        System.currentTimeMillis() - startTime, scannedTables, itemToLootIds.size());

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
            scanObjectRecursively(lootTable, items,
                    Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }
        return items;
    }

    private void collectItemsFromEntry(Object entry, Set<Item> items) {
        if (entry == null) return;
        scanObjectRecursively(entry, items,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
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
        if (obj instanceof net.minecraft.registry.entry.RegistryEntry<?> entry) {
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
                    if (idObj instanceof Identifier id) {
                        if (id.getPath().contains("loot_table") || className.contains("LootTable")) {
                            resolveAndScanLootTable(id, items, visited, depth + 1);
                        }
                    }
                }
            } catch (Exception e) {}
            return;
        }

        // 4. Résolution de TagKey d'items
        if (obj instanceof net.minecraft.registry.tag.TagKey<?> tagKey) {
            try {
                if (tagKey.registry().getValue().getPath().equals("item")) {
                    @SuppressWarnings("unchecked")
                    var itemTagKey = (net.minecraft.registry.tag.TagKey<Item>) tagKey;
                    var optionalList = Registries.ITEM.getEntryList(itemTagKey);
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
        if (obj instanceof Identifier id) {
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

        // 8 bis. Optional. Indispensable depuis que le parcours de champs
        // n'entre plus dans les classes du paquet java.* : sans ce cas, les
        // valeurs facultatives des loot tables deviendraient invisibles.
        if (obj instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                scanObjectRecursively(optional.get(), items, visited, depth + 1);
            }
            return;
        }

        // 9. Parcours par réflexion récursive des champs de l'objet.
        //    La liste des champs utiles est calculee une fois par classe (voir
        //    FIELD_CACHE) : c'est la correction qui supprime l'essentiel des
        //    108 Mio mesures sur G4.
        for (java.lang.reflect.Field field : scannableFields(clazz)) {
            try {
                Object fieldValue = field.get(obj);
                if (fieldValue != null) {
                    scanObjectRecursively(fieldValue, items, visited, depth + 1);
                }
            } catch (Exception e) {
                // Ignorer les échecs d'accès sécurité
            }
        }
    }

    /**
     * Champs d'instance d'une classe susceptibles de mener a un item, toutes
     * superclasses confondues, rendus accessibles une fois pour toutes.
     */
    private static synchronized java.lang.reflect.Field[] scannableFields(Class<?> clazz) {
        java.lang.reflect.Field[] cached = FIELD_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        List<java.lang.reflect.Field> keep = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            String pkg = current.getName();
            // Les conteneurs de la bibliotheque standard sont deja traites plus
            // haut (cas 6 a 8 bis) : descendre dans leurs entrailles ne
            // rapporte rien et coute cher.
            if (pkg.startsWith("java.") || pkg.startsWith("jdk.") || pkg.startsWith("sun.")) {
                break;
            }
            try {
                for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (!mayHoldItems(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                    } catch (Exception e) {
                        continue;
                    }
                    keep.add(field);
                }
            } catch (Exception e) {
                // Module ferme ou verificateur d'acces : on passe.
            }
            current = current.getSuperclass();
        }
        java.lang.reflect.Field[] result = keep.toArray(new java.lang.reflect.Field[0]);
        FIELD_CACHE.put(clazz, result);
        return result;
    }

    /**
     * Un champ de ce type peut-il, meme indirectement, contenir un item ?
     * Ecarte les primitifs et les valeurs scalaires : ils representent la
     * majorite des champs d'une loot table (poids, rangs, bornes, drapeaux).
     */
    private static boolean mayHoldItems(Class<?> type) {
        if (type.isPrimitive()) {
            return false;
        }
        if (type.isArray()) {
            return mayHoldItems(type.getComponentType()) || type.getComponentType() == Object.class;
        }
        if (type == String.class || type == Class.class || type == Boolean.class
                || type == Character.class || Number.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type)) {
            return false;
        }
        return true;
    }

    /**
     * Résout un Identifier de Loot Table en objet LootTable réel sur le serveur de jeu,
     * puis scanne cet objet de façon récursive pour en extraire tous les items.
     */
    private void resolveAndScanLootTable(Identifier id, Set<Item> items, Set<Object> visited, int depth) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                MinecraftServer server = client.getServer();
                if (server != null) {
                    var lootManager = server.getLootManager();
                    if (lootManager != null) {
                        LootTable table = (LootTable) lootManager.getElement(new net.minecraft.loot.LootDataKey<>(net.minecraft.loot.LootDataType.LOOT_TABLES, id));
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
        dropMemoIfLanguageChanged();

        // Cette methode est appelee depuis le rendu : sans memoire, chaque
        // frame reformatait les libelles ou reconstruisait la liste de secours.
        List<String> memo = sourceNameCache.get(item);
        if (memo != null) {
            return memo;
        }

        List<String> sources;
        List<Identifier> ids = itemToLootIds.get(item);
        if (ids != null && !ids.isEmpty()) {
            sources = new ArrayList<>(ids.size());
            for (Identifier id : ids) {
                String name = formatLootTableName(id);
                if (!sources.contains(name)) {
                    sources.add(name);
                }
            }
        } else {
            // Fallback pour le mode multijoueur (integrated server null) ou les items non indexés
            sources = getFallbackSources(item);
        }
        sourceNameCache.put(item, sources);
        return sources;
    }

    /**
     * Les libelles memorises sont traduits : ils ne valent que pour la langue
     * active. Un changement de langue les invalide, l'index d'identifiants non.
     */
    private void dropMemoIfLanguageChanged() {
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        if (memoLanguage == null || !memoLanguage.equals(lang)) {
            memoLanguage = lang;
            sourceNameCache.clear();
            worldLocationCache.clear();
        }
    }

    /**
     * Formate un ID de loot table en nom lisible et plus immersif.
     */
    private String formatLootTableName(Identifier id) {
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

        Identifier id = Registries.ITEM.getId(item);
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
        ensureCacheBuilt();
        dropMemoIfLanguageChanged();

        List<String> memo = worldLocationCache.get(item);
        if (memo != null) {
            return memo;
        }

        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFrench = lang != null && lang.toLowerCase().startsWith("fr");

        Set<String> locations = new LinkedHashSet<>();

        // 1. Dynamic scan
        List<Identifier> ids = itemToLootIds.get(item);
        if (ids != null) {
            for (Identifier id : ids) {
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

        List<String> result = new ArrayList<>(locations);
        worldLocationCache.put(item, result);
        return result;
    }

    private String parseLocationFromLootId(Identifier id, boolean isFrench) {
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
        Identifier id = Registries.ITEM.getId(item);
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
        itemToLootIds.clear();
        sourceNameCache.clear();
        worldLocationCache.clear();
        memoLanguage = null;
        isCacheBuilt = false;
        attempts = 0;
    }
}

