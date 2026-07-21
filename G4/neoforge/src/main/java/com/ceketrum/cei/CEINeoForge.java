package com.ceketrum.cei;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.data.FavoriteItemsManager;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.LootTableSourceManager;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;

@Mod("cei")
public class CEINeoForge {
    public CEINeoForge(IEventBus modEventBus) {
        // Register mod bus event subscribers
        modEventBus.register(ClientSetup.class);
        
        // Register game bus event subscribers
        NeoForge.EVENT_BUS.register(GameplayEvents.class);
    }

    public static class ClientSetup {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                // Initialize client-side config & managers
                ItemDescriptionManager.getInstance().loadCurrentLanguageDescriptions();
                CeiConfig.getInstance().load();
                FavoriteItemsManager.getInstance().load();
            });
        }

        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath("cei", "hud"), (guiGraphics, deltaTracker) -> {
                var client = Minecraft.getInstance();
                if (client.screen == null) {
                    var manager = com.ceketrum.cei.data.PinnedRecipeManager.getInstance();
                    for (var card : manager.getPinnedCards()) {
                        if (card.isShowInHud()) {
                            var pinnedScreen = card.getScreenInstance();
                            if (pinnedScreen == null) {
                                var target = card.getTargetStack();
                                if (target != null && !target.isEmpty()) {
                                    pinnedScreen = new com.ceketrum.cei.gui.screen.CeiItemInfoScreen(null, target, false);
                                    card.setScreenInstance(pinnedScreen);
                                }
                            }
                            if (pinnedScreen != null) {
                                var window = client.getWindow();
                                if (pinnedScreen.width != window.getGuiScaledWidth() || pinnedScreen.height != window.getGuiScaledHeight()) {
                                    pinnedScreen.init(client, window.getGuiScaledWidth(), window.getGuiScaledHeight());
                                }
                                pinnedScreen.render(guiGraphics, -999, -999, 1.0f);
                            }
                        }
                    }
                }
            });
        }
    }

    public static class GameplayEvents {
        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            BrewingRecipeManager.getInstance().clearCache();
            LootTableSourceManager.getInstance().clearCache();
        }
        
        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            BrewingRecipeManager.getInstance().clearCache();
            LootTableSourceManager.getInstance().clearCache();
        }
    }
}
