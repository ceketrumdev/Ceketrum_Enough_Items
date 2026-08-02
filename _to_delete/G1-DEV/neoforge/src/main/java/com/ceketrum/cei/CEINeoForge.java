package com.ceketrum.cei;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.data.FavoriteItemsManager;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.LootTableSourceManager;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.minecraft.client.MinecraftClient;

/**
 * Point d'entree NeoForge / Forge pour Minecraft 1.20.1.
 *
 * Ce fichier existait deja mais n'avait jamais ete compile : il etait la copie
 * conforme de celui de G2 (1.20.4), et G1:neoforge etait absent du
 * settings.gradle racine. Trois differences le rendaient invalide en 1.20.1 :
 *
 *   1. Les packages. NeoForge 47.x est encore un fork direct de Forge et
 *      conserve net.minecraftforge.* ; le rebranding vers
 *      net.neoforged.neoforge.* n'intervient qu'a partir de 1.20.2. Tous les
 *      imports designaient donc des classes inexistantes.
 *
 *   2. Le constructeur. L'injection du bus d'evenements en parametre, utilisee
 *      par G2, n'existe pas encore ici : FML appelle un constructeur SANS
 *      argument, et le bus se recupere via FMLJavaModLoadingContext.
 *
 *   3. La signature de l'overlay. IGuiOverlay.render prend cinq arguments en
 *      1.20.1 -- (ForgeGui, DrawContext, float, int, int) -- la lambda a deux
 *      parametres de G2 ne compile pas.
 *
 * Tous ces noms ont ete releves dans forge-1.20.1-47.1.106-universal.jar et
 * dans fancymodloader 47.2.2, pas deduits de G2.
 *
 * Note sur les mappings : ce module est compile en Yarn, donc les types
 * Minecraft apparaissent sous leurs noms Yarn (MinecraftClient, DrawContext)
 * y compris dans les signatures venant de Forge, qu'Architectury Loom remappe.
 */
@Mod("cei")
public class CEINeoForge {

    public CEINeoForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.register(ClientSetup.class);
        MinecraftForge.EVENT_BUS.register(GameplayEvents.class);
    }

    public static class ClientSetup {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemDescriptionManager.getInstance().loadCurrentLanguageDescriptions();
                CeiConfig.getInstance().load();
                FavoriteItemsManager.getInstance().load();
            });
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "hud",
                    (gui, drawContext, partialTick, screenWidth, screenHeight) -> {
                    	// Prechauffage : 2 ms par frame, uniquement quand aucun ecran
                    	// n'est ouvert -- c'est-a-dire pendant que le joueur ne demande rien.
                    	com.ceketrum.cei.CeiWarmup.onClientFrame();
                var client = MinecraftClient.getInstance();
                if (client.currentScreen != null) {
                    return;
                }
                var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
                for (var card : manager.getPinnedCards()) {
                    if (!card.isShowInHud()) {
                        continue;
                    }
                    var pinnedScreen = card.getScreenInstance();
                    if (pinnedScreen == null) {
                        var target = card.getTargetStack();
                        if (target == null || target.isEmpty()) {
                            continue;
                        }
                        pinnedScreen = new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(null, target, false);
                        card.setScreenInstance(pinnedScreen);
                    }
                    // L'overlay recoit directement les dimensions a l'echelle :
                    // inutile de repasser par la fenetre comme cote Fabric.
                    if (pinnedScreen.width != screenWidth || pinnedScreen.height != screenHeight) {
                        pinnedScreen.init(client, screenWidth, screenHeight);
                    }
                    pinnedScreen.render(drawContext, -999, -999, partialTick);
                }
            });
        }
    }

    public static class GameplayEvents {

        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            // La liste d'items, l'index de recettes et le prechauffage
            // dependent du monde : on repart de zero a chaque connexion.
            com.ceketrum.cei.gui.module.cei.CeiModule.invalidateItemCache();
            com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex.invalidate();
            com.ceketrum.cei.CeiWarmup.reset();
            BrewingRecipeManager.getInstance().clearCache();
            LootTableSourceManager.getInstance().clearCache();
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // La liste d'items, l'index de recettes et le prechauffage
            // dependent du monde : on repart de zero a chaque connexion.
            com.ceketrum.cei.gui.module.cei.CeiModule.invalidateItemCache();
            com.ceketrum.cei.gui.module.cei.recipe.CeiRecipeIndex.invalidate();
            com.ceketrum.cei.CeiWarmup.reset();
            BrewingRecipeManager.getInstance().clearCache();
            LootTableSourceManager.getInstance().clearCache();
        }
    }
}
