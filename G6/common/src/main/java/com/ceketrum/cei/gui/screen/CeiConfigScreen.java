package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.i18n.CeiText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Ecran de configuration de CEI.
 *
 * CE FICHIER EST GENERE PAR scratch/cfg_i18n.py, qui reprend la table
 * d'options de scratch/cfg_options.py. Editer ce fichier a la main, c'est
 * perdre la modification a la prochaine generation.
 *
 * Pas une chaine en dur : tout passe par CeiText.t(cle), et les six fichiers de
 * langue sont ecrits par le meme script. C'est la regle que le mod s'est deja
 * donnee -- "Ici, on ne peut plus afficher de texte sans passer par une cle",
 * dit le commentaire de CeiText -- et que cet ecran etait seul a ignorer.
 *
 * La disposition suit celle de Sodium : recherche en haut, categories a gauche,
 * options a droite avec la valeur alignee au bord, groupes separes par un filet.
 * Les options numeriques montrent une barre AU SURVOL seulement, comme le
 * reglage de taille d'interface du jeu.
 *
 * AUCUNE BARRE DE DEFILEMENT : preference etablie du proprietaire. Molette et
 * fleches.
 *
 * Le fond est un voile maison et non renderBackground() : le shader de flou de
 * Screen se dessine PAR-DESSUS l'interface. CeiItemInfoScreen avait deja
 * rencontre le probleme et le neutralise pour la meme raison.
 */
public class CeiConfigScreen extends Screen {

    private final Screen parent;
    private final CeiConfig config;

    /** Reperes de survie : le plantage vise n'ecrit aucune exception. */
    private static final org.slf4j.Logger CEI_CFG =
            org.slf4j.LoggerFactory.getLogger("cei-cfg");
    private int traced = 0;

    private enum Cat {
        GENERAL("general"),
        INTERFACE("interface"),
        FEATURES("features"),
        PERF("performance"),
        ADVANCED("advanced");

        final String key;
        Cat(String key) { this.key = key; }
        String label() { return CeiText.t("cei.config.cat." + key); }
    }

    /** Une option : ce qu'elle vaut, comment on la change, ce qu'elle dit. */
    private static final class Opt {
        final Cat cat;
        final int groupe;
        /** Nom du champ : sert de cle de traduction et de cle JSON. */
        final String key;
        /** 0 bascule, 1 entier, 2 choix. */
        final int kind;
        final int min, max, maxFree, step;
        final String[] choices;
        final IntSupplier get;
        final IntConsumer set;

        Opt(Cat cat, int groupe, String key, int kind, int min, int max,
            int maxFree, int step, String[] choices,
            IntSupplier get, IntConsumer set) {
            this.cat = cat;
            this.groupe = groupe;
            this.key = key;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.maxFree = maxFree;
            this.step = step;
            this.choices = choices;
            this.get = get;
            this.set = set;
        }

        String label() { return CeiText.t("cei.config.opt." + key); }
        String tip() { return CeiText.t("cei.config.tip." + key); }

        /** La borne haute du moment : celle de l'option, ou celle debridee. */
        int ceiling() {
            return CeiConfig.getInstance().isUnlockLimits() ? maxFree : max;
        }

        String value() {
            int v = get.getAsInt();
            if (kind == 0) {
                return CeiText.t(v == 1 ? "cei.config.value.on"
                                        : "cei.config.value.off");
            }
            if (kind == 2) {
                int i = Math.max(0, Math.min(choices.length - 1, v));
                return CeiText.t("cei.config.value." + choices[i]);
            }
            return String.valueOf(v);
        }

        void cycle(int sens) {
            int v = get.getAsInt();
            if (kind == 0) {
                set.accept(v == 1 ? 0 : 1);
            } else if (kind == 2) {
                int n = choices.length;
                set.accept(((v + sens) % n + n) % n);
            } else {
                int nv = v + sens * step;
                if (nv < min) nv = min;
                if (nv > ceiling()) nv = ceiling();
                set.accept(nv);
            }
        }

        /** Position 0..1 du curseur sur sa piste. */
        float ratio() {
            int haut = ceiling();
            if (haut <= min) return 0.0f;
            return (float) (get.getAsInt() - min) / (float) (haut - min);
        }

        /** Poser la valeur depuis une position 0..1, arrondie au pas. */
        void fromRatio(float r) {
            int haut = ceiling();
            if (r < 0.0f) r = 0.0f;
            if (r > 1.0f) r = 1.0f;
            int brut = Math.round(min + r * (haut - min));
            int cale = min + Math.round((brut - min) / (float) step) * step;
            if (cale < min) cale = min;
            if (cale > haut) cale = haut;
            set.accept(cale);
        }
    }

    private final List<Opt> options = new ArrayList<>();
    private Cat active = Cat.GENERAL;

    private String query = "";
    private boolean queryFocus = false;
    private int scroll = 0;

    /** Geometrie, memorisee AU DESSIN et relue par le clic. */
    private int listX, listY, listW, listH, rowH = 18;
    private int catX, catY, catW;
    private int searchX, searchY, searchW, searchH;
    private int applyX, cancelX, resetX, buttonY, buttonW = 74, buttonH = 20;
    private final List<int[]> catHits = new ArrayList<>();

    /** Largeur de la piste des options numeriques. */
    private static final int TRACK_W = 90;

    public CeiConfigScreen(Screen parent) {
        super(Component.literal("CEI"));
        this.parent = parent;
        this.config = CeiConfig.getInstance();
        buildOptions();
    }

    private void add(Cat cat, int groupe, String key,
                     IntSupplier get, IntConsumer set) {
        options.add(new Opt(cat, groupe, key, 0, 0, 1, 1, 1, null, get, set));
    }

    private void add(Cat cat, int groupe, String key, String[] choices,
                     IntSupplier get, IntConsumer set) {
        options.add(new Opt(cat, groupe, key, 2, 0, choices.length - 1,
                            choices.length - 1, 1, choices, get, set));
    }

    private void add(Cat cat, int groupe, String key, int min, int max,
                     int maxFree, int step, IntSupplier get, IntConsumer set) {
        options.add(new Opt(cat, groupe, key, 1, min, max, maxFree, step, null,
                            get, set));
    }

    private void buildOptions() {
        final CeiConfig c = this.config;
        add(Cat.GENERAL, 1, "panelOnLeft", new String[]{"right", "left"},
            () -> c.isPanelOnLeft() ? 1 : 0, v -> c.setPanelOnLeft(v == 1));
        add(Cat.GENERAL, 1, "panelWidth", 80, 200, 200, 10,
            () -> c.getPanelWidth(), v -> c.setPanelWidth(v));
        add(Cat.GENERAL, 2, "showFavoritesByDefault",
            () -> c.isShowFavoritesByDefault() ? 1 : 0, v -> c.setShowFavoritesByDefault(v == 1));
        add(Cat.GENERAL, 2, "showHelpPopup",
            () -> c.isShowHelpPopup() ? 1 : 0, v -> c.setShowHelpPopup(v == 1));
        add(Cat.INTERFACE, 1, "enableAnimations",
            () -> c.isEnableAnimations() ? 1 : 0, v -> c.setEnableAnimations(v == 1));
        add(Cat.INTERFACE, 1, "animationSpeed", 50, 200, 300, 10,
            () -> (int) (c.getAnimationSpeed() * 100.0f), v -> c.setAnimationSpeed(v / 100.0f));
        add(Cat.INTERFACE, 2, "showCalcButton",
            () -> c.isShowCalcButton() ? 1 : 0, v -> c.setShowCalcButton(v == 1));
        add(Cat.INTERFACE, 2, "showPinnedCards",
            () -> c.isShowPinnedCards() ? 1 : 0, v -> c.setShowPinnedCards(v == 1));
        add(Cat.INTERFACE, 3, "showTabDescription",
            () -> c.isShowTabDescription() ? 1 : 0, v -> c.setShowTabDescription(v == 1));
        add(Cat.INTERFACE, 3, "showTabLoot",
            () -> c.isShowTabLoot() ? 1 : 0, v -> c.setShowTabLoot(v == 1));
        add(Cat.INTERFACE, 3, "showTabWorld",
            () -> c.isShowTabWorld() ? 1 : 0, v -> c.setShowTabWorld(v == 1));
        add(Cat.FEATURES, 1, "featureCraftTree",
            () -> c.isFeatureCraftTree() ? 1 : 0, v -> c.setFeatureCraftTree(v == 1));
        add(Cat.FEATURES, 1, "featureLootSources",
            () -> c.isFeatureLootSources() ? 1 : 0, v -> c.setFeatureLootSources(v == 1));
        add(Cat.FEATURES, 1, "featureBlockGeneration",
            () -> c.isFeatureBlockGeneration() ? 1 : 0, v -> c.setFeatureBlockGeneration(v == 1));
        add(Cat.FEATURES, 1, "featureBrewing",
            () -> c.isFeatureBrewing() ? 1 : 0, v -> c.setFeatureBrewing(v == 1));
        add(Cat.FEATURES, 1, "featureDescriptions",
            () -> c.isFeatureDescriptions() ? 1 : 0, v -> c.setFeatureDescriptions(v == 1));
        add(Cat.FEATURES, 1, "featureFavorites",
            () -> c.isFeatureFavorites() ? 1 : 0, v -> c.setFeatureFavorites(v == 1));
        add(Cat.FEATURES, 2, "rendererCrafting",
            () -> c.isRendererCrafting() ? 1 : 0, v -> c.setRendererCrafting(v == 1));
        add(Cat.FEATURES, 2, "rendererSmelting",
            () -> c.isRendererSmelting() ? 1 : 0, v -> c.setRendererSmelting(v == 1));
        add(Cat.FEATURES, 2, "rendererSmithing",
            () -> c.isRendererSmithing() ? 1 : 0, v -> c.setRendererSmithing(v == 1));
        add(Cat.FEATURES, 2, "rendererStonecutter",
            () -> c.isRendererStonecutter() ? 1 : 0, v -> c.setRendererStonecutter(v == 1));
        add(Cat.FEATURES, 2, "rendererBrewing",
            () -> c.isRendererBrewing() ? 1 : 0, v -> c.setRendererBrewing(v == 1));
        add(Cat.FEATURES, 2, "rendererCustomMachine",
            () -> c.isRendererCustomMachine() ? 1 : 0, v -> c.setRendererCustomMachine(v == 1));
        add(Cat.PERF, 1, "warmupEnabled",
            () -> c.isWarmupEnabled() ? 1 : 0, v -> c.setWarmupEnabled(v == 1));
        add(Cat.PERF, 1, "warmupBudgetMs", 1, 8, 50, 1,
            () -> c.getWarmupBudgetMs(), v -> c.setWarmupBudgetMs(v));
        add(Cat.PERF, 2, "craftTreeDepth", 1, 8, 32, 1,
            () -> c.getCraftTreeDepth(), v -> c.setCraftTreeDepth(v));
        add(Cat.PERF, 2, "useNewRecipeRenderer",
            () -> c.isUseNewRecipeRenderer() ? 1 : 0, v -> c.setUseNewRecipeRenderer(v == 1));
        add(Cat.ADVANCED, 1, "devMode",
            () -> c.isDevMode() ? 1 : 0, v -> c.setDevMode(v == 1));
        add(Cat.ADVANCED, 1, "diagnostics",
            () -> c.isDiagnostics() ? 1 : 0, v -> c.setDiagnostics(v == 1));
        add(Cat.ADVANCED, 2, "unlockLimits",
            () -> c.isUnlockLimits() ? 1 : 0, v -> c.setUnlockLimits(v == 1));
    }

    /** Les options visibles : celles de la categorie, ou celles qui repondent
     *  a la recherche -- auquel cas la categorie ne compte plus. */
    private List<Opt> visible() {
        List<Opt> out = new ArrayList<>();
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        for (Opt o : options) {
            if (q.isEmpty()) {
                if (o.cat == active) out.add(o);
            } else if (o.label().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || o.tip().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(o);
            }
        }
        return out;
    }

    // ------------------------------------------------------------- le dessin

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean cei$trace = traced < 2;
        if (cei$trace) CEI_CFG.info("[cei-cfg] image {} : avant le fond", traced);
        context.fill(0, 0, this.width, this.height, 0xB0000000);
        if (cei$trace) CEI_CFG.info("[cei-cfg] image {} : fond dessine", traced);

        int margin = 16;
        int x0 = margin;
        int y0 = margin;
        int x1 = this.width - margin;
        int y1 = this.height - margin;

        // --- barre du haut -------------------------------------------------
        searchX = x0;
        searchY = y0;
        searchH = 22;
        searchW = (x1 - x0) - 150;
        drawPanel(context, searchX, searchY, searchW, searchH,
                  queryFocus ? 0xD9222222 : 0xD9141414);
        String shown = query.isEmpty() && !queryFocus
                ? CeiText.t("cei.config.search") : query;
        int shownColor = query.isEmpty() && !queryFocus ? 0xFF77777F : 0xFFFFFFFF;
        context.drawString(this.font, shown, searchX + 7,
                           searchY + (searchH - 8) / 2, shownColor, false);
        if (queryFocus && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cx = searchX + 7 + this.font.width(query) + 1;
            context.fill(cx, searchY + 5, cx + 1, searchY + searchH - 5, 0xFFFFFFFF);
        }

        String titre = CeiText.t("cei.config.title");
        int tw = this.font.width(titre);
        context.drawString(this.font, titre, x1 - tw, y0 + (searchH - 8) / 2,
                           0xFFFFD700, false);

        // --- colonne des categories ---------------------------------------
        catX = x0;
        catY = y0 + searchH + 6;
        catW = 132;
        catHits.clear();
        int cy = catY;
        for (Cat cat : Cat.values()) {
            boolean sel = (cat == active) && query.trim().isEmpty();
            boolean hov = mouseX >= catX && mouseX < catX + catW
                    && mouseY >= cy && mouseY < cy + 20;
            drawPanel(context, catX, cy, catW, 20,
                      sel ? 0xD9222222 : (hov ? 0xAA2D2D2D : 0xD9141414));
            if (sel) context.fill(catX, cy, catX + 2, cy + 20, 0xFFFFD700);
            context.drawString(this.font, cat.label(), catX + 10, cy + 6,
                               sel ? 0xFFFFD700 : 0xFFCCCCCC, false);
            catHits.add(new int[]{catX, cy, catW, 20, cat.ordinal()});
            cy += 22;
        }

        // --- panneau des options ------------------------------------------
        listX = catX + catW + 6;
        listY = catY;
        listW = x1 - listX;
        buttonY = y1 - buttonH;
        listH = (buttonY - 10) - listY - 22;
        drawPanel(context, listX, listY, listW, listH, 0xD9141414);

        List<Opt> vis = visible();
        int perPage = Math.max(1, listH / rowH);
        int maxScroll = Math.max(0, vis.size() - perPage);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        Opt survolee = null;
        context.enableScissor(listX, listY, listX + listW, listY + listH);
        int ry = listY + 1;
        int prevGroupe = -1;
        for (int i = scroll; i < vis.size() && ry + rowH <= listY + listH; i++) {
            Opt o = vis.get(i);
            if (prevGroupe != -1 && o.groupe != prevGroupe) {
                context.fill(listX + 6, ry, listX + listW - 6, ry + 1, 0x22FFFFFF);
            }
            prevGroupe = o.groupe;

            boolean hov = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= ry && mouseY < ry + rowH;
            if (hov) {
                context.fill(listX + 1, ry, listX + listW - 1, ry + rowH, 0x22FFFFFF);
                survolee = o;
            }
            context.drawString(this.font, o.label(), listX + 10, ry + 5,
                               0xFFEEEEEE, false);
            String val = o.value();
            int vw = this.font.width(val);
            int couleur = o.kind == 0
                    ? (o.get.getAsInt() == 1 ? 0xFF7ED07E : 0xFF9A9AA2)
                    : 0xFFDDDDDD;
            context.drawString(this.font, val, listX + listW - 10 - vw, ry + 5,
                               couleur, false);

            // La piste, seulement au survol : hors survol la ligne reste sobre,
            // exactement comme le reglage de taille d'interface du jeu.
            if (hov && o.kind == 1) {
                int tx = trackX(listX, listW, vw);
                int ty = ry + rowH / 2;
                context.fill(tx, ty, tx + TRACK_W, ty + 1, 0x66FFFFFF);
                int kx = tx + Math.round(o.ratio() * (TRACK_W - 4));
                context.fill(kx, ty - 5, kx + 4, ty + 6, 0xFFFFD700);
            }
            ry += rowH;
        }
        context.disableScissor();

        if (vis.size() > perPage) {
            String pos = (scroll + 1) + "-"
                    + Math.min(vis.size(), scroll + perPage) + "/" + vis.size();
            int pw = this.font.width(pos);
            context.drawString(this.font, pos, listX + listW - 10 - pw,
                               listY + listH + 6, 0xFF888888, false);
            drawTri(context, listX + listW - 24 - pw, listY + listH + 6, true);
            drawTri(context, listX + listW - 16 - pw, listY + listH + 6, false);
        }

        if (survolee != null) {
            context.drawString(this.font, survolee.tip(), listX,
                               listY + listH + 6, 0xFFAAAAAA, false);
        } else if (vis.isEmpty()) {
            context.drawString(this.font, CeiText.t("cei.config.empty"), listX,
                               listY + listH + 6, 0xFFAA6666, false);
        }

        // --- boutons du bas -----------------------------------------------
        resetX = x1 - buttonW;
        cancelX = resetX - buttonW - 6;
        applyX = cancelX - buttonW - 6;
        drawButton(context, applyX, buttonY, CeiText.t("cei.config.apply"),
                   mouseX, mouseY, true);
        drawButton(context, cancelX, buttonY, CeiText.t("cei.config.cancel"),
                   mouseX, mouseY, false);
        drawButton(context, resetX, buttonY, CeiText.t("cei.config.default"),
                   mouseX, mouseY, false);

        super.render(context, mouseX, mouseY, delta);
        if (cei$trace) {
            CEI_CFG.info("[cei-cfg] image {} : widgets dessines", traced);
            traced++;
        }
    }

    /** Ou commence la piste. Une seule source : le dessin et le clic la lisent. */
    private int trackX(int lx, int lw, int valueWidth) {
        return lx + lw - 10 - valueWidth - 8 - TRACK_W;
    }

    private void drawPanel(GuiGraphics context, int x, int y, int w, int h, int bg) {
        context.fill(x, y, x + w, y + h, bg);
        context.renderOutline(x, y, w, h, 0x33FFFFFF);
    }

    private void drawButton(GuiGraphics context, int x, int y, String label,
                            int mouseX, int mouseY, boolean accent) {
        boolean hov = mouseX >= x && mouseX < x + buttonW
                && mouseY >= y && mouseY < y + buttonH;
        drawPanel(context, x, y, buttonW, buttonH,
                  hov ? 0xAA2D2D2D : 0xD9141414);
        int lw = this.font.width(label);
        context.drawString(this.font, label, x + (buttonW - lw) / 2, y + 6,
                           accent ? 0xFFFFD700 : 0xFFEEEEEE, false);
    }

    private void drawTri(GuiGraphics context, int x, int y, boolean haut) {
        int c = 0xFFAAAAAA;
        if (haut) {
            context.fill(x + 3, y, x + 4, y + 1, c);
            context.fill(x + 2, y + 1, x + 5, y + 2, c);
            context.fill(x + 1, y + 2, x + 6, y + 3, c);
            context.fill(x, y + 3, x + 7, y + 4, c);
        } else {
            context.fill(x, y, x + 7, y + 1, c);
            context.fill(x + 1, y + 1, x + 6, y + 2, c);
            context.fill(x + 2, y + 2, x + 5, y + 3, c);
            context.fill(x + 3, y + 3, x + 4, y + 4, c);
        }
    }

    // ---------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= searchX && mouseX < searchX + searchW
                && mouseY >= searchY && mouseY < searchY + searchH) {
            queryFocus = true;
            return true;
        }
        queryFocus = false;

        for (int[] hit : catHits) {
            if (mouseX >= hit[0] && mouseX < hit[0] + hit[2]
                    && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                active = Cat.values()[hit[4]];
                query = "";
                scroll = 0;
                click();
                return true;
            }
        }

        if (mouseX >= applyX && mouseX < applyX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + buttonH) {
            config.save();
            click();
            this.minecraft.setScreen(parent);
            return true;
        }
        if (mouseX >= cancelX && mouseX < cancelX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + buttonH) {
            config.load();
            click();
            this.minecraft.setScreen(parent);
            return true;
        }
        if (mouseX >= resetX && mouseX < resetX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + buttonH) {
            config.reset();
            click();
            return true;
        }

        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int i = scroll + (int) ((mouseY - listY - 1) / rowH);
            List<Opt> vis = visible();
            if (i >= 0 && i < vis.size()) {
                Opt o = vis.get(i);
                // Sur la piste : on se place ou l'on clique. Ailleurs sur la
                // ligne : un cran, avant au clic gauche, arriere au clic droit.
                if (o.kind == 1) {
                    int tx = trackX(listX, listW, this.font.width(o.value()));
                    if (mouseX >= tx && mouseX <= tx + TRACK_W) {
                        o.fromRatio((float) (mouseX - tx) / (float) TRACK_W);
                        click();
                        return true;
                    }
                }
                o.cycle(button == 1 ? -1 : 1);
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        // Glisser sur une piste : la valeur suit la souris.
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int i = scroll + (int) ((mouseY - listY - 1) / rowH);
            List<Opt> vis = visible();
            if (i >= 0 && i < vis.size() && vis.get(i).kind == 1) {
                Opt o = vis.get(i);
                int tx = trackX(listX, listW, this.font.width(o.value()));
                o.fromRatio((float) (mouseX - tx) / (float) TRACK_W);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            scroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (queryFocus && chr >= ' ' && chr != 127) {
            query = query + chr;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (queryFocus) {
            if (keyCode == 259 && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                queryFocus = false;
                return true;
            }
            if (keyCode == 256) {
                if (!query.isEmpty()) { query = ""; scroll = 0; return true; }
                queryFocus = false;
                return true;
            }
        }
        if (keyCode == 265) { scroll--; return true; }
        if (keyCode == 264) { scroll++; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void click() {
        try {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } catch (Exception | LinkageError e) {
            // Pas de son : ce n'est pas une raison d'avaler le clic.
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY,
                                 float delta) {
        // Volontairement vide : voir le voile dessine en tete de render().
        // Le laisser faire son travail peint le flou par-dessus l'interface.
    }

    @Override
    public boolean isPauseScreen() {
        // FAUX, et ce n'est pas un detail : un ecran qui met le jeu en pause
        // declenche, en solo, la sauvegarde de TOUTES les dimensions.
        return false;
    }

    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }
}
