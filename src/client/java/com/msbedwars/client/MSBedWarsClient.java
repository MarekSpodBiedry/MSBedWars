package com.msbedwars.client;

import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.display.NameTagStats;
import com.msbedwars.client.display.StatsHud;
import com.msbedwars.client.lobby.LobbyTracker;
import com.msbedwars.client.party.PartyTracker;
import com.msbedwars.client.stats.HypixelProfileScraper;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MSBedWarsClient implements ClientModInitializer {
	public static final String MOD_ID = "msbedwars";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		PlayerDatabase database = new PlayerDatabase(FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("players.json"));
		StatsService stats = new StatsService(new HypixelProfileScraper(), database);
		LobbyTracker lobby = new LobbyTracker(stats, database);
		PartyTracker party = new PartyTracker(lobby, stats);
		lobby.register();
		party.register();
		MsbCommand.register(stats, database);
		NameTagStats.init(lobby, stats);

		// Drawn under the tab list, so holding Tab still shows the full list on top
		HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
				Identifier.fromNamespaceAndPath(MOD_ID, "stats_hud"), new StatsHud(lobby, party, stats));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			stats.shutdown();
			database.shutdown();
		});
	}
}
