package com.ceketrum.cei.gui.module.cei.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

/**
 * Index item -> tags, construit par REFLEXION.
 *
 * Il n'existe aucun accesseur commun aux sept lignees. Releve dans les jars :
 * ItemStack.getTags() existe en 1.21.11 et pas en 26.2, ou la pile expose
 * typeHolder() la ou 1.21.11 expose getItemHolder() ; en Yarn les deux noms
 * different encore.
 *
 * Nommer l'un d'eux en dur casserait la compilation de plusieurs groupes. Ici
 * rien n'est nomme : on essaie des candidats a l'execution. Si aucun ne
 * repond, le prefixe #tag ne trouve simplement rien -- la fonction est inerte,
 * le reste du mod intact, et deboguer revient a ajouter un nom ci-dessous.
 *
 * Holder.tags() est le seul nom que j'aie pu VERIFIER identique sur deux
 * lignees (1.21.11 et 26.2) : il est essaye en premier.
 */
public final class CeiTagIndex {

    private CeiTagIndex() {}

    /** Comment obtenir le porteur de registre d'une pile. */
    private static final String[] HOLDER_OF_STACK = {
            "getItemHolder", "typeHolder", "getRegistryEntry"
    };
    /** Comment obtenir le flux de tags d'un porteur. */
    private static final String[] TAGS_OF_HOLDER = { "tags", "streamTags" };
    /** Certaines lignees exposent les tags directement sur la pile. */
    private static final String[] TAGS_OF_STACK = { "getTags", "streamTags" };
    /** Comment lire l'identifiant d'une cle de tag. */
    private static final String[] ID_OF_KEY = { "location", "id" };

    private static Map<Object, Set<String>> byItem = null;
    private static List<String> names = Collections.emptyList();
    private static boolean failed = false;

    /**
     * Construit l'index, une seule fois.
     *
     * Declenche au premier # tape. Un passage sur la liste d'items, sur une
     * frappe manuelle : le cout ne tombe jamais dans une boucle de rendu.
     */
    public static synchronized void build(List<ItemStack> items) {
        if (byItem != null || failed || items == null) return;
        Map<Object, Set<String>> map = new HashMap<>();
        Set<String> all = new TreeSet<>();
        try {
            for (ItemStack stack : items) {
                Object item = stack.getItem();
                if (map.containsKey(item)) continue;
                Set<String> tags = new LinkedHashSet<>();
                for (Object key : tagKeys(stack)) {
                    String id = idOf(key);
                    if (id != null) {
                        tags.add(id);
                        all.add(id);
                    }
                }
                map.put(item, tags);
            }
        } catch (Exception | LinkageError e) {
            failed = true;
            return;
        }
        byItem = map;
        names = new ArrayList<>(all);
    }

    /** Vide l'index : la liste des tags depend du monde charge. */
    public static synchronized void invalidate() {
        byItem = null;
        names = Collections.emptyList();
        failed = false;
    }

    /** Les noms de tags connus, pour la completion. */
    public static List<String> tagNames() {
        return names;
    }

    /**
     * La pile porte-t-elle un tag correspondant ?
     *
     * La comparaison est un "contient" : "#ingots" trouve "c:ingots" comme
     * "forge:ingots/copper". Exiger le nom complet obligerait a connaitre par
     * coeur le prefixe de convention, qui a change deux fois.
     */
    public static boolean matches(ItemStack stack, String query) {
        if (query == null || query.isEmpty()) return false;
        Map<Object, Set<String>> map = byItem;
        if (map == null) return false;
        Set<String> tags = map.get(stack.getItem());
        if (tags == null) return false;
        String needle = query.toLowerCase();
        for (String t : tags) {
            if (t.contains(needle)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ reflexion

    private static Iterable<Object> tagKeys(ItemStack stack) {
        Stream<?> stream = call(stack, TAGS_OF_STACK);
        if (stream == null) {
            Object holder = callObject(stack, HOLDER_OF_STACK);
            if (holder != null) stream = call(holder, TAGS_OF_HOLDER);
        }
        if (stream == null) return Collections.emptyList();
        List<Object> out = new ArrayList<>();
        stream.forEach(out::add);
        return out;
    }

    private static Stream<?> call(Object target, String[] candidates) {
        Object r = callObject(target, candidates);
        return (r instanceof Stream<?>) ? (Stream<?>) r : null;
    }

    private static Object callObject(Object target, String[] candidates) {
        if (target == null) return null;
        for (String name : candidates) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Exception | LinkageError e) {
                // nom absent sur cette lignee : on essaie le suivant
            }
        }
        return null;
    }

    private static String idOf(Object tagKey) {
        Object id = callObject(tagKey, ID_OF_KEY);
        return id == null ? null : String.valueOf(id).toLowerCase();
    }
}
