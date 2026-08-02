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

/**
 * G5 (1.21.5+) version of the screen mixin.
 *
 * KEY DIFFERENCE from G4: In Minecraft 1.21.5, GuiGraphics.pose() was removed
 * as part of the switch to a deferred rendering system. The new system handles
 * Z-layering automatically, so we no longer need pushPose/translate/popPose.
 * All @Inject annotations use require=0 for robustness across 1.21.5-1.21.11.
 */
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
    
    @Unique
    private boolean cei$hasRecipeBook() {
        return (Object)this instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
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
        
        // 1.21.5+: No pose().pushPose()/translate()/popPose() needed.
        // The new deferred rendering system handles Z-ordering automatically.
        CeiScreenHelper.getOrCreateModule(screen).render(
            context, 
            mouseX, 
            mouseY, 
            screen.width, 
            screen.height, 
            this.font, 
            this.minecraft.getConnection().recipes(), 
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
                    pinnedScreen.init(screen.width, screen.height);
                }
                pinnedScreen.render(context, mouseX, mouseY, delta);
            }
        }
    }

    @Inject(
        method = "getHoveredSlot",
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
    private void cei$mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (cei$hasRecipeBook()) {
            return;
        }

        if (!cei$isValidScreen()) {
            return;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var cards = pinnedManager.getPinnedCards();
        for (int i = cards.size() - 1; i >= 0; i--) {
            var card = cards.get(i);
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(mouseX, mouseY)) {
                pinnedScreen.mouseClicked(event, doubleClick);
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
    private void cei$mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    if (pinnedScreen.mouseDragged(event, deltaX, deltaY)) {
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
    private void cei$mouseReleased(net.minecraft.client.input.MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Fin d'un eventuel glisser d'ascenseur. Appel sans
        // argument : la signature de cette methode change selon
        // la version, son contenu ne nous sert pas.
        if (cei$isValidScreen()) {
            CeiScreenHelper.getOrCreateModule((Screen)(Object)this)
                    .handleMouseRelease();
        }

        if (!cei$isValidScreen()) {
            return;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        
        // 1. Release dragging state globally on RELEASE action
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    pinnedScreen.mouseReleased(event);
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
