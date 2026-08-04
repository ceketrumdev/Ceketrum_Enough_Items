package com.ceketrum.cei.gui.module.cei.components;

import com.ceketrum.cei.gui.constants.GuiConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Gère la barre de recherche pour filtrer les items.
 */
public class SearchBar {
    private String searchText = "";
    private boolean isFocused = false;
    private int cursorPosition = 0;
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    private static final int SEARCH_BAR_HEIGHT = 14;
    private static final int SEARCH_BAR_PADDING = 3;
    private static final int CURSOR_BLINK_INTERVAL = 500; // ms

    /**
     * Rend la barre de recherche.
     */
    public void render(DrawContext context, int x, int y, int width, net.minecraft.client.font.TextRenderer textRenderer) {
        // Fond de la barre de recherche
        int backgroundColor = isFocused ? 0xFF2C2C2C : 0xFF1E1E1E;
        context.fill(x, y, x + width, y + SEARCH_BAR_HEIGHT, backgroundColor);
        context.drawBorder(x, y, width, SEARCH_BAR_HEIGHT, isFocused ? 0xFFFFFFFF : 0xFF808080);

        // Texte de recherche
        int textX = x + SEARCH_BAR_PADDING;
        int textY = y + (SEARCH_BAR_HEIGHT - textRenderer.fontHeight) / 2;
        String displayText = searchText;

        // Tronquer le texte si nécessaire
        int maxTextWidth = width - SEARCH_BAR_PADDING * 2 - (isFocused ? 6 : 0); // Réserver de l'espace pour le curseur
        if (textRenderer.getWidth(displayText) > maxTextWidth) {
            // Tronquer depuis le début pour montrer la fin du texte
            while (textRenderer.getWidth(displayText) > maxTextWidth && displayText.length() > 0) {
                displayText = displayText.substring(1);
            }
        }

        context.drawText(textRenderer, Text.literal(displayText), textX, textY, 0xFFFFFF, false);

        // Curseur si la barre est focusée
        if (isFocused) {
            updateCursorBlink();
            if (cursorVisible) {
                int cursorX = textX + textRenderer.getWidth(displayText);
                context.fill(cursorX, textY, cursorX + 1, textY + textRenderer.fontHeight, 0xFFFFFFFF);
            }
        }
    }

    /**
     * Gère le clic sur la barre de recherche.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int searchBarX, int searchBarY, int searchBarWidth) {
        if (mouseX >= searchBarX && mouseX < searchBarX + searchBarWidth &&
            mouseY >= searchBarY && mouseY < searchBarY + SEARCH_BAR_HEIGHT) {
            setFocused(true);
            return true;
        }
        return false;
    }

    /**
     * Gère la saisie de caractères.
     */
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused) {
            return false;
        }

        if (isValidChar(chr)) {
            // Insérer le caractère à la position du curseur
            searchText = searchText.substring(0, cursorPosition) + chr + searchText.substring(cursorPosition);
            cursorPosition++;
            resetCursorBlink();
            return true;
        }

        return false;
    }

    /**
     * Gère l'appui sur une touche.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) {
            return false;
        }

        // Historique, comme dans un terminal : haut pour remonter, bas pour
        // redescendre jusqu'a retrouver ce qu'on tapait.
        if (keyCode == 265) { // GLFW_KEY_UP
            return walkHistory(1);
        }
        else if (keyCode == 264) { // GLFW_KEY_DOWN
            return walkHistory(-1);
        }
        // Entree valide la ligne : elle entre dans l'historique et la barre
        // rend la main, comme un prompt.
        else if (keyCode == 257 || keyCode == 335) { // ENTER / KP_ENTER
            commitHistory();
            setFocused(false);
            return true;
        }
        // Backspace
        if (keyCode == 259) { // GLFW_KEY_BACKSPACE
            if (cursorPosition > 0) {
                searchText = searchText.substring(0, cursorPosition - 1) + searchText.substring(cursorPosition);
                cursorPosition--;
                resetCursorBlink();
                return true;
            }
        }
        // Delete
        else if (keyCode == 261) { // GLFW_KEY_DELETE
            if (cursorPosition < searchText.length()) {
                searchText = searchText.substring(0, cursorPosition) + searchText.substring(cursorPosition + 1);
                resetCursorBlink();
                return true;
            }
        }
        // Flèche gauche
        else if (keyCode == 263) { // GLFW_KEY_LEFT
            if (cursorPosition > 0) {
                cursorPosition--;
                resetCursorBlink();
                return true;
            }
        }
        // Flèche droite
        else if (keyCode == 262) { // GLFW_KEY_RIGHT
            if (cursorPosition < searchText.length()) {
                cursorPosition++;
                resetCursorBlink();
                return true;
            }
        }
        // Début de ligne
        else if (keyCode == 268) { // GLFW_KEY_HOME
            cursorPosition = 0;
            resetCursorBlink();
            return true;
        }
        // Fin de ligne
        else if (keyCode == 269) { // GLFW_KEY_END
            cursorPosition = searchText.length();
            resetCursorBlink();
            return true;
        }

        return false;
    }

    /**
     * Vérifie si un caractère est valide pour la recherche.
     */
    private boolean isValidChar(char chr) {
        // Accepter tous les caractères imprimables sauf les caractères de contrôle
        return chr >= 32 && chr != 127;
    }

    /**
     * Met à jour l'état du clignotement du curseur.
     */
    private void updateCursorBlink() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorBlink >= CURSOR_BLINK_INTERVAL) {
            cursorVisible = !cursorVisible;
            lastCursorBlink = currentTime;
        }
    }

    /**
     * Réinitialise le clignotement du curseur (appelé après une action).
     */
    private void resetCursorBlink() {
        cursorVisible = true;
        lastCursorBlink = System.currentTimeMillis();
    }

    /**
     * Obtient le texte de recherche.
     */
    public String getSearchText() {
        return searchText;
    }

    /**
     * Définit le texte de recherche.
     */
    public void setSearchText(String text) {
        this.searchText = text;
        this.cursorPosition = Math.min(cursorPosition, searchText.length());
    }

    /**
     * Vérifie si la barre de recherche est focusée.
     */
    public boolean isFocused() {
        return isFocused;
    }

    /**
     * Définit si la barre de recherche est focusée.
     */
    public void setFocused(boolean focused) {
        // Quitter la ligne l'enregistre. C'est le seul moment ou l'on sait
        // que la recherche est terminee : il n'y a pas d'autre validation.
        if (this.isFocused && !focused) {
            commitHistory();
        }
        this.isFocused = focused;
        if (focused) {
            resetCursorBlink();
        }
    }

    // ------------------------------------------------------------ historique
    /**
     * Les recherches precedentes, la plus recente en tete.
     *
     * STATIQUE, et non par barre. Il y a un CeiModule -- donc une SearchBar --
     * par instance d'ecran : CeiScreenHelper garde une WeakHashMap indexee par
     * ecran. Un champ d'instance repartait donc de zero au simple passage d'un
     * coffre a un fourneau, sans meme avoir quitte la partie.
     */
    private static final java.util.List<String> HISTORY = new java.util.ArrayList<>();
    private static final int HISTORY_MAX = 30;
    private static final String HISTORY_FILE = "search_history.json";
    private static boolean historyLoaded = false;

    /**
     * Position dans l'historique : -1 quand on est sur la ligne en cours.
     *
     * Sans cette position, remonter puis redescendre ne saurait pas ou
     * s'arreter -- et le brouillon serait perdu des le premier appui.
     *
     * Reste par barre, contrairement a la liste : deux ecrans ouverts l'un
     * apres l'autre partagent les entrees, pas l'endroit ou l'on en est.
     */
    private int historyIndex = -1;
    /** La ligne en cours de frappe, mise de cote pendant qu'on remonte. */
    private String draft = "";

    /**
     * Enregistre la ligne courante.
     *
     * Appele quand on QUITTE la ligne, pas pendant la frappe : sinon
     * l'historique se remplirait de "d", "di", "dia", "dia m"...
     */
    public void commitHistory() {
        String s = searchText == null ? "" : searchText.trim();
        historyIndex = -1;
        draft = "";
        if (s.isEmpty()) return;
        ensureHistoryLoaded();
        // Un doublon remonte au lieu de s'empiler : chercher deux fois la meme
        // chose ne doit pas donner deux entrees a traverser.
        HISTORY.remove(s);
        HISTORY.add(0, s);
        while (HISTORY.size() > HISTORY_MAX) HISTORY.remove(HISTORY.size() - 1);
        // Ecriture ici et nulle part ailleurs : on quitte la ligne, c'est un
        // geste de l'utilisateur, pas quelque chose qui arrive par image.
        saveHistory();
    }

    /**
     * Remonte (delta = +1) ou redescend (delta = -1) dans l'historique.
     *
     * @return true si la ligne a change
     */
    private boolean walkHistory(int delta) {
        ensureHistoryLoaded();
        if (HISTORY.isEmpty()) return false;
        if (historyIndex == -1 && delta > 0) {
            draft = searchText;   // on met de cote ce qu'on etait en train de taper
        }
        int next = historyIndex + delta;
        if (next < -1) return false;
        if (next >= HISTORY.size()) return false;
        historyIndex = next;
        searchText = (historyIndex == -1) ? draft : HISTORY.get(historyIndex);
        cursorPosition = searchText.length();
        resetCursorBlink();
        return true;
    }

    /**
     * Relit l'historique au premier besoin.
     *
     * Volontairement pas dans un initialiseur statique : la classe peut etre
     * chargee avant que le dossier de configuration soit utilisable, et une
     * lecture qui echoue au chargement de classe est autrement plus penible a
     * diagnostiquer qu'un historique vide.
     */
    private static void ensureHistoryLoaded() {
        if (historyLoaded) return;
        // Marque en premier : si la lecture echoue, on ne la retente pas a
        // chaque touche.
        historyLoaded = true;

        java.nio.file.Path file = historyFile();
        if (file == null || !java.nio.file.Files.exists(file)) return;
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser
                    .parseString(java.nio.file.Files.readString(file)).getAsJsonArray();
            HISTORY.clear();
            for (com.google.gson.JsonElement element : array) {
                if (HISTORY.size() >= HISTORY_MAX) break;
                String s = element.getAsString();
                // Memes regles qu'a l'ecriture : ni vide, ni doublon. Un
                // fichier retouche a la main ne doit pas donner un historique
                // que le code n'aurait jamais produit.
                if (s != null && !s.trim().isEmpty() && !HISTORY.contains(s)) {
                    HISTORY.add(s);
                }
            }
        } catch (Exception e) {
            // Fichier illisible : on repart d'un historique vide plutot que
            // d'empecher la barre de recherche de fonctionner.
            HISTORY.clear();
        }
    }

    private static void saveHistory() {
        java.nio.file.Path file = historyFile();
        if (file == null) return;
        try {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (String s : HISTORY) {
                array.add(s);
            }
            java.nio.file.Files.writeString(file, array.toString());
        } catch (Exception e) {
            // Rien a faire d'utile ici : perdre l'historique ne doit pas
            // interrompre une recherche.
        }
    }

    /** A cote de favorites.json et pinned_recipes.json. */
    private static java.nio.file.Path historyFile() {
        try {
            java.nio.file.Path dir = com.ceketrum.cei.util.PlatformHelper
                    .getConfigDirectory().resolve("cei");
            java.nio.file.Files.createDirectories(dir);
            return dir.resolve(HISTORY_FILE);
        } catch (Exception e) {
            return null;
        }
    }
}



