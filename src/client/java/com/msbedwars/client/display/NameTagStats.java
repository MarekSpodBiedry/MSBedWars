package com.msbedwars.client.display;

import com.msbedwars.client.Safe;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.data.PlayerRecord;
import com.msbedwars.client.lobby.LobbyTracker;
import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;

/**
 * Puts a stats line above a player's name. Minecraft draws the score line at the bottom
 * and the name above it, so the name moves into the score slot and our line takes the
 * name slot. This replaces the server's below-name score, which our line covers anyway.
 */
public final class NameTagStats {
	private static LobbyTracker lobby;
	private static StatsService stats;
	private static PlayerDatabase database;

	private NameTagStats() {
	}

	public static void init(LobbyTracker lobbyTracker, StatsService statsService, PlayerDatabase playerDatabase) {
		lobby = lobbyTracker;
		stats = statsService;
		database = playerDatabase;
	}

	/** Called by the player renderer once the vanilla name tag is filled in. */
	public static void apply(String playerName, AvatarRenderState state) {
		Safe.run("name tag stats", () -> addLine(playerName, state));
	}

	private static void addLine(String playerName, AvatarRenderState state) {
		ModConfig config = ModConfig.get();
		if (lobby == null || state.nameTag == null || !config.enabled || !config.nametags) return;

		Component line;
		if (config.testMode) {
			TestPlayers.Player fake = TestPlayers.forName(playerName);
			line = StatText.nameTagLine(fake.lookup(), BedwarsMode.DOUBLES, null, fake.health() < 0 ? null : fake.health());
		} else {
			if (!lobby.phase().inMatch() || !lobby.roster().contains(playerName)) return;
			String realName = lobby.roster().statsName(playerName);
			StatsLookup lookup = stats.get(realName).orElse(null);
			if (lookup == null) return;
			Integer seenStars = database.get(realName).map(PlayerRecord::stars).orElse(null);
			line = StatText.nameTagLine(lookup, lobby.mode(), seenStars, StatText.tabHealth(Minecraft.getInstance(), playerName));
		}
		state.scoreText = state.nameTag;
		state.nameTag = line;
	}
}
