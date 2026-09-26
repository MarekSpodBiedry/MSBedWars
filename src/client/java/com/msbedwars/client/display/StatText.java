package com.msbedwars.client.display;

import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.BedwarsStats;

import java.util.Locale;
import java.util.Optional;

/** How stats are written, shared by the HUD and the text above heads. */
public final class StatText {
	private StatText() {
	}

	public static String stars(int stars) {
		return stars + "✫";
	}

	/** One decimal below 5 ("3.1"), whole numbers from 5 up ("12"). */
	public static String fkdr(double fkdr) {
		return fkdr < 5 ? String.format(Locale.ROOT, "%.1f", fkdr) : String.valueOf(Math.round(fkdr));
	}

	/**
	 * The most played mode's FKDR, e.g. "(20 1s)", when it is not the current mode and
	 * differs from the current mode's FKDR by more than the configured share.
	 */
	public static Optional<String> topMode(BedwarsStats stats, BedwarsMode current) {
		if (current == BedwarsMode.OVERALL) return Optional.empty();
		BedwarsMode top = stats.mostPlayedMode().orElse(current);
		if (top == current) return Optional.empty();
		double currentFkdr = stats.mode(current).fkdr();
		double topFkdr = stats.mode(top).fkdr();
		boolean differs = currentFkdr == 0
				? topFkdr > 0
				: Math.abs(topFkdr - currentFkdr) / currentFkdr > ModConfig.get().topModeDifference;
		return differs ? Optional.of("(" + fkdr(topFkdr) + " " + top.shortName() + ")") : Optional.empty();
	}
}
