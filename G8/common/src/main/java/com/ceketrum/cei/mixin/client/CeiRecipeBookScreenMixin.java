package com.ceketrum.cei.mixin.client;

import com.ceketrum.cei.gui.util.CeiScreenHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

/**
 * Les ecrans a livre de recettes (inventaire, table de craft, fourneaux)
 * court-circuitent le render() du conteneur quand
 * `recipeBook.isVisible() && widthTooNarrow` : super.extractRenderState() n'est jamais
 * appele, donc l'injection TAIL de CeiScreenInjectionMixin ne se declenche pas et le panneau CEI
 * disparait. Meme chose pour mouseClicked(), qui retourne sans appeler super.
 *
 * Ce mixin reinjecte CEI au TAIL du render() de ces ecrans, qui lui est
 * toujours execute, dans les deux branches.
 */
@Mixin({AbstractRecipeBookScreen.class})
public abstract class CeiRecipeBookScreenMixin extends Screen {
    protected CeiRecipeBookScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean cei$isValidScreenRB() {
        return !((Object)this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
    }

    @Inject(
        method = "extractRenderState",
        at = @At("TAIL"),
        require = 0
    )
    private void cei$renderRecipeBook(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cei$isValidScreenRB()) {
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
                pinnedScreen.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }

    @Inject(
        method = "mouseClicked",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cei$mouseClickedRecipeBook(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!cei$isValidScreenRB()) {
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
}
