package com.msbedwars.client.stats;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Bed Wars numbers for one player, overall and per mode, as read from their hypixel.net profile. */
public record BedwarsStats(int stars, Map<BedwarsMode, ModeStats> modes) {
	public ModeStats mode(BedwarsMode mode) {
		return modes == null ? ModeStats.EMPTY : modes.getOrDefault(mode, ModeStats.EMPTY);
	}

	public ModeStats overall() {
		return mode(BedwarsMode.OVERALL);
	}

	/** Records saved before per-mode stats existed have no modes and must be pulled again. */
	public boolean complete() {
		return modes != null && modes.containsKey(BedwarsMode.OVERALL);
	}

	/** The team mode this player has played the most games in, if any. */
	public Optional<BedwarsMode> mostPlayedMode() {
		return Stream.of(BedwarsMode.SOLO, BedwarsMode.DOUBLES, BedwarsMode.THREES, BedwarsMode.FOURS)
				.filter(mode -> mode(mode).games() > 0)
				.max(Comparator.comparingLong(mode -> mode(mode).games()));
	}
}
