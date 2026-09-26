package com.msbedwars.client.stats;

/** Bed Wars modes that hypixel.net lists separately on a player's profile. */
public enum BedwarsMode {
	OVERALL("", "all"),
	SOLO("Solo", "1s"),
	DOUBLES("Doubles", "2s"),
	THREES("3v3v3v3", "3s"),
	FOURS("4v4v4v4", "4s");

	/** Prefix of this mode's rows on the profile page, e.g. "Doubles Final Kills". */
	private final String pagePrefix;
	private final String shortName;

	BedwarsMode(String pagePrefix, String shortName) {
		this.pagePrefix = pagePrefix;
		this.shortName = shortName;
	}

	public String rowName(String stat) {
		return pagePrefix.isEmpty() ? stat : pagePrefix + " " + stat;
	}

	public String shortName() {
		return shortName;
	}

	/** Reads the "Mode:" value from the waiting room sidebar, e.g. "Doubles" or "4v4v4v4". */
	public static BedwarsMode fromSidebar(String modeText) {
		String text = modeText.toLowerCase(java.util.Locale.ROOT);
		if (text.contains("4v4v4v4")) return FOURS;
		if (text.contains("3v3v3v3")) return THREES;
		if (text.contains("doubles")) return DOUBLES;
		if (text.contains("solo")) return SOLO;
		return OVERALL;
	}
}
