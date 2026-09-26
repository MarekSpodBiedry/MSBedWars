package com.msbedwars.client.stats;

import com.msbedwars.client.MSBedWarsClient;
import com.msbedwars.client.data.PlayerDatabase;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Caches stats per player and fetches missing ones one at a time on a background thread,
 * 1 s apart, so hypixel.net does not rate limit us. Stats are also saved to the player
 * database, so after a restart anyone pulled in the last day is not pulled again.
 */
public final class StatsService {
	private static final long REQUEST_SPACING_MS = 1_000;
	private static final long RATE_LIMIT_BACKOFF_MS = 10_000;
	/** A Cloudflare block is not lifted by retrying, only by leaving the site alone. */
	private static final long BLOCKED_PAUSE_MS = 10 * 60_000;
	private static final long FOUND_TTL_MS = 24 * 60 * 60_000;
	private static final long NICKED_TTL_MS = 5 * 60_000;
	private static final long FAILED_TTL_MS = 60_000;

	private record Entry(StatsLookup lookup, long storedAt) {
		boolean expired(long now) {
			long ttl = switch (lookup) {
				case StatsLookup.Found ignored -> FOUND_TTL_MS;
				case StatsLookup.Nicked ignored -> NICKED_TTL_MS;
				case StatsLookup.Failed ignored -> FAILED_TTL_MS;
				case StatsLookup.Loading ignored -> Long.MAX_VALUE;
			};
			return now - storedAt > ttl;
		}
	}

	private final HypixelProfileScraper scraper;
	private final PlayerDatabase database;
	private final Map<String, Entry> cache = new ConcurrentHashMap<>();
	private final BlockingDeque<String> queue = new LinkedBlockingDeque<>();
	private final Thread worker;
	private volatile long blockedUntil;

	public StatsService(HypixelProfileScraper scraper, PlayerDatabase database) {
		this.scraper = scraper;
		this.database = database;
		this.worker = new Thread(this::workLoop, "MSBedWars-stats");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	/** Queues a lookup at the back, unless we already have a fresh result or one is in flight. */
	public void request(String playerName) {
		if (needsFetch(playerName)) queue.offer(playerName);
	}

	/** Like {@link #request} but jumps the queue. Used for party members while queueing. */
	public void requestFirst(String playerName) {
		if (needsFetch(playerName)) {
			queue.offerFirst(playerName);
		} else if (get(playerName).orElse(null) instanceof StatsLookup.Loading && queue.remove(playerName)) {
			queue.offerFirst(playerName);
		}
	}

	/** @return the current lookup state, or empty if this player was never requested */
	public Optional<StatsLookup> get(String playerName) {
		Entry entry = cache.get(key(playerName));
		return entry == null ? Optional.empty() : Optional.of(entry.lookup());
	}

	/** True while hypixel.net is blocking us and lookups are paused. */
	public boolean blocked() {
		return System.currentTimeMillis() < blockedUntil;
	}

	/** Drops queued lookups, e.g. when a new match starts. Cached results stay. */
	public void cancelPending() {
		String name;
		while ((name = queue.poll()) != null) {
			cache.remove(key(name));
		}
	}

	public void shutdown() {
		worker.interrupt();
	}

	// Marks the player as loading and returns true when a network fetch is needed
	private boolean needsFetch(String playerName) {
		String key = key(playerName);
		long now = System.currentTimeMillis();
		Entry existing = cache.get(key);
		if (existing != null && !existing.expired(now)) return false;

		Entry saved = database.get(playerName)
				.filter(record -> record.stats() != null && record.stats().complete())
				.map(record -> new Entry(new StatsLookup.Found(record.stats()), record.statsFetchedAt()))
				.orElse(null);
		if (saved != null && !saved.expired(now)) {
			cache.put(key, saved);
			return false;
		}

		cache.put(key, new Entry(new StatsLookup.Loading(), now));
		return true;
	}

	private void workLoop() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				String name = queue.take();
				long wait = blockedUntil - System.currentTimeMillis();
				if (wait > 0) Thread.sleep(wait);
				store(name, lookup(name));
				Thread.sleep(REQUEST_SPACING_MS);
			}
		} catch (InterruptedException ignored) {
			// Game is closing
		}
	}

	private StatsLookup lookup(String name) throws InterruptedException {
		try {
			return scraper.fetch(name)
					.<StatsLookup>map(StatsLookup.Found::new)
					.orElseGet(StatsLookup.Nicked::new);
		} catch (HypixelProfileScraper.RateLimitedException e) {
			MSBedWarsClient.LOG.warn("Rate limited by hypixel.net, waiting {} ms", RATE_LIMIT_BACKOFF_MS);
			Thread.sleep(RATE_LIMIT_BACKOFF_MS);
			queue.offerFirst(name);
			return new StatsLookup.Loading();
		} catch (HypixelProfileScraper.BlockedException e) {
			MSBedWarsClient.LOG.warn("hypixel.net is blocking us, pausing lookups for {} ms", BLOCKED_PAUSE_MS);
			blockedUntil = System.currentTimeMillis() + BLOCKED_PAUSE_MS;
			queue.offerFirst(name);
			return new StatsLookup.Loading();
		} catch (IOException e) {
			MSBedWarsClient.LOG.warn("Stats lookup failed for {}: {}", name, e.getMessage());
			return new StatsLookup.Failed(e.getMessage());
		}
	}

	private void store(String name, StatsLookup lookup) {
		long now = System.currentTimeMillis();
		cache.put(key(name), new Entry(lookup, now));
		// Nicks are not saved: the same nick is reused by different people
		if (lookup instanceof StatsLookup.Found found) {
			database.recordStats(name, found.stats(), now);
		}
	}

	private static String key(String playerName) {
		return playerName.toLowerCase(Locale.ROOT);
	}
}
