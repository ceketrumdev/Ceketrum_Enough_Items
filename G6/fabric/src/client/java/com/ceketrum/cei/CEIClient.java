package com.ceketrum.cei;

import com.ceketrum.cei.config.CeiConfig;
import com.ceketrum.cei.data.FavoriteItemsManager;
import com.ceketrum.cei.data.ItemDescriptionManager;
import com.ceketrum.cei.data.BrewingRecipeManager;
import com.ceketrum.cei.data.LootTableSourceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client-side initialization for CEI.
 */
public class CEIClient implements ClientModInitializer {
	
	@Override
	public void onInitializeClient() {
		// Load item descriptions at startup
		ItemDescriptionManager.getInstance().loadCurrentLanguageDescriptions();
		
		// Initialize configuration managers
		CeiConfig.getInstance().load();
		FavoriteItemsManager.getInstance().load();
		
		// Register connection events to clear and re-initialize caches
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			BrewingRecipeManager.getInstance().clearCache();
			LootTableSourceManager.getInstance().clearCache();
		});
		
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			var client = net.minecraft.client.Minecraft.getInstance();
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
								pinnedScreen.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
							}
							
							// Render as clean, non-interactive overlay (on HUD, passing -999, -999 as mouse coordinates prevents hover overlays or active tooltips)
							pinnedScreen.render(drawContext, -999, -999, 1.0f);
						}
					}
				}
			}
		});
	}
}
