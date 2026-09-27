package com.msbedwars.client.stats;

import com.msbedwars.client.Safe;
import com.msbedwars.client.data.PlayerDatabase;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learns Bed Wars stars from chat. In Bed Wars lobbies and games Hypixel puts them in front
 * of every message: "[21✫] [MVP+] PlayerOne: hi" or "[1✫] PlayerThree: hi". The star symbol
 * changes with prestige, so any of them counts. hypixel.net's profile page always says
 * level 0, so this (and the lobby sidebar for our own stars) is where stars come from.
 */
public final class ChatStars {
	// [stars+symbol], then any rank tags like [MVP+], then the name right before the colon
	private static final Pattern STARS_THEN_NAME =
			Pattern.compile("^\\[(\\d{1,5})[✫✪⚝✥✯✰]\\]\\s*(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16})\\s*:");

	private final PlayerDatabase database;

	public ChatStars(PlayerDatabase database) {
		this.database = database;
	}

	public void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) Safe.run("chat stars", () -> read(message.getString()));
		});
	}

	private void read(String raw) {
		String text = raw.replaceAll("§.", "").trim();
		Matcher matcher = STARS_THEN_NAME.matcher(text);
		if (matcher.find()) {
			database.recordStars(matcher.group(2), Integer.parseInt(matcher.group(1)), System.currentTimeMillis());
		}
	}
}
