package com.ceketrum.cei.gui.module.cei.util;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/**
 * Mode developpeur : export presse-papier et inspecteur d'item. Variante Yarn.
 *
 * Tout ici est inerte tant que l'option "devMode" est decochee. Aucun appel
 * n'entre dans une boucle de rendu sans ce test en amont.
 *
 * Ce fichier est le jumeau Yarn de celui des lignees Mojang. Les differences
 * sont toutes des noms, sauf UNE de fond : 1.20.1 et 1.20.4 n'ont pas de
 * composants de donnees. L'inspecteur y lit donc le NBT brut, et le decoupe en
 * tranches lisibles plutot que d'en parcourir les cles -- une seule methode
 * appelee au lieu de plusieurs, donc une seule occasion de se tromper de nom.
 *
 * Reserve sur les tags : CeiTagIndex resout ses noms par REFLEXION. Cela
 * fonctionne sous NeoForge, qui garde les noms Mojang a l'execution, mais PAS
 * sous Fabric, ou le jar du mod est remappe vers l'intermediaire. Or ces deux
 * lignees-ci sont precisement celles ou Fabric domine : sur un jar Fabric,
 * l'export de tags rendra une liste vide, sans erreur.
 */
public final class CeiDevTools {

    private CeiDevTools() {}

    /** Formats d'export, dans l'ordre ou Maj+C les fait defiler. */
    public enum Format {
        ID("cei.dev.format.id"),
        KUBEJS("cei.dev.format.kubejs"),
        CRAFTTWEAKER("cei.dev.format.crafttweaker"),
        TAGS("cei.dev.format.tags");

        public final String key;

        Format(String key) {
            this.key = key;
        }

        /** Libelle court. Repli en clair si la cle n'est pas traduite. */
        public String label() {
            return com.ceketrum.cei.i18n.CeiText.has(key)
                    ? com.ceketrum.cei.i18n.CeiText.t(key)
                    : name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Format courant, partage par tous les ecrans.
     *
     * Volontairement statique : on veut que Maj+C poursuive le cycle d'un
     * ecran a l'autre, pas qu'il reparte de l'identifiant a chaque coffre.
     */
    private static Format current = Format.ID;

    public static Format currentFormat() {
        return current;
    }

    public static Format nextFormat() {
        Format[] all = Format.values();
        current = all[(current.ordinal() + 1) % all.length];
        return current;
    }

    public static boolean enabled() {
        return com.ceketrum.cei.config.CeiConfig.getInstance().isDevMode();
    }

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /** Le texte a copier. Chaine vide s'il n'y a rien a copier. */
    public static String render(ItemStack stack, Format format) {
        String id = idOf(stack);
        if (id.isEmpty()) return "";
        switch (format) {
            case KUBEJS:
                return "Item.of('" + id + "')";
            case CRAFTTWEAKER:
                return "<item:" + id + ">";
            case TAGS: {
                List<String> tags = CeiTagIndex.tagsOf(stack);
                if (tags.isEmpty()) return "";
                StringBuilder sb = new StringBuilder();
                for (String tag : tags) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append('#').append(tag);
                }
                return sb.toString();
            }
            default:
                return id;
        }
    }

    /**
     * Copie dans le presse-papier et previent le joueur.
     *
     * Le retour au joueur n'est pas un ornement : sans lui, une copie vide --
     * un item sans aucun tag, par exemple -- serait indiscernable d'une copie
     * reussie, et on collerait l'ancien contenu sans le savoir.
     */
    public static void copy(ItemStack stack, Format format) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String text = render(stack, format);
        if (text.isEmpty()) {
            say(mc, "§cCEI · " + format.label() + " : rien a copier");
            return;
        }
        mc.keyboard.setClipboard(text);
        String shown = text.replace('\n', ' ');
        if (shown.length() > 60) shown = shown.substring(0, 57) + "...";
        say(mc, "§aCEI · " + format.label() + " §7" + shown);
    }

    /** Au-dessus de la barre d'action : visible, et sans encombrer le chat. */
    private static void say(MinecraftClient mc, String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), true);
        }
    }

    // ------------------------------------------------------------ inspecteur

    /** Nombre de tranches de NBT affichees avant de couper. */
    private static final int MAX_NBT_LINES = 8;
    /** Longueur d'une tranche de NBT. */
    private static final int NBT_SLICE = 70;

    /**
     * Lignes de l'inspecteur, une par ligne, a afficher sous la description.
     *
     * Rendue vide si le mode dev est decoche : les appelants n'ont donc pas a
     * repeter le test.
     */
    public static String inspect(ItemStack stack) {
        if (!enabled() || stack == null || stack.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        line(sb, "§8" + idOf(stack));

        if (stack.isDamageable()) {
            line(sb, "§7Durabilite : §f"
                    + (stack.getMaxDamage() - stack.getDamage())
                    + " / " + stack.getMaxDamage());
        }

        if (stack.getItem().isFood()) {
            var food = stack.getItem().getFoodComponent();
            if (food != null) {
                line(sb, "§7Faim : §f" + food.getHunger()
                        + "   §7Saturation : §f"
                        + String.format(Locale.ROOT, "%.1f", food.getSaturationModifier()));
            }
        }

        List<String> tags = CeiTagIndex.tagsOf(stack);
        if (!tags.isEmpty()) {
            StringBuilder t = new StringBuilder();
            for (int i = 0; i < tags.size() && i < 4; i++) {
                if (i > 0) t.append(", ");
                t.append(tags.get(i));
            }
            if (tags.size() > 4) t.append(", +").append(tags.size() - 4);
            line(sb, "§7Tags (" + tags.size() + ") : §f" + t);
        }

        // NBT brut. Pas de parcours de cles : une seule methode appelee, donc
        // une seule occasion de se tromper de nom sur une lignee qu'aucun jar
        // deobfusque ne permet de verifier ici.
        try {
            var nbt = stack.getNbt();
            if (nbt != null) {
                String dump = nbt.toString();
                for (int i = 0, n = 0; i < dump.length() && n < MAX_NBT_LINES; i += NBT_SLICE, n++) {
                    int end = Math.min(dump.length(), i + NBT_SLICE);
                    line(sb, "§8  " + dump.substring(i, end));
                    if (end == dump.length()) break;
                    if (n == MAX_NBT_LINES - 1) line(sb, "§8  ...");
                }
            }
        } catch (Exception e) {
            // Un NBT moddee dont toString() leve ne doit pas emporter le rendu
            // de la fiche entiere.
            line(sb, "§c  (NBT illisible)");
        }

        return sb.toString();
    }

    private static void line(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(text);
    }
}
