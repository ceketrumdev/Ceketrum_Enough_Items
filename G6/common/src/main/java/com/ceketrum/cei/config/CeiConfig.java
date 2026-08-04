package com.ceketrum.cei.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ceketrum.cei.util.PlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gère la configuration du module CEI.
 * Sauvegarde et charge les paramètres depuis un fichier JSON.
 */
public class CeiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("cei-config");
    private static final String CONFIG_FILE = "cei_config.json";
    
    private static CeiConfig instance;
    private final Path configFile;
    
    // Position du panneau (true = gauche, false = droite)
    private boolean panelOnLeft = true;
    
    // Taille
    private int panelWidth = 120;
    private int panelHeight = 400;
    private int panelWidthMin = 80;
    private int panelWidthMax = 200;
    private static final int PANEL_MARGIN = 15; // Marge depuis le bord
    private static final int PANEL_Y = 15; // Position Y fixe
    
    // Couleurs (format ARGB)
    private int backgroundColor = 0x90000000;
    private int borderColor = 0xFFFFFFFF;
    private int textColor = 0xFFFFFFFF;
    private int hoverColor = 0x44FFFFFF;
    
    // Animations
    private boolean enableAnimations = true;
    private float animationSpeed = 1.0f;
    
    // Favoris
    private boolean showFavoritesByDefault = false;
    
    // Popup d'aide
    private boolean showHelpPopup = true;

    /**
     * Nouveau pipeline de recettes (CeiRecipeView) : la grille est dimensionnee
     * sur la recette au lieu d'etre supposee 3x3, et la station de travail est
     * derivee du type de recette. L'ancien chemin reste en place derriere ce
     * drapeau, le temps de comparer les deux rendus.
     */
    private boolean useNewRecipeRenderer = true;

    /**
     * Mode developpeur : export presse-papier a la touche C et inspecteur
     * d'item. Decoche, rien de tout cela ne s'execute.
     */
    private boolean devMode = false;
    
    private CeiConfig() {
        Path configDir = PlatformHelper.getConfigDirectory().resolve("cei");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Impossible de créer le dossier de configuration", e);
        }
        this.configFile = configDir.resolve(CONFIG_FILE);
        load();
    }
    
    public static CeiConfig getInstance() {
        if (instance == null) {
            instance = new CeiConfig();
        }
        return instance;
    }
    
    /**
     * Charge la configuration depuis le fichier JSON.
     */
    public void load() {
        if (!Files.exists(configFile)) {
            LOGGER.info("Fichier de configuration non trouvé, utilisation des valeurs par défaut");
            save(); // Créer le fichier avec les valeurs par défaut
            return;
        }
        
        try {
            String content = Files.readString(configFile);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            // Position
            if (json.has("panelOnLeft")) panelOnLeft = json.get("panelOnLeft").getAsBoolean();
            
            // Taille
            if (json.has("panelWidth")) panelWidth = json.get("panelWidth").getAsInt();
            if (json.has("panelHeight")) panelHeight = json.get("panelHeight").getAsInt();
            if (json.has("panelWidthMin")) panelWidthMin = json.get("panelWidthMin").getAsInt();
            if (json.has("panelWidthMax")) panelWidthMax = json.get("panelWidthMax").getAsInt();
            
            // Couleurs
            if (json.has("backgroundColor")) backgroundColor = parseColor(json.get("backgroundColor").getAsString());
            if (json.has("borderColor")) borderColor = parseColor(json.get("borderColor").getAsString());
            if (json.has("textColor")) textColor = parseColor(json.get("textColor").getAsString());
            if (json.has("hoverColor")) hoverColor = parseColor(json.get("hoverColor").getAsString());
            
            // Animations
            if (json.has("enableAnimations")) enableAnimations = json.get("enableAnimations").getAsBoolean();
            if (json.has("animationSpeed")) animationSpeed = json.get("animationSpeed").getAsFloat();
            
            // Favoris
            if (json.has("showFavoritesByDefault")) showFavoritesByDefault = json.get("showFavoritesByDefault").getAsBoolean();
            
            // Popup d'aide
            if (json.has("showHelpPopup")) showHelpPopup = json.get("showHelpPopup").getAsBoolean();

            // Nouveau pipeline de recettes (cf. CeiRecipeAdapter)
            if (json.has("useNewRecipeRenderer")) useNewRecipeRenderer = json.get("useNewRecipeRenderer").getAsBoolean();
            if (json.has("devMode")) devMode = json.get("devMode").getAsBoolean();
            
            LOGGER.info("Configuration chargée depuis {}", configFile);
        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement de la configuration depuis {}", configFile, e);
        }
    }
    
    /**
     * Sauvegarde la configuration dans le fichier JSON.
     */
    public void save() {
        try {
            JsonObject json = new JsonObject();
            
            // Position
            json.addProperty("panelOnLeft", panelOnLeft);
            
            // Taille
            json.addProperty("panelWidth", panelWidth);
            json.addProperty("panelHeight", panelHeight);
            json.addProperty("panelWidthMin", panelWidthMin);
            json.addProperty("panelWidthMax", panelWidthMax);
            
            // Couleurs
            json.addProperty("backgroundColor", formatColor(backgroundColor));
            json.addProperty("borderColor", formatColor(borderColor));
            json.addProperty("textColor", formatColor(textColor));
            json.addProperty("hoverColor", formatColor(hoverColor));
            
            // Animations
            json.addProperty("enableAnimations", enableAnimations);
            json.addProperty("animationSpeed", animationSpeed);
            
            // Favoris
            json.addProperty("showFavoritesByDefault", showFavoritesByDefault);
            
            // Popup d'aide
            json.addProperty("showHelpPopup", showHelpPopup);
            json.addProperty("useNewRecipeRenderer", useNewRecipeRenderer);
            json.addProperty("devMode", devMode);
            
            Files.writeString(configFile, json.toString());
            LOGGER.debug("Configuration sauvegardée dans {}", configFile);
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la sauvegarde de la configuration dans {}", configFile, e);
        }
    }
    
    /**
     * Parse une couleur depuis une chaîne hexadécimale (format: "#RRGGBB" ou "#AARRGGBB").
     */
    private int parseColor(String colorString) {
        try {
            if (colorString.startsWith("#")) {
                colorString = colorString.substring(1);
            }
            if (colorString.length() == 6) {
                // Ajouter alpha par défaut (FF)
                colorString = "FF" + colorString;
            }
            return (int) Long.parseLong(colorString, 16);
        } catch (Exception e) {
            LOGGER.warn("Couleur invalide: {}, utilisation de la valeur par défaut", colorString);
            return 0xFFFFFFFF;
        }
    }
    
    /**
     * Formate une couleur en chaîne hexadécimale (format: "#AARRGGBB").
     */
    private String formatColor(int color) {
        return String.format("#%08X", color);
    }
    
    // Getters et setters pour la position
    public boolean isPanelOnLeft() { return panelOnLeft; }
    public void setPanelOnLeft(boolean panelOnLeft) { this.panelOnLeft = panelOnLeft; }
    public int getPanelMargin() { return PANEL_MARGIN; }
    public int getPanelY() { return PANEL_Y; }
    
    // Getters et setters pour la taille
    public int getPanelWidth() { return panelWidth; }
    public void setPanelWidth(int panelWidth) { 
        this.panelWidth = Math.max(panelWidthMin, Math.min(panelWidthMax, panelWidth)); 
    }
    public int getPanelHeight() { return panelHeight; }
    public void setPanelHeight(int panelHeight) { this.panelHeight = panelHeight; }
    public int getPanelWidthMin() { return panelWidthMin; }
    public int getPanelWidthMax() { return panelWidthMax; }
    
    // Getters et setters pour les couleurs
    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { this.backgroundColor = backgroundColor; }
    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int borderColor) { this.borderColor = borderColor; }
    public int getTextColor() { return textColor; }
    public void setTextColor(int textColor) { this.textColor = textColor; }
    public int getHoverColor() { return hoverColor; }
    public void setHoverColor(int hoverColor) { this.hoverColor = hoverColor; }
    
    // Getters et setters pour les animations
    public boolean isEnableAnimations() { return enableAnimations; }
    public void setEnableAnimations(boolean enableAnimations) { this.enableAnimations = enableAnimations; }
    public float getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(float animationSpeed) { this.animationSpeed = Math.max(0.1f, Math.min(3.0f, animationSpeed)); }
    
    // Getters et setters pour les favoris
    public boolean isShowFavoritesByDefault() { return showFavoritesByDefault; }
    public void setShowFavoritesByDefault(boolean showFavoritesByDefault) { this.showFavoritesByDefault = showFavoritesByDefault; }
    
    // Getters et setters pour la popup d'aide
    public boolean isUseNewRecipeRenderer() { return useNewRecipeRenderer; }
    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean v) { this.devMode = v; }
    public void setUseNewRecipeRenderer(boolean v) { this.useNewRecipeRenderer = v; }

    public boolean isShowHelpPopup() { return showHelpPopup; }
    public void setShowHelpPopup(boolean showHelpPopup) { this.showHelpPopup = showHelpPopup; }
    
    /**
     * Réinitialise la configuration aux valeurs par défaut.
     */
    public void reset() {
        panelOnLeft = true;
        panelWidth = 120;
        panelHeight = 400;
        panelWidthMin = 80;
        panelWidthMax = 200;
        backgroundColor = 0x90000000;
        borderColor = 0xFFFFFFFF;
        textColor = 0xFFFFFFFF;
        hoverColor = 0x44FFFFFF;
        enableAnimations = true;
        animationSpeed = 1.0f;
        showFavoritesByDefault = false;
        showHelpPopup = true;
        useNewRecipeRenderer = true;
        save();
    }
}


