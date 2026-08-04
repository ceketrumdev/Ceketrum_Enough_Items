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
            int key = keyEvent.key();
            int scancode = keyEvent.scancode();
            int mods = keyEvent.modifiers();

            if (this.minecraft.screen instanceof AbstractContainerScreen<?> screen) {
                if (key == 82 || key == 85) { // 'R' or 'U'
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
                        boolean usage = (key == 85);
                        this.minecraft.setScreen(new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(screen, stackToOpen, usage));
                        ci.cancel();
                        return;
                    }
                }
                
                // Mode developpeur : C copie l'item survole, Maj+C change
                // de format puis copie. Teste APRES R et U pour ne rien leur
                // prendre, et seulement si l'option est cochee -- sinon la
                // touche reste disponible pour le reste du jeu.
                //
                // "mods" est ici le masque GLFW reel, dans les sept lignees :
                // le test de Maj y est donc le meme partout.
                if (key == 67 && com.ceketrum.cei.gui.module.cei.util.CeiDevTools.enabled()) {
                    ItemStack devStack = null;

                    net.minecraft.world.inventory.Slot devSlot =
                            ((com.ceketrum.cei.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
                    if (devSlot != null && devSlot.hasItem()) {
                        devStack = devSlot.getItem();
                    }
                    if (devStack == null && !(screen instanceof CreativeModeInventoryScreen)) {
                        devStack = CeiScreenHelper.getOrCreateModule(screen).getHoveredStack();
                    }

                    if (devStack != null && !devStack.isEmpty()) {
                        var fmt = (mods & 1) != 0   // GLFW_MOD_SHIFT
                                ? com.ceketrum.cei.gui.module.cei.util.CeiDevTools.nextFormat()
                                : com.ceketrum.cei.gui.module.cei.util.CeiDevTools.currentFormat();
                        com.ceketrum.cei.gui.module.cei.util.CeiDevTools.copy(devStack, fmt);
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

    @Inject(
        method = "charTyped",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$onChar(long window, net.minecraft.client.input.CharacterEvent characterEvent, CallbackInfo ci) {
        if (characterEvent != null && this.minecraft.screen instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            if (module.handleCharTyped((char)characterEvent.codepoint(), characterEvent.modifiers())) {
                ci.cancel();
            }
        }
    }
}
