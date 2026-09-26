package com.msbedwars.client.lobby;

public enum BedwarsPhase {
	/** Not on Hypixel, or not in Bed Wars. The mod stays idle. */
	NONE,
	/** Main Bed Wars lobby. Only the party matters here. */
	LOBBY,
	/** Waiting room before the game starts. The tab list holds just this game's players. */
	PREGAME,
	/** Game running. */
	INGAME;

	public boolean inMatch() {
		return this == PREGAME || this == INGAME;
	}

	public boolean active() {
		return this != NONE;
	}
}
