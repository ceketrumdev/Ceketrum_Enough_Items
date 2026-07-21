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
public abstract class CeiKeyboardInjectionMixin261 {
    @Shadow
    private Minecraft minecraft;

    @Inject(
        method = "keyPress",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$onKey261(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action != 0) { // GLFW_PRESS (1) or GLFW_REPEAT (2)
            net.minecraft.client.gui.screens.Screen currentScreen = CeiScreenHelper.getCurrentScreen(this.minecraft);
            if (currentScreen instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
                if (key == 82 || key == 85) { // 'R' or 'U'
                    ItemStack stackToOpen = null;
                    
                    var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
                    var cards = pinnedManager.getPinnedCards();
                    for (int i = cards.size() - 1; i >= 0; i--) {
                        var card = cards.get(i);
                        var pinnedScreen = card.getScreenInstance();
                        if (pinnedScreen != null) {
                            if (pinnedScreen.handleKeyPress261(key, scancode, mods)) {
                                ci.cancel();
                                return;
                            }
                        }
                    }
                    
                    net.minecraft.world.inventory.Slot focusedSlot = ((com.ceketrum.cei.mixin.client.HandledScreenAccessor) screen).getFocusedSlot();
                    if (focusedSlot != null && focusedSlot.hasItem()) {
                        stackToOpen = focusedSlot.getItem();
                    }
                    
                    if (stackToOpen == null) {
                        CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                        if (module.getHoveredStack() != null) {
                            stackToOpen = module.getHoveredStack();
                        }
                    }
                    
                    if (stackToOpen != null && !stackToOpen.isEmpty()) {
                        boolean usage = (key == 85);
                        CeiScreenHelper.setScreen(this.minecraft, new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(screen, stackToOpen, usage));
                        ci.cancel();
                        return;
                    }
                }
                
                CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
                if (module.handleKeyPress(key, scancode, mods)) {
                    ci.cancel();
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
    private void cei$onChar261(long window, int codePoint, int modifiers, CallbackInfo ci) {
        net.minecraft.client.gui.screens.Screen currentScreen = CeiScreenHelper.getCurrentScreen(this.minecraft);
        if (currentScreen instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            if (module.handleCharTyped((char)codePoint, modifiers)) {
                ci.cancel();
            }
        }
    }
}
