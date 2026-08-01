package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screen mixin implementation for Minecraft 26.2.
 */
@Mixin(Screen.class)
public abstract class CeiScreenInjectionMixin262 {

    @Unique
    private boolean cei$isValidScreen() {
        if ((Object)this instanceof CreativeModeInventoryScreen) {
            return false;
        }
        return (Object)this instanceof AbstractContainerScreen<?>;
    }

    @Inject(
        method = {
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            "extractRenderState"
        },
        at = @At("TAIL"),
        require = 0
    )
    private void cei$render262(@Coerce Object contextObj, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen)(Object)this;

        if (screen instanceof com.ceketrum.cei.gui.screen.CeiItemInfoScreen infoScreen) {
            infoScreen.renderImpl(contextObj, mouseX, mouseY, delta);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (screen instanceof AbstractContainerScreen<?> containerScreen && cei$isValidScreen()) {
            CeiScreenHelper.getOrCreateModule(containerScreen).render(
                contextObj,
                mouseX,
                mouseY,
                screen.width,
                screen.height,
                client.font,
                client.getConnection().recipes(),
                client.level.registryAccess()
            );

            // Render Pinned Recipe Card Overlays
            var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
            for (var card : pinnedManager.getPinnedCards()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen == null) {
                    var target = card.getTargetStack();
                    if (target != null && !target.isEmpty()) {
                        pinnedScreen = new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(containerScreen, target, false);
                        card.setScreenInstance(pinnedScreen);
                    }
                }
                if (pinnedScreen != null) {
                    pinnedScreen.setParentScreen(containerScreen);
                    if (pinnedScreen.width != screen.width || pinnedScreen.height != screen.height) {
                        CeiScreenHelper.initScreen(pinnedScreen, client, screen.width, screen.height);
                    }
                    pinnedScreen.renderImpl(contextObj, mouseX, mouseY, delta);
                }
            }
        }
    }

    @Inject(
        method = {"findSlot", "getHoveredSlot"},
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
    private void cei$mouseClicked262(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
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
        Minecraft client = Minecraft.getInstance();
        if (module.handleMouseClick(mouseX, mouseY, button, screen.width, screen.height, client.font)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "mouseDragged",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$mouseDragged262(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

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
    private void cei$mouseReleased262(net.minecraft.client.input.MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
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
                    pinnedScreen.mouseReleased(event);
                }
            }
        }
        
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
