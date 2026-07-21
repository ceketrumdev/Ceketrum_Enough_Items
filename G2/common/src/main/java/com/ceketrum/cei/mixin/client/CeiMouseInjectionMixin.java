package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.module.cei.CeiModule;
import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class CeiMouseInjectionMixin {
    @Shadow
    private MinecraftClient client;
    
    @Shadow
    private double x;
    
    @Shadow
    private double y;

    @Inject(
        method = "onMouseScroll",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.client.currentScreen instanceof HandledScreen<?> screen && !(screen instanceof CreativeInventoryScreen)) {
            double mouseX = this.x * (double)this.client.getWindow().getScaledWidth() / (double)this.client.getWindow().getWidth();
            double mouseY = this.y * (double)this.client.getWindow().getScaledHeight() / (double)this.client.getWindow().getHeight();
            
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
                this.client.textRenderer,
                animationSlideOffset
            )) {
                ci.cancel();
            }
        }
    }
}


