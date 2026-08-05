package com.ceketrum.cei.gui.module.cei.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Decomposition d'un objet en composants, directs ou bruts.
 *
 * CE FICHIER EST GENERE PAR scratch/gen_tree.py ET IDENTIQUE SUR LES SEPT
 * GROUPES, a six jetons pres. Ne pas l'editer a la main : la modification
 * serait perdue a la prochaine generation, et surtout elle ne vaudrait que
 * pour un groupe. Corriger le gabarit du generateur.
 *
 * Il ne pose que deux questions a une recette -- que produit-elle, que
 * consomme-t-elle -- et les pose a CeiRecipeShape, seul endroit du calculateur
 * qui connaisse la version.
 *
 * CONCEPTION GUIDEE PAR LE COUT. Rien ici ne doit tourner par image :
 *
 *   - l'index des recettes n'est PAS reconstruit : CeiRecipeIndex existe deja
 *     et repond en une recherche de table ;
 *   - le resultat est calcule une fois puis MEMORISE, et seul un changement de
 *     cible, de quantite ou de mode le refait ;
 *   - l'etat des stocks est relu au plus une fois toutes les 500 ms, pas a
 *     chaque image ;
 *   - la descente est bornee par quatre garde-fous independants : profondeur,
 *     nombre de noeuds, nombre de lignes, et largeur des deux remontees
 *     d'index.
 *
 * Le garde-fou de chemin n'est pas theorique : dans un pack moddee, les
 * recettes se referencent en boucle (lingot -> bloc -> 9 lingots). Sans la
 * pile de chemin, la descente ne terminerait jamais.
 */
public final class CeiCraftTree {

    private CeiCraftTree() {}

    /**
     * Profondeur maximale de la descente.
     *
     * BORNE DURE, et non le reglage par defaut : l'ecran de configuration
     * propose 8, et 32 seulement si "Debrider les limites" est coche. Laisser
     * 8 ici rendait l'option de debridage sans effet, get() ecretant en
     * silence.
     *
     * Ce n'est pas le garde-fou qui compte : MAX_NODES borne la descente a 512
     * expansions quelle que soit la profondeur demandee.
     */
    public static final int MAX_DEPTH = 32;
    /** Profondeur 1 : les ingredients directs, sans les decomposer. */
    public static final int MIN_DEPTH = 1;
    /** Nombre maximal d'expansions, tous chemins confondus. */
    private static final int MAX_NODES = 512;
    /** Au-dela, la liste devient illisible autant qu'inutile. */
    public static final int MAX_LINES = 64;
    /** Intervalle de relecture des stocks. */
    private static final long STOCK_INTERVAL_MS = 500L;

    /** Quantite demandee : bornes du reglage. */
    public static final int MIN_QTY = 1;
    public static final int MAX_QTY = 9999;

    /**
     * Plafond de recettes examinees pour rendre UN verdict de fourre-tout.
     *
     * Sans lui, le verdict coute la totalite des recettes consommant
     * l'ingredient : des milliers pour la redstone d'un pack technique, et
     * cela une fois par objet distinct de l'arbre. Mesure hors du jeu sur un
     * graphe de 4 000 recettes : 12 004 recettes parcourues pour un seul objet
     * cible, sur le fil de rendu ; 1 540 avec ce plafond.
     *
     * Il ne fausse aucun verdict legitime. Un vrai fourre-tout atteint ses
     * UNIVERSAL_OUTPUTS sorties distinctes dans ses toutes premieres recettes
     * -- ce sont justement des recettes a ingredient unique. Un ingredient
     * courant n'y arrive jamais : il est coupe au lieu d'etre parcouru en
     * entier.
     */
    private static final int MAX_UNIVERSAL_SCAN = 512;
    /** Meme plafond pour la remontee de reciprocite. */
    private static final int MAX_RECIPROCAL_SCAN = 256;
    /** Le journal ne le dit qu'une fois : sinon il le dirait par image. */
    private static boolean SCAN_WARNED = false;

    // ------------------------------------------------------------- resultat

    public static final class Line {
        public final ItemStack stack;
        public final double count;
        /** Vrai si aucune recette ne produit cet objet : c'est une matiere premiere. */
        public final boolean raw;
        /** Quantite possedee, relue periodiquement. */
        public int have;

        Line(ItemStack stack, double count, boolean raw) {
            this.stack = stack;
            this.count = count;
            this.raw = raw;
        }

        public boolean enough() {
            return have >= Math.ceil(count - 1.0e-6);
        }
    }

    /** Un noeud de l'arbre : un objet, sa quantite, et ce qu'il faut pour lui. */
    public static final class Node {
        public final ItemStack stack;
        public final double count;
        /** Vrai si aucune recette ne produit cet objet : matiere premiere. */
        public final boolean raw;
        /**
         * Vrai si l'objet est rendu, use, au lieu d'etre consomme : le marteau
         * de forge, la pince, le moule. Sa quantite ne suit alors PAS le
         * nombre de fabrications.
         */
        public final boolean tool;
        public final List<Node> children;
        /**
         * Identite de la branche, batie sur le CHEMIN D'INDICES et non sur
         * l'objet. Deux branches portant le meme objet a deux endroits de
         * l'arbre se replient donc independamment, et la cle survit a un
         * recalcul puisque l'ordre des ingredients est fixe.
         */
        public final long key;
        /** Quantite possedee, relue periodiquement. */
        public int have;

        Node(ItemStack stack, double count, boolean raw, boolean tool,
             List<Node> children, long key) {
            this.stack = stack;
            this.count = count;
            this.raw = raw;
            this.tool = tool;
            this.children = children;
            this.key = key;
        }

        public boolean enough() {
            return have >= Math.ceil(count - 1.0e-6);
        }
    }

    /** Une ligne d'arbre prete a dessiner : profondeur et traits de liaison. */
    public static final class Row {
        public final Node node;
        public final int depth;
        /** Dernier de sa fratrie : coude au lieu de te. */
        public final boolean last;
        /** Bit d : un trait vertical traverse encore la profondeur d. */
        public final long trail;

        Row(Node node, int depth, boolean last, long trail) {
            this.node = node;
            this.depth = depth;
            this.last = last;
            this.trail = trail;
        }
    }

    public static final class Result {
        /** Totaux cumules, une ligne par objet : la vue Liste. */
        public final List<Line> lines;
        /** Hierarchie sous l'objet vise : la vue Arbre. */
        public final List<Node> roots;
        /** Vrai si un garde-fou a coupe la descente : le total est incomplet. */
        public final boolean truncated;
        /** Vrai si l'objet vise n'a aucune recette connue. */
        public final boolean noRecipe;

        Result(List<Line> lines, List<Node> roots, boolean truncated, boolean noRecipe) {
            this.lines = lines;
            this.roots = roots;
            this.truncated = truncated;
            this.noRecipe = noRecipe;
        }
    }

    private static final Result EMPTY = new Result(List.of(), List.of(), false, true);

    /** Plafond de lignes mises a plat, garde-fou du seul parcours par image. */
    private static final int MAX_ROWS = 512;

    /**
     * Met l'arbre a plat en sautant ce qui est replie.
     *
     * Appele par image, mais il ne fait que parcourir des noeuds DEJA
     * CONSTRUITS : aucune recette n'y est relue, aucun stock n'y est compte.
     */
    public static List<Row> flatten(List<Node> roots, Set<Long> collapsed) {
        List<Row> out = new ArrayList<>();
        if (roots != null && !roots.isEmpty()) {
            walk(roots, collapsed == null ? Set.of() : collapsed, 0, 0L, out);
        }
        return out;
    }

    private static void walk(List<Node> nodes, Set<Long> collapsed,
                             int depth, long trail, List<Row> out) {
        if (depth > MAX_DEPTH) return;
        for (int i = 0; i < nodes.size(); i++) {
            if (out.size() >= MAX_ROWS) return;
            Node n = nodes.get(i);
            boolean last = (i == nodes.size() - 1);
            out.add(new Row(n, depth, last, trail));
            if (!n.children.isEmpty() && !collapsed.contains(n.key)) {
                // Le trait vertical ne se prolonge que sous un noeud qui a
                // encore des freres en dessous de lui.
                walk(n.children, collapsed, depth + 1,
                     last ? trail : (trail | (1L << depth)), out);
            }
        }
    }

    // ---------------------------------------------------------------- cache

    private static Item cachedItem;
    private static int cachedQty = -1;
    private static int cachedDepth = -1;
    private static Result cached;
    private static long stockAt;

    /**
     * Resolution objet -> recette, memorisee.
     *
     * recipeFor() est appelee plusieurs fois pour un meme objet : une fois par
     * noeud de l'arbre, une fois de plus par ligne de totaux. Depuis qu'elle
     * verifie aussi la reciprocite -- ce qui remonte l'index a l'envers -- la
     * repeter serait franchement couteux sur un pack ou un lingot est produit
     * par trente recettes.
     */
    private static final Map<Item, Object> RESOLVED = new HashMap<>();
    /** Marqueur d'absence : une Map ne distingue pas "absent" de "nul". */
    private static final Object NO_RECIPE = new Object();

    /**
     * Ce que rend un porteur, memorise par IDENTITE de porteur.
     *
     * A partir de 1.21.5, chaque appel a l'adaptateur reconstruit un
     * SlotDisplayContext et resout les presentations de la recette. Le meme
     * porteur est interroge jusqu'a trois fois -- reciprocite, fourre-tout,
     * puis descente -- pour une reponse qui ne change pas.
     */
    private static final Map<Object, ItemStack> OUT_MEMO = new IdentityHashMap<>();
    private static final Map<Object, List<ItemStack>> IN_MEMO = new IdentityHashMap<>();

    /**
     * Le monde auquel le tampon est adosse.
     *
     * C'est le meme temoin que CeiRecipeIndex.buildStep : changement de monde
     * ou rechargement, le tampon se vide tout seul. Le vider a chaque calcul
     * -- donc a chaque clic sur [+] et a chaque cran de molette -- faisait
     * repayer tout le decompte des fourre-tout alors que changer la quantite
     * ne change aucune recette.
     */
    private static Object memoWorld;

    /**
     * Nombre de sorties distinctes au-dela duquel un ingredient est un
     * FOURRE-TOUT : matiere universelle d'un mod (UU-Matter), boite a butin
     * aleatoire (Scrap Box), et leurs equivalents.
     *
     * Le seuil est volontairement haut. Ecarter a tort une recette legitime
     * coute plus cher que laisser passer une branche inutile.
     */
    private static final int UNIVERSAL_OUTPUTS = 24;
    /** Verdict par ingredient. */
    private static final Map<Item, Boolean> UNIVERSAL = new HashMap<>();

    /**
     * Le resultat pour (objet, quantite, mode).
     *
     * Recalcule uniquement si l'un des trois a change. Les stocks, eux, sont
     * rafraichis au rythme de STOCK_INTERVAL_MS -- le joueur ne verra pas la
     * difference, et on ne parcourt pas ses coffres soixante fois par seconde.
     */
    public static Result get(ItemStack target, int qty, int depth) {
        if (target == null || target.isEmpty()) return EMPTY;
        int d = Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, depth));

        if (cached == null || cachedItem != target.getItem()
                || cachedQty != qty || cachedDepth != d) {
            cached = compute(target, qty, d);
            cachedItem = target.getItem();
            cachedQty = qty;
            cachedDepth = d;
            stockAt = 0L;   // force une relecture immediate
        }
        refreshStock();
        return cached;
    }

    /** A appeler quand le monde change : les recettes ne sont plus les memes. */
    public static void invalidate() {
        cached = null;
        cachedItem = null;
        cachedQty = -1;
        cachedDepth = -1;
        RESOLVED.clear();
        UNIVERSAL.clear();
        OUT_MEMO.clear();
        IN_MEMO.clear();
        memoWorld = null;
        SCAN_WARNED = false;
    }

    // ------------------------------------------------------ l'adaptateur

    /** Ce que ce porteur produit. Jamais nul : une pile vide au pire. */
    private static ItemStack outputOf(net.minecraft.recipe.RecipeEntry<?> holder) {
        ItemStack memo = OUT_MEMO.get(holder);
        if (memo != null) return memo;
        ItemStack out = CeiRecipeShape.outputOf(holder);
        if (out == null) out = ItemStack.EMPTY;
        OUT_MEMO.put(holder, out);
        return out;
    }

    /** Ce que ce porteur consomme, un exemplaire par occurrence. */
    private static List<ItemStack> inputsOf(net.minecraft.recipe.RecipeEntry<?> holder) {
        List<ItemStack> memo = IN_MEMO.get(holder);
        if (memo != null) return memo;
        List<ItemStack> in = CeiRecipeShape.inputsOf(holder);
        if (in == null) in = List.of();
        IN_MEMO.put(holder, in);
        return in;
    }

    // ------------------------------------------------------------- descente

    private static Result compute(ItemStack target, int qty, int depth) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) return EMPTY;

        // Le tampon ne se vide que si le MONDE a change.
        if (memoWorld != client.world) {
            RESOLVED.clear();
            UNIVERSAL.clear();
            OUT_MEMO.clear();
            IN_MEMO.clear();
            memoWorld = client.world;
        }

        Map<Item, Double> totals = new LinkedHashMap<>();
        Map<Item, ItemStack> samples = new HashMap<>();
        int[] budget = { MAX_NODES };
        boolean[] cut = { false };

        net.minecraft.recipe.RecipeEntry<?> root = recipeFor(target.getItem());
        if (root == null) return EMPTY;

        // UNE SEULE descente produit les deux vues : l'arbre est la valeur de
        // retour, les totaux s'accumulent au passage. Les garde-fous valent
        // donc pour l'un comme pour l'autre.
        Node rootNode = expand(target.getItem(), qty, false, depth, 1L,
                               totals, samples, new HashSet<>(), budget, cut);

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Item, Double> e : totals.entrySet()) {
            if (lines.size() >= MAX_LINES) { cut[0] = true; break; }
            ItemStack sample = samples.get(e.getKey());
            if (sample == null) sample = new ItemStack(e.getKey());
            lines.add(new Line(sample, e.getValue(), recipeFor(e.getKey()) == null));
        }
        // La racine est l'objet vise, deja affiche en en-tete : on n'expose
        // que ce qu'il faut pour l'obtenir.
        return new Result(lines, rootNode.children, cut[0], false);
    }

    private static Node expand(Item item, double amount, boolean asTool,
                               int depthLeft, long key,
                               Map<Item, Double> totals, Map<Item, ItemStack> samples,
                               Set<Item> path, int[] budget, boolean[] cut) {
        ItemStack sample = samples.get(item);
        if (sample == null) sample = new ItemStack(item);

        if (budget[0]-- <= 0) {
            cut[0] = true;
            totals.merge(item, amount, Double::sum);
            return new Node(sample, amount, false, asTool, List.of(), key);
        }

        // path porte le CHEMIN courant, pas tout ce qui a ete vu : un objet
        // peut apparaitre dans deux branches soeurs sans que ce soit un cycle.
        boolean open = depthLeft > 0 && !path.contains(item);
        net.minecraft.recipe.RecipeEntry<?> holder = open ? recipeFor(item) : null;

        if (holder == null) {
            totals.merge(item, amount, Double::sum);
            // "Brut" au sens strict : aucune recette n'existe. Une branche
            // seulement coupee par la profondeur ou par un cycle n'est PAS une
            // matiere premiere et ne doit pas etre peinte comme telle.
            // Le test n'est paye que dans ce second cas, jamais dans le premier.
            boolean raw = open || recipeFor(item) == null;
            return new Node(sample, amount, raw, asTool, List.of(), key);
        }

        ItemStack outcome = outputOf(holder);
        int produced = Math.max(1, outcome.isEmpty() ? 1 : outcome.getCount());
        double runs = amount / produced;

        // Un ingredient a durabilite est rendu, abime, au lieu d'etre
        // consomme : quatre plaques ne demandent pas quatre marteaux.
        //
        // La reserve est essentielle : si le RESULTAT est lui-meme un objet a
        // durabilite, la regle ne s'applique pas. La table de forge consomme
        // bel et bien l'epee de diamant qu'elle transforme en netherite.
        boolean toolsApply = !outcome.isEmpty() && !outcome.isDamageable();

        Map<Item, Integer> ingredients = countInputs(holder, samples);
        if (ingredients.isEmpty()) {
            totals.merge(item, amount, Double::sum);
            return new Node(sample, amount, false, asTool, List.of(), key);
        }

        path.add(item);
        List<Node> children = new ArrayList<>(ingredients.size());
        int rank = 0;
        for (Map.Entry<Item, Integer> e : ingredients.entrySet()) {
            // countInputs a deja renseigne samples : l'exemplaire lu ici est
            // celui de la recette, avec sa durabilite d'origine.
            ItemStack ingSample = samples.get(e.getKey());
            boolean childTool = toolsApply && ingSample != null && isTool(ingSample);
            double childAmount = childTool
                    ? toolCount(ingSample, runs)
                    : runs * e.getValue();
            children.add(expand(e.getKey(), childAmount, childTool, depthLeft - 1,
                                key * 31L + (++rank),
                                totals, samples, path, budget, cut));
        }
        path.remove(item);
        // Le sample de l'objet courant a pu etre renseigne par le parent entre
        // temps : on le relit plutot que de garder celui d'avant la descente.
        ItemStack own = samples.get(item);
        return new Node(own == null ? sample : own, amount, false, asTool, children, key);
    }

    /**
     * Objet a durabilite : il s'use au lieu de disparaitre.
     */
    private static boolean isTool(ItemStack stack) {
        try {
            return stack.isDamageable() && stack.getMaxDamage() > 0;
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    /**
     * Combien d'exemplaires d'un outil il faut reellement.
     *
     * Une fabrication coute un point de durabilite : un marteau de soixante
     * points couvre soixante plaques, et il en faut neuf pour cinq cents.
     * Jamais moins d'un -- il en faut un, meme pour une seule fabrication.
     */
    private static double toolCount(ItemStack tool, double runs) {
        int uses;
        try {
            uses = Math.max(1, tool.getMaxDamage());
        } catch (Exception | LinkageError e) {
            uses = 1;
        }
        return Math.max(1.0, Math.ceil(runs / (double) uses));
    }

    /**
     * La premiere recette exploitable qui produit cet objet.
     *
     * Aucun nom de classe de recette n'est teste : on se contente d'exiger des
     * ingredients et un resultat. Cela couvre l'etabli, le four, la scie et les
     * recettes moddees sans nommer un seul type -- donc sans un seul nom qui
     * pourrait changer d'une version a l'autre.
     */
    private static net.minecraft.recipe.RecipeEntry<?> recipeFor(Item item) {
        Object memo = RESOLVED.get(item);
        if (memo != null) return memo == NO_RECIPE ? null : (net.minecraft.recipe.RecipeEntry<?>) memo;

        net.minecraft.recipe.RecipeEntry<?> found = resolve(item);
        RESOLVED.put(item, found == null ? NO_RECIPE : found);
        return found;
    }

    private static net.minecraft.recipe.RecipeEntry<?> resolve(Item item) {
        List<net.minecraft.recipe.RecipeEntry<?>> candidates =
                com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex.producedBy(item);
        if (candidates == null || candidates.isEmpty()) return null;
        for (net.minecraft.recipe.RecipeEntry<?> h : candidates) {
            try {
                if (inputsOf(h).isEmpty()) continue;
                ItemStack out = outputOf(h);
                if (out.isEmpty()) continue;
                // Ecartee sans repli de secours. Si le pack ne propose QUE le
                // sens retour -- l'eclat d'amethyste, qui ne s'obtient en
                // fabrication qu'en cassant son bloc -- alors l'objet est bel
                // et bien une base : le declarer matiere premiere est plus
                // juste que d'annoncer qu'il faut un quart de bloc.
                if (out.getCount() > 1 && isReciprocal(item, h)) continue;
                // Le mass fabricator fait de la pierre a partir d'UU-Matter,
                // la scrap box rend de la poussiere de diamant : ce sont de
                // vraies recettes, mais jamais celles qu'on veut suivre.
                if (usesUniversal(h)) continue;
                return h;
            } catch (Exception | LinkageError e) {
                // Recette moddee qui refuse de se decrire : on passe a la
                // suivante plutot que d'abandonner l'objet.
            }
        }
        return null;
    }

    /**
     * Vrai si cette recette est le RETOUR d'une autre.
     *
     * Le bloc rend neuf lingots, et le lingot vient du bloc : suivre ce
     * sens-la fait descendre l'arbre dans un detour qui revient a son point de
     * depart. Le garde-fou de chemin l'arrete, mais la branche affichee n'a
     * aucun sens -- c'est le defaut que cette methode supprime.
     *
     * L'appelant ne pose la question que pour une recette produisant plusieurs
     * exemplaires. C'est ce qui separe les deux sens : "1 bloc -> 9 lingots"
     * est ecarte, "9 lingots -> 1 bloc" est garde. Sans cette condition,
     * decomposer un bloc de fer le declarerait matiere premiere.
     */
    private static boolean isReciprocal(Item item, net.minecraft.recipe.RecipeEntry<?> holder) {
        int scanned = 0;
        try {
            for (ItemStack first : inputsOf(holder)) {
                if (first == null || first.isEmpty()) continue;

                List<net.minecraft.recipe.RecipeEntry<?>> back =
                        com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex
                                .producedBy(first.getItem());
                if (back == null) continue;
                for (net.minecraft.recipe.RecipeEntry<?> h : back) {
                    // Un lingot est produit par trente recettes dans un gros
                    // pack, et chacune se relit ingredient par ingredient.
                    if (++scanned > MAX_RECIPROCAL_SCAN) {
                        warnScan("reciprocite", MAX_RECIPROCAL_SCAN);
                        return false;
                    }
                    for (ItemStack backFirst : inputsOf(h)) {
                        if (backFirst != null && !backFirst.isEmpty()
                                && backFirst.getItem() == item) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            // Dans le doute, on ne bloque rien : la recette reste utilisable.
            return false;
        }
        return false;
    }

    /** Vrai si l'un des ingredients de la recette est un fourre-tout. */
    private static boolean usesUniversal(net.minecraft.recipe.RecipeEntry<?> holder) {
        try {
            for (ItemStack first : inputsOf(holder)) {
                if (first == null || first.isEmpty()) continue;
                if (isUniversal(first.getItem())) return true;
            }
        } catch (Exception | LinkageError e) {
            return false;
        }
        return false;
    }

    /**
     * Un ingredient qui produit a lui SEUL des dizaines d'objets differents.
     *
     * La restriction "a lui seul" est la clef de voute. Sans elle, le lingot
     * de fer -- ingredient de la moitie du jeu -- serait declare fourre-tout,
     * toute recette en consommant serait ecartee, et l'arbre entier
     * s'effondrerait. Comptees ainsi, les sorties d'un ingredient unique
     * donnent des dizaines pour l'UU-Matter et la scrap box, une quinzaine
     * pour la pierre au tailleur, et trois pour le lingot de fer.
     */
    private static boolean isUniversal(Item ingredient) {
        Boolean memo = UNIVERSAL.get(ingredient);
        if (memo != null) return memo;

        boolean verdict = false;
        try {
            List<net.minecraft.recipe.RecipeEntry<?>> uses =
                    com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex.usedIn(ingredient);
            // Moins de recettes que le seuil : inutile d'aller plus loin.
            if (uses.size() >= UNIVERSAL_OUTPUTS) {
                Set<Item> outputs = new HashSet<>();
                int scanned = 0;
                for (net.minecraft.recipe.RecipeEntry<?> h : uses) {
                    // LA borne qui manquait. Un vrai fourre-tout a rempli
                    // outputs bien avant d'arriver ici ; un ingredient courant,
                    // lui, ferait defiler ses milliers de recettes sur le fil
                    // de rendu pour finir par repondre "non".
                    if (++scanned > MAX_UNIVERSAL_SCAN) {
                        warnScan("fourre-tout", MAX_UNIVERSAL_SCAN);
                        break;
                    }
                    if (!onlyIngredient(h, ingredient)) continue;
                    ItemStack out = outputOf(h);
                    if (out.isEmpty()) continue;
                    outputs.add(out.getItem());
                    if (outputs.size() >= UNIVERSAL_OUTPUTS) break;
                }
                verdict = outputs.size() >= UNIVERSAL_OUTPUTS;
            }
        } catch (Exception | LinkageError e) {
            verdict = false;
        }
        UNIVERSAL.put(ingredient, verdict);
        return verdict;
    }

    /** Vrai si la recette ne consomme que cet objet, repete ou non. */
    private static boolean onlyIngredient(net.minecraft.recipe.RecipeEntry<?> holder, Item ingredient) {
        boolean seen = false;
        for (ItemStack first : inputsOf(holder)) {
            if (first == null || first.isEmpty()) continue;
            if (first.getItem() != ingredient) return false;
            seen = true;
        }
        return seen;
    }

    /**
     * Dit une fois, au journal, qu'un plafond a coupe.
     *
     * Le meme motif que "[cei] exploration bornee" pose sur les descentes
     * reflexives : la ligne nomme le plafond atteint, et son absence dit que
     * le calculateur n'a rien eu a couper.
     */
    private static void warnScan(String quoi, int plafond) {
        if (SCAN_WARNED) return;
        SCAN_WARNED = true;
        try {
            org.slf4j.LoggerFactory.getLogger("cei-calc").warn(
                    "[cei-calc] parcours de {} borne a {} recettes."
                    + " Sans cette borne, le calculateur figeait le rendu.",
                    quoi, plafond);
        } catch (Exception | LinkageError e) {
            // Journal indisponible : ce n'est pas une raison d'echouer.
        }
    }

    /** Un exemplaire par Item, avec le nombre d'occurrences. */
    private static Map<Item, Integer> countInputs(net.minecraft.recipe.RecipeEntry<?> holder,
                                                  Map<Item, ItemStack> samples) {
        Map<Item, Integer> map = new LinkedHashMap<>();
        try {
            for (ItemStack first : inputsOf(holder)) {
                if (first == null || first.isEmpty()) continue;
                map.merge(first.getItem(), 1, Integer::sum);
                samples.putIfAbsent(first.getItem(), first);
            }
        } catch (Exception | LinkageError e) {
            return Map.of();
        }
        return map;
    }

    // --------------------------------------------------------------- stocks

    /**
     * Relit les stocks, au plus une fois toutes les 500 ms.
     *
     * On ne compte que l'inventaire du joueur. Les coffres ouverts sont
     * ignores : c'est ce qui garantit qu'aucun objet n'est compte deux fois,
     * et c'est la reserve que le joueur a en tete quand il lit la liste.
     */
    private static void refreshStock() {
        if (cached == null || (cached.lines.isEmpty() && cached.roots.isEmpty())) return;
        long now = System.currentTimeMillis();
        if (now - stockAt < STOCK_INTERVAL_MS) return;
        stockAt = now;

        // Un seul comptage sert les deux vues.
        Map<Item, Integer> stock = countStock();
        for (Line line : cached.lines) {
            Integer n = stock.get(line.stack.getItem());
            line.have = n == null ? 0 : n;
        }
        applyStock(cached.roots, stock);
    }

    /** Recursion bornee par MAX_DEPTH et MAX_NODES, comme la descente. */
    private static void applyStock(List<Node> nodes, Map<Item, Integer> stock) {
        for (Node n : nodes) {
            Integer q = stock.get(n.stack.getItem());
            n.have = q == null ? 0 : q;
            if (!n.children.isEmpty()) applyStock(n.children, stock);
        }
    }

    private static Map<Item, Integer> countStock() {
        Map<Item, Integer> stock = new HashMap<>();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) return stock;
        try {
            var inventory = client.player.getInventory();
            // 36 = barre d'action plus sac principal. Cette borne litterale et
            // la lecture d'un emplacement sont exactement ce qu'emploie
            // CraftingHelper sur ce meme groupe : des symboles deja valides par
            // une construction reussie.
            for (int i = 0; i < 36; i++) {
                ItemStack s = inventory.getStack(i);
                if (s != null && !s.isEmpty()) {
                    stock.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            }
        } catch (Exception | LinkageError e) {
            // Inventaire dans un etat transitoire : mieux vaut afficher zero
            // que interrompre le rendu de la fiche.
        }
        return stock;
    }

    // -------------------------------------------------------------- affichage

    /** "1", "1.5", "12" -- jamais "1.0". */
    public static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        if (Math.abs(r - Math.rint(r)) < 1.0e-6) {
            return String.valueOf((long) Math.rint(r));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", r);
    }
}
