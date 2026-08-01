package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class CeiKeyboardInjectionMixin {
    @Shadow
    private Minecraft minecraft;

    @Inject(
        method = "keyPress",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$onKey(long window, int action, net.minecraft.client.input.KeyEvent keyEvent, CallbackInfo ci) {
        if (action != 0 && keyEvent != null) { // GLFW_PRESS (1) or GLFW_REPEAT (2)
            com.ceketrum.cei.gui.util.CeiScreenHelper.setShiftDown(keyEvent.hasShiftDown());
            int key = com.ceketrum.cei.gui.util.CeiInput.key(keyEvent);
            int scancode = com.ceketrum.cei.gui.util.CeiInput.scancode(keyEvent);
            int mods = keyEvent.modifiers();

            if (com.ceketrum.cei.gui.util.CeiScreens.current() instanceof AbstractContainerScreen<?> screen) {
                if (key == com.ceketrum.cei.gui.util.CeiKeys.R || key == com.ceketrum.cei.gui.util.CeiKeys.U) { // 'R' or 'U'
                    ItemStack stackToOpen = null;
                    
                    // Check pinned overlay hovered slot first!
                    var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
                    var cards = pinnedManager.getPinnedCards();
                    for (int i = cards.size() - 1; i >= 0; i--) {
                        var card = cards.get(i);
                        var pinnedScreen = card.getScreenInstance();
                        if (pinnedScreen != null) {
                            if (pinnedScreen.keyPressed(keyEvent)) {
                                ci.cancel();
                                return;
                            }
                        }
                    }
                    
                    net.minecraft.world.inventory.Slot focusedSlot = ((com.ceketrum.cei.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
                    if (focusedSlot != null && focusedSlot.hasItem()) {
                        stackToOpen = focusedSlot.getItem();
                    }
                    
                    if (stackToOpen == null && !(screen instanceof CreativeModeInventoryScreen)) {
                        CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                        if (module.getHoveredStack() != null) {
                            stackToOpen = module.getHoveredStack();
                        }
                    }
                    
                    if (stackToOpen != null && !stackToOpen.isEmpty()) {
                        boolean usage = (key == com.ceketrum.cei.gui.util.CeiKeys.U);
                        com.ceketrum.cei.gui.util.CeiScreens.set(new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(screen, stackToOpen, usage));
                        ci.cancel();
                        return;
                    }
                }
                
                if (!(screen instanceof CreativeModeInventoryScreen)) {
                    CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                    if (module.handleKeyPress(key, scancode, mods)) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    /**
     * 26.3 : SDL3 remplace le callback caractere de GLFW.
     *
     * KeyboardHandler y gagne textInput(long, String) et n'appelle plus
     * charTyped(long, CharacterEvent) -- qui existe pourtant toujours, d'ou un
     * bug invisible en 26.1/26.2 et une barre de recherche muette en 26.3.
     *
     * Le module est compile contre 26.2, ou textInput n'existe pas : avec
     * require = 0 l'injection y est simplement ignoree. On annule l'evenement
     * quand il est consomme, ce qui evite au passage toute double saisie si une
     * version faisait passer textInput par charTyped.
     */
    @Inject(
        method = "textInput",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$onTextInput(long window, String text, CallbackInfo ci) {
        if (text == null || text.isEmpty()) return;
        if (com.ceketrum.cei.gui.util.CeiScreens.current() instanceof AbstractContainerScreen<?> screen
                && !(screen instanceof CreativeModeInventoryScreen)) {
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            boolean handled = false;
            for (int i = 0; i < text.length(); ) {
                int codepoint = text.codePointAt(i);
                i += Character.charCount(codepoint);
                // La barre de recherche travaille sur des char : on laisse
                // passer ce qui sort du plan multilingue de base.
                if (Character.isBmpCodePoint(codepoint) && module.handleCharTyped((char) codepoint, 0)) {
                    handled = true;
                }
            }
            if (handled) ci.cancel();
        }
    }

    @Inject(
        method = "charTyped",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$onChar(long window, net.minecraft.client.input.CharacterEvent characterEvent, CallbackInfo ci) {
        if (characterEvent != null && com.ceketrum.cei.gui.util.CeiScreens.current() instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            if (module.handleCharTyped((char)characterEvent.codepoint(), 0)) {
                ci.cancel();
            }
        }
    }
}
