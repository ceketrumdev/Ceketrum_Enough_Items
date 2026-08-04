package com.ceketrum.cei.data;

import com.ceketrum.cei.gui.screen.CeiItemInfoScreen;
import com.ceketrum.cei.util.PlatformHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the global state of multiple pinned recipe cards.
 * Shared across CeiItemInfoScreen, screen mixins, and HUD overlays.
 */
public class PinnedRecipeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-pinned");
    private static final String STATE_FILE = "pinned_recipes.json";

    private static PinnedRecipeManager instance;

    /**
     * Fichier d'etat, a cote de favorites.json. Global au client comme les
     * favoris : ce qui est epingle l'est pour toutes les parties.
     */
    private final Path stateFile;
    /** Dernier contenu ecrit : evite de reecrire le fichier pour rien. */
    private String lastSavedJson = null;
    /** Vrai pendant load() : empeche les setters de relancer une sauvegarde. */
    private boolean loading = false;
    
    private final List<PinnedCard> pinnedCards = new ArrayList<>();
    private PinnedCard activeDraggingCard = null;
    
    public static class PinnedCard {
        /**
         * Taille par defaut d'une fiche. Source unique : CeiItemInfoScreen
         * initialise containerWidth/containerHeight a partir d'ici.
         */
        public static final int DEFAULT_WIDTH = 240;
        public static final int DEFAULT_HEIGHT = 180;
        /** En dessous, la grille de craft et la pagination ne tiennent plus. */
        public static final int MIN_WIDTH = 190;
        public static final int MIN_HEIGHT = 140;

        private final ItemStack targetStack;
        private CeiItemInfoScreen.TabType activeTab;
        private CeiItemInfoScreen.RecipeCategory activeCategory;
        private int currentPage;
        
        private double xOffset;
        private double yOffset;

        // Taille de la fiche. cardWidth/cardHeight sont ce qui est dessine.
        // rawWidth/rawHeight suivent la souris sans borne pendant le glisser :
        // quand le curseur repart en arriere apres avoir bute sur une borne,
        // la fiche redemarre au pixel exact au lieu de repartir de la borne.
        private int cardWidth = DEFAULT_WIDTH;
        private int cardHeight = DEFAULT_HEIGHT;
        private double rawWidth = DEFAULT_WIDTH;
        private double rawHeight = DEFAULT_HEIGHT;
        private boolean resizing = false;
        // Coin haut-gauche fige au moment de la saisie de la poignee : c'est
        // lui qui reste immobile pendant que le coin bas-droit suit la souris.
        private int resizeAnchorX;
        private int resizeAnchorY;

        private float opacity = 1.0f;
        private boolean showInHud = false;
        
        private CeiItemInfoScreen screenInstance;
        
        public PinnedCard(ItemStack targetStack, CeiItemInfoScreen.TabType activeTab, CeiItemInfoScreen.RecipeCategory activeCategory, int currentPage, double xOffset, double yOffset) {
            this.targetStack = targetStack.copy();
            this.activeTab = activeTab;
            this.activeCategory = activeCategory;
            this.currentPage = currentPage;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
        
        public ItemStack getTargetStack() { return targetStack; }
        public CeiItemInfoScreen.TabType getActiveTab() { return activeTab; }
        public void setActiveTab(CeiItemInfoScreen.TabType activeTab) {
            this.activeTab = activeTab;
            PinnedRecipeManager.getInstance().saveIfChanged();
        }
        public CeiItemInfoScreen.RecipeCategory getActiveCategory() { return activeCategory; }
        public void setActiveCategory(CeiItemInfoScreen.RecipeCategory activeCategory) {
            this.activeCategory = activeCategory;
            PinnedRecipeManager.getInstance().saveIfChanged();
        }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
            PinnedRecipeManager.getInstance().saveIfChanged();
        }
        
        public double getxOffset() { return xOffset; }
        public void setxOffset(double xOffset) { this.xOffset = xOffset; }
        public double getyOffset() { return yOffset; }
        public void setyOffset(double yOffset) { this.yOffset = yOffset; }

        public int getCardWidth() { return cardWidth; }
        public int getCardHeight() { return cardHeight; }

        public boolean isResizing() { return resizing; }
        public void setResizing(boolean resizing) {
            this.resizing = resizing;
            // Au relachement seulement : pendant le glisser la taille change a
            // chaque image, on n'ecrit pas le fichier soixante fois par seconde.
            if (!resizing) PinnedRecipeManager.getInstance().saveIfChanged();
        }

        public int getResizeAnchorX() { return resizeAnchorX; }
        public int getResizeAnchorY() { return resizeAnchorY; }

        /**
         * Saisie de la poignee : on fige le coin haut-gauche et on repart de
         * la taille courante.
         */
        public void beginResize(int anchorX, int anchorY) {
            this.resizing = true;
            this.resizeAnchorX = anchorX;
            this.resizeAnchorY = anchorY;
            this.rawWidth = this.cardWidth;
            this.rawHeight = this.cardHeight;
        }

        /**
         * Applique un deplacement de souris a la taille. Les bornes hautes
         * sont passees par l'ecran : lui seul connait la taille de la fenetre.
         */
        public void resizeBy(double deltaX, double deltaY, int maxWidth, int maxHeight) {
            this.rawWidth += deltaX;
            this.rawHeight += deltaY;
            this.cardWidth = clampInt((int) Math.round(this.rawWidth),
                    MIN_WIDTH, Math.max(MIN_WIDTH, maxWidth));
            this.cardHeight = clampInt((int) Math.round(this.rawHeight),
                    MIN_HEIGHT, Math.max(MIN_HEIGHT, maxHeight));
        }

        private static int clampInt(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
        
        public float getOpacity() { return opacity; }
        public void cycleOpacity() {
            if (opacity == 1.0f) {
                opacity = 0.75f;
            } else if (opacity == 0.75f) {
                opacity = 0.5f;
            } else if (opacity == 0.5f) {
                opacity = 0.25f;
            } else {
                opacity = 1.0f;
            }
            PinnedRecipeManager.getInstance().saveIfChanged();
        }
        
        public boolean isShowInHud() { return showInHud; }
        public void setShowInHud(boolean showInHud) {
            this.showInHud = showInHud;
            PinnedRecipeManager.getInstance().saveIfChanged();
        }
        
        public CeiItemInfoScreen getScreenInstance() { return screenInstance; }
        public void setScreenInstance(CeiItemInfoScreen screenInstance) { this.screenInstance = screenInstance; }

        public boolean isDragging() {
            return PinnedRecipeManager.getInstance().getActiveDraggingCard() == this;
        }

        public void setDragging(boolean dragging) {
            if (dragging) {
                PinnedRecipeManager.getInstance().setActiveDraggingCard(this);
            } else {
                if (PinnedRecipeManager.getInstance().getActiveDraggingCard() == this) {
                    PinnedRecipeManager.getInstance().setActiveDraggingCard(null);
                }
                // Au relachement seulement, pour la meme raison qu'au
                // redimensionnement : la position bouge a chaque image.
                PinnedRecipeManager.getInstance().saveIfChanged();
            }
        }
    }
    
    private PinnedRecipeManager() {
        Path dir = PlatformHelper.getConfigDirectory().resolve("cei");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Impossible de creer le dossier de configuration", e);
        }
        this.stateFile = dir.resolve(STATE_FILE);
        load();
    }

    /**
     * Ecrit l'etat des fiches -- et seulement s'il a change depuis la derniere
     * ecriture.
     *
     * Sans cette comparaison, un simple changement d'onglet reecrirait le
     * fichier trois fois de suite : updatePinnedState() repose l'onglet, la
     * categorie et la page a chaque clic.
     */
    public void saveIfChanged() {
        if (loading) return;

        JsonArray cards = new JsonArray();
        for (PinnedCard card : pinnedCards) {
            var itemId = net.minecraft.registry.Registries.ITEM.getId(card.targetStack.getItem());
            if (itemId == null) continue;

            JsonObject o = new JsonObject();
            o.addProperty("item", itemId.toString());
            o.addProperty("x", card.xOffset);
            o.addProperty("y", card.yOffset);
            o.addProperty("w", card.cardWidth);
            o.addProperty("h", card.cardHeight);
            o.addProperty("opacity", card.opacity);
            o.addProperty("hud", card.showInHud);
            if (card.activeTab != null) {
                o.addProperty("tab", card.activeTab.name());
            }
            if (card.activeCategory != null) {
                o.addProperty("category", card.activeCategory.type.name());
                if (card.activeCategory.customRecipeTypeId != null) {
                    o.addProperty("categoryId", card.activeCategory.customRecipeTypeId.toString());
                }
            }
            o.addProperty("page", card.currentPage);
            cards.add(o);
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("cards", cards);

        String json = root.toString();
        if (json.equals(lastSavedJson)) return;

        try {
            Files.writeString(stateFile, json);
            lastSavedJson = json;
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la sauvegarde des fiches epinglees dans {}", stateFile, e);
        }
    }

    /**
     * Relit l'etat, une seule fois, a la construction du gestionnaire.
     *
     * Ce moment est celui du premier affichage en jeu -- le HUD et les mixins
     * d'ecran sont les seuls appelants de getInstance() -- donc les registres
     * contiennent deja les items des mods.
     *
     * Un item introuvable fait sauter sa fiche en silence : c'est le seul
     * comportement qui ne casse pas au chargement d'une partie ou le mod
     * concerne n'est plus la.
     */
    private void load() {
        if (!Files.exists(stateFile)) return;

        loading = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
            JsonArray saved = root.getAsJsonArray("cards");
            if (saved == null) return;

            pinnedCards.clear();
            for (JsonElement element : saved) {
                JsonObject o = element.getAsJsonObject();
                if (!o.has("item")) continue;

                var itemId = net.minecraft.util.Identifier.tryParse(o.get("item").getAsString());
                if (itemId == null) continue;
                var item = net.minecraft.registry.Registries.ITEM.getOrEmpty(itemId).orElse(null);
                if (item == null) continue;

                CeiItemInfoScreen.TabType tab = CeiItemInfoScreen.TabType.CRAFTING;
                if (o.has("tab")) {
                    try {
                        tab = CeiItemInfoScreen.TabType.valueOf(o.get("tab").getAsString());
                    } catch (IllegalArgumentException ignored) {
                        // Onglet disparu d'une version a l'autre : on retombe
                        // sur CRAFTING plutot que de perdre la fiche.
                    }
                }

                CeiItemInfoScreen.RecipeCategory category = null;
                if (o.has("category")) {
                    try {
                        CeiItemInfoScreen.CategoryType type =
                                CeiItemInfoScreen.CategoryType.valueOf(o.get("category").getAsString());
                        var customId = o.has("categoryId")
                                ? net.minecraft.util.Identifier.tryParse(o.get("categoryId").getAsString())
                                : null;
                        category = customId != null
                                ? new CeiItemInfoScreen.RecipeCategory(type, customId)
                                : new CeiItemInfoScreen.RecipeCategory(type);
                    } catch (IllegalArgumentException ignored) {
                        // Idem : categorie inconnue, l'ecran reprendra la premiere.
                    }
                }

                PinnedCard card = new PinnedCard(
                        new ItemStack(item), tab, category,
                        o.has("page") ? o.get("page").getAsInt() : 0,
                        o.has("x") ? o.get("x").getAsDouble() : 0.0,
                        o.has("y") ? o.get("y").getAsDouble() : 0.0);

                // Ecriture directe des champs : passer par les setters
                // relancerait une sauvegarde a chaque fiche relue.
                if (o.has("w")) {
                    card.cardWidth = Math.max(PinnedCard.MIN_WIDTH, o.get("w").getAsInt());
                }
                if (o.has("h")) {
                    card.cardHeight = Math.max(PinnedCard.MIN_HEIGHT, o.get("h").getAsInt());
                }
                card.rawWidth = card.cardWidth;
                card.rawHeight = card.cardHeight;
                if (o.has("opacity")) card.opacity = o.get("opacity").getAsFloat();
                if (o.has("hud")) card.showInHud = o.get("hud").getAsBoolean();

                pinnedCards.add(card);
            }
            LOGGER.info("Charge {} fiche(s) epinglee(s) depuis {}", pinnedCards.size(), stateFile);
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement des fiches epinglees depuis {}", stateFile, e);
        } finally {
            loading = false;
        }
    }
    
    public static synchronized PinnedRecipeManager getInstance() {
        if (instance == null) {
            instance = new PinnedRecipeManager();
        }
        return instance;
    }
    
    public List<PinnedCard> getPinnedCards() {
        return pinnedCards;
    }
    
    public PinnedCard getActiveDraggingCard() {
        return activeDraggingCard;
    }
    
    public void setActiveDraggingCard(PinnedCard card) {
        this.activeDraggingCard = card;
    }
    
    public boolean isPinned(ItemStack stack) {
        if (stack == null) return false;
        for (PinnedCard card : pinnedCards) {
            if (card.getTargetStack().isOf(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
    
    public PinnedCard getPinnedCard(ItemStack stack) {
        if (stack == null) return null;
        for (PinnedCard card : pinnedCards) {
            if (card.getTargetStack().isOf(stack.getItem())) {
                return card;
            }
        }
        return null;
    }
    
    public void pinRecipe(ItemStack stack, CeiItemInfoScreen.TabType tab, CeiItemInfoScreen.RecipeCategory cat, int page, CeiItemInfoScreen screen) {
        if (stack == null) return;
        
        // Remove if already pinned to toggle it off
        PinnedCard existing = getPinnedCard(stack);
        if (existing != null) {
            pinnedCards.remove(existing);
            saveIfChanged();
            return;
        }
        
        // Offset each new card slightly so they don't stack directly on top of each other
        double offset = pinnedCards.size() * 15.0;
        PinnedCard card = new PinnedCard(stack, tab, cat, page, offset, offset);
        card.setScreenInstance(screen);
        pinnedCards.add(card);
        saveIfChanged();
    }
    
    public void unpinRecipe(ItemStack stack) {
        PinnedCard existing = getPinnedCard(stack);
        if (existing != null) {
            pinnedCards.remove(existing);
            if (activeDraggingCard == existing) {
                activeDraggingCard = null;
            }
            saveIfChanged();
        }
    }
    
    public void bringToFront(PinnedCard card) {
        if (pinnedCards.contains(card)) {
            pinnedCards.remove(card);
            pinnedCards.add(card); // Adds to end of list (topmost)
            saveIfChanged();
        }
    }
}


