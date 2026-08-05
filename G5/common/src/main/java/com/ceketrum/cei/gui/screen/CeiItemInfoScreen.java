package com.ceketrum.cei.gui.screen;

import com.ceketrum.cei.i18n.CeiText;
import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.data.LootTableSourceManager;
import com.ceketrum.cei.data.BlockGenerationManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.ceketrum.cei.gui.constants.GuiConstants;
import com.ceketrum.cei.gui.util.GuiRenderHelper;
import com.ceketrum.cei.gui.util.TextRenderHelper;
import com.ceketrum.cei.gui.module.cei.components.RecipePopupRenderer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.Collection;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;

/**
 * Premium glassmorphic recipe book and info screen.
 * Accessible by pressing R or U keys on any item.
 */
public class CeiItemInfoScreen extends Screen {
    private Screen parentScreen;

    public void setParentScreen(Screen parentScreen) {
        this.parentScreen = parentScreen;
    }
    private final ItemStack targetStack;
    private boolean isUsage;
    private com.ceketrum.cei.gui.module.cei.CeiModule ceiModule;

    // Position and size
    private int containerWidth = com.ceketrum.cei.data.PinnedRecipeManager.PinnedCard.DEFAULT_WIDTH;
    private int containerHeight = com.ceketrum.cei.data.PinnedRecipeManager.PinnedCard.DEFAULT_HEIGHT;
    private int containerX;
    private int containerY;

    /** Cote de la zone sensible de la poignee de redimensionnement, en pixels. */
    private static final int RESIZE_GRIP = 12;

    // --- calculateur de composants ---------------------------------------
    /**
     * Etat du calculateur. Tant que calcOpen est faux, rien de ce mecanisme
     * n'est evalue : le test tient en une lecture de booleen, en tete de
     * drawTabContent.
     */
    private boolean calcOpen = false;
    private int calcQty = 1;
    /** Branches repliees, par cle de chemin. */
    private final java.util.Set<Long> calcCollapsed = new java.util.HashSet<>();
    /**
     * Vrai une fois le repli initial pose.
     *
     * A l'ouverture, TOUTES les branches sont repliees : la profondeur est
     * maximale, et c'est le repli -- non un reglage de profondeur -- qui tient
     * l'affichage court. Le semis n'a lieu qu'une fois par ouverture, sans
     * quoi changer la quantite rabattrait ce que le joueur vient d'ouvrir.
     */
    private boolean calcSeeded = false;
    /**
     * Premiere ligne visible.
     *
     * Le deplacement se fait aux fleches et a la molette. PAS de barre de
     * defilement : c'est une preference etablie du proprietaire du mod, notee
     * ici pour qu'elle ne soit pas reintroduite par megarde.
     */
    private int calcScroll = 0;
    /**
     * Zones sensibles, memorisees AU DESSIN.
     *
     * Le clic les relit telles quelles : une position calculee deux fois finit
     * toujours par diverger, la lecon du bouton "+" a suffi.
     */
    private int calcMinusX = -1, calcMinusY = -1;
    private int calcPlusX = -1, calcPlusY = -1;
    private int calcAreaX = -1, calcAreaY = -1, calcAreaW = 0, calcAreaH = 0;
    private int calcUpX = -1, calcUpY = -1, calcDownX = -1, calcDownY = -1;
    private boolean calcHasArrows = false;
    /** Bande des lignes : la molette y defile, ailleurs elle regle la quantite. */
    private int calcRowsY = -1, calcRowsH = 0;
    /** Chevrons de repli : {x, y, largeur, hauteur, cle}, memorises AU DESSIN. */
    private final java.util.List<long[]> calcChevrons = new java.util.ArrayList<>();


    /**
     * Hauteur minimale laissee a la recette. En dessous, le texte de l'item ne
     * prend aucune place : c'est la recette qui prime.
     */
    private static final int RECIPE_MIN_H = 92;
    /** Au-dela, une infobulle moddee volumineuse mangerait toute la fiche. */
    private static final int DESC_MAX_LINES = 24;

    /**
     * Zone de texte sous la recette : defilement, et bornes memorisees au
     * dessin. C'est ce qui a ete reellement dessine qui borne la molette,
     * jamais un second calcul de mise en page.
     */
    private int descScroll = 0;
    private int descBandX = 0;
    private int descBandY = 0;
    private int descBandW = 0;
    private int descBandH = 0;
    private int descTotalH = 0;

    // Tabs configuration
    public enum TabType {
        DESCRIPTION,
        CRAFTING,
        USAGES,
        LOOT,
        WORLD
    }

    public enum CategoryType {
        CRAFTING,
        SMELTING,
        BREWING,
        STONECUTTING,
        SMITHING,
        CUSTOM
    }

    public static class RecipeCategory {
        public final CategoryType type;
        public final ResourceLocation customRecipeTypeId; // null if vanilla category

        public RecipeCategory(CategoryType type) {
            this.type = type;
            this.customRecipeTypeId = null;
        }

        public RecipeCategory(CategoryType type, ResourceLocation customRecipeTypeId) {
            this.type = type;
            this.customRecipeTypeId = customRecipeTypeId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecipeCategory that = (RecipeCategory) o;
            if (type != that.type) return false;
            return java.util.Objects.equals(customRecipeTypeId, that.customRecipeTypeId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(type, customRecipeTypeId);
        }
    }

    private final List<TabType> visibleMainTabs = new ArrayList<>();
    private TabType activeMainTab;

    private final List<RecipeCategory> categories = new ArrayList<>();
    private RecipeCategory activeCategory;

    /** Onglets de categorie affiches simultanement dans la colonne de gauche. */
    /** Hauteur des boutons de defilement. */
    private static final int SCROLL_BTN_H = 12;
    /** Index du premier onglet affiche. */
    private int categoryScroll = 0;
    private int currentPage = 0;

    // Recipes database for target item (Output)
    private final List<RecipeHolder<?>> craftingRecipes = new ArrayList<>();
    private final List<RecipeHolder<?>> smeltingRecipes = new ArrayList<>();
    private final List<RecipeHolder<?>> smithingRecipes = new ArrayList<>();
    private final List<RecipeHolder<?>> stonecuttingRecipes = new ArrayList<>();
    private final List<BrewingRecipeManager.BrewingRecipe> brewingRecipes = new ArrayList<>();
    private final List<RecipeHolder<?>> customRecipes = new ArrayList<>();

    // Usages database for target item (Input)
    private final List<RecipeHolder<?>> craftingUsages = new ArrayList<>();
    private final List<RecipeHolder<?>> smeltingUsages = new ArrayList<>();
    private final List<RecipeHolder<?>> smithingUsages = new ArrayList<>();
    private final List<RecipeHolder<?>> stonecuttingUsages = new ArrayList<>();
    private final List<BrewingRecipeManager.BrewingRecipe> brewingUsages = new ArrayList<>();
    private final List<RecipeHolder<?>> customUsages = new ArrayList<>();

    // Active slots for click/hover tracking
    private static class RenderedSlot {
        public final ItemStack stack;
        public final int x;
        public final int y;
        public final int size;

        public RenderedSlot(ItemStack stack, int x, int y, int size) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    private final List<RenderedSlot> activeSlots = new ArrayList<>();
    private RenderedSlot hoveredSlot = null;

    public CeiItemInfoScreen(Screen parentScreen, ItemStack targetStack, boolean isUsage) {
        super(Component.literal(CeiText.t("cei.screen.item_info")));
        this.parentScreen = parentScreen;
        this.targetStack = targetStack.copy();
        this.isUsage = isUsage;

        // Restore pinned state if identical item is pinned
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(targetStack);
        if (card != null) {
            this.activeMainTab = card.getActiveTab();
            this.activeCategory = card.getActiveCategory();
            this.currentPage = card.getCurrentPage();
        } else {
            this.activeMainTab = isUsage ? TabType.USAGES : TabType.CRAFTING;
        }
    }

    @Override
    protected void init() {
        super.init();

        this.containerX = (this.width - this.containerWidth) / 2;
        this.containerY = (this.height - this.containerHeight) / 2;

        // Quality of Life: reuse parent screen's CeiModule to preserve search queries, scroll position, and favorites state!
        Screen moduleScreen = this.parentScreen != null ? this.parentScreen : this;
        this.ceiModule = com.ceketrum.cei.gui.util.CeiScreenHelper.getOrCreateModule(moduleScreen);
        this.ceiModule.init();

        // Scan recipes
        scanRecipes();

        // Update tabs visibility
        updateMainTabs();

        // Smart tab selection
        if (activeMainTab == TabType.CRAFTING && craftingRecipes.isEmpty() && smeltingRecipes.isEmpty() && brewingRecipes.isEmpty() && stonecuttingRecipes.isEmpty() && smithingRecipes.isEmpty() && customRecipes.isEmpty()) {
            boolean hasUsages = !craftingUsages.isEmpty() || !smeltingUsages.isEmpty() || !brewingUsages.isEmpty() || !stonecuttingUsages.isEmpty() || !smithingUsages.isEmpty() || !customUsages.isEmpty();
            if (hasUsages) {
                activeMainTab = TabType.USAGES;
            } else {
                activeMainTab = TabType.DESCRIPTION;
            }
        }
        if (activeMainTab == TabType.USAGES && craftingUsages.isEmpty() && smeltingUsages.isEmpty() && brewingUsages.isEmpty() && stonecuttingUsages.isEmpty() && smithingUsages.isEmpty() && customUsages.isEmpty()) {
            boolean hasRecipes = !craftingRecipes.isEmpty() || !smeltingRecipes.isEmpty() || !brewingRecipes.isEmpty() || !stonecuttingRecipes.isEmpty() || !smithingRecipes.isEmpty() || !customRecipes.isEmpty();
            if (hasRecipes) {
                activeMainTab = TabType.CRAFTING;
            } else {
                activeMainTab = TabType.DESCRIPTION;
            }
        }

        // Validate starting main tab
        if (!visibleMainTabs.contains(activeMainTab)) {
            activeMainTab = TabType.DESCRIPTION;
        }

        // Update categories
        updateCategories();

        // updateCategories() repart toujours de la premiere categorie et de la
        // page zero -- c'est ce qu'il faut pour un ecran ouvert a la volee.
        // Mais pour une fiche epinglee, ce qu'elle avait retenu vient d'etre
        // efface. On le repose, apres coup et seulement s'il existe encore :
        // une categorie disparue laisserait l'ecran sur du vide.
        var pinnedCard = com.ceketrum.cei.data.PinnedRecipeManager.getInstance()
                .getPinnedCard(this.targetStack);
        if (pinnedCard != null) {
            var savedCategory = pinnedCard.getActiveCategory();
            if (savedCategory != null && categories.contains(savedCategory)) {
                activeCategory = savedCategory;
                int savedPage = pinnedCard.getCurrentPage();
                if (savedPage >= 0 && savedPage < getMaxPages()) {
                    currentPage = savedPage;
                }
            }
        }
    }

    private void scanRecipes() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        craftingRecipes.clear();
        smeltingRecipes.clear();
        smithingRecipes.clear();
        stonecuttingRecipes.clear();
        brewingRecipes.clear();
        customRecipes.clear();

        craftingUsages.clear();
        smeltingUsages.clear();
        smithingUsages.clear();
        stonecuttingUsages.clear();
        brewingUsages.clear();
        customUsages.clear();

        // L'ancien code balayait toutes les recettes du pack en lancant DEUX
        // explorations reflexives par recette -- 244 ms mesurees sur G4 a chaque
        // ouverture de fiche. Le meme travail est desormais fait une seule fois
        // dans CeiRecipeIndex, et l'ouverture se resume a deux acces de table.
        com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex.ensureBuilt(client);

        for (var entry : com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex
                .producedBy(targetStack.getItem())) {
            categorizeRecipe(entry, false);
        }
        for (var entry : com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex
                .usedIn(targetStack.getItem())) {
            categorizeRecipe(entry, true);
        }

        // 3. Scan Brewing recipes
        var brewingOutputs = BrewingRecipeManager.getInstance().getRecipesForOutput(targetStack);
        brewingRecipes.addAll(brewingOutputs);

        var brewingInputs = BrewingRecipeManager.getInstance().getRecipesForInput(targetStack);
        brewingUsages.addAll(brewingInputs);
    }

    private void categorizeRecipe(RecipeHolder<?> entry, boolean isUsage) {
        Recipe<?> recipe = entry.value();
        var type = recipe.getType();

        if (type == net.minecraft.world.item.crafting.RecipeType.CRAFTING) {
            if (isUsage) craftingUsages.add(entry); else craftingRecipes.add(entry);
        } else if (type == net.minecraft.world.item.crafting.RecipeType.SMELTING ||
                   type == net.minecraft.world.item.crafting.RecipeType.BLASTING ||
                   type == net.minecraft.world.item.crafting.RecipeType.SMOKING ||
                   type == net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING) {
            if (isUsage) smeltingUsages.add(entry); else smeltingRecipes.add(entry);
        } else if (type == net.minecraft.world.item.crafting.RecipeType.SMITHING) {
            if (isUsage) smithingUsages.add(entry); else smithingRecipes.add(entry);
        } else if (type == net.minecraft.world.item.crafting.RecipeType.STONECUTTING) {
            if (isUsage) stonecuttingUsages.add(entry); else stonecuttingRecipes.add(entry);
        } else {
            if (isUsage) customUsages.add(entry); else customRecipes.add(entry);
        }
    }

    private void updateMainTabs() {
        visibleMainTabs.clear();
        if (com.ceketrum.cei.config.CeiConfig.getInstance().isShowTabDescription()) {
            visibleMainTabs.add(TabType.DESCRIPTION);
        }
        visibleMainTabs.add(TabType.CRAFTING);
        visibleMainTabs.add(TabType.USAGES);

        // Loot eligibility
        List<String> lootSources = LootTableSourceManager.getInstance().getSourcesForItem(targetStack.getItem());
        if (!lootSources.isEmpty() && com.ceketrum.cei.config.CeiConfig.getInstance().isShowTabLoot()) {
            visibleMainTabs.add(TabType.LOOT);
        }

        // World location eligibility
        List<String> locations = LootTableSourceManager.getInstance().getWorldLocationsForItem(targetStack.getItem());
        List<String> blockGen = BlockGenerationManager.getInstance().getBlockGenerationSources(targetStack.getItem());
        String worldPlaceholder = CeiText.t("cei.loot.where.unspecified");
        // On compare a la valeur reellement produite, pas a une copie
        // figee : les deux chaines qui etaient ici n'existaient plus
        // depuis que le libelle est traduit, et l'onglet s'affichait
        // donc pour des items qui n'avaient rien a y montrer.
        boolean hasWorldLocs = !locations.isEmpty()
                && !(locations.size() == 1 && locations.contains(worldPlaceholder));
        boolean hasBlockGen = !blockGen.isEmpty();

        if ((hasWorldLocs || hasBlockGen) && com.ceketrum.cei.config.CeiConfig.getInstance().isShowTabWorld()) {
            visibleMainTabs.add(TabType.WORLD);
        }
    }

    private void updateCategories() {
        categories.clear();
        // La colonne change de contenu : on revient en haut, sinon on peut
        // rester bloque sur une fenetre qui n'existe plus.
        categoryScroll = 0;
        categoryScrollAnim = 0f;   // pas de glissement a l'ouverture
        if (activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES) {
            boolean useUsages = (activeMainTab == TabType.USAGES);
            var craftList = useUsages ? craftingUsages : craftingRecipes;
            var smeltList = useUsages ? smeltingUsages : smeltingRecipes;
            var brewList = useUsages ? brewingUsages : brewingRecipes;
            var stoneList = useUsages ? stonecuttingUsages : stonecuttingRecipes;
            var smithList = useUsages ? smithingUsages : smithingRecipes;
            var customList = useUsages ? customUsages : customRecipes;

            if (!craftList.isEmpty()) categories.add(new RecipeCategory(CategoryType.CRAFTING));
            if (!smeltList.isEmpty()) categories.add(new RecipeCategory(CategoryType.SMELTING));
            if (!brewList.isEmpty()) categories.add(new RecipeCategory(CategoryType.BREWING));
            if (!stoneList.isEmpty()) categories.add(new RecipeCategory(CategoryType.STONECUTTING));
            if (!smithList.isEmpty()) categories.add(new RecipeCategory(CategoryType.SMITHING));

            if (!customList.isEmpty()) {
                Set<ResourceLocation> customTypes = new LinkedHashSet<>();
                for (Object recipeObj : customList) {
                    Recipe<?> r = getRecipeFromObj(recipeObj);
                    if (r != null) {
                        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
                        if (typeId != null) {
                            customTypes.add(typeId);
                        }
                    }
                }
                for (ResourceLocation typeId : customTypes) {
                    categories.add(new RecipeCategory(CategoryType.CUSTOM, typeId));
                }
            }

            if (!categories.isEmpty()) {
                activeCategory = categories.get(0);
            } else {
                activeCategory = null;
            }
        } else {
            activeCategory = null;
        }
        currentPage = 0;
    }

    private List<?> getActiveRecipesList() {
        if (activeCategory == null) return java.util.Collections.emptyList();
        boolean useUsages = (activeMainTab == TabType.USAGES);
        return switch (activeCategory.type) {
            case CRAFTING -> useUsages ? craftingUsages : craftingRecipes;
            case SMELTING -> useUsages ? smeltingUsages : smeltingRecipes;
            case BREWING -> useUsages ? brewingUsages : brewingRecipes;
            case STONECUTTING -> useUsages ? stonecuttingUsages : stonecuttingRecipes;
            case SMITHING -> useUsages ? smithingUsages : smithingRecipes;
            case CUSTOM -> {
                List<Object> filtered = new ArrayList<>();
                List<?> rawList = useUsages ? customUsages : customRecipes;
                for (Object recipeObj : rawList) {
                    Recipe<?> r = getRecipeFromObj(recipeObj);
                    if (r != null) {
                        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
                        if (java.util.Objects.equals(typeId, activeCategory.customRecipeTypeId)) {
                            filtered.add(recipeObj);
                        }
                    }
                }
                yield filtered;
            }
        };
    }

    private int getMaxPages() {
        List<?> list = getActiveRecipesList();
        if (list.isEmpty()) return 0;
        return list.size();
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Overridden to do nothing to prevent super.render() from drawing the background/blur shader on top of our GUI!
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        syncCardGeometry(card);

        // 1. Draw the native background blur shader and dimming first (flouts the world cleanly in the background)
        // ONLY if this is the active current screen (not rendered as an overlay on top of inventory!)
        if (this.minecraft.screen == this) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }

        // Apply global opacity multiplier for premium look
        float currentOpacity = card != null ? card.getOpacity() : 1.0f;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentOpacity);

        // Draw background shadow
        int shadowColor = 0x80000000;
        context.fill(containerX + 3, containerY + 3, containerX + containerWidth + 3, containerY + containerHeight + 3, shadowColor);

        // Draw Main & Category Tab backgrounds FIRST (drawn behind the main container border/bg)
        drawMainTabsBackground(context, mouseX, mouseY);
        drawCategoryTabsBackground(context, mouseX, mouseY);

        int bgColor = 0xD9101010; // Dark premium glass
        GuiRenderHelper.drawRoundedBackground(context, containerX, containerY, containerWidth, containerHeight, 12, bgColor);
        int borderColor = 0x33FFFFFF; // glowing silver border
        context.renderOutline(containerX, containerY, containerWidth, containerHeight, borderColor);

        // Draw Title (Target Item Name)
        String titleText = targetStack.getHoverName().getString();
        String truncatedTitle = TextRenderHelper.truncateText(titleText, containerWidth - 60, this.font); // left space for header buttons
        int titleWidth = this.font.width(truncatedTitle);
        context.drawString(this.font, Component.literal(truncatedTitle).withStyle(ChatFormatting.GOLD),
                        containerX + (containerWidth - titleWidth) / 2, containerY + 8, 0xFFFFFFFF, false);
        context.fill(containerX + 10, containerY + 20, containerX + containerWidth - 10, containerY + 21, 0x22FFFFFF);

        // Draw Header Action Buttons (Pin, HUD, Opacity)
        drawHeaderButtons(context, mouseX, mouseY);

        // Reset active slots for hover detection
        activeSlots.clear();
        hoveredSlot = null;

        // Draw Tab Icons on top
        drawMainTabsIcons(context);
        drawCategoryTabsIcons(context);

        // Draw Tab Content
        drawTabContent(context, mouseX, mouseY);

        // Render the CEI Search Panel and Item list on the side (NOT blurred!)
        // ONLY if this is the active current screen (do not render list if overlay!)
        if (this.ceiModule != null && this.minecraft.screen == this) {
            this.ceiModule.render(
                context,
                mouseX,
                mouseY,
                this.width,
                this.height,
                this.font,
                this.minecraft.getConnection().recipes(),
                this.minecraft.level.registryAccess()
            );
        }

        // Poignee de redimensionnement : deux traits obliques dans le coin
        // bas-droit. Dessinee apres le contenu, avant les infobulles. Les
        // coordonnees viennent des memes resizeGripX/Y que le test de clic.
        if (card != null) {
            int gripX = resizeGripX();
            int gripY = resizeGripY();
            boolean gripHot = card.isResizing() || isOverResizeGrip(mouseX, mouseY);
            int gripColor = gripHot ? 0xFFFFFFFF : 0xAAFFFFFF;
            // Trait exterieur, puis trait interieur, du bas-gauche vers le haut-droit.
            for (int i = 0; i < 9; i++) {
                context.fill(gripX + 2 + i, gripY + 10 - i, gripX + 3 + i, gripY + 11 - i, gripColor);
            }
            for (int i = 0; i < 5; i++) {
                context.fill(gripX + 6 + i, gripY + 10 - i, gripX + 7 + i, gripY + 11 - i, gripColor);
            }
        }

        // Draw Tooltips (Hovered slot or Tab icons)
        drawTooltips(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);

        // Reset global shader color opacity to normal!
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawHeaderButtons(GuiGraphics context, int mouseX, int mouseY) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;

        // 1. PIN button
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        boolean hoverPin = mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12;
        int pinBg = hoverPin ? 0x66FFFFFF : (isPinned ? 0xAAFFD700 : 0x22FFFFFF); // Golden color if pinned!
        context.fill(pinX, pinY, pinX + 12, pinY + 12, pinBg);
        context.renderOutline(pinX, pinY, 12, 12, isPinned ? 0xFFFFD700 : 0x44FFFFFF);
        // Draw a small anchor/pin dot in the center
        context.fill(pinX + 5, pinY + 3, pinX + 7, pinY + 9, 0xFFFFFFFF);
        context.fill(pinX + 3, pinY + 5, pinX + 9, pinY + 7, 0xFFFFFFFF);

        if (isPinned) {
            // 2. HUD button
            int hudX = containerX + 20;
            int hudY = containerY + 5;
            boolean showInHud = card.isShowInHud();
            boolean hoverHud = mouseX >= hudX && mouseX < hudX + 12 && mouseY >= hudY && mouseY < hudY + 12;
            int hudBg = hoverHud ? 0x66FFFFFF : (showInHud ? 0xAA00FF00 : 0x22FFFFFF); // Green color if active!
            context.fill(hudX, hudY, hudX + 12, hudY + 12, hudBg);
            context.renderOutline(hudX, hudY, 12, 12, showInHud ? 0xFF00FF00 : 0x44FFFFFF);
            // Draw a small eye (horizontal line + center dot)
            context.fill(hudX + 3, hudY + 5, hudX + 9, hudY + 7, 0xFFFFFFFF);
            context.fill(hudX + 5, hudY + 4, hudX + 7, hudY + 8, 0xFFFFFFFF);

            // 3. OPACITY button
            int opX = containerX + 34;
            int opY = containerY + 5;
            float opacity = card.getOpacity();
            boolean hoverOp = mouseX >= opX && mouseX < opX + 12 && mouseY >= opY && mouseY < opY + 12;
            int opBg = hoverOp ? 0x66FFFFFF : 0x22FFFFFF;
            context.fill(opX, opY, opX + 12, opY + 12, opBg);
            context.renderOutline(opX, opY, 12, 12, 0x44FFFFFF);
            // Draw small columns or dots representing transparency level
            int dotCount = opacity == 1.0f ? 4 : (opacity == 0.75f ? 3 : (opacity == 0.5f ? 2 : 1));
            for (int d = 0; d < dotCount; d++) {
                context.fill(opX + 2 + d * 2, opY + 3, opX + 3 + d * 2, opY + 9, 0xFFFFFFFF);
            }
        }

        // Arbre de fabrication. Toujours visible, a une position fixe et a
        // l'ecart des trois boutons conditionnels : places a sa gauche, ils le
        // faisaient sauter d'un endroit a l'autre selon que la fiche etait
        // epinglee ou non.
        // Bouton masque : ce bloc est le dernier de la methode.
        if (!calcAvailable()) return;

        int calcX = calcButtonX();
        int calcY = calcButtonY();
        boolean hoverCalc = mouseX >= calcX && mouseX < calcX + 12
                && mouseY >= calcY && mouseY < calcY + 12;
        context.fill(calcX, calcY, calcX + 12, calcY + 12,
                calcOpen ? 0x66FFD700 : (hoverCalc ? 0x66FFFFFF : 0x22FFFFFF));
        context.renderOutline(calcX, calcY, 12, 12, calcOpen ? 0xFFFFD700 : 0x44FFFFFF);
        // Deux machines reliees par un cable coude. Le pave de calculatrice
        // d'avant disait "on compte" ; celui-ci dit "on suit une chaine", ce
        // qui est le propos de la fonction.
        int glyph = calcOpen ? 0xFFFFD700 : 0xFFFFFFFF;
        int wire = calcOpen ? 0xFFDDAA00 : 0xFFAAAAAA;
        context.fill(calcX + 2, calcY + 2, calcX + 5, calcY + 5, glyph);
        context.fill(calcX + 7, calcY + 7, calcX + 10, calcY + 10, glyph);
        context.fill(calcX + 5, calcY + 3, calcX + 9, calcY + 4, wire);
        // Jusqu'a calcY + 7 seulement : la seconde machine commence a cette
        // ligne, et le cable etant trace apres elle, un pixel de plus lui
        // percait le boitier.
        context.fill(calcX + 8, calcY + 3, calcX + 9, calcY + 7, wire);
    }

    /**
     * Position du bouton du calculateur.
     *
     * Une seule source : le dessin, le clic et l'infobulle passent tous par
     * ces deux methodes.
     */
    /** Le calculateur est-il disponible ? Module et bouton sont deux options. */
    private boolean calcAvailable() {
        var cfg = com.ceketrum.cei.config.CeiConfig.getInstance();
        return cfg.isFeatureCraftTree() && cfg.isShowCalcButton();
    }

    private int calcButtonX() {
        // En haut a DROITE, a l'ecart des trois boutons d'epinglage.
        //
        // La place est libre et c'est verifiable : le titre est centre et
        // tronque a containerWidth - 60, il ne peut donc jamais depasser
        // containerWidth - 30. Douze pixels dans une marge de trente.
        return containerX + containerWidth - 18;
    }

    private int calcButtonY() {
        return containerY + 5;
    }

    private boolean isOverCalcButton(double mouseX, double mouseY) {
        if (!calcAvailable()) return false;

        int x = calcButtonX();
        int y = calcButtonY();
        return mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
    }


    private void drawMainTabsBackground(GuiGraphics context, int mouseX, int mouseY) {
        for (int i = 0; i < visibleMainTabs.size(); i++) {
            TabType tab = visibleMainTabs.get(i);
            int tabX = containerX + containerWidth - 3;
            int tabY = containerY + 10 + i * 26;

            boolean active = (tab == activeMainTab);
            boolean hovered = mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22;

            int tabBg = active ? 0xD9222222 : (hovered ? 0xAA2D2D2D : 0xD9141414);
            GuiRenderHelper.drawRoundedBackground(context, tabX, tabY, 27, 22, 6, tabBg);
            context.renderOutline(tabX, tabY, 27, 22, active ? 0x66FFFFFF : 0x22FFFFFF);
        }
    }

    private void drawMainTabsIcons(GuiGraphics context) {
        for (int i = 0; i < visibleMainTabs.size(); i++) {
            TabType tab = visibleMainTabs.get(i);
            int tabX = containerX + containerWidth - 3;
            int tabY = containerY + 10 + i * 26;

            ItemStack iconStack = switch (tab) {
                case DESCRIPTION -> new ItemStack(Items.WRITABLE_BOOK);
                case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
                case USAGES -> new ItemStack(Items.HOPPER);
                case LOOT -> new ItemStack(Items.CHEST);
                case WORLD -> new ItemStack(Items.COMPASS);
            };

            drawStack(context, iconStack, tabX + 6, tabY + 3);
        }
    }


    /**
     * Index du premier onglet affiche, borne a la volee.
     *
     * Le bornage est fait ici et non au moment du clic : la liste des
     * categories change quand on passe de "Fabrication" a "Utilisations", et
     * elle peut raccourcir. Un decalage devenu trop grand afficherait une
     * colonne vide sans aucun moyen d'en sortir.
     */

    /**
     * Position courante du defilement, en onglets, avec sa partie
     * fractionnaire.
     *
     * categoryScroll est la cible, entiere : c'est elle qui dit quels onglets
     * sont "visibles". Celle-ci est ce qu'on DESSINE, et elle la rejoint
     * progressivement. Les deux sont necessaires : confondre la cible et la
     * position rendrait le clic dependant de l'animation en cours.
     */
    private float categoryScrollAnim = 0f;
    private long categoryScrollNanos = 0L;

    /**
     * Rapproche la position dessinee de la cible.
     *
     * Amortissement exponentiel plutot qu'un pas fixe par image : un pas fixe
     * defile deux fois plus vite a 120 images par seconde qu'a 60. Ici la
     * fraction rattrapee depend du temps ecoule, pas du nombre d'images.
     *
     * Le delta est plafonne : apres une pause du jeu ou un chargement, un
     * ecart d'une seconde ferait sauter l'animation d'un coup, ce qui revient
     * a ne pas en avoir.
     */
    private void stepCategoryScroll() {
        long now = System.nanoTime();
        float dt = (categoryScrollNanos == 0L) ? 0f
                : Math.min(0.1f, (now - categoryScrollNanos) / 1_000_000_000f);
        categoryScrollNanos = now;

        float target = firstVisibleCategory();
        float delta = target - categoryScrollAnim;
        if (Math.abs(delta) < 0.002f) {
            categoryScrollAnim = target;
            return;
        }
        categoryScrollAnim += delta * (1f - (float) Math.exp(-16.0 * dt));
    }

    /** Ordonnee d'un onglet, animation comprise. */
    private int categoryTabY(int index) {
        return categoryTabsTop() + Math.round((index - categoryScrollAnim) * 26f);
    }

    /**
     * Premier onglet a DESSINER : un cran avant le premier onglet visible.
     *
     * Pendant le glissement un onglet deborde par le haut de la bande. Ne pas
     * le dessiner ferait apparaitre les onglets d'un coup au lieu de les faire
     * entrer.
     */
    private int categoryDrawFrom() {
        firstVisibleCategory();   // borne categoryScroll si la liste a raccourci
        return Math.max(0, (int) Math.floor(categoryScrollAnim) - 1);
    }

    /** Fin (exclue) de la fenetre dessinee, un cran apres le dernier visible. */
    private int categoryDrawTo() {
        return Math.min(categories.size(),
                (int) Math.ceil(categoryScrollAnim) + maxVisibleCategories() + 1);
    }

    /** Haut de la bande visible. */
    private int categoryBandTop() {
        return categoryTabsTop();
    }

    /**
     * Bas de la bande visible.
     *
     * On descend jusqu'au bord utile de la colonne, et non jusqu'a la fin du
     * dernier onglet entier : la hauteur d'une fiche tombe rarement sur un
     * multiple de 26, et ce qui restait ne servait a rien. Le haut de l'onglet
     * suivant y depasse desormais, ce qui montre qu'il y en a d'autres.
     *
     * Le Math.max garantit qu'on ne coupe jamais en deca des onglets entiers
     * que maxVisibleCategories() a comptes.
     */
    private int categoryBandBottom() {
        int usable = containerY + containerHeight
                - (categoriesScrollable() ? SCROLL_BTN_H + 2 : 10);
        return Math.max(categoryTabsTop() + (maxVisibleCategories() - 1) * 26 + 22, usable);
    }

    /**
     * Le curseur est-il sur cet onglet ?
     *
     * La zone est rognee par la bande : pendant le glissement un onglet
     * deborde, et on ne doit pouvoir cliquer que ce qu'on voit.
     */
    private boolean categoryTabHit(int tabX, int tabY, double mouseX, double mouseY) {
        int top = Math.max(tabY, categoryBandTop());
        int bottom = Math.min(tabY + 22, categoryBandBottom());
        return bottom > top
                && mouseX >= tabX && mouseX < tabX + 24
                && mouseY >= top && mouseY < bottom;
    }

    /**
     * Molette sur la colonne d'onglets.
     *
     * L'evenement est consomme des que le curseur est sur la colonne, meme si
     * on est deja en butee : sinon la liste d'items se mettrait a defiler
     * derriere alors qu'on visait manifestement la colonne.
     */
    private boolean categoryWheel(double mouseX, double mouseY, double amount) {
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return false;
        if (!categoriesScrollable()) return false;

        int x = containerX - 24;
        if (mouseX < x || mouseX >= x + 27) return false;
        if (mouseY < categoryScrollUpY()
                || mouseY >= categoryScrollDownY() + SCROLL_BTN_H) return false;

        int max = Math.max(0, categories.size() - maxVisibleCategories());
        int step = (amount > 0) ? -1 : (amount < 0 ? 1 : 0);
        categoryScroll = Math.max(0, Math.min(max, categoryScroll + step));
        return true;
    }

    private int firstVisibleCategory() {
        int max = Math.max(0, categories.size() - maxVisibleCategories());
        if (categoryScroll > max) categoryScroll = max;
        if (categoryScroll < 0) categoryScroll = 0;
        return categoryScroll;
    }

    /** Index de fin (exclu) de la fenetre affichee. */
    private int lastVisibleCategory() {
        return Math.min(categories.size(), firstVisibleCategory() + maxVisibleCategories());
    }

    /** Y a-t-il assez de categories pour qu'on ait besoin de defiler ? */
    private boolean categoriesScrollable() {
        return categories.size() > maxVisibleCategories();
    }

    /**
     * Ordonnee du premier onglet.
     *
     * Sans fleches, la colonne demarre a la meme hauteur que celle de droite.
     * Avec fleches, elle descend juste ce qu'il faut pour que celle du haut
     * tienne dans le cadre : la colonne complete mesure 180 pixels pour un
     * panneau de 179, elle s'y loge exactement.
     */
    private int categoryTabsTop() {
        return categoriesScrollable() ? containerY + SCROLL_BTN_H + 2 : containerY + 10;
    }

    private int categoryScrollUpY() {
        return containerY;
    }

    /**
     * Calee sur le bord inferieur du panneau, sauf si celui-ci est trop court
     * pour les six onglets -- auquel cas elle se contente de passer dessous.
     */
    private int categoryScrollDownY() {
        int belowTabs = categoryTabsTop() + (maxVisibleCategories() - 1) * 26 + 24;
        return Math.max(belowTabs, containerY + containerHeight - SCROLL_BTN_H);
    }

    private boolean categoryScrollHit(double mouseX, double mouseY, int y) {
        int x = containerX - 24;
        return mouseX >= x && mouseX < x + 27 && mouseY >= y && mouseY < y + SCROLL_BTN_H;
    }

    /** Fleches de defilement de la colonne d'onglets. */
    private void drawCategoryScrollArrows(GuiGraphics context, int mouseX, int mouseY) {
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return;
        if (!categoriesScrollable()) return;

        if (firstVisibleCategory() > 0) {
            drawScrollButton(context, categoryScrollUpY(), true, mouseX, mouseY);
        }
        if (lastVisibleCategory() < categories.size()) {
            drawScrollButton(context, categoryScrollDownY(), false, mouseX, mouseY);
        }
    }

    private void drawScrollButton(GuiGraphics context, int y, boolean up, int mouseX, int mouseY) {
        int x = containerX - 24;
        boolean hovered = mouseX >= x && mouseX < x + 27 && mouseY >= y && mouseY < y + SCROLL_BTN_H;

        GuiRenderHelper.drawRoundedBackground(context, x, y, 27, SCROLL_BTN_H, 4,
                hovered ? 0xAA2D2D2D : 0xD9141414);
        context.renderOutline(x, y, 27, SCROLL_BTN_H, hovered ? 0x66FFFFFF : 0x22FFFFFF);

        // Triangle dessine au pixel : la police ne garantit pas les caracteres
        // de fleche dans toutes les langues, et un pack de ressources peut les
        // remplacer par n'importe quoi.
        int cx = x + 13;
        int top = y + 4;
        int tint = 0xFFFFFFFF;
        for (int r = 0; r < 4; r++) {
            int w = up ? (r * 2 + 1) : ((3 - r) * 2 + 1);
            context.fill(cx - w / 2, top + r, cx + w / 2 + 1, top + r + 1, tint);
        }
    }

    private void drawCategoryTabsBackground(GuiGraphics context, int mouseX, int mouseY) {
        // Une seule fois par image : c'est la premiere des deux
        // passes de dessin de la colonne.
        stepCategoryScroll();
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return;

        int first = categoryDrawFrom();
        // Ce qui glisse doit etre coupe : pendant l'animation les onglets
        // qui entrent et sortent depassent de la bande.
        context.enableScissor(containerX - 24, categoryBandTop(),
                containerX - 24 + 27, categoryBandBottom());
        for (int i = first; i < categoryDrawTo(); i++) {
            RecipeCategory cat = categories.get(i);
            int tabX = containerX - 24;
            int tabY = categoryTabY(i);

            boolean active = (cat.equals(activeCategory));
            boolean hovered = categoryTabHit(tabX, tabY, mouseX, mouseY);

            int tabBg = active ? 0xD9222222 : (hovered ? 0xAA2D2D2D : 0xD9141414);
            GuiRenderHelper.drawRoundedBackground(context, tabX, tabY, 27, 22, 6, tabBg);
            context.renderOutline(tabX, tabY, 27, 22, active ? 0x66FFFFFF : 0x22FFFFFF);
        }

        context.disableScissor();

        drawCategoryScrollArrows(context, mouseX, mouseY);
    }


    /**
     * Dessine une pile AVEC sa quantite.
     *
     * renderItem() ne dessine que la texture ; le nombre vient de
     * renderItemDecorations(), la meme methode que le jeu utilise pour l'inventaire.
     * Elle n'ecrit rien quand la quantite vaut 1, ce qui evite d'avoir a
     * distinguer les cases de recette des icones d'onglet.
     */
    private void drawStack(GuiGraphics context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        context.renderItem(stack, x, y);
        context.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, x, y);
    }

    private void drawCategoryTabsIcons(GuiGraphics context) {
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return;

        int first = categoryDrawFrom();
        // Ce qui glisse doit etre coupe : pendant l'animation les onglets
        // qui entrent et sortent depassent de la bande.
        context.enableScissor(containerX - 24, categoryBandTop(),
                containerX - 24 + 27, categoryBandBottom());
        for (int i = first; i < categoryDrawTo(); i++) {
            RecipeCategory cat = categories.get(i);
            int tabX = containerX - 24;
            int tabY = categoryTabY(i);

            ItemStack iconStack = switch (cat.type) {
                case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
                case SMELTING -> new ItemStack(Items.FURNACE);
                case BREWING -> new ItemStack(Items.BREWING_STAND);
                case STONECUTTING -> new ItemStack(Items.STONECUTTER);
                case SMITHING -> new ItemStack(Items.SMITHING_TABLE);
                case CUSTOM -> {
                    boolean useUsages = (activeMainTab == TabType.USAGES);
                    List<?> list = useUsages ? customUsages : customRecipes;
                    Recipe<?> matchedRecipe = null;
                    for (Object recipeObj : list) {
                        Recipe<?> r = getRecipeFromObj(recipeObj);
                        if (r != null) {
                            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
                            if (java.util.Objects.equals(typeId, cat.customRecipeTypeId)) {
                                matchedRecipe = r;
                                break;
                            }
                        }
                    }
                    if (matchedRecipe != null) {
                        yield getMachineIcon(matchedRecipe);
                    }
                    yield new ItemStack(Items.DISPENSER);
                }
            };

            drawStack(context, iconStack, tabX + 4, tabY + 3);
        }
        context.disableScissor();
    }

    private void drawTabContent(GuiGraphics context, int mouseX, int mouseY) {
        int contentX = containerX + 15;
        int contentY = containerY + 28;
        int contentWidth = containerWidth - 30;
        int contentHeight = containerHeight - 45;

        // Un booleen suffit a tout court-circuiter : calculateur ferme, aucune
        // recette n'est parcourue, aucun stock n'est lu.
        if (calcOpen && calcAvailable()) {
            drawCalculator(context, mouseX, mouseY, contentX, contentY, contentWidth, contentHeight);
            return;
        }


        switch (activeMainTab) {
            case DESCRIPTION: {
                String desc = ItemDescriptionManager.getInstance().getDescription(targetStack.getItem());
                if (desc.isEmpty()) {
                    desc = CeiText.t("cei.info.no_description");
                }

                // Real stats integration in description tab
                RecipePopupRenderer.ItemStats stats = new RecipePopupRenderer().getItemStats(targetStack);
                boolean hasAnyStats = stats.hasDurability || stats.hasFood || stats.hasAttackDamage || stats.hasAttackSpeed || stats.hasArmor || stats.hasToughness;

                int currY = TextRenderHelper.drawWrappedText(context, desc, contentX, contentY, contentWidth, 0xDDFFFFFF, 0.75f, 3, this.font);

                if (hasAnyStats) {
                    currY += 6;
                    context.fill(contentX, currY, contentX + contentWidth, currY + 1, 0x22FFFFFF);
                    currY += 6;

                    String statsTitle = CeiText.t("cei.info.statistics");
                    context.drawString(this.font, statsTitle, contentX, currY, 0xFFD700, false);
                    currY += 11;

                    int statColor = 0xAAAAAAAA;
                    float scale = 0.75f;

                    if (stats.hasAttackDamage) {
                        String val = String.format("%s: +%.1f", CeiText.t("cei.stat.damage"), stats.attackDamage);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                    if (stats.hasAttackSpeed) {
                        double speed = 4.0 + stats.attackSpeed;
                        String val = String.format("%s: %.1f", CeiText.t("cei.stat.attack_speed"), speed);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                    if (stats.hasArmor) {
                        String val = String.format("%s: +%.0f", CeiText.t("cei.stat.armor"), stats.armor);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                    if (stats.hasToughness) {
                        String val = String.format("%s: +%.0f", CeiText.t("cei.stat.toughness"), stats.toughness);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                    if (stats.hasFood) {
                        String val = String.format("%s: +%d (Saturation: +%.1f)", CeiText.t("cei.stat.food"), stats.foodPoints, stats.saturation);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                    if (stats.hasDurability) {
                        String val = String.format("%s: %d / %d", CeiText.t("cei.stat.durability"), stats.durability, stats.maxDurability);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.font);
                    }
                }
                break;
            }
            case CRAFTING:
            case USAGES: {
                if (activeCategory == null || getActiveRecipesList().isEmpty()) {
                    String emptyMsg = CeiText.t(activeMainTab == TabType.CRAFTING
                            ? "cei.info.no_recipes" : "cei.info.no_usages");
                    int msgW = this.font.width(emptyMsg);
                    context.drawString(this.font, emptyMsg, contentX + (contentWidth - msgW) / 2, contentY + 40, 0xFFFF0000, false);
                } else {
                    // La recette est servie d'abord ; le texte de l'item prend
                    // ce qui reste. La hauteur amputee est bien celle passee a
                    // drawRecipeContent, donc les dispositions qui se centrent
                    // verticalement (le brassage) se centrent dans la place
                    // qu'elles ont reellement.
                    int descH = descBandHeight(contentHeight);
                    drawRecipeContent(context, mouseX, mouseY, contentX, contentY,
                            contentWidth, contentHeight - descH);
                    if (descH > 0) {
                        drawItemDescription(context, contentX,
                                contentY + contentHeight - descH, contentWidth, descH);
                    } else {
                        descBandH = 0;
                    }
                }
                break;
            }
            case LOOT: {
                List<String> lootSources = LootTableSourceManager.getInstance().getSourcesForItem(targetStack.getItem());
                String header = CeiText.t("cei.info.obtaining_sources");
                context.drawString(this.font, header, contentX, contentY, 0xFFD700, false);
                int currY = contentY + 14;

                for (String source : lootSources) {
                    currY = TextRenderHelper.drawWrappedText(context, "• " + source, contentX, currY, contentWidth, 0xDDFFFFFF, 0.75f, 6, this.font);
                }
                break;
            }
            case WORLD: {
                List<String> locations = new ArrayList<>(LootTableSourceManager.getInstance().getWorldLocationsForItem(targetStack.getItem()));

                // Add natural block generation details!
                List<String> blockGenSources = BlockGenerationManager.getInstance().getBlockGenerationSources(targetStack.getItem());
                locations.addAll(blockGenSources);

                // Remove duplicates and placeholders
                Set<String> uniqueLocs = new LinkedHashSet<>();
                // Un seul libelle de repli, partage avec LootTableSourceManager.
                // Avant, deux chaines codees en dur servaient a la fois de
                // valeur et de filtre : il suffisait qu'une des deux bouge
                // pour que le doublon reapparaisse a l'ecran.
                String placeholder = CeiText.t("cei.loot.where.unspecified");

                for (String loc : locations) {
                    if (!loc.equals(placeholder)) {
                        uniqueLocs.add(loc);
                    }
                }

                if (uniqueLocs.isEmpty()) {
                    uniqueLocs.add(placeholder);
                }

                String header = CeiText.t("cei.info.biomes_structures");
                context.drawString(this.font, header, contentX, contentY, 0xFFD700, false);
                int currY = contentY + 14;

                for (String location : uniqueLocs) {
                    currY = TextRenderHelper.drawWrappedText(context, location.startsWith(" ") || location.endsWith(":") ? location : "• " + location, contentX, currY, contentWidth, 0xDDFFFFFF, 0.75f, 6, this.font);
                }
                break;
            }
        }
    }

    private void drawRecipeContent(GuiGraphics context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth, int contentHeight) {
        List<?> list = getActiveRecipesList();
        if (currentPage >= list.size()) currentPage = 0;
        Object recipeObj = list.get(currentPage);

        var client = Minecraft.getInstance();
        var rm = client.level.registryAccess();
        var contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level);

        // Draw Header of recipe
        Recipe<?> activeRecipe = null;
        if (recipeObj instanceof RecipeHolder<?> entry) {
            activeRecipe = entry.value();
        }

        Recipe<?> finalRecipe = activeRecipe;
        ItemStack titleIcon = switch (activeCategory.type) {
            case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
            case SMELTING -> new ItemStack(Items.FURNACE);
            case BREWING -> new ItemStack(Items.BREWING_STAND);
            case STONECUTTING -> new ItemStack(Items.STONECUTTER);
            case SMITHING -> new ItemStack(Items.SMITHING_TABLE);
            case CUSTOM -> finalRecipe != null ? getMachineIcon(finalRecipe) : new ItemStack(Items.DISPENSER);
        };

        String catName = switch (activeCategory.type) {
            case CRAFTING -> CeiText.t("cei.station.crafting_table");
            case SMELTING -> CeiText.t("cei.station.furnace");
            case BREWING -> CeiText.t("cei.station.brewing_stand");
            case STONECUTTING -> CeiText.t("cei.station.stonecutter");
            case SMITHING -> CeiText.t("cei.station.smithing_table");
            case CUSTOM -> {
                // Le libelle vient desormais du type de recette lui-meme :
                // create:crushing doit s'afficher "Crushing Wheel", pas
                // "Custom Machine". Cf. CeiRecipeStation.
                if (finalRecipe != null) {
                    yield getMachineLabel(finalRecipe);
                }
                yield CeiText.t("cei.station.custom");
            }
        };

        drawStack(context, titleIcon, contentX, contentY - 4);
        activeSlots.add(new RenderedSlot(titleIcon, contentX, contentY - 4, 16));
        context.drawString(this.font, catName, contentX + 20, contentY, 0xFFD700, false);

        // Render slots and arrow based on category
        switch (activeCategory.type) {
            case CRAFTING: {
                RecipeHolder<?> entry = (RecipeHolder<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                var layout = com.ceketrum.cei.gui.module.cei.recipe.RecipeDisplayHelper.getRecipeLayout(recipe, entry, contextMap);

                int gridStartX = contentX + 10;
                int gridStartY = contentY + 18;

                // Draw 3x3 slots
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotX = gridStartX + c * 20;
                        int slotY = gridStartY + r * 20;

                        // Draw slot border & bg
                        drawSlotBg(context, slotX, slotY);

                        ItemStack inputStack = ItemStack.EMPTY;
                        if (r < layout.height && c < layout.width) {
                            inputStack = layout.ingredients[r][c];
                        }

                        if (inputStack != null && !inputStack.isEmpty()) {
                            drawStack(context, inputStack, slotX + 1, slotY + 1);
                            activeSlots.add(new RenderedSlot(inputStack, slotX, slotY, 18));
                        }
                    }
                }

                // Arrow
                int arrowX = gridStartX + 65;
                int arrowY = gridStartY + 22;
                drawArrow(context, arrowX, arrowY);

                // Plus button for crafting table auto-transfer
                if (isParentCraftingTable()) {
                    int plusX = plusButtonX();
                    int plusY = plusButtonY();

                    boolean hoverPlus = mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12;

                    // Draw sleek button background
                    int btnBg = hoverPlus ? 0xFF3D3D3D : 0xFF1C1C1C;
                    int btnBorder = hoverPlus ? 0x88FFFFFF : 0x33FFFFFF;

                    context.fill(plusX, plusY, plusX + 12, plusY + 12, btnBg);
                    context.renderOutline(plusX, plusY, 12, 12, btnBorder);

                    // Draw horizontal and vertical lines of the "+" sign
                    int textColor = hoverPlus ? 0xFFFFFFFF : 0xFFAAAAAA;
                    context.fill(plusX + 3, plusY + 5, plusX + 9, plusY + 7, textColor);
                    context.fill(plusX + 5, plusY + 3, plusX + 7, plusY + 9, textColor);
                }

                // Output slot
                int outputX = gridStartX + 100;
                int outputY = gridStartY + 18;
                drawSlotBg(context, outputX, outputY);
                try {
                    ItemStack outStack = ItemStack.EMPTY;
                    List<RecipeDisplay> displays = recipe.display();
                    if (!displays.isEmpty()) {
                        outStack = displays.get(0).result().resolveForFirstStack(contextMap);
                    }
                    drawStack(context, outStack, outputX + 1, outputY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outputX, outputY, 18));
                } catch (Exception e) {}
                break;
            }
            case SMELTING: {
                RecipeHolder<?> entry = (RecipeHolder<?>) recipeObj;
                Recipe<?> recipe = entry.value();

                int slotX = contentX + 25;
                int slotY = contentY + 28;

                // Input slot
                drawSlotBg(context, slotX, slotY);
                List<RecipeDisplay> displays = recipe.display();
                if (!displays.isEmpty() && displays.get(0) instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace) {
                    ItemStack inStack = furnace.ingredient().resolveForFirstStack(contextMap);
                    if (inStack != null && !inStack.isEmpty()) {
                        drawStack(context, inStack, slotX + 1, slotY + 1);
                        activeSlots.add(new RenderedSlot(inStack, slotX, slotY, 18));
                    }
                }

                // Flame/Arrow
                drawArrow(context, slotX + 30, slotY + 2);

                // Output slot
                int outX = slotX + 65;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = ItemStack.EMPTY;
                    if (!displays.isEmpty()) {
                        outStack = displays.get(0).result().resolveForFirstStack(contextMap);
                    }
                    drawStack(context, outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case BREWING: {
                BrewingRecipeManager.BrewingRecipe recipe = (BrewingRecipeManager.BrewingRecipe) recipeObj;

                // Disposition en alambic. L'ingredient est au-dessus et sa
                // fleche descend SUR la fleche base -> resultat : les deux
                // apports convergent la ou la transformation a lieu.
                //
                // L'ancienne version posait tout a contentX + 25 / contentY + 14,
                // une position fixe sans rapport avec la taille de la zone, avec
                // une fleche descendante qui ne rejoignait rien et aucune fleche
                // entre les deux potions.
                // On centre la RECETTE seule. Le carburant n'en fait pas
                // partie : le compter dans la largeur centree decalait toute la
                // recette vers la droite de la moitie de ce qu'il occupe.
                // Ainsi l'ingredient et sa fleche tombent pile au milieu du
                // panneau, ce qui est le repere que l'oeil suit.
                int recipeW = BREW_SLOT + BREW_ARROW_GAP + BREW_SLOT;
                int blockH = BREW_SLOT + BREW_DROP + BREW_SLOT;

                int topY = contentY + BREW_HEADER
                        + Math.max(0, (contentHeight - BREW_HEADER - blockH) / 2);

                int baseX = contentX + (contentWidth - recipeW) / 2;
                int outX  = baseX + BREW_SLOT + BREW_ARROW_GAP;
                // Borne a gauche : sur un panneau etroit le carburant se serre
                // contre la recette plutot que d'en sortir.
                int fuelX = Math.max(contentX, baseX - BREW_FUEL_GAP - BREW_SLOT);
                int rowY  = topY + BREW_SLOT + BREW_DROP;

                // Potion de base, fleche, resultat.
                drawSlotBg(context, baseX, rowY);
                drawStack(context, recipe.inputPotion, baseX + 1, rowY + 1);
                activeSlots.add(new RenderedSlot(recipe.inputPotion, baseX, rowY, 18));

                int arrowX = baseX + BREW_SLOT + (BREW_ARROW_GAP - 18) / 2;
                drawArrow(context, arrowX, rowY + 2);

                drawSlotBg(context, outX, rowY);
                drawStack(context, recipe.outputPotion, outX + 1, rowY + 1);
                activeSlots.add(new RenderedSlot(recipe.outputPotion, outX, rowY, 18));

                // Ingredient, centre sur la fleche, et sa descente jusqu'a elle.
                int arrowCx = arrowX + 9;
                int ingX = arrowCx - BREW_SLOT / 2;
                drawSlotBg(context, ingX, topY);
                drawStack(context, recipe.ingredient, ingX + 1, topY + 1);
                activeSlots.add(new RenderedSlot(recipe.ingredient, ingX, topY, 18));
                drawArrowDown(context, arrowCx, topY + BREW_SLOT + 3, BREW_DROP - 6);

                // Carburant, A L'ECART : la poudre de Blaze ne fait pas partie
                // de la recette. Collee aux autres cases elle se lirait comme un
                // ingredient, ce qui serait faux ; le trait et l'espace disent
                // qu'elle releve du contexte.
                drawSlotBg(context, fuelX, rowY);
                ItemStack fuel = new ItemStack(Items.BLAZE_POWDER);
                drawStack(context, fuel, fuelX + 1, rowY + 1);
                activeSlots.add(new RenderedSlot(fuel, fuelX, rowY, 18));
                int sepX = fuelX + BREW_SLOT + BREW_FUEL_GAP / 2;
                context.fill(sepX, rowY + 2, sepX + 1, rowY + BREW_SLOT - 2, 0x33FFFFFF);
                break;
            }
            case STONECUTTING: {
                RecipeHolder<?> entry = (RecipeHolder<?>) recipeObj;
                Recipe<?> recipe = entry.value();

                int slotX = contentX + 25;
                int slotY = contentY + 28;

                // Input
                drawSlotBg(context, slotX, slotY);
                List<RecipeDisplay> displays = recipe.display();
                if (!displays.isEmpty() && displays.get(0) instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay stonecutter) {
                    ItemStack inStack = stonecutter.input().resolveForFirstStack(contextMap);
                    if (inStack != null && !inStack.isEmpty()) {
                        drawStack(context, inStack, slotX + 1, slotY + 1);
                        activeSlots.add(new RenderedSlot(inStack, slotX, slotY, 18));
                    }
                }

                // Arrow
                drawArrow(context, slotX + 30, slotY + 2);

                // Output
                int outX = slotX + 65;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = ItemStack.EMPTY;
                    if (!displays.isEmpty()) {
                        outStack = displays.get(0).result().resolveForFirstStack(contextMap);
                    }
                    drawStack(context, outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case SMITHING: {
                RecipeHolder<?> entry = (RecipeHolder<?>) recipeObj;
                Recipe<?> recipe = entry.value();

                int slotX = contentX + 10;
                int slotY = contentY + 28;

                // 3 Inputs (Template, Base, Addition)
                List<RecipeDisplay> displays = recipe.display();
                if (!displays.isEmpty() && displays.get(0) instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay smithing) {
                    List<net.minecraft.world.item.crafting.display.SlotDisplay> slots = List.of(smithing.template(), smithing.base(), smithing.addition());
                    for (int i = 0; i < 3; i++) {
                        int sX = slotX + i * 20;
                        drawSlotBg(context, sX, slotY);
                        ItemStack match = slots.get(i).resolveForFirstStack(contextMap);
                        if (match != null && !match.isEmpty()) {
                            drawStack(context, match, sX + 1, slotY + 1);
                            activeSlots.add(new RenderedSlot(match, sX, slotY, 18));
                        }
                    }
                }

                // Arrow
                drawArrow(context, slotX + 65, slotY + 2);

                // Output
                int outX = slotX + 100;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = ItemStack.EMPTY;
                    if (!displays.isEmpty()) {
                        outStack = displays.get(0).result().resolveForFirstStack(contextMap);
                    }
                    drawStack(context, outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case CUSTOM: {
                RecipeHolder<?> entry = (RecipeHolder<?>) recipeObj;
                Recipe<?> recipe = entry.value();

                // Nouveau pipeline : la grille vient de la recette elle-meme.
                // L'ancien code plafonnait a 4 entrees sur une seule ligne
                // (Math.min(4, totalInputs)), ce qui tronquait toute recette
                // moddee depassant ce format et en perdait la disposition.
                var view = com.ceketrum.cei.config.CeiConfig.getInstance().isUseNewRecipeRenderer()
                        ? com.ceketrum.cei.gui.module.cei.recipe.view.CeiRecipeAdapter.from(recipe, contextMap)
                        : null;

                if (view != null) {
                    long now = System.currentTimeMillis();
                    int cols = Math.max(1, view.gridWidth());
                    int rows = Math.max(1, view.gridHeight());

                    List<ItemStack> vOutputs = view.outputs();
                    if (vOutputs.isEmpty() && activeMainTab == TabType.CRAFTING) {
                        vOutputs = List.of(targetStack);
                    }
                    int displayOut = Math.max(1, Math.min(4, vOutputs.size()));

                    int gridW = cols * 20;
                    int outW = displayOut * 20;
                    int totalW = gridW + 24 + outW;
                    int startX = contentX + (contentWidth - totalW) / 2;
                    int startY = contentY + 24;

                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            int slotX = startX + c * 20;
                            int slotY = startY + r * 20;
                            drawSlotBg(context, slotX, slotY);
                            ItemStack stack = view.slotAt(c, r).current(now);
                            if (stack != null && !stack.isEmpty()) {
                                drawStack(context, stack, slotX + 1, slotY + 1);
                                activeSlots.add(new RenderedSlot(stack, slotX, slotY, 18));
                            }
                        }
                    }

                    int midY = startY + (rows * 20) / 2 - 9;
                    drawArrow(context, startX + gridW + 3, midY + 2);

                    for (int i = 0; i < displayOut; i++) {
                        int slotX = startX + gridW + 24 + i * 20;
                        drawSlotBg(context, slotX, midY);
                        ItemStack stack = i < vOutputs.size() ? vOutputs.get(i) : ItemStack.EMPTY;
                        if (stack != null && !stack.isEmpty()) {
                            drawStack(context, stack, slotX + 1, midY + 1);
                            activeSlots.add(new RenderedSlot(stack, slotX, midY, 18));
                        }
                    }
                    break;
                }

                // --- ancien chemin, conserve tant que les deux cohabitent ---

                List<ItemStack> inputs = extractCustomInputs(recipe, contextMap);
                List<ItemStack> outputs = extractCustomOutputs(recipe, contextMap);

                // Ensure we have at least one input and output to show
                if (inputs.isEmpty()) {
                    if (activeMainTab == TabType.USAGES) {
                        inputs.add(targetStack);
                    } else {
                        inputs.add(ItemStack.EMPTY);
                    }
                }
                if (outputs.isEmpty()) {
                    if (activeMainTab == TabType.CRAFTING) {
                        outputs.add(targetStack);
                    } else {
                        outputs.add(ItemStack.EMPTY);
                    }
                }

                int totalInputs = inputs.size();
                int totalOutputs = outputs.size();

                // Limit to 4 inputs and 4 outputs for single-line horizontal alignment
                int displayInputs = Math.min(4, totalInputs);
                int displayOutputs = Math.min(4, totalOutputs);

                int inputsWidth = displayInputs * 20;
                int outputsWidth = displayOutputs * 20;
                int totalWidth = inputsWidth + 24 + outputsWidth;

                int startX = contentX + (contentWidth - totalWidth) / 2;
                int startY = contentY + 24;

                // Draw Inputs
                for (int i = 0; i < displayInputs; i++) {
                    int slotX = startX + i * 20;
                    drawSlotBg(context, slotX, startY);
                    ItemStack stack = inputs.get(i);
                    if (stack != null && !stack.isEmpty()) {
                        drawStack(context, stack, slotX + 1, startY + 1);
                        activeSlots.add(new RenderedSlot(stack, slotX, startY, 18));
                    }
                }

                // Draw Arrow
                int arrowX = startX + inputsWidth + 3;
                int arrowY = startY + 2;
                drawArrow(context, arrowX, arrowY);

                // Draw Outputs
                for (int i = 0; i < displayOutputs; i++) {
                    int slotX = startX + inputsWidth + 24 + i * 20;
                    drawSlotBg(context, slotX, startY);
                    ItemStack stack = outputs.get(i);
                    if (stack != null && !stack.isEmpty()) {
                        drawStack(context, stack, slotX + 1, startY + 1);
                        activeSlots.add(new RenderedSlot(stack, slotX, startY, 18));
                    }
                }
                break;
            }
        }

        // Draw Pagination details
        int pageY = containerY + containerHeight - 16;
        String pageStr = String.format("%d / %d", currentPage + 1, getMaxPages());
        int pageW = this.font.width(pageStr);
        context.drawString(this.font, pageStr, contentX + (contentWidth - pageW) / 2, pageY, 0xFF888888, false);

        // Left & Right Arrow buttons
        int arrowLeftX = contentX + (contentWidth - pageW) / 2 - 15;
        int arrowRightX = contentX + (contentWidth + pageW) / 2 + 5;

        boolean hoverLeft = mouseX >= arrowLeftX && mouseX < arrowLeftX + 8 && mouseY >= pageY && mouseY < pageY + 9;
        boolean hoverRight = mouseX >= arrowRightX && mouseX < arrowRightX + 8 && mouseY >= pageY && mouseY < pageY + 9;

        context.drawString(this.font, "<", arrowLeftX, pageY, hoverLeft ? 0xFFFFFFFF : 0xFF888888, false);
        context.drawString(this.font, ">", arrowRightX, pageY, hoverRight ? 0xFFFFFFFF : 0xFF888888, false);
    }

    private void drawSlotBg(GuiGraphics context, int x, int y) {
        int color = 0xFF181818;
        context.fill(x, y, x + 18, y + 18, color);
        context.renderOutline(x, y, 18, 18, 0x33FFFFFF);
    }


    // ------------------------------------------------------- brassage
    /** Cote d'une case, identique partout dans la fiche. */
    private static final int BREW_SLOT = 18;
    /** Espace entre la potion de base et le resultat ; la fleche s'y centre. */
    private static final int BREW_ARROW_GAP = 30;
    /** Chute de l'ingredient jusqu'a la ligne des potions. */
    private static final int BREW_DROP = 26;
    /** Ecart du carburant : assez large pour qu'il ne se lise pas comme une entree. */
    private static final int BREW_FUEL_GAP = 26;
    /** Bande reservee a l'en-tete de categorie, au-dessus du contenu. */
    private static final int BREW_HEADER = 24;

    /**
     * Fleche verticale, pointe vers le bas.
     *
     * L'ancienne etait dessinee a la main dans le bloc du brassage, en deux
     * appels a fill, et sa "pointe" etait un simple trait horizontal plus large
     * que la hampe -- ce qui ressemblait a un T, pas a une fleche.
     */
    /**
     * Fleche verticale, pointe vers le bas : le meme profil, pivote.
     *
     * La hampe se raccourcit de la hauteur de la pointe, donc la fleche
     * occupe exactement la hauteur demandee.
     */
    private void drawArrowDown(GuiGraphics context, int cx, int y, int height) {
        int color = 0x88FFFFFF;
        int headY = y + Math.max(0, height - ARROW_HEAD.length);
        context.fill(cx - 1, y, cx + 1, headY, color);
        for (int i = 0; i < ARROW_HEAD.length; i++) {
            context.fill(cx - ARROW_HEAD[i], headY + i,
                         cx + ARROW_HEAD[i], headY + i + 1, color);
        }
    }

    /**
     * Profil de la pointe : demi-hauteurs du talon vers l'extremite.
     *
     * Une seule table pour la fleche horizontale et la descendante. Deux
     * tables, c'est deux fleches qui finissent par ne plus se ressembler.
     */
    private static final int[] ARROW_HEAD = {5, 5, 4, 3, 2, 1, 1};

    /**
     * Fleche horizontale, 18 pixels de large.
     *
     * L'ancienne pointe tenait en trois colonnes ecrites a la main, avec un
     * trou au milieu : un petit tas asymetrique plutot qu'un triangle.
     *
     * L'axe est inchange -- la hampe reste sur y + 6 et y + 7 -- parce que
     * cinq des sept appels compensent ce decalage en passant slotY + 2.
     */
    private void drawArrow(GuiGraphics context, int x, int y) {
        int color = 0x88FFFFFF;
        int headX = x + 18 - ARROW_HEAD.length;
        context.fill(x, y + 6, headX, y + 8, color);
        for (int i = 0; i < ARROW_HEAD.length; i++) {
            context.fill(headX + i, y + 7 - ARROW_HEAD[i],
                         headX + i + 1, y + 7 + ARROW_HEAD[i], color);
        }
    }

    private void drawTooltips(GuiGraphics context, int mouseX, int mouseY) {

        // 0. Header buttons tooltips
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        if (mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12) {
            String tooltipText = isPinned
                ? (CeiText.t("cei.pin.unpin"))
                : (CeiText.t("cei.pin.pin"));
            context.renderComponentTooltip(this.font, List.of(Component.literal(tooltipText)), mouseX, mouseY);
            return;
        }

        if (isPinned) {
            int hudX = containerX + 20;
            int hudY = containerY + 5;
            if (mouseX >= hudX && mouseX < hudX + 12 && mouseY >= hudY && mouseY < hudY + 12) {
                String tooltipText = card.isShowInHud()
                    ? (CeiText.t("cei.pin.hud_hide"))
                    : (CeiText.t("cei.pin.hud_show"));
                context.renderComponentTooltip(this.font, List.of(Component.literal(tooltipText)), mouseX, mouseY);
                return;
            }

            int opX = containerX + 34;
            int opY = containerY + 5;
            if (mouseX >= opX && mouseX < opX + 12 && mouseY >= opY && mouseY < opY + 12) {
                String tooltipText = String.format("%s : %d%%",
                    CeiText.t("cei.pin.opacity"),
                    (int) (card.getOpacity() * 100)
                );
                context.renderComponentTooltip(this.font, List.of(Component.literal(tooltipText)), mouseX, mouseY);
                return;
            }
        }

        // 1. Tooltips for main tabs
        for (int i = 0; i < visibleMainTabs.size(); i++) {
            TabType tab = visibleMainTabs.get(i);
            int tabX = containerX + containerWidth;
            int tabY = containerY + 10 + i * 26;

            if (mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22) {
                String tooltipText = switch (tab) {
                    case DESCRIPTION -> CeiText.t("cei.tab.description");
                    case CRAFTING -> CeiText.t("cei.tab.craft");
                    case USAGES -> CeiText.t("cei.tab.usages");
                    case LOOT -> CeiText.t("cei.tab.loot");
                    case WORLD -> CeiText.t("cei.tab.biomes");
                };
                context.renderComponentTooltip(this.font, List.of(Component.literal(tooltipText)), mouseX, mouseY);
                return;
            }
        }

        // 2. Tooltips for category tabs
        if (activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES) {
            int first = categoryDrawFrom();
            for (int i = first; i < categoryDrawTo(); i++) {
                RecipeCategory cat = categories.get(i);
                int tabX = containerX - 24;
                int tabY = categoryTabY(i);

                if (categoryTabHit(tabX, tabY, mouseX, mouseY)) {
                    String tooltipText = switch (cat.type) {
                        case CRAFTING -> CeiText.t("cei.station.crafting_table");
                        case SMELTING -> CeiText.t("cei.cat.smelting");
                        case BREWING -> CeiText.t("cei.cat.brewing");
                        case STONECUTTING -> CeiText.t("cei.station.stonecutter");
                        case SMITHING -> CeiText.t("cei.station.smithing_table");
                        case CUSTOM -> {
                            boolean useUsages = (activeMainTab == TabType.USAGES);
                            List<?> list = useUsages ? customUsages : customRecipes;
                            Recipe<?> matchedRecipe = null;
                            for (Object recipeObj : list) {
                                Recipe<?> r = getRecipeFromObj(recipeObj);
                                if (r != null) {
                                    ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
                                    if (java.util.Objects.equals(typeId, cat.customRecipeTypeId)) {
                                        matchedRecipe = r;
                                        break;
                                    }
                                }
                            }
                            if (matchedRecipe != null) {
                                yield getMachineLabel(matchedRecipe);
                            }
                            yield CeiText.t("cei.cat.custom");
                        }
                    };
                    context.renderComponentTooltip(this.font, List.of(Component.literal(tooltipText)), mouseX, mouseY);
                    return;
                }
            }
        }

        // 3. Tooltips for hovered slots
        for (RenderedSlot slot : activeSlots) {
            if (mouseX >= slot.x && mouseX < slot.x + slot.size && mouseY >= slot.y && mouseY < slot.y + slot.size) {
                hoveredSlot = slot;
                context.renderComponentTooltip(this.font, this.getTooltipFromItem(this.minecraft, slot.stack), mouseX, mouseY);
                return;
            }
        }

        // 4. Tooltip for "+" auto-transfer button
        if (activeCategory != null && activeCategory.type == CategoryType.CRAFTING && isParentCraftingTable() && !getActiveRecipesList().isEmpty()) {
            int gridStartX = containerX + 15 + 10;
            int gridStartY = containerY + 28 + 18;
            int arrowX = gridStartX + 65;
            int arrowY = gridStartY + 22;
            int plusX = plusButtonX();
            int plusY = plusButtonY();

            if (mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12) {
                String title = CeiText.t("cei.craft.fill");
                String hint = CeiText.t("cei.craft.fill_hint");
                context.renderComponentTooltip(this.font, List.of(
                    Component.literal(title).withStyle(ChatFormatting.GREEN),
                    Component.literal(hint).withStyle(ChatFormatting.GRAY)
                ), mouseX, mouseY);
                return;
            }
        }
    }



    /**
     * Position du bouton "+", pour le dessin ET pour le clic.
     *
     * Elle etait refaite a trois endroits, a partir de reperes differents. Un
     * bouton qui se dessine a un endroit et se clique a un autre est
     * indetectable a la lecture : il faut l'essayer pour s'en apercevoir.
     */
    private int plusButtonX() {
        return containerX + 15 + 10 + 65 + 3;
    }

    private int plusButtonY() {
        // +10 : le bouton collait a la fleche.
        return containerY + 28 + 18 + 22 + 12 + 10;
    }

    private boolean isParentCraftingTable() {
        if (this.parentScreen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handledScreen) {
            return handledScreen.getMenu() instanceof net.minecraft.world.inventory.CraftingMenu;
        }
        return false;
    }

    /**
     * Source unique de la geometrie de la fiche : taille et position sont
     * calculees ici, au rendu comme avant tout test de clic. Tant que les
     * deux passent par cette methode, ils ne peuvent pas diverger.
     */
    private void syncCardGeometry(com.ceketrum.cei.data.PinnedRecipeManager.PinnedCard card) {
        this.containerWidth = card != null
                ? card.getCardWidth()
                : com.ceketrum.cei.data.PinnedRecipeManager.PinnedCard.DEFAULT_WIDTH;
        this.containerHeight = card != null
                ? card.getCardHeight()
                : com.ceketrum.cei.data.PinnedRecipeManager.PinnedCard.DEFAULT_HEIGHT;
        int currentXOffset = card != null ? (int) card.getxOffset() : 0;
        int currentYOffset = card != null ? (int) card.getyOffset() : 0;
        this.containerX = (this.width - this.containerWidth) / 2 + currentXOffset;
        this.containerY = (this.height - this.containerHeight) / 2 + currentYOffset;
    }

    /**
     * Nombre d'onglets de categorie tenant dans la colonne, calcule sur la
     * hauteur de la fiche.
     *
     * La place des deux boutons de defilement est toujours retiree, meme
     * quand ils ne sont pas affiches : sinon ce nombre dependrait de
     * categoriesScrollable(), qui depend de lui.
     */
    private int maxVisibleCategories() {
        int band = containerHeight - 2 * (SCROLL_BTN_H + 2);
        return Math.max(3, (band + 4) / 26);
    }

    /**
     * Les lignes d'infobulle d'un item, en texte brut, telles que le jeu les
     * produirait -- description du mod comprise.
     *
     * La premiere ligne est le nom : elle est sautee, il est deja affiche en
     * titre partout ou cette methode sert. Les lignes techniques (identifiant,
     * nombre de composants) ne sont produites par le jeu que si les infobulles
     * avancees sont actives : il n'y a donc rien a filtrer, F3+H suffit a les
     * faire apparaitre ou disparaitre.
     *
     * STATIQUE, et posee sur une sous-classe de Screen a dessein : c'est la
     * seule position d'ou l'appel a getTooltipFromItem compile quel que soit
     * son niveau d'acces dans la lignee visee.
     */
    public static String itemTooltipText(ItemStack stack, int maxLines) {
        return itemTooltipText(stack, maxLines, true);
    }

    /**
     * @param withInspector ajoute les lignes du mode developpeur en fin de
     *                      texte. Elles ne sont produites que si l'option est
     *                      cochee, l'appelant n'a donc rien a tester.
     */
    public static String itemTooltipText(ItemStack stack, int maxLines, boolean withInspector) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int kept = 0;

        // Avec les infobulles avancees (F3+H), le jeu ajoute en queue
        // l'identifiant de l'item puis son nombre de composants. On coupe des
        // l'identifiant : sa valeur se calcule au caractere pres depuis le
        // registre, donc le repere ne depend d'aucune traduction -- alors que
        // "6 component(s)" vient d'une cle traduite. Tout ce qui suit tombe
        // avec, et la durabilite, placee avant, est conservee.
        String idLine = "";
        try {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) idLine = id.toString();
        } catch (Exception e) {
            // Registre indisponible : mieux vaut tout garder que rien.
        }

        try {
            var lines = getTooltipFromItem(Minecraft.getInstance(), stack);
            for (int i = 1; i < lines.size() && kept < maxLines; i++) {
                String line = lines.get(i).getString();
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!idLine.isEmpty() && line.equals(idLine)) break;
                if (kept > 0) out.append('\n');
                out.append(line);
                kept++;
            }
        } catch (Exception e) {
            // Une infobulle moddee qui leve ne doit pas emporter le rendu de
            // toute la fiche.
            return "";
        }
        String inspector = withInspector
                ? com.ceketrum.cei.gui.module.cei.util.CeiDevTools.inspect(stack)
                : "";
        if (!inspector.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append(inspector);
        }
        return out.toString();
    }

    /**
     * Dessine la decomposition.
     *
     * Toutes les zones sensibles sont enregistrees ici, au moment ou elles sont
     * tracees. Le clic et la molette les relisent : il n'existe donc pas de
     * seconde geometrie a garder en phase.
     */
    private void drawCalculator(GuiGraphics context, int mouseX, int mouseY,
                                int x, int y, int w, int h) {
        calcAreaX = x;
        calcAreaY = y;
        calcAreaW = w;
        calcAreaH = h;
        calcChevrons.clear();

        // Une seule ligne d'en-tete depuis que les bascules ont disparu.
        //
        // Les hauteurs ne sont pas approximatives, elles se comptent :
        //
        //   y-4 .. y+11   icone de l'objet vise (seize pixels)
        //   y+12, y+13    marge
        //   y+14          filet
        //   y+15          marge
        //   y+16 ..       premiere icone de l'arbre, dessinee a listTop - 4
        //
        // A y+17, l'icone de l'en-tete descendait jusqu'a y+12 et mordait le
        // filet, trace a cette meme ligne.
        int rowY = y;
        int listTop = y + 20;
        int bottom = y + h;
        int visible = Math.max(1, (bottom - listTop) / 18);
        calcRowsY = listTop;
        calcRowsH = Math.max(0, bottom - listTop);

        // Le resultat est demande AVANT de dessiner l'en-tete : c'est le
        // nombre de lignes qui decide si les fleches y prennent leur place.
        var result = com.ceketrum.cei.gui.module.cei.util.CeiCraftTree
                .get(targetStack, calcQty, com.ceketrum.cei.config.CeiConfig.getInstance().getCraftTreeDepth());

        // -4 et non -3 : l'icone se centre ainsi exactement sur le texte --
        // seize pixels contre huit, les deux centres tombent sur y+3 -- et
        // cesse de mordre le filet.
        drawStack(context, targetStack, x, rowY - 4);

        int boxW = 46;
        int boxX = x + w - boxW;
        calcMinusX = boxX;
        calcMinusY = rowY - 2;
        calcPlusX = boxX + boxW - 11;
        calcPlusY = rowY - 2;

        drawMiniButton(context, calcMinusX, calcMinusY, "-", mouseX, mouseY);
        drawMiniButton(context, calcPlusX, calcPlusY, "+", mouseX, mouseY);
        String qty = String.valueOf(calcQty);
        int qtyW = this.font.width(qty);
        context.drawString(this.font, qty,
                boxX + (boxW - qtyW) / 2, rowY, 0xFFFFFFFF, false);

        context.fill(x, rowY + 14, x + w, rowY + 15, 0x22FFFFFF);

        if (result.noRecipe) {
            calcHasArrows = false;
            calcRowsH = 0;
            drawCalcTitle(context, x, rowY, boxX - 6);
            context.drawString(this.font, CeiText.t("cei.info.no_recipes"),
                    x, listTop, 0xFFFF6666, false);
            return;
        }

        // Tout replie a l'ouverture, une seule fois : changer la quantite ne
        // doit pas rabattre ce que le joueur vient d'ouvrir.
        if (!calcSeeded) {
            seedCollapsed(result.roots);
            calcSeeded = true;
        }

        // La mise a plat ne parcourt que des noeuds deja construits : aucune
        // recette n'est relue ici, malgre l'appel par image.
        java.util.List<com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.Row> rows =
                com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.flatten(result.roots, calcCollapsed);
        int total = rows.size();

        // Le bornage se fait ICI parce que le dessin est le seul endroit qui
        // connaisse la hauteur disponible -- laquelle change quand on
        // redimensionne la fiche, et quand on replie une branche.
        int maxScroll = Math.max(0, total - visible);
        if (calcScroll > maxScroll) calcScroll = maxScroll;
        if (calcScroll < 0) calcScroll = 0;

        int titleRight = boxX - 6;
        calcHasArrows = total > visible;
        if (calcHasArrows) {
            calcUpX = boxX - 27;
            calcUpY = rowY - 2;
            calcDownX = calcUpX + 12;
            calcDownY = rowY - 2;
            drawArrowButton(context, calcUpX, calcUpY, true, mouseX, mouseY);
            drawArrowButton(context, calcDownX, calcDownY, false, mouseX, mouseY);
            titleRight = calcUpX - 6;

            String pos = (calcScroll + 1) + "-" + Math.min(total, calcScroll + visible)
                    + "/" + total;
            int pw = this.font.width(pos);
            int px = calcUpX - 4 - pw;
            // Le compteur cede la place au titre plutot que de l'ecraser.
            if (px > x + 60) {
                context.drawString(this.font, pos, px, rowY, 0xFF888888, false);
                titleRight = px - 6;
            }
        }
        drawCalcTitle(context, x, rowY, titleRight);

        drawTreeRows(context, rows, x, listTop, w, bottom, visible, mouseX, mouseY);

        if (result.truncated) {
            context.drawString(this.font, "...", x, bottom - 8, 0xFF888888, false);
        }
    }

    /** Le nom de l'objet vise, tronque a la place que lui laisse l'en-tete. */
    private void drawCalcTitle(GuiGraphics context, int x, int rowY, int right) {
        String title = TextRenderHelper.truncateText(
                targetStack.getHoverName().getString(),
                Math.max(12, right - (x + 20)), this.font);
        context.drawString(this.font, title, x + 20, rowY, 0xFFFFD700, false);
    }

    /**
     * Replie toutes les branches ayant des enfants.
     *
     * Recursion bornee par les memes garde-fous que la descente : au plus
     * MAX_NODES noeuds, MAX_DEPTH niveaux.
     */
    private void seedCollapsed(java.util.List<com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.Node> nodes) {
        for (var n : nodes) {
            if (n.children.isEmpty()) continue;
            calcCollapsed.add(n.key);
            seedCollapsed(n.children);
        }
    }

    /**
     * Les lignes de l'arbre.
     *
     * Les traits de liaison se lisent dans row.trail : un bit par profondeur
     * ou un ancetre a encore des freres en dessous. C'est ce qui evite de
     * remonter la hierarchie a chaque ligne pour savoir s'il faut prolonger un
     * trait.
     */
    private void drawTreeRows(GuiGraphics context,
                              java.util.List<com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.Row> rows,
                              int x, int rowY, int w, int bottom, int visible,
                              int mouseX, int mouseY) {
        int end = Math.min(rows.size(), calcScroll + visible);
        for (int i = calcScroll; i < end; i++) {
            if (rowY + 18 > bottom) break;
            var row = rows.get(i);
            var node = row.node;
            int indent = row.depth * 10;

            for (int d = 0; d < row.depth; d++) {
                if ((row.trail & (1L << d)) != 0) {
                    context.fill(x + d * 10 + 3, rowY - 5, x + d * 10 + 4, rowY + 13, 0x33FFFFFF);
                }
            }
            if (row.depth > 0) {
                int gx = x + (row.depth - 1) * 10 + 3;
                context.fill(gx, rowY - 5, gx + 1, rowY + 4, 0x55FFFFFF);
                context.fill(gx, rowY + 3, gx + 7, rowY + 4, 0x55FFFFFF);
            }

            if (!node.children.isEmpty()) {
                int cx = x + indent;
                boolean open = !calcCollapsed.contains(node.key);
                boolean hover = mouseX >= cx - 1 && mouseX < cx + 9
                        && mouseY >= rowY - 4 && mouseY < rowY + 10;
                drawChevron(context, cx, rowY + 1, open, hover);
                // Cible de clic elargie a la hauteur de la ligne : un triangle
                // de 7 px se rate a la souris.
                calcChevrons.add(new long[]{ cx - 1, rowY - 4, 10, 14, node.key });
            }

            int ix = x + indent + 9;
            drawStack(context, node.stack, ix, rowY - 4);
            activeSlots.add(new RenderedSlot(node.stack, ix, rowY - 4, 16));

            String amount = com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.fmt(node.count);
            int amountW = this.font.width(amount);
            int amountRight = x + w - 14;
            int nameX = ix + 20;
            String name = TextRenderHelper.truncateText(
                    node.stack.getHoverName().getString(),
                    Math.max(12, amountRight - amountW - 4 - nameX), this.font);
            // L'outil se distingue a l'oeil, nom et quantite en bleu clair :
            // une ligne qui affiche 1 quand ses voisines affichent 4 doit dire
            // pourquoi.
            int nameColor = node.tool ? 0xFF88CCFF
                    : (node.raw ? 0xFFDDDDDD : 0xFFAAAAAA);
            context.drawString(this.font, name, nameX, rowY, nameColor, false);
            context.drawString(this.font, amount, amountRight - amountW, rowY,
                    node.tool ? 0xFF88CCFF : 0xFFFFFFFF, false);

            int px = x + w - 9;
            context.fill(px, rowY + 1, px + 6, rowY + 7,
                    node.enough() ? 0xFF44CC44 : 0xFFCC4444);

            rowY += 18;
        }
    }

    /** Petit bouton carre de 11 px, pour le reglage de la quantite. */
    private void drawMiniButton(GuiGraphics context, int x, int y, String label,
                                int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 11 && mouseY >= y && mouseY < y + 11;
        context.fill(x, y, x + 11, y + 11, hover ? 0x66FFFFFF : 0x22FFFFFF);
        context.renderOutline(x, y, 11, 11, 0x44FFFFFF);
        int lw = this.font.width(label);
        context.drawString(this.font, label, x + (11 - lw) / 2, y + 2, 0xFFFFFFFF, false);
    }

    /**
     * Fleche de pagination.
     *
     * Dessinee en rectangles plutot qu'en caractere : la police par defaut ne
     * garantit pas les triangles Unicode, et une fleche manquante passerait
     * pour un bouton mort.
     */
    private void drawArrowButton(GuiGraphics context, int x, int y, boolean up,
                                 int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 11 && mouseY >= y && mouseY < y + 11;
        context.fill(x, y, x + 11, y + 11, hover ? 0x66FFFFFF : 0x22FFFFFF);
        context.renderOutline(x, y, 11, 11, 0x44FFFFFF);
        for (int i = 0; i < 4; i++) {
            int ly = up ? y + 3 + i : y + 7 - i;
            context.fill(x + 5 - i, ly, x + 6 + i, ly + 1, 0xFFFFFFFF);
        }
    }

    /** Chevron de repli : plein vers le bas si la branche est ouverte. */
    private void drawChevron(GuiGraphics context, int x, int y, boolean open, boolean hover) {
        int c = hover ? 0xFFFFFFFF : 0xFFAAAAAA;
        if (open) {
            context.fill(x, y, x + 7, y + 1, c);
            context.fill(x + 1, y + 1, x + 6, y + 2, c);
            context.fill(x + 2, y + 2, x + 5, y + 3, c);
            context.fill(x + 3, y + 3, x + 4, y + 4, c);
        } else {
            context.fill(x + 1, y - 1, x + 2, y + 6, c);
            context.fill(x + 2, y, x + 3, y + 5, c);
            context.fill(x + 3, y + 1, x + 4, y + 4, c);
            context.fill(x + 4, y + 2, x + 5, y + 3, c);
        }
    }

    /** Borne la quantite et rien d'autre : le recalcul se fait a la lecture. */
    private void setCalcQty(int q) {
        calcQty = Math.max(com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.MIN_QTY,
                Math.min(com.ceketrum.cei.gui.module.cei.util.CeiCraftTree.MAX_QTY, q));
    }

    /**
     * Molette dans le calculateur.
     *
     * Sur la bande des lignes elle fait defiler ; ailleurs -- donc sur
     * l'en-tete, la ou se trouvent justement [-] et [+] -- elle regle la
     * quantite. Toujours aucune barre de defilement : le deplacement reste aux
     * fleches et a la molette.
     */
    private boolean calculatorWheel(double mouseX, double mouseY, double amount) {
        if (!calcOpen || calcAreaW <= 0) return false;
        if (mouseX < calcAreaX || mouseX >= calcAreaX + calcAreaW) return false;
        if (mouseY < calcAreaY || mouseY >= calcAreaY + calcAreaH) return false;

        if (calcHasArrows && mouseY >= calcRowsY && mouseY < calcRowsY + calcRowsH) {
            calcScroll = Math.max(0, calcScroll - (int) Math.signum(amount));
            return true;
        }

        int step = hasShiftDown() ? 16 : 1;
        setCalcQty(calcQty + (int) Math.signum(amount) * step);
        return true;
    }


    /**
     * Hauteur reservee au texte de l'item, sous la recette.
     *
     * Elle ne prend que ce qui reste une fois la recette servie, et jamais
     * plus de la moitie de la zone. C'est ce qui donne enfin un usage a la
     * hauteur gagnee en agrandissant la fiche.
     */
    private int descBandHeight(int contentHeight) {
        int free = contentHeight - RECIPE_MIN_H;
        if (free < 20) return 0;
        return Math.min(free, contentHeight / 2);
    }

    /**
     * Dessine le texte de l'item dans la bande, coupe au ciseau.
     *
     * La hauteur totale n'est pas estimee : elle est celle que le dessin a
     * reellement produite, drawWrappedText rendant le Y d'arrivee. Mesure et
     * dessin ne peuvent donc pas diverger.
     */
    private void drawItemDescription(GuiGraphics context, int x, int y, int w, int h) {
        String text = itemTooltipText(this.targetStack, DESC_MAX_LINES);
        if (text.isEmpty()) {
            descBandH = 0;
            descTotalH = 0;
            return;
        }

        descBandX = x;
        descBandY = y;
        descBandW = w;
        descBandH = h;

        int maxScroll = Math.max(0, descTotalH - h);
        if (descScroll > maxScroll) descScroll = maxScroll;
        if (descScroll < 0) descScroll = 0;

        // Un filet separe la recette du texte : colles, les deux blocs se
        // lisent comme un seul.
        context.fill(x, y - 5, x + w, y - 4, 0x22FFFFFF);

        context.enableScissor(x, y, x + w, y + h);
        int end = TextRenderHelper.drawWrappedText(context, text, x, y - descScroll, w,
                0xDDFFFFFF, 0.75f, 0, this.font);
        context.disableScissor();
        descTotalH = end - (y - descScroll);
    }

    /**
     * Molette sur la zone de texte.
     *
     * Bornee par ce que le dessin a pose, et rendue seulement si le texte
     * deborde reellement : sinon la liste d'items defilerait derriere alors
     * qu'on visait manifestement ce bloc.
     */
    private boolean descriptionWheel(double mouseX, double mouseY, double amount) {
        if (descBandH <= 0 || descTotalH <= descBandH) return false;
        if (mouseX < descBandX || mouseX >= descBandX + descBandW) return false;
        if (mouseY < descBandY || mouseY >= descBandY + descBandH) return false;

        int maxScroll = descTotalH - descBandH;
        descScroll -= (int) Math.signum(amount) * 12;
        if (descScroll < 0) descScroll = 0;
        if (descScroll > maxScroll) descScroll = maxScroll;
        return true;
    }

    /** Coin haut-gauche de la poignee, logee dans le coin bas-droit de la fiche. */
    private int resizeGripX() {
        return containerX + containerWidth - RESIZE_GRIP - 1;
    }

    private int resizeGripY() {
        return containerY + containerHeight - RESIZE_GRIP - 1;
    }

    private boolean isOverResizeGrip(double mouseX, double mouseY) {
        int gx = resizeGripX();
        int gy = resizeGripY();
        return mouseX >= gx && mouseX < gx + RESIZE_GRIP
            && mouseY >= gy && mouseY < gy + RESIZE_GRIP;
    }

    public boolean isMouseOverCard(double mouseX, double mouseY) {
        return mouseX >= (containerX - 24) && mouseX < (containerX + containerWidth + 24) &&
               mouseY >= containerY && mouseY < (containerY + containerHeight);
    }

    private void updatePinnedState() {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        if (card != null) {
            card.setActiveTab(this.activeMainTab);
            card.setActiveCategory(this.activeCategory);
            card.setCurrentPage(this.currentPage);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.ceiModule != null && this.ceiModule.handleMouseClick(mouseX, mouseY, button, this.width, this.height, this.font)) {
            return true;
        }


        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;

        // La fiche a pu etre redimensionnee depuis la derniere image : on
        // recalcule la geometrie avant le moindre test de position.
        syncCardGeometry(card);

        // 0. Header buttons click handling
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        if (mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12) {
            if (isPinned) {
                manager.unpinRecipe(this.targetStack);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } else {
                manager.pinRecipe(this.targetStack, this.activeMainTab, this.activeCategory, this.currentPage, null);

                // Play click sound
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));

                // Return to base interface (parent screen) since recipe card is now pinned and rendered as overlay!
                this.minecraft.setScreen(this.parentScreen);
            }
            return true;
        }

        if (isPinned) {
            int hudX = containerX + 20;
            int hudY = containerY + 5;
            if (mouseX >= hudX && mouseX < hudX + 12 && mouseY >= hudY && mouseY < hudY + 12) {
                card.setShowInHud(!card.isShowInHud());
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int opX = containerX + 34;
            int opY = containerY + 5;
            if (mouseX >= opX && mouseX < opX + 12 && mouseY >= opY && mouseY < opY + 12) {
                card.cycleOpacity();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // Poignee de redimensionnement. Testee avant la barre de titre et
        // avant le contenu : sinon un clic dans le coin tomberait sur un slot.
        // Bouton du calculateur, teste avec les autres boutons d'en-tete.
        if (isOverCalcButton(mouseX, mouseY)) {
            calcOpen = !calcOpen;
            if (calcOpen) {
                setCalcQty(calcQty);
                // Chaque ouverture repart d'un arbre entierement replie.
                calcCollapsed.clear();
                calcSeeded = false;
                calcScroll = 0;
            }
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Commandes du calculateur. Testees avant le contenu de l'onglet :
        // quand il est ouvert, c'est lui qui occupe la zone.
        if (calcOpen) {
            if (mouseX >= calcMinusX && mouseX < calcMinusX + 11
                    && mouseY >= calcMinusY && mouseY < calcMinusY + 11) {
                setCalcQty(calcQty - (hasShiftDown() ? 16 : 1));
                return true;
            }
            if (mouseX >= calcPlusX && mouseX < calcPlusX + 11
                    && mouseY >= calcPlusY && mouseY < calcPlusY + 11) {
                setCalcQty(calcQty + (hasShiftDown() ? 16 : 1));
                return true;
            }
            if (calcHasArrows) {
                if (mouseX >= calcUpX && mouseX < calcUpX + 11
                        && mouseY >= calcUpY && mouseY < calcUpY + 11) {
                    calcScroll = Math.max(0, calcScroll - 1);
                    return true;
                }
                if (mouseX >= calcDownX && mouseX < calcDownX + 11
                        && mouseY >= calcDownY && mouseY < calcDownY + 11) {
                    // Le plafond est applique au dessin, seul endroit qui
                    // connaisse la hauteur disponible.
                    calcScroll++;
                    return true;
                }
            }
            // Chevrons, relus exactement tels qu'ils ont ete dessines.
            for (long[] hit : calcChevrons) {
                if (mouseX >= hit[0] && mouseX < hit[0] + hit[2]
                        && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                    if (!calcCollapsed.remove(hit[4])) calcCollapsed.add(hit[4]);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }


        if (isPinned && isOverResizeGrip(mouseX, mouseY)) {
            manager.bringToFront(card);
            card.beginResize(containerX, containerY);
            return true;
        }

        // Dragging Detection
        if (mouseX >= containerX && mouseX < containerX + containerWidth && mouseY >= containerY && mouseY < containerY + 20) {
            if (card != null) {
                manager.bringToFront(card);
                card.setDragging(true);
            }
            return true;
        }

        // 0. Check "+" auto-transfer button click
        if (activeCategory != null && activeCategory.type == CategoryType.CRAFTING && isParentCraftingTable() && !getActiveRecipesList().isEmpty()) {
            RecipeHolder<?> entry = (RecipeHolder<?>) getActiveRecipesList().get(currentPage);
            Recipe<?> recipe = entry.value();

            int gridStartX = containerX + 15 + 10;
            int gridStartY = containerY + 28 + 18;
            int arrowX = gridStartX + 65;
            int arrowY = gridStartY + 22;
            int plusX = plusButtonX();
            int plusY = plusButtonY();

            if (mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12) {
                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handledScreen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) this.parentScreen;
                net.minecraft.world.inventory.CraftingMenu craftingHandler = (net.minecraft.world.inventory.CraftingMenu) handledScreen.getMenu();

                int quantity = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1;
                if (button == 1) quantity = -1; // Right-click fills maximum

                com.ceketrum.cei.gui.module.cei.util.CraftingHelper.placeRecipeIngredients(
                    craftingHandler,
                    recipe,
                    this.minecraft.level.registryAccess(),
                    this.minecraft.player,
                    quantity
                );

                // Play click sound
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));

                // Instantly go back to parent screen (crafting table) so they can take the crafted item!
                this.minecraft.setScreen(this.parentScreen);
                return true;
            }
        }

        // 1. Check main vertical tabs clicks
        for (int i = 0; i < visibleMainTabs.size(); i++) {
            TabType tab = visibleMainTabs.get(i);
            int tabX = containerX + containerWidth;
            int tabY = containerY + 10 + i * 26;

            if (mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22) {
                activeMainTab = tab;
                updateCategories();
                descScroll = 0;
                updatePinnedState();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // 2 bis. Fleches de defilement de la colonne d'onglets.
        //         Testees AVANT les onglets : elles se trouvent juste au-dessus
        //         et juste en dessous de la colonne, et un clic doit revenir a
        //         la fleche, pas au premier onglet.
        if ((activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES)
                && categoriesScrollable()) {
            if (firstVisibleCategory() > 0 && categoryScrollHit(mouseX, mouseY, categoryScrollUpY())) {
                categoryScroll--;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (lastVisibleCategory() < categories.size()
                    && categoryScrollHit(mouseX, mouseY, categoryScrollDownY())) {
                categoryScroll++;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // 2. Check category tabs clicks
        if (activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES) {
            int first = categoryDrawFrom();
            for (int i = first; i < categoryDrawTo(); i++) {
                RecipeCategory cat = categories.get(i);
                int tabX = containerX - 24;
                int tabY = categoryTabY(i);

                if (categoryTabHit(tabX, tabY, mouseX, mouseY)) {
                    activeCategory = cat;
                    currentPage = 0;
                    descScroll = 0;
                    updatePinnedState();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        // 3. Check pagination arrow clicks
        if (activeCategory != null && !getActiveRecipesList().isEmpty()) {
            int contentWidth = containerWidth - 30;
            String pageStr = String.format("%d / %d", currentPage + 1, getMaxPages());
            int pageW = this.font.width(pageStr);
            int pageY = containerY + containerHeight - 16;

            int arrowLeftX = containerX + 15 + (contentWidth - pageW) / 2 - 15;
            int arrowRightX = containerX + 15 + (contentWidth + pageW) / 2 + 5;

            if (mouseX >= arrowLeftX && mouseX < arrowLeftX + 8 && mouseY >= pageY && mouseY < pageY + 9) {
                currentPage--;
                if (currentPage < 0) currentPage = getMaxPages() - 1;
                updatePinnedState();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= arrowRightX && mouseX < arrowRightX + 8 && mouseY >= pageY && mouseY < pageY + 9) {
                currentPage++;
                if (currentPage >= getMaxPages()) currentPage = 0;
                updatePinnedState();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // 4. Clicked on a recipe slot -> Navigate to that item!
        if (hoveredSlot != null && (button == 0 || button == 1)) {
            boolean showUsage = (button == 1);
            Minecraft.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoveredSlot.stack, showUsage));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        if (card != null && card.isResizing()) {
            // Le coin haut-gauche reste fige, le coin bas-droit suit la souris
            // au pixel : pas d'amortissement, la fiche ne traine pas derriere
            // le curseur. La taille est bornee pour rester dans la fenetre.
            int anchorX = card.getResizeAnchorX();
            int anchorY = card.getResizeAnchorY();
            card.resizeBy(deltaX, deltaY, this.width - anchorX, this.height - anchorY);

            // Meme expression que dans syncCardGeometry, division entiere
            // comprise : l'offset recalcule redonne exactement anchorX/anchorY.
            card.setxOffset(anchorX - (this.width - card.getCardWidth()) / 2);
            card.setyOffset(anchorY - (this.height - card.getCardHeight()) / 2);
            syncCardGeometry(card);
            return true;
        }
        if (card != null && card.isDragging()) {
            double newXOffset = card.getxOffset() + deltaX;
            double newYOffset = card.getyOffset() + deltaY;

            // Constrain dragging offsets to the bounds of the window so the recipe card cannot be lost off-screen.
            double minX = - (double)(this.width - this.containerWidth) / 2;
            double maxX = (double)(this.width - this.containerWidth) / 2;
            double minY = - (double)(this.height - this.containerHeight) / 2;
            double maxY = (double)(this.height - this.containerHeight) / 2;

            card.setxOffset(Math.max(minX, Math.min(maxX, newXOffset)));
            card.setyOffset(Math.max(minY, Math.min(maxY, newYOffset)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.ceiModule != null) this.ceiModule.handleMouseRelease();

        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        if (card != null && (card.isDragging() || card.isResizing())) {
            card.setDragging(false);
            card.setResizing(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.ceiModule != null && this.ceiModule.getPanelRenderer().getSearchBar().isFocused()) {
            if (this.ceiModule.handleKeyPress(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (keyCode == 256 || this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }

        ItemStack hoverStack = null;
        if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
            hoverStack = hoveredSlot.stack;
        } else if (this.ceiModule != null && this.ceiModule.getHoveredStack() != null) {
            hoverStack = this.ceiModule.getHoveredStack();
        }

        if (hoverStack != null && !hoverStack.isEmpty()) {
            // Mode developpeur : meme touche que dans l'inventaire, pour que
            // le geste soit le meme partout.
            if (keyCode == 67 && com.ceketrum.cei.gui.module.cei.util.CeiDevTools.enabled()) {
                var fmt = (modifiers & 1) != 0   // GLFW_MOD_SHIFT
                        ? com.ceketrum.cei.gui.module.cei.util.CeiDevTools.nextFormat()
                        : com.ceketrum.cei.gui.module.cei.util.CeiDevTools.currentFormat();
                com.ceketrum.cei.gui.module.cei.util.CeiDevTools.copy(hoverStack, fmt);
                return true;
            }
            if (keyCode == 82) { // 'R'
                Minecraft.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoverStack, false));
                return true;
            }
            if (keyCode == 85) { // 'U'
                Minecraft.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoverStack, true));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.ceiModule != null && this.ceiModule.handleCharTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (calculatorWheel(mouseX, mouseY, verticalAmount)) return true;
        if (categoryWheel(mouseX, mouseY, verticalAmount)) return true;
        if (descriptionWheel(mouseX, mouseY, verticalAmount)) return true;
        if (this.ceiModule != null) {
            float animationSlideOffset = this.ceiModule.getPanelRenderer().getAnimationSlideOffset();
            if (this.ceiModule.handleMouseScroll(mouseX, mouseY, verticalAmount, this.width, this.height, this.font, animationSlideOffset)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static List<ItemStack> extractCustomOutputs(Recipe<?> recipe, net.minecraft.util.context.ContextMap contextMap) {
        List<ItemStack> list = new ArrayList<>();
        var rm = Minecraft.getInstance().level.registryAccess();

        // 1. Try RecipeDisplay result
        try {
            for (var display : recipe.display()) {
                ItemStack result = display.result().resolveForFirstStack(contextMap);
                if (result != null && !result.isEmpty()) {
                    list.add(result.copy());
                }
            }
        } catch (Exception e) {}

        // 2. Reflectively search for outputs/results methods and fields
        Class<?> clazz = recipe.getClass();
        List<String> methodNames = List.of(
            "getOutputs", "getOutputsList", "getResults", "getRecipeOutputs",
            "getOutput", "getResult", "getOutputItems", "getResultsList"
        );

        for (String methodName : methodNames) {
            try {
                Method method;
                Object resultObj = null;
                try {
                    method = clazz.getMethod(methodName, net.minecraft.core.RegistryAccess.class);
                    resultObj = method.invoke(recipe, rm);
                } catch (NoSuchMethodException e) {
                    try {
                        method = clazz.getMethod(methodName, net.minecraft.core.HolderLookup.Provider.class);
                        resultObj = method.invoke(recipe, rm);
                    } catch (NoSuchMethodException e2) {
                        try {
                            method = clazz.getMethod(methodName);
                            resultObj = method.invoke(recipe);
                        } catch (Exception e3) {}
                    }
                }

                if (resultObj != null) {
                    unpackOutputObject(resultObj, list);
                }
            } catch (Exception e) {}
        }

        // Search fields if list is still empty or to find secondary outputs
        List<String> fieldNames = List.of(
            "outputs", "results", "output", "result", "outputItems",
            "recipeOutputs", "outputItem", "resultItem"
        );

        for (String fieldName : fieldNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object val = field.get(recipe);
                if (val != null) {
                    unpackOutputObject(val, list);
                }
            } catch (Exception e) {}
        }

        // Remove duplicates and empty stacks
        List<ItemStack> cleanList = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (ItemStack stack : list) {
            if (stack != null && !stack.isEmpty()) {
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + ":" + stack.getCount();
                if (keys.add(key)) {
                    cleanList.add(stack);
                }
            }
        }

        return cleanList;
    }

    /** Profondeur maximale de la descente reflexive. */
    private static final int CEI_UNPACK_MAX_DEPTH = 8;
    /**
     * Nombre maximal d'objets visites pour UNE recette.
     *
     * Valeur reprise de G7, qui la portait deja depuis le meme incident sur
     * 26.3. Deux mille objets decrivent tres largement une recette ; au-dela,
     * on n'explore plus une recette, on erre dans le graphe d'objets du jeu.
     */
    private static final int CEI_UNPACK_MAX_VISITED = 2000;
    /** La premiere coupe est journalisee, les suivantes non. */
    private static boolean CEI_UNPACK_WARNED = false;

    private static void unpackOutputObject(Object obj, List<ItemStack> list) {
        unpackOutputObject(obj, list, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
    }

    private static void unpackOutputObject(Object obj, List<ItemStack> list, Set<Object> visited, int depth) {
        if (obj == null || depth > CEI_UNPACK_MAX_DEPTH) return;
        // PLAFOND DE LARGEUR -- c'est lui qui manquait.
        //
        // La profondeur etait bornee, le nombre de branches ne l'etait pas.
        // Cette methode invoque toute methode sans argument de l'objet : avec
        // b branches par noeud, le cout est b puissance 8. L'ensemble
        // anti-cycle n'y peut rien, stream() et copy() rendant un objet NEUF a
        // chaque appel -- visited.add() reussit donc toujours.
        //
        // Sans ce plafond, une seule recette moddee au graphe touffu fige le
        // fil de rendu pendant des minutes en allouant sans cesse, jusqu'a la
        // mort du processus par manque de memoire, et sans un mot au journal.
        if (visited.size() > CEI_UNPACK_MAX_VISITED) {
            if (!CEI_UNPACK_WARNED) {
                CEI_UNPACK_WARNED = true;
                org.slf4j.LoggerFactory.getLogger("cei-unpack").warn(
                        "[cei] exploration bornee a {} objets ; premiere coupe sur {}."
                        + " Sans cette borne, cette recette figeait le rendu.",
                        CEI_UNPACK_MAX_VISITED, obj.getClass().getName());
            }
            return;
        }
        if (!visited.add(obj)) return;

        if (obj instanceof ItemStack stack) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
            return;
        }

        if (obj instanceof Block block) {
            list.add(new ItemStack(block));
            return;
        }

        if (obj instanceof Item item) {
            list.add(new ItemStack(item));
            return;
        }

        if (obj instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            java.util.List<ItemStack> matches = ing.items().map(h -> new ItemStack(h.value())).toList();
            for (ItemStack match : matches) {
                if (match != null && !match.isEmpty()) {
                    list.add(match.copy());
                }
            }
            return;
        }

        if (obj instanceof Collection<?> col) {
            for (Object el : col) {
                unpackOutputObject(el, list, visited, depth + 1);
            }
            return;
        }

        if (obj.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                unpackOutputObject(java.lang.reflect.Array.get(obj, i), list, visited, depth + 1);
            }
            return;
        }

        if (obj instanceof java.util.Optional<?> opt) {
            if (opt.isPresent()) {
                unpackOutputObject(opt.get(), list, visited, depth + 1);
            }
            return;
        }

        // Avoid reflecting on engine / framework classes to prevent traversing server/client state
        String className = obj.getClass().getName();
        if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.") || className.startsWith("com.sun.") ||
            className.startsWith("net.minecraft.") || className.startsWith("com.mojang.") || className.startsWith("com.google.") ||
            className.startsWith("io.netty.") || className.startsWith("org.lwjgl.")) {
            return;
        }

        // Reflectively search the object's methods and fields
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            // Fields
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null) {
                        unpackOutputObject(val, list, visited, depth + 1);
                    }
                } catch (Exception e) {}
            }

            // Methods - only invoke zero-arg getters that sound like output getters
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && !m.getReturnType().equals(Void.TYPE)) {
                    String mName = m.getName().toLowerCase();
                    if (mName.contains("result") || mName.contains("output") || mName.contains("item") ||
                        mName.contains("stack") || mName.contains("ingredient") || mName.contains("recipe") ||
                        mName.contains("icon") || mName.contains("value") || mName.contains("display")) {
                        try {
                            m.setAccessible(true);
                            Object val = m.invoke(obj);
                            if (val != null) {
                                unpackOutputObject(val, list, visited, depth + 1);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static List<ItemStack> extractCustomInputs(Recipe<?> recipe, net.minecraft.util.context.ContextMap contextMap) {
        List<ItemStack> list = new ArrayList<>();

        // 1. Try RecipeDisplay ingredients
        try {
            for (var display : recipe.display()) {
                for (var inputSlot : getRecipeIngredients(display)) {
                    if (inputSlot != null) {
                        List<ItemStack> stacks = inputSlot.resolveForStacks(contextMap);
                        if (!stacks.isEmpty()) {
                            list.add(stacks.get(0).copy());
                        } else {
                            list.add(ItemStack.EMPTY);
                        }
                    }
                }
            }
        } catch (Exception e) {}

        // If we only have empty stacks, clear it so we can try custom extraction
        if (list.stream().allMatch(ItemStack::isEmpty)) {
            list.clear();
        }

        // 2. Reflectively search for inputs/ingredients methods and fields
        Class<?> clazz = recipe.getClass();
        List<String> methodNames = List.of(
            "getInputs", "getIngredients", "getRecipeInputs", "getInput", "getInputItems"
        );

        for (String methodName : methodNames) {
            try {
                Method method = clazz.getMethod(methodName);
                Object resultObj = method.invoke(recipe);
                if (resultObj != null) {
                    unpackInputObject(resultObj, list);
                }
            } catch (Exception e) {}
        }

        List<String> fieldNames = List.of(
            "ingredients", "inputs", "input", "recipeInputs", "inputItems"
        );

        for (String fieldName : fieldNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object val = field.get(recipe);
                if (val != null) {
                    unpackInputObject(val, list);
                }
            } catch (Exception e) {}
        }

        // Remove empty stacks and duplicates
        List<ItemStack> cleanList = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (ItemStack stack : list) {
            if (stack != null && !stack.isEmpty()) {
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + ":" + stack.getCount();
                if (keys.add(key)) {
                    cleanList.add(stack);
                }
            }
        }

        return cleanList;
    }

    private static void unpackInputObject(Object obj, List<ItemStack> list) {
        unpackInputObject(obj, list, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
    }

    /**
     * @param depth profondeur restante. Indispensable : cette methode invoque
     *              des accesseurs, et un accesseur qui construit son resultat
     *              renvoie un objet neuf a chaque appel -- l'ensemble
     *              anti-cycle, qui raisonne par identite, ne le reconnait
     *              jamais et la descente ne s'arrete pas d'elle-meme.
     */
    private static void unpackInputObject(Object obj, List<ItemStack> list, Set<Object> visited, int depth) {
        if (obj == null || depth > CEI_UNPACK_MAX_DEPTH) return;
        // PLAFOND DE LARGEUR -- c'est lui qui manquait.
        //
        // La profondeur etait bornee, le nombre de branches ne l'etait pas.
        // Cette methode invoque toute methode sans argument de l'objet : avec
        // b branches par noeud, le cout est b puissance 8. L'ensemble
        // anti-cycle n'y peut rien, stream() et copy() rendant un objet NEUF a
        // chaque appel -- visited.add() reussit donc toujours.
        //
        // Sans ce plafond, une seule recette moddee au graphe touffu fige le
        // fil de rendu pendant des minutes en allouant sans cesse, jusqu'a la
        // mort du processus par manque de memoire, et sans un mot au journal.
        if (visited.size() > CEI_UNPACK_MAX_VISITED) {
            if (!CEI_UNPACK_WARNED) {
                CEI_UNPACK_WARNED = true;
                org.slf4j.LoggerFactory.getLogger("cei-unpack").warn(
                        "[cei] exploration bornee a {} objets ; premiere coupe sur {}."
                        + " Sans cette borne, cette recette figeait le rendu.",
                        CEI_UNPACK_MAX_VISITED, obj.getClass().getName());
            }
            return;
        }
        if (!visited.add(obj)) return;

        if (obj instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            java.util.List<ItemStack> matches = ing.items().map(h -> new ItemStack(h.value())).toList();
            for (ItemStack match : matches) {
                if (match != null && !match.isEmpty()) {
                    list.add(match.copy());
                }
            }
            return;
        }

        if (obj instanceof ItemStack stack) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
            return;
        }

        if (obj instanceof Block block) {
            list.add(new ItemStack(block));
            return;
        }

        if (obj instanceof Item item) {
            list.add(new ItemStack(item));
            return;
        }

        if (obj instanceof Collection<?> col) {
            for (Object el : col) {
                unpackInputObject(el, list, visited, depth + 1);
            }
            return;
        }

        if (obj.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                unpackInputObject(java.lang.reflect.Array.get(obj, i), list, visited, depth + 1);
            }
            return;
        }

        if (obj instanceof java.util.Optional<?> opt) {
            if (opt.isPresent()) {
                unpackInputObject(opt.get(), list, visited, depth + 1);
            }
            return;
        }

        // Avoid reflecting on typical standard library / minecraft / game classes that aren't custom recipe objects
        String className = obj.getClass().getName();
        if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.") || className.startsWith("com.sun.") ||
            // La RACINE du paquet, et rien de plus fin.
            //
            // "net.minecraft.registry." est un nom Yarn et "net.minecraft.class_"
            // un prefixe intermediaire : ni l'un ni l'autre n'existe a
            // l'execution sur NeoForge, ou les classes portent les noms
            // officiels Mojang (seules les methodes sont en SRG). Ces gardes
            // ne se declenchaient donc jamais la, et la reflexion descendait
            // dans Minecraft lui-meme -- d'ou le debordement de pile constate
            // sur G1 NeoForge, et sur lui seul.
            //
            // "net.minecraft." vaut sous Yarn, intermediaire, SRG et Mojang.
            // C'est exactement ce qu'ecrit deja la jumelle unpackOutputObject.
            className.startsWith("net.minecraft.") || className.startsWith("com.mojang.") ||
            className.startsWith("com.google.") || className.startsWith("io.netty.") ||
            className.startsWith("org.lwjgl.")) {
            return;
        }

        // Reflectively search the object's methods and fields
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            // Fields
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null) {
                        unpackInputObject(val, list, visited, depth + 1);
                    }
                } catch (Exception e) {}
            }

            // Methods
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && !m.getReturnType().equals(Void.TYPE)) {
                    // Filter out standard Object methods and dangerous methods
                    String mName = m.getName();
                    if (mName.equals("toString") || mName.equals("hashCode") || mName.equals("getClass") ||
                        mName.equals("clone") || mName.equals("notify") || mName.equals("notifyAll")) {
                        continue;
                    }

                    try {
                        m.setAccessible(true);
                        Object val = m.invoke(obj);
                        if (val != null) {
                            unpackInputObject(val, list, visited, depth + 1);
                        }
                    } catch (Exception e) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Libelle de la station de travail d'une recette moddee.
     *
     * L'ancien comportement affichait "Custom Machine" des que l'icone retombait
     * sur le distributeur, c'est-a-dire pour la quasi-totalite des recettes
     * moddees. On passe ici par CeiRecipeStation, qui derive un nom du type de
     * recette (create:crushing -> "Crushing Wheel", a defaut "Crushing").
     */
    private String getMachineLabel(Recipe<?> recipe) {
        if (recipe == null) return CeiText.t("cei.station.custom");
        ItemStack icon = getMachineIcon(recipe);
        if (icon != null && !icon.isEmpty() && icon.getItem() != Items.DISPENSER) {
            String name = icon.getHoverName().getString();
            if (name != null && !name.isBlank()) return name;
        }
        ResourceLocation typeId = null;
        try {
            typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (Exception e) {
            // type non enregistre : on laisse CeiRecipeStation gerer le null
        }
        return com.ceketrum.cei.gui.module.cei.recipe.view.CeiRecipeStation.labelFor(typeId);
    }

    /**
     * Memorisation des icones de machine.
     *
     * getMachineIconUncached finit par balayer tout le registre des items dans
     * son dernier repli. Sans cache, ce balayage avait lieu a CHAQUE frame et
     * pour chaque onglet de categorie affiche -- un cout invisible en vanilla,
     * net sur un gros pack modde. La cle est le type de recette : deux recettes
     * du meme type donnent la meme icone.
     */
    private static final java.util.Map<ResourceLocation, ItemStack> MACHINE_ICON_CACHE = new java.util.HashMap<>();

    private ItemStack getMachineIcon(Recipe<?> recipe) {
        if (recipe == null) return new ItemStack(Items.DISPENSER);
        ResourceLocation typeId = null;
        try {
            typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (Exception e) {
            // type non enregistre : on ne memorise pas, on calcule a chaque fois
        }
        if (typeId == null) return getMachineIconUncached(recipe);
        ItemStack cached = MACHINE_ICON_CACHE.get(typeId);
        if (cached == null) {
            cached = getMachineIconUncached(recipe);
            MACHINE_ICON_CACHE.put(typeId, cached);
        }
        return cached.copy();
    }

    private ItemStack getMachineIconUncached(Recipe<?> recipe) {
        var client = Minecraft.getInstance();
        if (client.level == null) return new ItemStack(Items.DISPENSER);

        try {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());

            String namespace = null;
            String typePath = null;
            String serPath = null;

            // Try to get type path
            if (typeId != null) {
                namespace = typeId.getNamespace();
                typePath = typeId.getPath();
            } else if (recipe.getType() != null) {
                String s = recipe.getType().toString();
                if (s.contains(":")) {
                    String[] parts = s.split(":");
                    namespace = parts[0];
                    typePath = parts[1];
                } else {
                    typePath = s;
                }
            }

            // Try to get serializer path
            if (serializerId != null) {
                if (namespace == null) namespace = serializerId.getNamespace();
                serPath = serializerId.getPath();
            } else if (recipe.getSerializer() != null) {
                String s = recipe.getSerializer().toString();
                if (s.contains(":")) {
                    String[] parts = s.split(":");
                    if (namespace == null) namespace = parts[0];
                    serPath = parts[1];
                } else {
                    serPath = s;
                }
            }

            String className = recipe.getClass().getSimpleName();
            if (namespace == null || namespace.equals("minecraft")) {
                String fullClass = recipe.getClass().getName().toLowerCase();
                if (fullClass.contains("oritech")) namespace = "oritech";
                else if (fullClass.contains("techreborn")) namespace = "techreborn";
                else if (fullClass.contains("modern_industrialization") || fullClass.contains("modernindustrialization")) namespace = "modern_industrialization";
                else if (fullClass.contains("pokedna")) namespace = "pokedna";
                else if (fullClass.contains("ecliptea")) namespace = "ecliptea";
            }

            if (typePath == null || typePath.contains(".") || typePath.contains("@")) {
                typePath = className.toLowerCase();
                if (typePath.endsWith("recipe")) {
                    typePath = typePath.substring(0, typePath.length() - 6);
                }
            }

            if (serPath == null || serPath.contains(".") || serPath.contains("@")) {
                serPath = className.toLowerCase();
                if (serPath.endsWith("recipe")) {
                    serPath = serPath.substring(0, serPath.length() - 6);
                }
            }

            // 2. Direct lookup fallbacks using solved namespace and path
            if (typePath != null && namespace != null) {
                Item item = getItem(namespace, typePath);
                if (item != null && item != Items.AIR) return new ItemStack(item);

                Item blockItem = getItem(namespace, typePath + "_block");
                if (blockItem != null && blockItem != Items.AIR) return new ItemStack(blockItem);

                Item machineItem = getItem(namespace, typePath + "_machine");
                if (machineItem != null && machineItem != Items.AIR) return new ItemStack(machineItem);

                String path = typePath;
                if (path.endsWith("_recipe")) {
                    path = path.substring(0, path.length() - 7);
                    Item fallback = getItem(namespace, path);
                    if (fallback != null && fallback != Items.AIR) return new ItemStack(fallback);

                    Item fallbackBlock = getItem(namespace, path + "_block");
                    if (fallbackBlock != null && fallbackBlock != Items.AIR) return new ItemStack(fallbackBlock);
                }
            }

            if (serPath != null && namespace != null) {
                Item item = getItem(namespace, serPath);
                if (item != null && item != Items.AIR) return new ItemStack(item);

                Item blockItem = getItem(namespace, serPath + "_block");
                if (blockItem != null && blockItem != Items.AIR) return new ItemStack(blockItem);

                Item machineItem = getItem(namespace, serPath + "_machine");
                if (machineItem != null && machineItem != Items.AIR) return new ItemStack(machineItem);

                String path = serPath;
                if (path.endsWith("_serializer")) {
                    path = path.substring(0, path.length() - 11);
                } else if (path.endsWith("_recipe")) {
                    path = path.substring(0, path.length() - 7);
                }
                Item fallback = getItem(namespace, path);
                if (fallback != null && fallback != Items.AIR) return new ItemStack(fallback);

                Item fallbackBlock = getItem(namespace, path + "_block");
                if (fallbackBlock != null && fallbackBlock != Items.AIR) return new ItemStack(fallbackBlock);
            }

            // 1.21.5+ : la recette porte elle-meme sa station de travail.
            ItemStack declared = com.ceketrum.cei.gui.module.cei.recipe.view.CeiRecipeStation
                    .fromDisplay(recipe);
            if (!declared.isEmpty()) return declared;

            // Repli generique fonde sur l'identifiant du type de recette :
            // create:crushing -> create:crushing_wheel. Aucune connaissance
            // specifique a un mod, et le resultat est memorise. Place AVANT la
            // recherche floue ci-dessous, qui compare des sous-chaines de noms
            // de classe et peut renvoyer a peu pres n'importe quel item.
            ItemStack station = com.ceketrum.cei.gui.module.cei.recipe.view.CeiRecipeStation
                    .iconFor(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            if (!station.isEmpty()) return station;

            // La table de mots-cles, DERNIER recours avant la recherche floue.
            // Elle raisonne par sous-chaine : "industrial_grinder" contient
            // "grinder", donc elle ramenait le broyeur industriel sur l'item du
            // broyeur simple. Elle ne doit intervenir que si l'identifiant n'a
            // rien donne.
            if (typePath != null) {
                ItemStack match = findMachineItem(typePath, namespace);
                if (match != null) return match;
            }
            if (serPath != null) {
                ItemStack match = findMachineItem(serPath, namespace);
                if (match != null) return match;
            }
            ItemStack classMatch = findMachineItem(className, namespace);
            if (classMatch != null) return classMatch;

            // 3. Fuzzy search in same namespace
            if (namespace != null) {
                for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                    if (id.getNamespace().equals(namespace)) {
                        String itemPath = id.getPath().toLowerCase();
                        if (className.toLowerCase().contains(itemPath) || itemPath.contains(className.toLowerCase())) {
                            return new ItemStack(getItem(id));
                        }
                    }
                }
            }
        } catch (Exception e) {}

        return new ItemStack(Items.DISPENSER);
    }

    private Recipe<?> getRecipeFromObj(Object obj) {
        if (obj == null) return null;
        if (obj instanceof RecipeHolder<?> entry) {
            return entry.value();
        }
        if (obj instanceof Recipe<?> r) {
            return r;
        }
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && Recipe.class.isAssignableFrom(m.getReturnType())) {
                    return (Recipe<?>) m.invoke(obj);
                }
            }
            for (Field f : obj.getClass().getDeclaredFields()) {
                if (Recipe.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (Recipe<?>) f.get(obj);
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private Item getItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id).map(net.minecraft.core.Holder::value).orElse(Items.AIR);
    }

    private Item getItem(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path)).map(net.minecraft.core.Holder::value).orElse(Items.AIR);
    }

    private ItemStack findMachineItem(String searchStr, String namespace) {
        if (searchStr == null) return null;
        String s = searchStr.toLowerCase();

        // Custom Oritech Mappings
        if (namespace != null && namespace.equals("oritech")) {
            String path = null;
            if (s.contains("assembly") || s.contains("assembler")) {
                path = "assembler_block";
            } else if (s.contains("grinder")) {
                path = "fragment_forge_block";
            } else if (s.contains("pulverizer")) {
                path = "pulverizer_block";
            } else if (s.contains("centrifuge")) {
                path = "centrifuge_block";
            } else if (s.contains("foundry")) {
                path = "foundry_block";
            } else if (s.contains("furnace")) {
                path = "powered_furnace_block";
            } else if (s.contains("forge") || s.contains("atomic")) {
                path = "atomic_forge_block";
            } else if (s.contains("refinery")) {
                path = "refinery_block";
            }

            if (path != null) {
                var item = getItem("oritech", path);
                if (item != null && item != Items.AIR) return new ItemStack(item);
            }
        }

        String path = null;
        if (s.contains("assembly") || s.contains("assembler")) {
            path = "assembly_machine";
        } else if (s.contains("grinder") || s.contains("macerator")) {
            path = "grinder";
        } else if (s.contains("compressor") || s.contains("press")) {
            path = "compressor";
        } else if (s.contains("centrifuge")) {
            path = "industrial_centrifuge";
        } else if (s.contains("extractor")) {
            path = "extractor";
        } else if (s.contains("blast_furnace") || s.contains("blastfurnace")) {
            path = "industrial_blast_furnace";
        } else if (s.contains("chemical") || s.contains("reactor")) {
            path = "chemical_reactor";
        } else if (s.contains("electrolyzer")) {
            path = "industrial_electrolyzer";
        } else if (s.contains("distillation") || s.contains("distill")) {
            path = "distillation_tower";
        } else if (s.contains("sawmill")) {
            path = "industrial_sawmill";
        } else if (s.contains("implosion")) {
            path = "implosion_compressor";
        } else if (s.contains("rolling")) {
            path = "rolling_machine";
        } else if (s.contains("canner") || s.contains("canning")) {
            path = "canning_machine";
        } else if (s.contains("alloy")) {
            path = "alloy_smelter";
        } else if (s.contains("foundry")) {
            path = "foundry_block";
        }

        if (path != null) {
            if (namespace != null && !namespace.isEmpty() && !namespace.equals("minecraft")) {
                var item = getItem(namespace, path);
                if (item != null && item != Items.AIR) return new ItemStack(item);

                if (namespace.equals("modern_industrialization")) {
                    String miPath = path;
                    if (path.equals("assembly_machine")) miPath = "assembler";
                    else if (path.equals("grinder")) miPath = "macerator";
                    var miItem = getItem(namespace, miPath);
                    if (miItem != null && miItem != Items.AIR) return new ItemStack(miItem);
                }
            }

            for (String ns : List.of("techreborn", "modern_industrialization", "minecraft")) {
                var item = getItem(ns, path);
                if (item != null && item != Items.AIR) return new ItemStack(item);

                if (ns.equals("modern_industrialization")) {
                    String miPath = path;
                    if (path.equals("assembly_machine")) miPath = "assembler";
                    else if (path.equals("grinder")) miPath = "macerator";
                    var miItem = getItem(ns, miPath);
                    if (miItem != null && miItem != Items.AIR) return new ItemStack(miItem);
                }
            }
        }

        return null;
    }

    public static List<net.minecraft.world.item.crafting.display.SlotDisplay> getRecipeIngredients(net.minecraft.world.item.crafting.display.RecipeDisplay display) {
        if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
            return shaped.ingredients();
        } else if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients();
        } else if (display instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace) {
            return List.of(furnace.ingredient());
        } else if (display instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay stonecutter) {
            return List.of(stonecutter.input());
        } else if (display instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay smithing) {
            return List.of(smithing.template(), smithing.base(), smithing.addition());
        }
        return List.of();
    }
}
