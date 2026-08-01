package com.ceketrum.cei.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pipeline 26.2 -- vestige.
 *
 * Le support reel de 26.1 / 26.2 / 26.3 vit desormais dans le module G8, compile
 * contre un vrai jar 26.2. Ce mixin ne peut pas fonctionner ici : G7 compile
 * contre 1.21.11, ou ni GuiGraphicsExtractor ni extractRenderState n'existent.
 * CeiMixinConfigPlugin le desactive donc a l'execution.
 *
 * Ses injections sur findSlot / mouseClicked / mouseDragged / mouseReleased ont
 * ete retirees : elles ciblaient Screen, qui ne declare aucune de ces methodes,
 * et polluaient le build de "Cannot remap ..." sans rien faire.
 */
@Mixin(Screen.class)
public abstract class CeiScreenInjectionMixin262 {

    @Inject(
        method = {"extractRenderState", "render"},
        at = @At("TAIL"),
        require = 0
    )
    private void cei$render262(@Coerce Object contextObj, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object)this instanceof com.ceketrum.cei.gui.screen.CeiItemInfoScreen infoScreen) {
            infoScreen.renderImpl(contextObj, mouseX, mouseY, delta);
        }
    }
}
