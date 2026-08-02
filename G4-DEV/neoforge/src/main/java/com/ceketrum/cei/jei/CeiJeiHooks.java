package com.ceketrum.cei.jei;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Accroche du pont JEI sur le cycle de vie du client.
 *
 * Point de conception important : @EventBusSubscriber est resolu par scan
 * d'annotations, donc CETTE CLASSE N'A PAS BESOIN D'ETRE APPELEE depuis
 * CEINeoForge. C'est ce qui permet de respecter la regle du labo -- le core
 * de CEI n'est pas touche, le pont s'ajoute par simple presence.
 *
 * On se declenche a l'entree en monde, pas au demarrage : les recettes
 * n'existent pas avant, et c'est aussi le moment ou JEI paie sa facture --
 * on veut pouvoir comparer.
 */
@EventBusSubscriber(modid = "cei", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CeiJeiHooks {

    private CeiJeiHooks() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CeiJeiBridge.discoverOnce();
    }
}
