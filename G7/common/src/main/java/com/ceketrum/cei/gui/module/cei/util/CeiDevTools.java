package com.ceketrum.cei.gui.module.cei.util;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Mode developpeur : export presse-papier et inspecteur d'item.
 *
 * Tout ici est inerte tant que l'option "devMode" est decochee. Aucun appel
 * n'entre dans une boucle de rendu sans ce test en amont -- la lecon de
 * l'optimisation precedente est encore fraiche : ce qui s'execute par case et
 * par image se paie en millisecondes, pas en microsecondes.
 *
 * Ce fichier n'existe pour l'instant que dans G4. La forme sera propagee aux
 * six autres groupes une fois eprouvee en jeu. Les deux points qui divergeront
 * sont deja connus et releves dans les jars :
 *
 *   - G1/G2 (1.20.1, 1.20.4) n'ont pas de composants : l'inspecteur devra y
 *     lire le NBT (ItemStack.getNbt en Yarn).
 *   - En 26.2, ItemStack.getTags() n'existe plus. C'est pour cette raison que
 *     les tags passent par CeiTagIndex, qui essaie plusieurs noms.
 *
 * Reserve sur les tags : CeiTagIndex resout ses noms par REFLEXION. Cela
 * fonctionne sous NeoForge, qui garde les noms Mojang a l'execution, mais PAS
 * sous Fabric, ou le jar du mod est remappe vers l'intermediaire et ou
 * getMethod("getTags") ne trouve donc rien. Sur un jar Fabric, l'export de
 * tags rendra une liste vide -- sans erreur, comme le reste du mecanisme.
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
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        Minecraft mc = Minecraft.getInstance();
        String text = render(stack, format);
        if (text.isEmpty()) {
            say(mc, "§cCEI · " + format.label() + " : rien a copier");
            return;
        }
        mc.keyboardHandler.setClipboard(text);
        String shown = text.replace('\n', ' ');
        if (shown.length() > 60) shown = shown.substring(0, 57) + "...";
        say(mc, "§aCEI · " + format.label() + " §7" + shown);
    }

    /**
     * Au-dessus de la barre d'action : visible, et sans encombrer le chat.
     *
     * 26.x a scinde displayClientMessage(Component, boolean) en deux methodes a
     * un seul argument : sendOverlayMessage pour la barre d'action,
     * sendSystemMessage pour le chat.
     *
     * Les deux noms ne se recouvrent nulle part -- displayClientMessage est
     * absent du jar 26.2.0.17-beta, sendOverlayMessage est absent de 21.11.42.
     * Il n'existe donc pas d'appel unique valable sur les sept lignees, et ce
     * fichier ne peut pas etre partage avec G3/G4/G5/G6.
     */
    private static void say(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(message));
        }
    }

    // ------------------------------------------------------------ inspecteur

    /** Nombre de composants detailles avant de couper. */
    private static final int MAX_COMPONENTS = 8;
    /** Longueur au-dela de laquelle la valeur d'un composant est tronquee. */
    private static final int MAX_VALUE_LEN = 70;

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

        if (stack.isDamageableItem()) {
            line(sb, "§7Durabilite : §f"
                    + (stack.getMaxDamage() - stack.getDamageValue())
                    + " / " + stack.getMaxDamage());
        }

        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            line(sb, "§7Faim : §f" + food.nutrition()
                    + "   §7Saturation : §f"
                    + String.format(Locale.ROOT, "%.1f", food.saturation()));
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

        // Composants. Le nom vient du registre : DataComponentType ne redefinit
        // pas toString(), l'afficher directement ne donnerait qu'une adresse.
        int shown = 0;
        try {
            for (var typed : stack.getComponents()) {
                if (shown >= MAX_COMPONENTS) {
                    line(sb, "§8  ...");
                    break;
                }
                var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type());
                if (key == null) continue;
                String value = String.valueOf(typed.value());
                if (value.length() > MAX_VALUE_LEN) {
                    value = value.substring(0, MAX_VALUE_LEN - 3) + "...";
                }
                line(sb, "§8  " + key + " = " + value);
                shown++;
            }
        } catch (Exception e) {
            // Un composant moddee dont toString() leve ne doit pas emporter le
            // rendu de la fiche entiere.
            line(sb, "§c  (composants illisibles)");
        }

        return sb.toString();
    }

    private static void line(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(text);
    }
}
