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
}
