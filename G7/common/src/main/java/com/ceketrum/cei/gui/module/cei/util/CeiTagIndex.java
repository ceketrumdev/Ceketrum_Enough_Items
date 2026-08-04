package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    /**
     * Les tags d'une pile, resolus a la demande.
     *
     * Ne passe PAS par l'index : celui-ci n'est construit qu'au premier # tape,
     * et le mode developpeur doit pouvoir repondre sans qu'on ait cherche quoi
     * que ce soit. Le cout est celui d'un appel reflexif, sur une frappe.
     */
    public static List<String> tagsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object key : tagKeys(stack)) {
            String id = idOf(key);
            if (id != null && !out.contains(id)) out.add(id);
        }
        return out;
    }

    // --------------------------------------------------------- acces direct
    //
    // L'ancienne version resolvait ces deux operations par reflexion sur des
    // noms candidats. Cela ne pouvait pas fonctionner en production : Fabric
    // remappe le jar du mod vers l'intermediaire, NeoForge 1.20.1 vers le SRG.
    // Un nom Yarn ou Mojang ecrit dans une chaine n'existe plus a l'execution,
    // et l'index restait vide sans le moindre message.
    //
    // La divergence entre lignees est reelle, mais elle se traite au patch,
    // par jetons -- pas au vol. L'appel ci-dessous est compile, donc remappe
    // comme le reste du mod.

    private static Iterable<Object> tagKeys(ItemStack stack) {
        List<Object> out = new ArrayList<>();
        try {
            stack.typeHolder().tags().forEach(out::add);
        } catch (Exception | LinkageError e) {
            // Une pile sans porteur de registre (cas limite d'un mod) ne doit
            // pas interrompre la construction de l'index.
            return Collections.emptyList();
        }
        return out;
    }

    private static String idOf(Object tagKey) {
        if (!(tagKey instanceof net.minecraft.tags.TagKey<?> key)) return null;
        var id = key.location();
        return id == null ? null : id.toString().toLowerCase();
    }
}
