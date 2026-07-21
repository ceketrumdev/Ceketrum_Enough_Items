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
		
		registerHudRenderer();
	}

	private void registerHudRenderer() {
		try {
			Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
			Object eventObj = callbackClass.getField("EVENT").get(null);
			java.lang.reflect.Method registerMethod = eventObj.getClass().getMethod("register", Object.class);
			
			Object listener = java.lang.reflect.Proxy.newProxyInstance(
				callbackClass.getClassLoader(),
				new Class<?>[]{callbackClass},
				(proxy, method, args) -> {
					if (args != null && args.length > 0) {
						renderPinnedHudCards(args[0]);
					}
					return null;
				}
			);
			registerMethod.invoke(eventObj, listener);
		} catch (Throwable t) {
			System.err.println("[CEI] HudRenderCallback skipped or incompatible: " + t.getMessage());
		}
	}

	private static void renderPinnedHudCards(Object drawContextObj) {
		if (!(drawContextObj instanceof net.minecraft.client.gui.GuiGraphics drawContext)) return;
		var client = net.minecraft.client.Minecraft.getInstance();
		if (com.ceketrum.cei.gui.util.CeiScreenHelper.getCurrentScreen(client) == null) {
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
						
						// Render as clean, non-interactive overlay
						pinnedScreen.render(drawContext, -999, -999, 1.0f);
					}
				}
			}
		}
	}
}
