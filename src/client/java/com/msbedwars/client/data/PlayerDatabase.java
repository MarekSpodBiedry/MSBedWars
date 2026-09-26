package com.msbedwars.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.msbedwars.client.MSBedWarsClient;
import com.msbedwars.client.stats.BedwarsStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Every player we have ever met, saved to .minecraft/msbedwars/players.json.
 * Used by both the client thread (roster) and the stats thread, so every method locks.
 * Changes are written to disk at most every 30 s and once more when the game closes.
 */
public final class PlayerDatabase {
	private static final int FILE_VERSION = 1;
	private static final long SAVE_EVERY_SECONDS = 30;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class FileContents {
		int version = FILE_VERSION;
		Map<String, PlayerRecord> players = new HashMap<>();
	}

	private final Path file;
	private final Map<String, PlayerRecord> players;
	private final ScheduledExecutorService saver;
	private boolean dirty;
	/** Set when an unreadable file could not be moved aside, so saving would destroy it. */
	private boolean savingBlocked;

	public PlayerDatabase(Path file) {
		this.file = file;
		this.players = load();
		this.saver = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "MSBedWars-save");
			thread.setDaemon(true);
			return thread;
		});
		saver.scheduleWithFixedDelay(this::saveIfDirty, SAVE_EVERY_SECONDS, SAVE_EVERY_SECONDS, TimeUnit.SECONDS);
		MSBedWarsClient.LOG.info("Loaded {} known players from {}", players.size(), file);
	}

	/** @return a snapshot of what we know, or empty for a player we never met */
	public synchronized Optional<PlayerRecord> get(String name) {
		PlayerRecord record = players.get(key(name));
		return record == null ? Optional.empty() : Optional.of(record.copy());
	}

	/** Counts a new match together with this player. Call once per player per match. */
	public synchronized void recordMatch(String name, long now) {
		PlayerRecord record = players.computeIfAbsent(key(name), k -> new PlayerRecord(name));
		record.name = name;
		if (record.firstSeen == 0) record.firstSeen = now;
		record.lastSeen = now;
		record.matchesTogether++;
		dirty = true;
	}

	public synchronized void recordStats(String name, BedwarsStats stats, long fetchedAt) {
		PlayerRecord record = players.computeIfAbsent(key(name), k -> new PlayerRecord(name));
		record.stats = stats;
		record.statsFetchedAt = fetchedAt;
		dirty = true;
	}

	public void shutdown() {
		saver.shutdownNow();
		saveIfDirty();
	}

	private void saveIfDirty() {
		String json;
		synchronized (this) {
			if (!dirty || savingBlocked) return;
			FileContents contents = new FileContents();
			players.forEach((k, record) -> contents.players.put(k, record.copy()));
			json = GSON.toJson(contents);
			dirty = false;
		}
		try {
			write(json);
		} catch (IOException e) {
			MSBedWarsClient.LOG.error("Could not save {}", file, e);
			synchronized (this) {
				dirty = true;
			}
		}
	}

	// Write next to the real file, then swap, so a crash mid-write never leaves a half file
	private synchronized void write(String json) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, json, StandardCharsets.UTF_8);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private Map<String, PlayerRecord> load() {
		if (!Files.exists(file)) return new HashMap<>();
		try {
			FileContents contents = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileContents.class);
			if (contents == null || contents.players == null) return new HashMap<>();
			return new HashMap<>(contents.players);
		} catch (IOException | JsonParseException e) {
			// Keep the unreadable file for recovery instead of overwriting it with an empty one
			Path aside = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			MSBedWarsClient.LOG.error("Could not read {}, moving it to {}", file, aside, e);
			try {
				Files.move(file, aside);
			} catch (IOException moveError) {
				MSBedWarsClient.LOG.error("Could not move unreadable {}, not saving this session", file, moveError);
				savingBlocked = true;
			}
			return new HashMap<>();
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
