package com.msbedwars.client.stats;

/** Where a player's stats lookup currently stands. Display code switches on this. */
public sealed interface StatsLookup {
	record Loading() implements StatsLookup {
	}

	record Found(BedwarsStats stats) implements StatsLookup {
	}

	/** hypixel.net has no profile under this name, so in a game it is almost always a nick. */
	record Nicked() implements StatsLookup {
	}

	record Failed(String reason) implements StatsLookup {
	}
}
