package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class CeiScreenInjectionMixin extends Screen {
    protected CeiScreenInjectionMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean cei$isValidScreen() {
        if ((Object)this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            return false;
        }
        return true;
    }
    
    // require=0: les intermédiaires Fabric changent entre les sous-versions 1.21.x,
    // cette annotation permet au mod de charger même si la méthode n'est pas trouvée.
    @Unique
    private boolean cei$hasRecipeBook() {
        return (Object)this instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen || (Object)this instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen || (Object)this instanceof net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
    }

    @Inject(
        method = "render",
        at = @At("TAIL"),
        require = 0
    )
    private void cei$render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (cei$hasRecipeBook()) {
            return;
        }

        if (!cei$isValidScreen()) {
            return;
        }
        
        Screen screen = (Screen)(Object)this;
        
        // Push and translate Z-matrix to draw everything on top of vanilla slots and item renders!
        context.pose().pushPose();
        context.pose().translate(0.0f, 0.0f, 300.0f);
        
        CeiScreenHelper.getOrCreateModule(screen).render(
            context, 
            mouseX, 
            mouseY, 
            screen.width, 
            screen.height, 
            this.font, 
            this.minecraft.level.getRecipeManager(), 
            this.minecraft.level.registryAccess()
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
                    pinnedScreen.init(this.minecraft, screen.width, screen.height);
                }
                pinnedScreen.render(context, mouseX, mouseY, delta);
            }
        }
        
        // Flush buffer and restore matrices
        context.flush();
        context.pose().popPose();
    }

    @Inject(
        method = "findSlot",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
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
        cancellable = true,
        require = 0
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
        if (module.handleMouseClick(mouseX, mouseY, button, screen.width, screen.height, this.font)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "mouseDragged",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
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
        cancellable = true,
        require = 0
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
