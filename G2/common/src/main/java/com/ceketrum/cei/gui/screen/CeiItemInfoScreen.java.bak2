package com.ceketrum.cei.gui.screen;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import net.minecraft.block.Block;

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
    private int containerWidth = 240;
    private int containerHeight = 180;
    private int containerX;
    private int containerY;
    
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
        public final Identifier customRecipeTypeId; // null if vanilla category
        
        public RecipeCategory(CategoryType type) {
            this.type = type;
            this.customRecipeTypeId = null;
        }
        
        public RecipeCategory(CategoryType type, Identifier customRecipeTypeId) {
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
    private int currentPage = 0;
    
    // Recipes database for target item (Output)
    private final List<RecipeEntry<?>> craftingRecipes = new ArrayList<>();
    private final List<RecipeEntry<?>> smeltingRecipes = new ArrayList<>();
    private final List<RecipeEntry<?>> smithingRecipes = new ArrayList<>();
    private final List<RecipeEntry<?>> stonecuttingRecipes = new ArrayList<>();
    private final List<BrewingRecipeManager.BrewingRecipe> brewingRecipes = new ArrayList<>();
    private final List<RecipeEntry<?>> customRecipes = new ArrayList<>();
    
    // Usages database for target item (Input)
    private final List<RecipeEntry<?>> craftingUsages = new ArrayList<>();
    private final List<RecipeEntry<?>> smeltingUsages = new ArrayList<>();
    private final List<RecipeEntry<?>> smithingUsages = new ArrayList<>();
    private final List<RecipeEntry<?>> stonecuttingUsages = new ArrayList<>();
    private final List<BrewingRecipeManager.BrewingRecipe> brewingUsages = new ArrayList<>();
    private final List<RecipeEntry<?>> customUsages = new ArrayList<>();
    
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
        super(Text.literal("CEI Item Info"));
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
    }
    
    private void scanRecipes() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        var recipeManager = client.world.getRecipeManager();
        var registryManager = client.world.getRegistryManager();
        
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
        
        // Scan Minecraft recipes for both outputs (crafting) and inputs (usages)
        for (var entry : recipeManager.values()) {
            Recipe<?> recipe = entry.value();
            
            // 1. Recipes: Produces this item
            try {
                boolean producesItem = false;
                ItemStack standardResult = recipe.getResult(registryManager);
                if (standardResult != null && standardResult.isOf(targetStack.getItem())) {
                    producesItem = true;
                } else {
                    // Try our omniscient custom outputs extractor!
                    List<ItemStack> customOutputs = extractCustomOutputs(recipe, registryManager);
                    for (ItemStack out : customOutputs) {
                        if (out.isOf(targetStack.getItem())) {
                            producesItem = true;
                            break;
                        }
                    }
                }
                
                if (producesItem) {
                    categorizeRecipe(entry, false);
                }
            } catch (Exception e) {}
            
            // 2. Usages: Uses this item as input
            boolean isInput = false;
            for (var ingredient : recipe.getIngredients()) {
                for (ItemStack match : ingredient.getMatchingStacks()) {
                    if (match.isOf(targetStack.getItem())) {
                        isInput = true;
                        break;
                    }
                }
                if (isInput) break;
            }
            
            if (!isInput) {
                // Try our omniscient custom inputs extractor!
                List<ItemStack> customInputs = extractCustomInputs(recipe);
                for (ItemStack in : customInputs) {
                    if (in.isOf(targetStack.getItem())) {
                        isInput = true;
                        break;
                    }
                }
            }
            
            if (isInput) {
                categorizeRecipe(entry, true);
            }
        }
        
        // 3. Scan Brewing recipes
        var brewingOutputs = BrewingRecipeManager.getInstance().getRecipesForOutput(targetStack);
        brewingRecipes.addAll(brewingOutputs);
        
        var brewingInputs = BrewingRecipeManager.getInstance().getRecipesForInput(targetStack);
        brewingUsages.addAll(brewingInputs);
    }
    
    private void categorizeRecipe(RecipeEntry<?> entry, boolean isUsage) {
        Recipe<?> recipe = entry.value();
        var type = recipe.getType();
        
        if (type == net.minecraft.recipe.RecipeType.CRAFTING) {
            if (isUsage) craftingUsages.add(entry); else craftingRecipes.add(entry);
        } else if (type == net.minecraft.recipe.RecipeType.SMELTING || 
                   type == net.minecraft.recipe.RecipeType.BLASTING || 
                   type == net.minecraft.recipe.RecipeType.SMOKING || 
                   type == net.minecraft.recipe.RecipeType.CAMPFIRE_COOKING) {
            if (isUsage) smeltingUsages.add(entry); else smeltingRecipes.add(entry);
        } else if (type == net.minecraft.recipe.RecipeType.SMITHING) {
            if (isUsage) smithingUsages.add(entry); else smithingRecipes.add(entry);
        } else if (type == net.minecraft.recipe.RecipeType.STONECUTTING) {
            if (isUsage) stonecuttingUsages.add(entry); else stonecuttingRecipes.add(entry);
        } else {
            if (isUsage) customUsages.add(entry); else customRecipes.add(entry);
        }
    }
    
    private void updateMainTabs() {
        visibleMainTabs.clear();
        visibleMainTabs.add(TabType.DESCRIPTION);
        visibleMainTabs.add(TabType.CRAFTING);
        visibleMainTabs.add(TabType.USAGES);
        
        // Loot eligibility
        List<String> lootSources = LootTableSourceManager.getInstance().getSourcesForItem(targetStack.getItem());
        if (!lootSources.isEmpty()) {
            visibleMainTabs.add(TabType.LOOT);
        }
        
        // World location eligibility
        List<String> locations = LootTableSourceManager.getInstance().getWorldLocationsForItem(targetStack.getItem());
        List<String> blockGen = BlockGenerationManager.getInstance().getBlockGenerationSources(targetStack.getItem());
        boolean hasWorldLocs = !locations.isEmpty() && !locations.contains("Everywhere / Not specified") && !locations.contains("Partout dans le monde / Non spécifié");
        boolean hasBlockGen = !blockGen.isEmpty();
        
        if (hasWorldLocs || hasBlockGen) {
            visibleMainTabs.add(TabType.WORLD);
        }
    }
    
    private void updateCategories() {
        categories.clear();
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
                Set<Identifier> customTypes = new LinkedHashSet<>();
                for (Object recipeObj : customList) {
                    Recipe<?> r = getRecipeFromObj(recipeObj);
                    if (r != null) {
                        Identifier typeId = Registries.RECIPE_TYPE.getId(r.getType());
                        if (typeId != null) {
                            customTypes.add(typeId);
                        }
                    }
                }
                for (Identifier typeId : customTypes) {
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
                        Identifier typeId = Registries.RECIPE_TYPE.getId(r.getType());
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Overridden to do nothing to prevent super.render() from drawing the background/blur shader on top of our GUI!
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        int currentXOffset = card != null ? (int) card.getxOffset() : 0;
        int currentYOffset = card != null ? (int) card.getyOffset() : 0;
        
        this.containerX = (this.width - this.containerWidth) / 2 + currentXOffset;
        this.containerY = (this.height - this.containerHeight) / 2 + currentYOffset;
        
        // 1. Draw the native background blur shader and dimming first (flouts the world cleanly in the background)
        // ONLY if this is the active current screen (not rendered as an overlay on top of inventory!)
        if (this.client.currentScreen == this) {
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
        context.drawBorder(containerX, containerY, containerWidth, containerHeight, borderColor);
        
        // Draw Title (Target Item Name)
        String titleText = targetStack.getName().getString();
        String truncatedTitle = TextRenderHelper.truncateText(titleText, containerWidth - 60, this.textRenderer); // left space for header buttons
        int titleWidth = this.textRenderer.getWidth(truncatedTitle);
        context.drawText(this.textRenderer, Text.literal(truncatedTitle).formatted(Formatting.GOLD),
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
        if (this.ceiModule != null && this.client.currentScreen == this) {
            this.ceiModule.render(
                context, 
                mouseX, 
                mouseY, 
                this.width, 
                this.height, 
                this.textRenderer, 
                this.client.world.getRecipeManager(), 
                this.client.world.getRegistryManager()
            );
        }
        
        // Draw Tooltips (Hovered slot or Tab icons)
        drawTooltips(context, mouseX, mouseY);
        
        super.render(context, mouseX, mouseY, delta);
        
        // Reset global shader color opacity to normal!
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private void drawHeaderButtons(DrawContext context, int mouseX, int mouseY) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;
        
        // 1. PIN button
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        boolean hoverPin = mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12;
        int pinBg = hoverPin ? 0x66FFFFFF : (isPinned ? 0xAAFFD700 : 0x22FFFFFF); // Golden color if pinned!
        context.fill(pinX, pinY, pinX + 12, pinY + 12, pinBg);
        context.drawBorder(pinX, pinY, 12, 12, isPinned ? 0xFFFFD700 : 0x44FFFFFF);
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
            context.drawBorder(hudX, hudY, 12, 12, showInHud ? 0xFF00FF00 : 0x44FFFFFF);
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
            context.drawBorder(opX, opY, 12, 12, 0x44FFFFFF);
            // Draw small columns or dots representing transparency level
            int dotCount = opacity == 1.0f ? 4 : (opacity == 0.75f ? 3 : (opacity == 0.5f ? 2 : 1));
            for (int d = 0; d < dotCount; d++) {
                context.fill(opX + 2 + d * 2, opY + 3, opX + 3 + d * 2, opY + 9, 0xFFFFFFFF);
            }
        }
    }
    
    private void drawMainTabsBackground(DrawContext context, int mouseX, int mouseY) {
        for (int i = 0; i < visibleMainTabs.size(); i++) {
            TabType tab = visibleMainTabs.get(i);
            int tabX = containerX + containerWidth - 3;
            int tabY = containerY + 10 + i * 26;
            
            boolean active = (tab == activeMainTab);
            boolean hovered = mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22;
            
            int tabBg = active ? 0xD9222222 : (hovered ? 0xAA2D2D2D : 0xD9141414);
            GuiRenderHelper.drawRoundedBackground(context, tabX, tabY, 27, 22, 6, tabBg);
            context.drawBorder(tabX, tabY, 27, 22, active ? 0x66FFFFFF : 0x22FFFFFF);
        }
    }
    
    private void drawMainTabsIcons(DrawContext context) {
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
            
            context.drawItem(iconStack, tabX + 6, tabY + 3);
        }
    }
    
    private void drawCategoryTabsBackground(DrawContext context, int mouseX, int mouseY) {
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return;
        
        for (int i = 0; i < categories.size(); i++) {
            RecipeCategory cat = categories.get(i);
            int tabX = containerX - 24;
            int tabY = containerY + 10 + i * 26;
            
            boolean active = (cat.equals(activeCategory));
            boolean hovered = mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22;
            
            int tabBg = active ? 0xD9222222 : (hovered ? 0xAA2D2D2D : 0xD9141414);
            GuiRenderHelper.drawRoundedBackground(context, tabX, tabY, 27, 22, 6, tabBg);
            context.drawBorder(tabX, tabY, 27, 22, active ? 0x66FFFFFF : 0x22FFFFFF);
        }
    }
    
    private void drawCategoryTabsIcons(DrawContext context) {
        if (activeMainTab != TabType.CRAFTING && activeMainTab != TabType.USAGES) return;
        
        for (int i = 0; i < categories.size(); i++) {
            RecipeCategory cat = categories.get(i);
            int tabX = containerX - 24;
            int tabY = containerY + 10 + i * 26;
            
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
                            Identifier typeId = Registries.RECIPE_TYPE.getId(r.getType());
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
            
            context.drawItem(iconStack, tabX + 4, tabY + 3);
        }
    }
    
    private void drawTabContent(DrawContext context, int mouseX, int mouseY) {
        int contentX = containerX + 15;
        int contentY = containerY + 28;
        int contentWidth = containerWidth - 30;
        int contentHeight = containerHeight - 45;
        
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
        
        switch (activeMainTab) {
            case DESCRIPTION: {
                String desc = ItemDescriptionManager.getInstance().getDescription(targetStack.getItem());
                if (desc.isEmpty()) {
                    desc = isFr ? "Aucune description disponible pour cet item." : "No description available for this item.";
                }
                
                // Real stats integration in description tab
                RecipePopupRenderer.ItemStats stats = new RecipePopupRenderer().getItemStats(targetStack);
                boolean hasAnyStats = stats.hasDurability || stats.hasFood || stats.hasAttackDamage || stats.hasAttackSpeed || stats.hasArmor || stats.hasToughness;
                
                int currY = TextRenderHelper.drawWrappedText(context, desc, contentX, contentY, contentWidth, 0xDDFFFFFF, 0.75f, 3, this.textRenderer);
                
                if (hasAnyStats) {
                    currY += 6;
                    context.fill(contentX, currY, contentX + contentWidth, currY + 1, 0x22FFFFFF);
                    currY += 6;
                    
                    String statsTitle = isFr ? "Statistiques :" : "Statistics:";
                    context.drawText(this.textRenderer, statsTitle, contentX, currY, 0xFFD700, false);
                    currY += 11;
                    
                    int statColor = 0xAAAAAAAA;
                    float scale = 0.75f;
                    
                    if (stats.hasAttackDamage) {
                        String val = String.format("%s: +%.1f", isFr ? "Dégâts" : "Damage", stats.attackDamage);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                    if (stats.hasAttackSpeed) {
                        double speed = 4.0 + stats.attackSpeed;
                        String val = String.format("%s: %.1f", isFr ? "Vitesse d'attaque" : "Attack Speed", speed);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                    if (stats.hasArmor) {
                        String val = String.format("%s: +%.0f", isFr ? "Armure" : "Armor", stats.armor);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                    if (stats.hasToughness) {
                        String val = String.format("%s: +%.0f", isFr ? "Robustesse" : "Toughness", stats.toughness);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                    if (stats.hasFood) {
                        String val = String.format("%s: +%d (Saturation: +%.1f)", isFr ? "Nourriture" : "Food", stats.foodPoints, stats.saturation);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                    if (stats.hasDurability) {
                        String val = String.format("%s: %d / %d", isFr ? "Durabilité" : "Durability", stats.durability, stats.maxDurability);
                        currY = TextRenderHelper.drawWrappedText(context, val, contentX, currY, contentWidth, statColor, scale, 3, this.textRenderer);
                    }
                }
                break;
            }
            case CRAFTING:
            case USAGES: {
                if (activeCategory == null || getActiveRecipesList().isEmpty()) {
                    String emptyMsg = isFr ? (activeMainTab == TabType.CRAFTING ? "Aucune recette de craft." : "Aucun usage de craft.")
                                           : (activeMainTab == TabType.CRAFTING ? "No recipes found." : "No usages found.");
                    int msgW = this.textRenderer.getWidth(emptyMsg);
                    context.drawText(this.textRenderer, emptyMsg, contentX + (contentWidth - msgW) / 2, contentY + 40, 0xFFFF0000, false);
                } else {
                    drawRecipeContent(context, mouseX, mouseY, contentX, contentY, contentWidth, contentHeight, isFr);
                }
                break;
            }
            case LOOT: {
                List<String> lootSources = LootTableSourceManager.getInstance().getSourcesForItem(targetStack.getItem());
                String header = isFr ? "Sources d'Obtention :" : "Obtaining Sources:";
                context.drawText(this.textRenderer, header, contentX, contentY, 0xFFD700, false);
                int currY = contentY + 14;
                
                for (String source : lootSources) {
                    currY = TextRenderHelper.drawWrappedText(context, "• " + source, contentX, currY, contentWidth, 0xDDFFFFFF, 0.75f, 6, this.textRenderer);
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
                String placeholderFr = "Partout dans le monde / Non spécifié";
                String placeholderEn = "Everywhere / Not specified";
                
                for (String loc : locations) {
                    if (!loc.equals(placeholderFr) && !loc.equals(placeholderEn)) {
                        uniqueLocs.add(loc);
                    }
                }
                
                if (uniqueLocs.isEmpty()) {
                    uniqueLocs.add(isFr ? placeholderFr : placeholderEn);
                }
                
                String header = isFr ? "Biomes et Structures :" : "Biomes and Structures:";
                context.drawText(this.textRenderer, header, contentX, contentY, 0xFFD700, false);
                int currY = contentY + 14;
                
                for (String location : uniqueLocs) {
                    currY = TextRenderHelper.drawWrappedText(context, location.startsWith(" ") || location.endsWith(":") ? location : "• " + location, contentX, currY, contentWidth, 0xDDFFFFFF, 0.75f, 6, this.textRenderer);
                }
                break;
            }
        }
    }
    
    private void drawRecipeContent(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth, int contentHeight, boolean isFr) {
        List<?> list = getActiveRecipesList();
        if (currentPage >= list.size()) currentPage = 0;
        Object recipeObj = list.get(currentPage);
        
        var client = MinecraftClient.getInstance();
        var rm = client.world.getRegistryManager();
        
        // Draw Header of recipe
        Recipe<?> activeRecipe = null;
        if (recipeObj instanceof RecipeEntry<?> entry) {
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
            case CRAFTING -> isFr ? "Table de Craft" : "Crafting Table";
            case SMELTING -> isFr ? "Fourneau" : "Furnace";
            case BREWING -> isFr ? "Alambic" : "Brewing Stand";
            case STONECUTTING -> isFr ? "Tailleur de Pierre" : "Stonecutter";
            case SMITHING -> isFr ? "Table de Forgeron" : "Smithing Table";
            case CUSTOM -> {
                if (titleIcon.getItem() == Items.DISPENSER) {
                    yield isFr ? "Machine Spéciale" : "Custom Machine";
                }
                yield titleIcon.getName().getString();
            }
        };
        
        context.drawItem(titleIcon, contentX, contentY - 4);
        activeSlots.add(new RenderedSlot(titleIcon, contentX, contentY - 4, 16));
        context.drawText(this.textRenderer, catName, contentX + 20, contentY, 0xFFD700, false);
        
        // Render slots and arrow based on category
        switch (activeCategory.type) {
            case CRAFTING: {
                RecipeEntry<?> entry = (RecipeEntry<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                var layout = com.ceketrum.cei.gui.module.cei.recipe.RecipeDisplayHelper.getRecipeLayout(recipe, entry, rm);
                
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
                            context.drawItem(inputStack, slotX + 1, slotY + 1);
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
                    int plusX = arrowX + 3;
                    int plusY = arrowY + 12;
                    
                    boolean hoverPlus = mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12;
                    
                    // Draw sleek button background
                    int btnBg = hoverPlus ? 0xFF3D3D3D : 0xFF1C1C1C;
                    int btnBorder = hoverPlus ? 0x88FFFFFF : 0x33FFFFFF;
                    
                    context.fill(plusX, plusY, plusX + 12, plusY + 12, btnBg);
                    context.drawBorder(plusX, plusY, 12, 12, btnBorder);
                    
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
                    ItemStack outStack = recipe.getResult(rm);
                    context.drawItem(outStack, outputX + 1, outputY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outputX, outputY, 18));
                } catch (Exception e) {}
                break;
            }
            case SMELTING: {
                RecipeEntry<?> entry = (RecipeEntry<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                
                int slotX = contentX + 25;
                int slotY = contentY + 28;
                
                // Input slot
                drawSlotBg(context, slotX, slotY);
                List<Ingredient> ingredients = recipe.getIngredients();
                if (!ingredients.isEmpty() && ingredients.get(0).getMatchingStacks().length > 0) {
                    ItemStack inStack = ingredients.get(0).getMatchingStacks()[0];
                    context.drawItem(inStack, slotX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(inStack, slotX, slotY, 18));
                }
                
                // Flame/Arrow
                drawArrow(context, slotX + 30, slotY + 2);
                
                // Output slot
                int outX = slotX + 65;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = recipe.getResult(rm);
                    context.drawItem(outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case BREWING: {
                BrewingRecipeManager.BrewingRecipe recipe = (BrewingRecipeManager.BrewingRecipe) recipeObj;
                
                int gridStartX = contentX + 25;
                int gridStartY = contentY + 14;
                
                // Ingredient (top slot)
                int ingX = gridStartX + 25;
                int ingY = gridStartY;
                drawSlotBg(context, ingX, ingY);
                context.drawItem(recipe.ingredient, ingX + 1, ingY + 1);
                activeSlots.add(new RenderedSlot(recipe.ingredient, ingX, ingY, 18));
                
                // Arrow pointing down
                context.fill(ingX + 8, ingY + 21, ingX + 10, ingY + 36, 0x66FFFFFF);
                context.fill(ingX + 6, ingY + 33, ingX + 12, ingY + 35, 0x66FFFFFF);
                
                // Potions (bottom slots)
                int potY = gridStartY + 40;
                
                // Input potion (left)
                int leftX = gridStartX;
                drawSlotBg(context, leftX, potY);
                context.drawItem(recipe.inputPotion, leftX + 1, potY + 1);
                activeSlots.add(new RenderedSlot(recipe.inputPotion, leftX, potY, 18));
                
                // Output potion (right)
                int rightX = gridStartX + 50;
                drawSlotBg(context, rightX, potY);
                context.drawItem(recipe.outputPotion, rightX + 1, potY + 1);
                activeSlots.add(new RenderedSlot(recipe.outputPotion, rightX, potY, 18));
                break;
            }
            case STONECUTTING: {
                RecipeEntry<?> entry = (RecipeEntry<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                
                int slotX = contentX + 25;
                int slotY = contentY + 28;
                
                // Input
                drawSlotBg(context, slotX, slotY);
                List<Ingredient> ingredients = recipe.getIngredients();
                if (!ingredients.isEmpty() && ingredients.get(0).getMatchingStacks().length > 0) {
                    ItemStack inStack = ingredients.get(0).getMatchingStacks()[0];
                    context.drawItem(inStack, slotX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(inStack, slotX, slotY, 18));
                }
                
                // Arrow
                drawArrow(context, slotX + 30, slotY + 2);
                
                // Output
                int outX = slotX + 65;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = recipe.getResult(rm);
                    context.drawItem(outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case SMITHING: {
                RecipeEntry<?> entry = (RecipeEntry<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                
                int slotX = contentX + 10;
                int slotY = contentY + 28;
                
                // 3 Inputs (Template, Base, Addition)
                List<Ingredient> ingredients = recipe.getIngredients();
                for (int i = 0; i < 3; i++) {
                    int sX = slotX + i * 20;
                    drawSlotBg(context, sX, slotY);
                    if (i < ingredients.size()) {
                        ItemStack[] matching = ingredients.get(i).getMatchingStacks();
                        if (matching.length > 0) {
                            context.drawItem(matching[0], sX + 1, slotY + 1);
                            activeSlots.add(new RenderedSlot(matching[0], sX, slotY, 18));
                        }
                    }
                }
                
                // Arrow
                drawArrow(context, slotX + 65, slotY + 2);
                
                // Output
                int outX = slotX + 100;
                drawSlotBg(context, outX, slotY);
                try {
                    ItemStack outStack = recipe.getResult(rm);
                    context.drawItem(outStack, outX + 1, slotY + 1);
                    activeSlots.add(new RenderedSlot(outStack, outX, slotY, 18));
                } catch (Exception e) {}
                break;
            }
            case CUSTOM: {
                RecipeEntry<?> entry = (RecipeEntry<?>) recipeObj;
                Recipe<?> recipe = entry.value();
                
                List<ItemStack> inputs = extractCustomInputs(recipe);
                List<ItemStack> outputs = extractCustomOutputs(recipe, rm);
                
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
                        context.drawItem(stack, slotX + 1, startY + 1);
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
                        context.drawItem(stack, slotX + 1, startY + 1);
                        activeSlots.add(new RenderedSlot(stack, slotX, startY, 18));
                    }
                }
                break;
            }
        }
        
        // Draw Pagination details
        int pageY = containerY + containerHeight - 16;
        String pageStr = String.format("%d / %d", currentPage + 1, getMaxPages());
        int pageW = this.textRenderer.getWidth(pageStr);
        context.drawText(this.textRenderer, pageStr, contentX + (contentWidth - pageW) / 2, pageY, 0xFF888888, false);
        
        // Left & Right Arrow buttons
        int arrowLeftX = contentX + (contentWidth - pageW) / 2 - 15;
        int arrowRightX = contentX + (contentWidth + pageW) / 2 + 5;
        
        boolean hoverLeft = mouseX >= arrowLeftX && mouseX < arrowLeftX + 8 && mouseY >= pageY && mouseY < pageY + 9;
        boolean hoverRight = mouseX >= arrowRightX && mouseX < arrowRightX + 8 && mouseY >= pageY && mouseY < pageY + 9;
        
        context.drawText(this.textRenderer, "<", arrowLeftX, pageY, hoverLeft ? 0xFFFFFFFF : 0xFF888888, false);
        context.drawText(this.textRenderer, ">", arrowRightX, pageY, hoverRight ? 0xFFFFFFFF : 0xFF888888, false);
    }
    
    private void drawSlotBg(DrawContext context, int x, int y) {
        int color = 0xFF181818;
        context.fill(x, y, x + 18, y + 18, color);
        context.drawBorder(x, y, 18, 18, 0x33FFFFFF);
    }
    
    private void drawArrow(DrawContext context, int x, int y) {
        // Render simple elegant horizontal arrow
        context.fill(x, y + 6, x + 18, y + 8, 0x66FFFFFF);
        context.fill(x + 14, y + 4, x + 15, y + 10, 0x66FFFFFF);
        context.fill(x + 16, y + 5, x + 17, y + 9, 0x66FFFFFF);
        context.fill(x + 17, y + 6, x + 18, y + 8, 0x66FFFFFF);
    }
    
    private void drawTooltips(DrawContext context, int mouseX, int mouseY) {
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
        
        // 0. Header buttons tooltips
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        if (mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12) {
            String tooltipText = isPinned 
                ? (isFr ? "Désancrer la recette" : "Unpin recipe")
                : (isFr ? "Ancrer la recette" : "Pin recipe");
            context.drawTooltip(this.textRenderer, List.of(Text.literal(tooltipText)), mouseX, mouseY);
            return;
        }
        
        if (isPinned) {
            int hudX = containerX + 20;
            int hudY = containerY + 5;
            if (mouseX >= hudX && mouseX < hudX + 12 && mouseY >= hudY && mouseY < hudY + 12) {
                String tooltipText = card.isShowInHud()
                    ? (isFr ? "Masquer sur l'écran de jeu" : "Hide on HUD")
                    : (isFr ? "Afficher sur l'écran de jeu" : "Show on HUD");
                context.drawTooltip(this.textRenderer, List.of(Text.literal(tooltipText)), mouseX, mouseY);
                return;
            }
            
            int opX = containerX + 34;
            int opY = containerY + 5;
            if (mouseX >= opX && mouseX < opX + 12 && mouseY >= opY && mouseY < opY + 12) {
                String tooltipText = String.format("%s : %d%%", 
                    isFr ? "Opacité" : "Opacity", 
                    (int) (card.getOpacity() * 100)
                );
                context.drawTooltip(this.textRenderer, List.of(Text.literal(tooltipText)), mouseX, mouseY);
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
                    case DESCRIPTION -> isFr ? "Description & Stats" : "Description & Stats";
                    case CRAFTING -> isFr ? "Comment Crafter" : "How to Craft";
                    case USAGES -> isFr ? "Usages (Ingrédient)" : "Usages (Ingredient)";
                    case LOOT -> isFr ? "Comment l'obtenir (Loot)" : "Obtaining (Loot)";
                    case WORLD -> isFr ? "Biomes et Structures" : "Biomes and Structures";
                };
                context.drawTooltip(this.textRenderer, List.of(Text.literal(tooltipText)), mouseX, mouseY);
                return;
            }
        }
        
        // 2. Tooltips for category tabs
        if (activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES) {
            for (int i = 0; i < categories.size(); i++) {
                RecipeCategory cat = categories.get(i);
                int tabX = containerX - 24;
                int tabY = containerY + 10 + i * 26;
                
                if (mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22) {
                    String tooltipText = switch (cat.type) {
                        case CRAFTING -> isFr ? "Table de Craft" : "Crafting Table";
                        case SMELTING -> isFr ? "Cuisson & Fourneau" : "Smelting & Furnace";
                        case BREWING -> isFr ? "Alambic (Potions)" : "Brewing Stand";
                        case STONECUTTING -> isFr ? "Tailleur de Pierre" : "Stonecutter";
                        case SMITHING -> isFr ? "Table de Forgeron (Smithing)" : "Smithing Table";
                        case CUSTOM -> {
                            boolean useUsages = (activeMainTab == TabType.USAGES);
                            List<?> list = useUsages ? customUsages : customRecipes;
                            Recipe<?> matchedRecipe = null;
                            for (Object recipeObj : list) {
                                Recipe<?> r = getRecipeFromObj(recipeObj);
                                if (r != null) {
                                    Identifier typeId = Registries.RECIPE_TYPE.getId(r.getType());
                                    if (java.util.Objects.equals(typeId, cat.customRecipeTypeId)) {
                                        matchedRecipe = r;
                                        break;
                                    }
                                }
                            }
                            if (matchedRecipe != null) {
                                ItemStack machineStack = getMachineIcon(matchedRecipe);
                                if (machineStack.getItem() != Items.DISPENSER) {
                                    yield machineStack.getName().getString();
                                }
                            }
                            yield isFr ? "Machine Spéciale (Mod)" : "Special Machine (Mod)";
                        }
                    };
                    context.drawTooltip(this.textRenderer, List.of(Text.literal(tooltipText)), mouseX, mouseY);
                    return;
                }
            }
        }
        
        // 3. Tooltips for hovered slots
        for (RenderedSlot slot : activeSlots) {
            if (mouseX >= slot.x && mouseX < slot.x + slot.size && mouseY >= slot.y && mouseY < slot.y + slot.size) {
                hoveredSlot = slot;
                context.drawTooltip(this.textRenderer, this.getTooltipFromItem(this.client, slot.stack), mouseX, mouseY);
                return;
            }
        }
        
        // 4. Tooltip for "+" auto-transfer button
        if (activeCategory != null && activeCategory.type == CategoryType.CRAFTING && isParentCraftingTable() && !getActiveRecipesList().isEmpty()) {
            int gridStartX = containerX + 15 + 10;
            int gridStartY = containerY + 28 + 18;
            int arrowX = gridStartX + 65;
            int arrowY = gridStartY + 22;
            int plusX = arrowX + 3;
            int plusY = arrowY + 12;
            
            if (mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12) {
                String title = isFr ? "Remplir la Table de Craft (+)" : "Fill Crafting Table (+)";
                String hint = isFr ? "Shift + Clic : Remplir au maximum" : "Shift + Click: Fill maximum";
                context.drawTooltip(this.textRenderer, List.of(
                    Text.literal(title).formatted(Formatting.GREEN),
                    Text.literal(hint).formatted(Formatting.GRAY)
                ), mouseX, mouseY);
                return;
            }
        }
    }
    
    private boolean isParentCraftingTable() {
        if (this.parentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handledScreen) {
            return handledScreen.getScreenHandler() instanceof net.minecraft.screen.CraftingScreenHandler;
        }
        return false;
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
        if (this.ceiModule != null && this.ceiModule.handleMouseClick(mouseX, mouseY, button, this.width, this.height, this.textRenderer)) {
            return true;
        }
        
        String lang = ItemDescriptionManager.getInstance().getCurrentLanguage();
        boolean isFr = lang != null && lang.toLowerCase().startsWith("fr");
        
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        boolean isPinned = card != null;
        
        // 0. Header buttons click handling
        int pinX = containerX + 6;
        int pinY = containerY + 5;
        if (mouseX >= pinX && mouseX < pinX + 12 && mouseY >= pinY && mouseY < pinY + 12) {
            if (isPinned) {
                manager.unpinRecipe(this.targetStack);
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } else {
                manager.pinRecipe(this.targetStack, this.activeMainTab, this.activeCategory, this.currentPage, null);
                
                // Play click sound
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                
                // Return to base interface (parent screen) since recipe card is now pinned and rendered as overlay!
                this.client.setScreen(this.parentScreen);
            }
            return true;
        }
        
        if (isPinned) {
            int hudX = containerX + 20;
            int hudY = containerY + 5;
            if (mouseX >= hudX && mouseX < hudX + 12 && mouseY >= hudY && mouseY < hudY + 12) {
                card.setShowInHud(!card.isShowInHud());
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            
            int opX = containerX + 34;
            int opY = containerY + 5;
            if (mouseX >= opX && mouseX < opX + 12 && mouseY >= opY && mouseY < opY + 12) {
                card.cycleOpacity();
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
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
            RecipeEntry<?> entry = (RecipeEntry<?>) getActiveRecipesList().get(currentPage);
            Recipe<?> recipe = entry.value();
            
            int gridStartX = containerX + 15 + 10;
            int gridStartY = containerY + 28 + 18;
            int arrowX = gridStartX + 65;
            int arrowY = gridStartY + 22;
            int plusX = arrowX + 3;
            int plusY = arrowY + 12;
            
            if (mouseX >= plusX && mouseX < plusX + 12 && mouseY >= plusY && mouseY < plusY + 12) {
                net.minecraft.client.gui.screen.ingame.HandledScreen<?> handledScreen = (net.minecraft.client.gui.screen.ingame.HandledScreen<?>) this.parentScreen;
                net.minecraft.screen.CraftingScreenHandler craftingHandler = (net.minecraft.screen.CraftingScreenHandler) handledScreen.getScreenHandler();
                
                int quantity = net.minecraft.client.gui.screen.Screen.hasShiftDown() ? -1 : 1;
                if (button == 1) quantity = -1; // Right-click fills maximum
                
                com.ceketrum.cei.gui.module.cei.util.CraftingHelper.placeRecipeIngredients(
                    craftingHandler,
                    recipe,
                    this.client.world.getRegistryManager(),
                    this.client.player,
                    quantity
                );
                
                // Play click sound
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                
                // Instantly go back to parent screen (crafting table) so they can take the crafted item!
                this.client.setScreen(this.parentScreen);
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
                updatePinnedState();
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        
        // 2. Check category tabs clicks
        if (activeMainTab == TabType.CRAFTING || activeMainTab == TabType.USAGES) {
            for (int i = 0; i < categories.size(); i++) {
                RecipeCategory cat = categories.get(i);
                int tabX = containerX - 24;
                int tabY = containerY + 10 + i * 26;
                
                if (mouseX >= tabX && mouseX < tabX + 24 && mouseY >= tabY && mouseY < tabY + 22) {
                    activeCategory = cat;
                    currentPage = 0;
                    updatePinnedState();
                    MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        
        // 3. Check pagination arrow clicks
        if (activeCategory != null && !getActiveRecipesList().isEmpty()) {
            int contentWidth = containerWidth - 30;
            String pageStr = String.format("%d / %d", currentPage + 1, getMaxPages());
            int pageW = this.textRenderer.getWidth(pageStr);
            int pageY = containerY + containerHeight - 16;
            
            int arrowLeftX = containerX + 15 + (contentWidth - pageW) / 2 - 15;
            int arrowRightX = containerX + 15 + (contentWidth + pageW) / 2 + 5;
            
            if (mouseX >= arrowLeftX && mouseX < arrowLeftX + 8 && mouseY >= pageY && mouseY < pageY + 9) {
                currentPage--;
                if (currentPage < 0) currentPage = getMaxPages() - 1;
                updatePinnedState();
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= arrowRightX && mouseX < arrowRightX + 8 && mouseY >= pageY && mouseY < pageY + 9) {
                currentPage++;
                if (currentPage >= getMaxPages()) currentPage = 0;
                updatePinnedState();
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        
        // 4. Clicked on a recipe slot -> Navigate to that item!
        if (hoveredSlot != null && (button == 0 || button == 1)) {
            boolean showUsage = (button == 1);
            MinecraftClient.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoveredSlot.stack, showUsage));
            MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
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
        var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var card = manager.getPinnedCard(this.targetStack);
        if (card != null && card.isDragging()) {
            card.setDragging(false);
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
        
        if (keyCode == 256 || this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.client.setScreen(parentScreen);
            return true;
        }
        
        ItemStack hoverStack = null;
        if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
            hoverStack = hoveredSlot.stack;
        } else if (this.ceiModule != null && this.ceiModule.getHoveredStack() != null) {
            hoverStack = this.ceiModule.getHoveredStack();
        }
        
        if (hoverStack != null && !hoverStack.isEmpty()) {
            if (keyCode == 82) { // 'R'
                MinecraftClient.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoverStack, false));
                return true;
            }
            if (keyCode == 85) { // 'U'
                MinecraftClient.getInstance().setScreen(new CeiItemInfoScreen(this.parentScreen, hoverStack, true));
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
        if (this.ceiModule != null) {
            float animationSlideOffset = this.ceiModule.getPanelRenderer().getAnimationSlideOffset();
            if (this.ceiModule.handleMouseScroll(mouseX, mouseY, verticalAmount, this.width, this.height, this.textRenderer, animationSlideOffset)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }

    private List<ItemStack> extractCustomOutputs(Recipe<?> recipe, net.minecraft.registry.DynamicRegistryManager rm) {
        List<ItemStack> list = new ArrayList<>();
        
        // 1. Try standard getResult()
        try {
            ItemStack standardResult = recipe.getResult(rm);
            if (standardResult != null && !standardResult.isEmpty()) {
                list.add(standardResult.copy());
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
                    method = clazz.getMethod(methodName, net.minecraft.registry.DynamicRegistryManager.class);
                    resultObj = method.invoke(recipe, rm);
                } catch (NoSuchMethodException e) {
                    try {
                        method = clazz.getMethod(methodName, net.minecraft.registry.RegistryWrapper.WrapperLookup.class);
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
                String key = Registries.ITEM.getId(stack.getItem()).toString() + ":" + stack.getCount();
                if (keys.add(key)) {
                    cleanList.add(stack);
                }
            }
        }
        
        return cleanList;
    }
    
    private void unpackOutputObject(Object obj, List<ItemStack> list) {
        unpackOutputObject(obj, list, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
    }

    private void unpackOutputObject(Object obj, List<ItemStack> list, Set<Object> visited, int depth) {
        if (obj == null || depth > 8 || !visited.add(obj)) return;
        
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

        if (obj instanceof net.minecraft.recipe.Ingredient ing) {
            for (ItemStack match : ing.getMatchingStacks()) {
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

    private List<ItemStack> extractCustomInputs(Recipe<?> recipe) {
        List<ItemStack> list = new ArrayList<>();
        
        // 1. Try standard getIngredients()
        try {
            List<net.minecraft.recipe.Ingredient> ingredients = recipe.getIngredients();
            if (ingredients != null && !ingredients.isEmpty()) {
                for (net.minecraft.recipe.Ingredient ing : ingredients) {
                    ItemStack[] matching = ing.getMatchingStacks();
                    if (matching.length > 0) {
                        list.add(matching[0].copy());
                    } else {
                        list.add(ItemStack.EMPTY);
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
                String key = Registries.ITEM.getId(stack.getItem()).toString() + ":" + stack.getCount();
                if (keys.add(key)) {
                    cleanList.add(stack);
                }
            }
        }
        
        return cleanList;
    }
    
    private void unpackInputObject(Object obj, List<ItemStack> list) {
        unpackInputObject(obj, list, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private void unpackInputObject(Object obj, List<ItemStack> list, Set<Object> visited) {
        if (obj == null || !visited.add(obj)) return;
        
        if (obj instanceof net.minecraft.recipe.Ingredient ing) {
            for (ItemStack match : ing.getMatchingStacks()) {
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
                unpackInputObject(el, list, visited);
            }
            return;
        }
        
        if (obj.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                unpackInputObject(java.lang.reflect.Array.get(obj, i), list, visited);
            }
            return;
        }

        if (obj instanceof java.util.Optional<?> opt) {
            if (opt.isPresent()) {
                unpackInputObject(opt.get(), list, visited);
            }
            return;
        }

        // Avoid reflecting on typical standard library / minecraft / game classes that aren't custom recipe objects
        String className = obj.getClass().getName();
        if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.") || className.startsWith("com.sun.") ||
            className.startsWith("net.minecraft.registry.") || className.startsWith("net.minecraft.class_") ||
            className.equals("net.minecraft.recipe.RecipeEntry") || className.startsWith("com.google.gson.") ||
            className.startsWith("com.mojang.datafixers.") || className.startsWith("com.mojang.serialization.")) {
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
                        unpackInputObject(val, list, visited);
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
                            unpackInputObject(val, list, visited);
                        }
                    } catch (Exception e) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private ItemStack getMachineIcon(Recipe<?> recipe) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return new ItemStack(Items.DISPENSER);
        
        try {
            Identifier typeId = Registries.RECIPE_TYPE.getId(recipe.getType());
            Identifier serializerId = Registries.RECIPE_SERIALIZER.getId(recipe.getSerializer());
            
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
            
            // 1. Try our smart class name and ID mapping helper first!
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
            
            // 2. Direct lookup fallbacks using solved namespace and path
            if (typePath != null && namespace != null) {
                Item item = Registries.ITEM.get(Identifier.of(namespace, typePath));
                if (item != null && item != Items.AIR) return new ItemStack(item);
                
                Item blockItem = Registries.ITEM.get(Identifier.of(namespace, typePath + "_block"));
                if (blockItem != null && blockItem != Items.AIR) return new ItemStack(blockItem);
                
                Item machineItem = Registries.ITEM.get(Identifier.of(namespace, typePath + "_machine"));
                if (machineItem != null && machineItem != Items.AIR) return new ItemStack(machineItem);
                
                String path = typePath;
                if (path.endsWith("_recipe")) {
                    path = path.substring(0, path.length() - 7);
                    Item fallback = Registries.ITEM.get(Identifier.of(namespace, path));
                    if (fallback != null && fallback != Items.AIR) return new ItemStack(fallback);
                    
                    Item fallbackBlock = Registries.ITEM.get(Identifier.of(namespace, path + "_block"));
                    if (fallbackBlock != null && fallbackBlock != Items.AIR) return new ItemStack(fallbackBlock);
                }
            }
            
            if (serPath != null && namespace != null) {
                Item item = Registries.ITEM.get(Identifier.of(namespace, serPath));
                if (item != null && item != Items.AIR) return new ItemStack(item);
                
                Item blockItem = Registries.ITEM.get(Identifier.of(namespace, serPath + "_block"));
                if (blockItem != null && blockItem != Items.AIR) return new ItemStack(blockItem);
                
                Item machineItem = Registries.ITEM.get(Identifier.of(namespace, serPath + "_machine"));
                if (machineItem != null && machineItem != Items.AIR) return new ItemStack(machineItem);
                
                String path = serPath;
                if (path.endsWith("_serializer")) {
                    path = path.substring(0, path.length() - 11);
                } else if (path.endsWith("_recipe")) {
                    path = path.substring(0, path.length() - 7);
                }
                Item fallback = Registries.ITEM.get(Identifier.of(namespace, path));
                if (fallback != null && fallback != Items.AIR) return new ItemStack(fallback);
                
                Item fallbackBlock = Registries.ITEM.get(Identifier.of(namespace, path + "_block"));
                if (fallbackBlock != null && fallbackBlock != Items.AIR) return new ItemStack(fallbackBlock);
            }
            
            // 3. Fuzzy search in same namespace
            if (namespace != null) {
                for (Identifier id : Registries.ITEM.getIds()) {
                    if (id.getNamespace().equals(namespace)) {
                        String itemPath = id.getPath().toLowerCase();
                        if (className.toLowerCase().contains(itemPath) || itemPath.contains(className.toLowerCase())) {
                            return new ItemStack(Registries.ITEM.get(id));
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return new ItemStack(Items.DISPENSER);
    }

    private Recipe<?> getRecipeFromObj(Object obj) {
        if (obj == null) return null;
        if (obj instanceof RecipeEntry<?> entry) {
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
                var item = Registries.ITEM.get(Identifier.of("oritech", path));
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
                var item = Registries.ITEM.get(Identifier.of(namespace, path));
                if (item != null && item != Items.AIR) return new ItemStack(item);
                
                if (namespace.equals("modern_industrialization")) {
                    String miPath = path;
                    if (path.equals("assembly_machine")) miPath = "assembler";
                    else if (path.equals("grinder")) miPath = "macerator";
                    var miItem = Registries.ITEM.get(Identifier.of(namespace, miPath));
                    if (miItem != null && miItem != Items.AIR) return new ItemStack(miItem);
                }
            }
            
            for (String ns : List.of("techreborn", "modern_industrialization", "minecraft")) {
                var item = Registries.ITEM.get(Identifier.of(ns, path));
                if (item != null && item != Items.AIR) return new ItemStack(item);
                
                if (ns.equals("modern_industrialization")) {
                    String miPath = path;
                    if (path.equals("assembly_machine")) miPath = "assembler";
                    else if (path.equals("grinder")) miPath = "macerator";
                    var miItem = Registries.ITEM.get(Identifier.of(ns, miPath));
                    if (miItem != null && miItem != Items.AIR) return new ItemStack(miItem);
                }
            }
        }
        
        return null;
    }
}


