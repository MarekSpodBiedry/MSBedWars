package com.msbedwars.client.display;

import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.BedwarsStats;
import com.msbedwars.client.stats.ModeStats;
import com.msbedwars.client.stats.StatsLookup;
import net.minecraft.ChatFormatting;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixed made-up players for test mode, covering low and high stats, a nick and a pending lookup. */
final class TestPlayers {
	private TestPlayers() {
	}

	/** @param health tab list health, or -1 for none (e.g. final killed) */
	record Player(String name, ChatFormatting team, StatsLookup lookup, int health) {
	}

	static List<Player> doublesMatch(String self) {
		return List.of(
				new Player(self, ChatFormatting.RED, found(412, 3.1, 2.4, 5.8), 20),
				new Player("PlayerOne", ChatFormatting.RED, found(188, 1.4, 1.3, 1.5), 17),
				new Player("PlayerTwo", ChatFormatting.BLUE, found(1204, 14.2, 13.5, 15.0), 20),
				new Player("PlayerThree", ChatFormatting.BLUE, found(97, 0.62, 0.55, 0.7), 6),
				new Player("PlayerFour", ChatFormatting.GREEN, new StatsLookup.Nicked(), 20),
				new Player("PlayerFive", ChatFormatting.GREEN, found(655, 6.4, 6.1, 6.8), 12),
				new Player("PlayerSix", ChatFormatting.YELLOW, found(23, 0.21, 0.2, 0.25), 20),
				new Player("PlayerSeven", ChatFormatting.YELLOW, new StatsLookup.Loading(), 20),
				new Player("PlayerEight", ChatFormatting.AQUA, found(301, 2.2, 3.9, 2.0), 9),
				new Player("PlayerNine", ChatFormatting.AQUA, found(840, 8.9, 5.1, 9.2), 20),
				new Player("PlayerTen", ChatFormatting.WHITE, found(145, 1.1, 1.2, 1.0), 20),
				new Player("PlayerEleven", ChatFormatting.WHITE, found(512, 4.4, 4.2, 4.6), -1),
				new Player("PlayerTwelve", ChatFormatting.LIGHT_PURPLE, found(76, 0.9, 1.6, 0.8), 3),
				new Player("PlayerThirteen", ChatFormatting.LIGHT_PURPLE, found(1999, 22.7, 21.0, 24.1), 20),
				new Player("PlayerFourteen", ChatFormatting.GRAY, new StatsLookup.Failed("test"), 20),
				new Player("PlayerFifteen", ChatFormatting.GRAY, found(1003, 3.3, 3.2, 3.4), 14));
	}

	static UUID uuid(String name) {
		return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
	}

	// doublesFkdr is shown; the other mode is the most played so the bracket logic gets exercised
	private static StatsLookup found(int stars, double overallFkdr, double doublesFkdr, double foursFkdr) {
		Map<BedwarsMode, ModeStats> modes = new EnumMap<>(BedwarsMode.class);
		modes.put(BedwarsMode.OVERALL, withFkdr(overallFkdr, 1000));
		modes.put(BedwarsMode.DOUBLES, withFkdr(doublesFkdr, 300));
		modes.put(BedwarsMode.FOURS, withFkdr(foursFkdr, 500));
		return new StatsLookup.Found(new BedwarsStats(stars, modes));
	}

	private static ModeStats withFkdr(double fkdr, long games) {
		long deaths = games / 2;
		return new ModeStats(games / 2, games / 2, Math.round(fkdr * deaths), deaths);
	}
}
