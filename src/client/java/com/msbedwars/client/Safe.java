package com.msbedwars.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Runs mod code so that a bug never crashes the game. The first error of each feature is
 * logged with its stack trace; after that the feature keeps being tried but stays quiet.
 */
public final class Safe {
	private static final Set<String> reported = ConcurrentHashMap.newKeySet();

	private Safe() {
	}

	public static void run(String feature, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | LinkageError e) {
			report(feature, e);
		}
	}

	/** Like {@link #run} for code that answers yes or no; a failure answers {@code fallback}. */
	public static boolean check(String feature, BooleanSupplier action, boolean fallback) {
		try {
			return action.getAsBoolean();
		} catch (RuntimeException | LinkageError e) {
			report(feature, e);
			return fallback;
		}
	}

	private static void report(String feature, Throwable e) {
		if (reported.add(feature)) {
			MSBedWarsClient.LOG.error("MSBedWars {} failed, skipping it (further errors hidden)", feature, e);
		}
	}
}
