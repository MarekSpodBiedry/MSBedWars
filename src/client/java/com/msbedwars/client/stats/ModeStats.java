package com.msbedwars.client.stats;

public record ModeStats(long wins, long losses, long finalKills, long finalDeaths) {
	public static final ModeStats EMPTY = new ModeStats(0, 0, 0, 0);

	public long games() {
		return wins + losses;
	}

	public double fkdr() {
		return ratio(finalKills, finalDeaths);
	}

	public double wlr() {
		return ratio(wins, losses);
	}

	// Same convention as Hypixel: dividing by zero deaths/losses just gives the top number
	private static double ratio(long top, long bottom) {
		return bottom == 0 ? top : (double) top / bottom;
	}
}
