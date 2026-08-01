package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class CeiMouseInjectionMixin {
    @Shadow
    private Minecraft minecraft;
    
    @Shadow
    private double xpos;
    
    @Shadow
    private double ypos;

    @Inject(
        method = "onScroll",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (com.ceketrum.cei.gui.util.CeiScreens.current() instanceof AbstractContainerScreen<?> screen && !(screen instanceof CreativeModeInventoryScreen)) {
            double mouseX = this.xpos * (double)this.minecraft.getWindow().getGuiScaledWidth() / (double)this.minecraft.getWindow().getScreenWidth();
            double mouseY = this.ypos * (double)this.minecraft.getWindow().getGuiScaledHeight() / (double)this.minecraft.getWindow().getScreenHeight();
            
            // 1. Check pinned overlays scroll first (from topmost/last to backmost/first)
            var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
            var cards = pinnedManager.getPinnedCards();
            for (int i = cards.size() - 1; i >= 0; i--) {
                var card = cards.get(i);
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null && pinnedScreen.isMouseOverCard(mouseX, mouseY)) {
                    if (pinnedScreen.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
                        ci.cancel();
                        return;
                    }
                }
            }

            // 2. Check CEI side panel scroll
            CeiModule module = CeiScreenHelper.getOrCreateModule(screen);
            float animationSlideOffset = module.getPanelRenderer().getAnimationSlideOffset();
            if (module.handleMouseScroll(
                mouseX, 
                mouseY, 
                vertical, 
                screen.width, 
                screen.height, 
                this.minecraft.font,
                animationSlideOffset
            )) {
                ci.cancel();
            }
        }
    }
}
