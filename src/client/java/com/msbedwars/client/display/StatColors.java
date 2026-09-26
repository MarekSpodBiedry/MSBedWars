package com.msbedwars.client.display;

/** ARGB colors for stat values. Alpha must be set or the text is invisible. */
final class StatColors {
	static final int SEPARATOR = 0xFFAAAAAA;
	static final int NICK = 0xFFFF5555;

	private StatColors() {
	}

	// Rough Hypixel prestige colors, one per 100 stars
	private static final int[] PRESTIGE = {
			0xFFAAAAAA, 0xFFFFFFFF, 0xFFFFAA00, 0xFF55FFFF, 0xFF00AA00,
			0xFF00AAAA, 0xFFAA0000, 0xFFFF55FF, 0xFF5555FF, 0xFFAA00AA};

	static int stars(int stars) {
		return PRESTIGE[Math.min(stars / 100, PRESTIGE.length - 1)];
	}

	static int fkdr(double fkdr) {
		if (fkdr < 1) return 0xFFAAAAAA;
		if (fkdr < 2) return 0xFFFFFFFF;
		if (fkdr < 3.5) return 0xFFFFFF55;
		if (fkdr < 6) return 0xFFFFAA00;
		if (fkdr < 10) return 0xFFFF5555;
		return 0xFFAA0000;
	}

	static int health(int hp) {
		if (hp >= 15) return 0xFF55FF55;
		if (hp >= 8) return 0xFFFFFF55;
		return 0xFFFF5555;
	}
}
