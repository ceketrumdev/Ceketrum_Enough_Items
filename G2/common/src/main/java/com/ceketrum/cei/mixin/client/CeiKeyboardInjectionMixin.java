package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class CeiKeyboardInjectionMixin {
    @Shadow
    private MinecraftClient client;

    @Inject(
        method = "onKey",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$onKey(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action != 0) { // GLFW_PRESS (1) or GLFW_REPEAT (2)
            if (this.client.currentScreen instanceof HandledScreen<?> screen) {
                if (key == 82 || key == 85) { // 'R' or 'U'
                    ItemStack stackToOpen = null;
                    
                    // Check pinned overlay hovered slot first!
                    var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
                    var cards = pinnedManager.getPinnedCards();
                    for (int i = cards.size() - 1; i >= 0; i--) {
                        var card = cards.get(i);
                        var pinnedScreen = card.getScreenInstance();
                        if (pinnedScreen != null) {
                            if (pinnedScreen.keyPressed(key, scancode, mods)) {
                                ci.cancel();
                                return;
                            }
                        }
                    }
                    
                    net.minecraft.screen.slot.Slot focusedSlot = ((com.ceketrum.cei.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
                    if (focusedSlot != null && focusedSlot.hasStack()) {
                        stackToOpen = focusedSlot.getStack();
                    }
                    
                    if (stackToOpen == null && !(screen instanceof CreativeInventoryScreen)) {
                        CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                        if (module.getHoveredStack() != null) {
                            stackToOpen = module.getHoveredStack();
                        }
                    }
                    
                    if (stackToOpen != null && !stackToOpen.isEmpty()) {
                        boolean usage = (key == 85);
                        this.client.setScreen(new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(screen, stackToOpen, usage));
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

                    net.minecraft.screen.slot.Slot devSlot =
                            ((com.ceketrum.cei.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
                    if (devSlot != null && devSlot.hasStack()) {
                        devStack = devSlot.getStack();
                    }
                    if (devStack == null && !(screen instanceof CreativeInventoryScreen)) {
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

                if (!(screen instanceof CreativeInventoryScreen)) {
                    CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                    if (module.handleKeyPress(key, scancode, mods)) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Inject(
        method = "onChar",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (this.client.currentScreen instanceof HandledScreen<?> screen && !(screen instanceof CreativeInventoryScreen)) {
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            if (module.handleCharTyped((char)codePoint, modifiers)) {
                ci.cancel();
            }
        }
    }
}


