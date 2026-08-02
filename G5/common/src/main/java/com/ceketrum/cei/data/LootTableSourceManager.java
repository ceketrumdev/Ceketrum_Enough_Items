package com.ceketrum.cei.data;

import com.ceketrum.cei.i18n.CeiText;
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

    // Index inverse : Item -> identifiants des tables qui le produisent.
    // On ne retient QUE des ResourceLocation, deja crees par le registre et donc
    // partages : l'index ne fait qu'y renvoyer. Les libelles lisibles etaient
    // auparavant stockes pour les ~1700 items indexes alors que le joueur n'en
    // consulte qu'une poignee ; ils sont maintenant formates a la demande et
    // memorises dans les deux caches ci-dessous.
    private final Map<Item, List<ResourceLocation>> itemToLootIds = new HashMap<>();
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

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        // En multijoueur, on n'a pas accès à l'integrated server. On skip le scan de loot tables pour éviter les spams/lags
        if (!client.isLocalServer()) {
            isCacheBuilt = true;
            LOGGER.info("[LOOT] Client en multijoueur, skip du scan global de loot tables (fallbacks locaux activés).");
            return;
        }

        // client.player.getServer() delegue a ClientLevel.getServer(), qui
        // renvoie null cote client : le scan echouait donc systematiquement et
        // se desactivait au bout de MAX_ATTEMPTS. getSingleplayerServer() est
        // le bon accesseur, deja utilise par G6 et G7.
        MinecraftServer server = client.getSingleplayerServer();
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

            // Itérer sur toutes les loot tables enregistrées (vanilla et moddées)
            for (var key : lootTableRegistry.registryKeySet()) {
                LootTable lootTable = lootTableRegistry.get(key).map(net.minecraft.core.Holder::value).orElse(null);
                ResourceLocation id = key.location();

                if (lootTable != null && lootTable != LootTable.EMPTY) {
                    scannedTables++;
                    visited.clear();
                    itemsInTable.clear();
                    scanObjectRecursively(lootTable, itemsInTable, visited, 0);

                    for (Item item : itemsInTable) {
                        // ArrayList et non LinkedHashSet : quelques entrees par
                        // item, un contains() lineaire coute moins cher qu'une
                        // table de hachage -- et bien moins en memoire.
                        List<ResourceLocation> ids =
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
    private void resolveAndScanLootTable(ResourceLocation id, Set<Item> items, Set<Object> visited, int depth) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                MinecraftServer server = client.getSingleplayerServer();
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
        dropMemoIfLanguageChanged();

        // Cette methode est appelee depuis le rendu : sans memoire, chaque
        // frame reformatait les libelles ou reconstruisait la liste de secours.
        List<String> memo = sourceNameCache.get(item);
        if (memo != null) {
            return memo;
        }

        List<String> sources;
        List<ResourceLocation> ids = itemToLootIds.get(item);
        if (ids != null && !ids.isEmpty()) {
            sources = new ArrayList<>(ids.size());
            for (ResourceLocation id : ids) {
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
                case "chests" -> CeiText.t("cei.loot.category.chests");
                case "entities" -> CeiText.t("cei.loot.category.entities");
                case "gameplay" -> CeiText.t("cei.loot.category.gameplay");
                case "blocks" -> CeiText.t("cei.loot.category.blocks");
                case "archaeology" -> CeiText.t("cei.loot.category.archaeology");
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

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();

        String path = id.getPath();
        List<String> sources = new ArrayList<>();

        switch (path) {
            case "diamond":
                sources.add(CeiText.t("cei.loot.src.diamond_chests"));
                sources.add(CeiText.t("cei.loot.src.diamond_ore"));
                break;
            case "netherite_scrap":
            case "ancient_debris":
                sources.add(CeiText.t("cei.loot.src.ancient_debris_ore"));
                break;
            case "netherite_ingot":
                sources.add(CeiText.t("cei.loot.src.smithing_netherite"));
                sources.add(CeiText.t("cei.loot.src.bastion_chests"));
                break;
            case "blaze_rod":
                sources.add(CeiText.t("cei.loot.src.blaze_drop"));
                break;
            case "ender_pearl":
                sources.add(CeiText.t("cei.loot.src.enderman_drop"));
                sources.add(CeiText.t("cei.loot.src.piglin_barter"));
                sources.add(CeiText.t("cei.loot.src.cleric_trade"));
                break;
            case "slime_ball":
                sources.add(CeiText.t("cei.loot.src.slime_drop"));
                sources.add(CeiText.t("cei.loot.src.wandering_trader"));
                break;
            case "saddle":
                sources.add(CeiText.t("cei.loot.src.saddle_chests"));
                sources.add(CeiText.t("cei.loot.src.fishing"));
                break;
            case "name_tag":
                sources.add(CeiText.t("cei.loot.src.nametag_chests"));
                sources.add(CeiText.t("cei.loot.src.librarian_trade"));
                break;
            case "nether_star":
                sources.add(CeiText.t("cei.loot.src.wither_drop"));
                break;
            case "elytra":
                sources.add(CeiText.t("cei.loot.src.elytra_frame"));
                break;
            case "totem_of_undying":
                sources.add(CeiText.t("cei.loot.src.evoker_drop"));
                break;
            case "heart_of_the_sea":
                sources.add(CeiText.t("cei.loot.src.buried_treasure_chests"));
                break;
            case "nautilus_shell":
                sources.add(CeiText.t("cei.loot.src.drowned_fishing"));
                sources.add(CeiText.t("cei.loot.src.wandering_trader"));
                break;
            case "sponge":
                sources.add(CeiText.t("cei.loot.src.sponge_room"));
                break;
            case "wither_skeleton_skull":
                sources.add(CeiText.t("cei.loot.src.wither_skeleton_drop"));
                break;
            case "dragon_egg":
                sources.add(CeiText.t("cei.loot.src.dragon_egg"));
                break;
            case "trident":
                sources.add(CeiText.t("cei.loot.src.drowned_drop"));
                break;
            case "string":
                sources.add(CeiText.t("cei.loot.src.spider_drop"));
                break;
            case "gunpowder":
                sources.add(CeiText.t("cei.loot.src.creeper_drop"));
                break;
            case "bone":
                sources.add(CeiText.t("cei.loot.src.skeleton_drop"));
                break;
            case "rotten_flesh":
                sources.add(CeiText.t("cei.loot.src.zombie_drop"));
                break;
            case "leather":
                sources.add(CeiText.t("cei.loot.src.cow_drop"));
                break;
            case "feather":
                sources.add(CeiText.t("cei.loot.src.chicken_drop"));
                break;
            case "ink_sac":
                sources.add(CeiText.t("cei.loot.src.squid_drop"));
                break;
            case "glow_ink_sac":
                sources.add(CeiText.t("cei.loot.src.glow_squid_drop"));
                break;
            case "prismarine_shard":
            case "prismarine_crystals":
                sources.add(CeiText.t("cei.loot.src.guardian_drop"));
                break;
            case "emerald":
                sources.add(CeiText.t("cei.loot.src.emerald_trade"));
                break;
            case "echo_shard":
                sources.add(CeiText.t("cei.loot.src.echo_shard"));
                break;
            case "disc_fragment_5":
                sources.add(CeiText.t("cei.loot.src.ancient_city_chests"));
                break;
            case "disc_fragment_11":
            case "music_disc_11":
                sources.add(CeiText.t("cei.loot.src.disc_11"));
                break;
            case "music_disc_otherside":
                sources.add(CeiText.t("cei.loot.src.disc_otherside"));
                break;
            case "music_disc_relic":
                sources.add(CeiText.t("cei.loot.src.disc_relic"));
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
                sources.add(CeiText.t("cei.loot.src.sherd_trial"));
                break;
            case "shelter_pottery_sherd":
                sources.add(CeiText.t("cei.loot.src.sherd_archaeology"));
                break;
            case "enchanted_book":
                sources.add(CeiText.t("cei.loot.src.book_chests"));
                sources.add(CeiText.t("cei.loot.src.book_trade"));
                sources.add(CeiText.t("cei.loot.src.book_fishing"));
                break;
            case "oak_sapling":
            case "spruce_sapling":
            case "birch_sapling":
            case "jungle_sapling":
            case "acacia_sapling":
            case "dark_oak_sapling":
            case "cherry_sapling":
            case "mangrove_propagule":
                sources.add(CeiText.t("cei.loot.src.sapling_leaves"));
                break;
            case "oak_log":
            case "spruce_log":
            case "birch_log":
            case "jungle_log":
            case "acacia_log":
            case "dark_oak_log":
            case "mangrove_log":
            case "cherry_log":
                sources.add(CeiText.t("cei.loot.src.log_chop"));
                break;
            case "heavy_core":
                sources.add(CeiText.t("cei.loot.src.heavy_core_vault"));
                break;
            case "breeze_rod":
                sources.add(CeiText.t("cei.loot.src.breeze_drop"));
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


        Set<String> locations = new LinkedHashSet<>();

        // 1. Dynamic scan
        List<ResourceLocation> ids = itemToLootIds.get(item);
        if (ids != null) {
            for (ResourceLocation id : ids) {
                String loc = parseLocationFromLootId(id);
                if (loc != null) {
                    locations.add(loc);
                }
            }
        }

        // 2. Fallbacks/Overrides
        List<String> fallbacks = getFallbackWorldLocations(item);
        locations.addAll(fallbacks);

        // If still empty, return Everywhere / Not specified
        if (locations.isEmpty()) {
            locations.add(CeiText.t("cei.loot.where.unspecified"));
        }

        List<String> result = new ArrayList<>(locations);
        worldLocationCache.put(item, result);
        return result;
    }

    private String parseLocationFromLootId(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("desert_pyramid")) {
            return CeiText.t("cei.loot.struct.desert_pyramid");
        }
        if (path.contains("desert_well")) {
            return CeiText.t("cei.loot.struct.desert_well");
        }
        if (path.contains("mineshaft") || path.contains("abandoned_mineshaft")) {
            return CeiText.t("cei.loot.struct.mineshaft");
        }
        if (path.contains("jungle_temple") || path.contains("jungle_pyramid")) {
            return CeiText.t("cei.loot.struct.jungle_temple");
        }
        if (path.contains("stronghold")) {
            return CeiText.t("cei.loot.struct.stronghold");
        }
        if (path.contains("bastion")) {
            return CeiText.t("cei.loot.struct.bastion");
        }
        if (path.contains("ancient_city")) {
            return CeiText.t("cei.loot.struct.ancient_city");
        }
        if (path.contains("end_city")) {
            return CeiText.t("cei.loot.struct.end_city");
        }
        if (path.contains("buried_treasure")) {
            return CeiText.t("cei.loot.struct.buried_treasure");
        }
        if (path.contains("simple_dungeon") || path.contains("dungeon")) {
            return CeiText.t("cei.loot.struct.dungeon");
        }
        if (path.contains("woodland_mansion") || path.contains("mansion")) {
            return CeiText.t("cei.loot.struct.woodland_mansion");
        }
        if (path.contains("shipwreck")) {
            return CeiText.t("cei.loot.struct.shipwreck");
        }
        if (path.contains("pillager_outpost") || path.contains("outpost")) {
            return CeiText.t("cei.loot.struct.pillager_outpost");
        }
        if (path.contains("ruined_portal")) {
            return CeiText.t("cei.loot.struct.ruined_portal");
        }
        if (path.contains("trail_ruins") || path.contains("trail_ruin")) {
            return CeiText.t("cei.loot.struct.trail_ruins");
        }
        if (path.contains("ocean_ruin")) {
            return CeiText.t("cei.loot.struct.ocean_ruin");
        }
        if (path.contains("trial_chambers") || path.contains("trial_chamber") || path.contains("trial/")) {
            return CeiText.t("cei.loot.struct.trial_chambers");
        }
        if (path.contains("fortress") || path.contains("nether_bridge")) {
            return CeiText.t("cei.loot.struct.fortress");
        }
        if (path.contains("monument") || path.contains("ocean_monument")) {
            return CeiText.t("cei.loot.struct.monument");
        }
        if (path.contains("village")) {
            return CeiText.t("cei.loot.struct.village");
        }
        if (path.contains("swamp") || path.contains("witch")) {
            return CeiText.t("cei.loot.struct.swamp");
        }
        if (path.contains("cherry_grove")) {
            return CeiText.t("cei.loot.struct.cherry_grove");
        }
        return null;
    }

    private List<String> getFallbackWorldLocations(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Collections.emptyList();
        String path = id.getPath();

        List<String> locs = new ArrayList<>();
        if (path.equals("diamond") || path.equals("diamond_ore") || path.equals("deepslate_diamond_ore")) {
            locs.add(CeiText.t("cei.loot.where.underground"));
            locs.add(CeiText.t("cei.loot.where.structure_chests"));
        } else if (path.equals("ancient_debris") || path.equals("netherite_scrap")) {
            locs.add(CeiText.t("cei.loot.where.nether_debris"));
        } else if (path.equals("netherite_ingot")) {
            locs.add(CeiText.t("cei.loot.where.bastion_treasure"));
        } else if (path.equals("blaze_rod")) {
            locs.add(CeiText.t("cei.loot.where.nether_fortress_blaze"));
        } else if (path.equals("ender_pearl")) {
            locs.add(CeiText.t("cei.loot.where.warped_forest_end"));
        } else if (path.equals("elytra")) {
            locs.add(CeiText.t("cei.loot.where.end_ships"));
        } else if (path.equals("totem_of_undying")) {
            locs.add(CeiText.t("cei.loot.where.mansions_raids"));
        } else if (path.equals("heart_of_the_sea")) {
            locs.add(CeiText.t("cei.loot.where.buried_treasures"));
        } else if (path.equals("heavy_core") || path.equals("breeze_rod")) {
            locs.add(CeiText.t("cei.loot.where.trial_chambers"));
        } else if (path.contains("pottery_sherd")) {
            locs.add(CeiText.t("cei.loot.where.archaeology"));
        } else if (path.contains("template")) {
            if (path.contains("netherite")) {
                locs.add(CeiText.t("cei.loot.where.bastion_rooms"));
            } else if (path.contains("tide")) {
                locs.add(CeiText.t("cei.loot.where.ocean_monuments"));
            } else if (path.contains("sentry")) {
                locs.add(CeiText.t("cei.loot.where.pillager_outposts"));
            } else if (path.contains("coast")) {
                locs.add(CeiText.t("cei.loot.where.shipwrecks"));
            } else if (path.contains("wild")) {
                locs.add(CeiText.t("cei.loot.where.jungle_temples"));
            } else if (path.contains("ward") || path.contains("silence")) {
                locs.add(CeiText.t("cei.loot.where.ancient_cities"));
            } else if (path.contains("spire")) {
                locs.add(CeiText.t("cei.loot.where.end_cities"));
            } else {
                locs.add(CeiText.t("cei.loot.where.temples_strongholds"));
            }
        } else if (path.contains("cherry")) {
            locs.add(CeiText.t("cei.loot.where.cherry_biomes"));
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
