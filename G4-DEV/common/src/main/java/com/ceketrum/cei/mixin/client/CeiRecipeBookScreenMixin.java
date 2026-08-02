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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;

/**
 * Les ecrans a livre de recettes (inventaire, table de craft, fourneaux)
 * court-circuitent le render() du conteneur quand
 * `recipeBook.isVisible() && widthTooNarrow` : super.render() n'est jamais
 * appele, donc l'injection TAIL de CeiScreenInjectionMixin ne se declenche pas et le panneau CEI
 * disparait. Meme chose pour mouseClicked(), qui retourne sans appeler super.
 *
 * Ce mixin reinjecte CEI au TAIL du render() de ces ecrans, qui lui est
 * toujours execute, dans les deux branches.
 */
@Mixin({InventoryScreen.class, CraftingScreen.class, AbstractFurnaceScreen.class})
public abstract class CeiRecipeBookScreenMixin extends Screen {
    protected CeiRecipeBookScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean cei$isValidScreenRB() {
        return !((Object)this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
    }

    @Inject(
        method = "render",
        at = @At("TAIL"),
        require = 0
    )
    private void cei$renderRecipeBook(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cei$isValidScreenRB()) {
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
        method = "mouseClicked",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$mouseClickedRecipeBook(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreenRB()) {
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
}
