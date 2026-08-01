package com.ceketrum.cei.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pipeline 26.1 / 1.21.11 -- aiguillage de rendu au niveau Screen.
 *
 * Ce mixin ne garde qu'une seule responsabilite : faire dessiner
 * CeiItemInfoScreen, qui est un Screen simple et non un ecran conteneur.
 *
 * Tout ce qui touche au conteneur (panneau CEI, slots, souris) a migre dans
 * CeiContainerScreenInjectionMixin261 : ces methodes ne sont pas declarees sur
 * Screen, donc les injections echouaient en silence, et le panneau dessine au
 * TAIL de Screen.render passait sous les slots de l'inventaire.
 */
@Mixin(Screen.class)
public abstract class CeiScreenInjectionMixin261 {

    @Inject(
        method = "render",
        at = @At("TAIL"),
        require = 0
    )
    private void cei$render261(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object)this instanceof com.ceketrum.cei.gui.screen.CeiItemInfoScreen infoScreen) {
            infoScreen.renderImpl(graphics, mouseX, mouseY, delta);
        }
    }
}
