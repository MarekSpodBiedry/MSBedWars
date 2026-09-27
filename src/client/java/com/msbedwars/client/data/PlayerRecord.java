package com.msbedwars.client.data;

import com.msbedwars.client.stats.BedwarsStats;

/** One player as saved in players.json. Mutable so Gson can fill it; only PlayerDatabase changes it. */
public final class PlayerRecord {
	String name;
	/** Last stats we pulled, null if never pulled (e.g. they were nicked every time). */
	BedwarsStats stats;
	long statsFetchedAt;
	long firstSeen;
	long lastSeen;
	/** Matches we have been in together, counting the current one once it is recorded. */
	int matchesTogether;
	/**
	 * Bed Wars stars as last seen in chat ("[21✫] Name: hi") or, for us, the lobby sidebar.
	 * hypixel.net's profile page always says level 0, so this is the only source. Null if never seen.
	 */
	Integer stars;
	long starsSeenAt;

	PlayerRecord() {
	}

	PlayerRecord(String name) {
		this.name = name;
	}

	PlayerRecord copy() {
		PlayerRecord copy = new PlayerRecord(name);
		copy.stats = stats;
		copy.statsFetchedAt = statsFetchedAt;
		copy.firstSeen = firstSeen;
		copy.lastSeen = lastSeen;
		copy.matchesTogether = matchesTogether;
		copy.stars = stars;
		copy.starsSeenAt = starsSeenAt;
		return copy;
	}

	public String name() {
		return name;
	}

	public BedwarsStats stats() {
		return stats;
	}

	public long statsFetchedAt() {
		return statsFetchedAt;
	}

	public long firstSeen() {
		return firstSeen;
	}

	public long lastSeen() {
		return lastSeen;
	}

	public int matchesTogether() {
		return matchesTogether;
	}

	public Integer stars() {
		return stars;
	}
}
