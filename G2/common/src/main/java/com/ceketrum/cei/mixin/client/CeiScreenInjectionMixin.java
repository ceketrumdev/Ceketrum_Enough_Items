package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

@Mixin(HandledScreen.class)
public abstract class CeiScreenInjectionMixin extends Screen {
    protected CeiScreenInjectionMixin(Text title) {
        super(title);
    }

    @Unique
    private boolean cei$isValidScreen() {
        if ((Object)this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
            return false;
        }
        return true;
    }
    
    @Unique
    private boolean cei$hasRecipeBook() {
        return (Object)this instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen || (Object)this instanceof net.minecraft.client.gui.screen.ingame.CraftingScreen || (Object)this instanceof net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
    }

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void cei$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (cei$hasRecipeBook()) {
            return;
        }

        if (!cei$isValidScreen()) {
            return;
        }
        
        Screen screen = (Screen)(Object)this;
        
        // Push and translate Z-matrix to draw everything on top of vanilla slots and item renders!
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 300.0f);
        
        CeiScreenHelper.getOrCreateModule(screen).render(
            context, 
            mouseX, 
            mouseY, 
            screen.width, 
            screen.height, 
            this.textRenderer, 
            this.client.world.getRecipeManager(), 
            this.client.world.getRegistryManager()
        );
        
        // Render Pinned Recipe Card Overlays
        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen == null) {
                var target = card.getTargetStack();
                if (target != null && !target.isEmpty()) {
                    pinnedScreen = new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(screen, target, false);
                    card.setScreenInstance(pinnedScreen);
                }
            }
            if (pinnedScreen != null) {
                pinnedScreen.setParentScreen(screen);
                if (pinnedScreen.width != screen.width || pinnedScreen.height != screen.height) {
                    pinnedScreen.init(this.client, screen.width, screen.height);
                }
                pinnedScreen.render(context, mouseX, mouseY, delta);
            }
        }
        
        // Flush buffer and restore matrices
        context.draw();
        context.getMatrices().pop();
    }

    @Inject(
        method = "getSlotAt",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$getSlotAt(double x, double y, CallbackInfoReturnable<Slot> cir) {
        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(x, y)) {
                cir.setReturnValue(null);
                return;
            }
        }
    }

    @Inject(
        method = "mouseClicked",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (cei$hasRecipeBook()) {
            return;
        }

        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var cards = pinnedManager.getPinnedCards();
        for (int i = cards.size() - 1; i >= 0; i--) {
            var card = cards.get(i);
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(mouseX, mouseY)) {
                pinnedScreen.mouseClicked(mouseX, mouseY, button);
                pinnedManager.bringToFront(card);
                cir.setReturnValue(true);
                return;
            }
        }

        Screen screen = (Screen)(Object)this;
        var module = CeiScreenHelper.getOrCreateModule(screen);
        if (module.handleMouseClick(mouseX, mouseY, button, screen.width, screen.height, this.textRenderer)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "mouseDragged",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    if (pinnedScreen.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }

    @Inject(
        method = "mouseReleased",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cei$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        
        // 1. Release dragging state globally on RELEASE action
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    pinnedScreen.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
        
        // 2. Intercept release over card bounds to prevent slot click triggers
        var cards = pinnedManager.getPinnedCards();
        for (int i = cards.size() - 1; i >= 0; i--) {
            var card = cards.get(i);
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(mouseX, mouseY)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }


}


