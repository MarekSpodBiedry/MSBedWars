package com.msbedwars.client.stats;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads Bed Wars stats from the public profile page at hypixel.net/player/NAME.
 * The page has a table with id "stats-content-bedwars" made of statName / statValue cells,
 * with rows like "Final Kills" and "Doubles Final Kills". It does not list beds broken or lost.
 */
public final class HypixelProfileScraper {
	private static final String PROFILE_URL = "https://hypixel.net/player/";
	private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final int TIMEOUT_MS = 10_000;

	/** hypixel.net answered 429 "Too fast". */
	public static final class RateLimitedException extends IOException {
		public RateLimitedException() {
			super("hypixel.net rate limit (429)");
		}
	}

	/** Cloudflare answered 403: our IP is being challenged as a bot. Stop asking for a while. */
	public static final class BlockedException extends IOException {
		public BlockedException() {
			super("hypixel.net blocked us (403)");
		}
	}

	/**
	 * @return the stats, or empty when hypixel.net has no profile with this name
	 */
	public Optional<BedwarsStats> fetch(String playerName) throws IOException {
		if (!VALID_NAME.matcher(playerName).matches()) {
			return Optional.empty();
		}

		Document page;
		try {
			page = Jsoup.connect(PROFILE_URL + playerName)
					.userAgent("Mozilla/5.0")
					.timeout(TIMEOUT_MS)
					.get();
		} catch (HttpStatusException e) {
			switch (e.getStatusCode()) {
				case 404 -> {
					return Optional.empty();
				}
				case 429 -> throw new RateLimitedException();
				case 403 -> throw new BlockedException();
				default -> throw e;
			}
		}

		Map<String, String> rows = readBedwarsTable(page);
		if (rows.isEmpty()) {
			throw new IOException("Bed Wars table missing, hypixel.net layout may have changed");
		}
		Map<BedwarsMode, ModeStats> modes = new EnumMap<>(BedwarsMode.class);
		for (BedwarsMode mode : BedwarsMode.values()) {
			modes.put(mode, new ModeStats(
					number(rows, mode.rowName("Wins")),
					number(rows, mode.rowName("Losses")),
					number(rows, mode.rowName("Final Kills")),
					number(rows, mode.rowName("Final Deaths"))));
		}
		return Optional.of(new BedwarsStats((int) number(rows, "Level"), modes));
	}

	private static Map<String, String> readBedwarsTable(Document page) {
		Map<String, String> rows = new HashMap<>();
		for (Element nameCell : page.select("#stats-content-bedwars td.statName")) {
			Element valueCell = nameCell.nextElementSibling();
			if (valueCell != null && valueCell.hasClass("statValue")) {
				rows.putIfAbsent(nameCell.text().trim(), valueCell.text().trim());
			}
		}
		return rows;
	}

	private static long number(Map<String, String> rows, String statName) {
		String raw = rows.getOrDefault(statName, "0").replace(",", "");
		try {
			return Long.parseLong(raw);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
