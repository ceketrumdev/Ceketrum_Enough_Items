package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injections liees au conteneur pour le pipeline 26.1 / 1.21.11.
 *
 * Elles vivaient dans CeiScreenInjectionMixin261, qui cible Screen : or
 * getHoveredSlot, mouseClicked, mouseDragged et mouseReleased ne sont pas
 * declares sur Screen mais sur AbstractContainerScreen. Avec require = 0 elles
 * echouaient en silence -- le build le disait :
 *   "Cannot remap mouseClicked because it does not exists in any of the
 *    targets [net/minecraft/client/gui/screens/Screen]"
 * Aucun clic, aucun drag, aucune interception de slot ne fonctionnait sur G7.
 *
 * Le rendu du panneau migre ici aussi : au TAIL de Screen.render il etait
 * dessine AVANT les slots (AbstractContainerScreen.renderContents appelle
 * super.render puis renderSlots), donc l'inventaire passait par-dessus.
 *
 * Signatures verifiees dans le jar nomme 1.21.11 :
 *   private Slot getHoveredSlot(double, double)
 *   public boolean mouseClicked(MouseButtonEvent, boolean)
 *   public boolean mouseDragged(MouseButtonEvent, double, double)
 *   public boolean mouseReleased(MouseButtonEvent)
 * findSlot n'existe plus en 1.21.11 : la cible est getHoveredSlot.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class CeiContainerScreenInjectionMixin261 {

    @Unique
    private boolean cei$isValidScreenC() {
        return !((Object)this instanceof CreativeModeInventoryScreen);
    }

    @Unique
    private boolean cei$hasRecipeBookC() {
        return (Object)this instanceof AbstractRecipeBookScreen;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cei$renderContainer261(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cei$isValidScreenC() || cei$hasRecipeBookC()) {
            return;
        }

        Screen screen = (Screen)(Object)this;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.getConnection() == null) {
            return;
        }

        CeiScreenHelper.getOrCreateModule(screen).render(
            graphics,
            mouseX,
            mouseY,
            screen.width,
            screen.height,
            client.font,
            client.getConnection().recipes(),
            client.level.registryAccess()
        );

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
                    CeiScreenHelper.initScreen(pinnedScreen, client, screen.width, screen.height);
                }
                pinnedScreen.renderImpl(graphics, mouseX, mouseY, delta);
            }
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void cei$getSlotAt261(double x, double y, CallbackInfoReturnable<Slot> cir) {
        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            var pinnedScreen = card.getScreenInstance();
            if (pinnedScreen != null && pinnedScreen.isMouseOverCard(x, y)) {
                cir.setReturnValue(null);
                return;
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void cei$mouseClickedContainer261(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreenC() || cei$hasRecipeBookC()) {
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
                pinnedScreen.handleMouseClick(mouseX, mouseY, button);
                pinnedManager.bringToFront(card);
                cir.setReturnValue(true);
                return;
            }
        }

        Screen screen = (Screen)(Object)this;
        Minecraft client = Minecraft.getInstance();
        if (CeiScreenHelper.getOrCreateModule(screen)
                .handleMouseClick(mouseX, mouseY, button, screen.width, screen.height, client.font)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void cei$mouseDraggedContainer261(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreenC()) {
            return;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        var pinnedManager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
        for (var card : pinnedManager.getPinnedCards()) {
            if (card.isDragging()) {
                var pinnedScreen = card.getScreenInstance();
                if (pinnedScreen != null && pinnedScreen.mouseDragged261(mouseX, mouseY, button, deltaX, deltaY)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void cei$mouseReleasedContainer261(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreenC()) {
            return;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

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
