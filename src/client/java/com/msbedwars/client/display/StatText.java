package com.msbedwars.client.display;

import com.msbedwars.client.MSBedWarsClient;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.BedwarsStats;
import com.msbedwars.client.stats.StatsLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;
import java.util.Optional;

/** How stats are written, shared by the HUD and the text above heads. */
public final class StatText {
	/** Gap between the FKDR and its bracket, in GUI pixels. */
	public static final int HAIR_SPACE_WIDTH = 2;
	// Minecraft's font has no hair space, so our font "msbedwars:hair" draws a space 2 px wide
	private static final Component HAIR_SPACE = Component.literal(" ").withStyle(style ->
			style.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(MSBedWarsClient.MOD_ID, "hair"))));

	private StatText() {
	}

	/** Health shown for this player in the tab list, or null if the tab list shows none. */
	public static Integer tabHealth(Minecraft client, String name) {
		if (client.level == null) return null;
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective tabObjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		if (tabObjective == null) return null;
		ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), tabObjective);
		return score == null ? null : score.value();
	}

	/** The line above a player's name: "350✫ | 12 (20 1s) | 15" with a hair space, each part in its own color. */
	public static Component nameTagLine(StatsLookup lookup, BedwarsMode mode, Integer health) {
		MutableComponent line = Component.empty();
		switch (lookup) {
			case StatsLookup.Found found -> {
				BedwarsStats stats = found.stats();
				double fkdr = stats.mode(mode).fkdr();
				line.append(colored(stars(stats.stars()), StatColors.stars(stats.stars())))
						.append(colored(" | ", StatColors.SEPARATOR))
						.append(colored(fkdr(fkdr), StatColors.fkdr(fkdr)));
				topMode(stats, mode).ifPresent(bracket -> line.append(HAIR_SPACE).append(colored(bracket, StatColors.SEPARATOR)));
			}
			case StatsLookup.Nicked ignored -> line.append(colored("NICK", StatColors.NICK));
			case StatsLookup.Failed ignored -> line.append(colored("?", StatColors.SEPARATOR));
			case StatsLookup.Loading ignored -> line.append(colored("...", StatColors.SEPARATOR));
		}
		if (health != null) {
			line.append(colored(" | ", StatColors.SEPARATOR)).append(colored(String.valueOf(health), StatColors.health(health)));
		}
		return line;
	}

	private static Component colored(String text, int argb) {
		return Component.literal(text).withStyle(style -> style.withColor(argb & 0xFFFFFF));
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
