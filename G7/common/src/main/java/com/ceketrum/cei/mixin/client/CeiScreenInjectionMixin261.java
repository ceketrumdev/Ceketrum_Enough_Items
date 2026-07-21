package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screen mixin implementation for Minecraft 26.1 / 1.21.x.
 */
@Mixin(Screen.class)
public abstract class CeiScreenInjectionMixin261 {

    @Unique
    private boolean cei$isValidScreen() {
        if ((Object)this instanceof CreativeModeInventoryScreen) {
            return false;
        }
        return (Object)this instanceof AbstractContainerScreen<?>;
    }

    @Inject(
        method = "render",
        at = @At("TAIL"),
        require = 0
    )
    private void cei$render261(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen)(Object)this;

        if (screen instanceof com.ceketrum.cei.gui.screen.CeiItemInfoScreen infoScreen) {
            infoScreen.renderImpl(graphics, mouseX, mouseY, delta);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (screen instanceof AbstractContainerScreen<?> containerScreen && cei$isValidScreen()) {
            CeiScreenHelper.getOrCreateModule(containerScreen).render(
                graphics,
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
                    pinnedScreen.renderImpl(graphics, mouseX, mouseY, delta);
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
    private void cei$mouseClicked261(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        var cards = pinnedManager.getPinnedCards();
        for (int i = cards.size() - 1; i >= 0; i--) {
            var card = cards.get(i);
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(mouseX, mouseY)) {
                pinnedScreen.handleMouseClick(mouseX, mouseY, button);
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
    private void cei$mouseDragged261(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    if (pinnedScreen.mouseDragged261(mouseX, mouseY, button, deltaX, deltaY)) {
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
    private void cei$mouseReleased261(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreen()) {
            return;
        }

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null) {
                    pinnedScreen.mouseReleased261(mouseX, mouseY, button);
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
