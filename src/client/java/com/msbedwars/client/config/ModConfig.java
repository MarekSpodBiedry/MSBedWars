package com.msbedwars.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.msbedwars.client.MSBedWarsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Settings saved in config/msbedwars.json, changed in {@link ConfigScreen}.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch. Off means the mod does nothing at all. */
	public boolean enabled = true;
	/** Look up everyone in the match. */
	public boolean fetchGamePlayers = true;
	/** Read the party from chat (/pl typed by the player) and look its members up first while queueing. */
	public boolean fetchParty = true;

	public boolean hud = true;
	public boolean hudHeads = true;
	public boolean hudStars = true;
	public boolean hudFkdr = true;
	/** Most played mode's FKDR in brackets, when it is enough higher than this mode's. */
	public boolean hudTopMode = true;
	public boolean hudHealth = true;
	/** Stars, FKDR and health in a line above each player's name. */
	public boolean nametags = true;
	/** Redraws Hypixel's Bed Wars sidebar shorter: merged stat lines, no web address. */
	public boolean sidebar = true;
	/** Color of the letters the mod adds to the sidebar (K, F, B, FKDR...), as 0xRRGGBB. Gray by default. */
	public int accentColor = 0xAAAAAA;
	/** Time on the sidebar's date line as 13:05 instead of 1:05 PM. */
	public boolean clock24h = true;
	/** Shows the HUD everywhere with made-up players, for checking the layout. Fetches nothing. */
	public boolean testMode = false;
	/** Writes chat (with colors), sidebar and tab list changes to msbedwars/capture.log, for development. */
	public boolean capture = false;

	public float hudScale = 0.75f;
	/** How much higher the top mode's FKDR must be before it is shown, 0.15 = 15 %. */
	public double topModeDifference = 0.15;

	private static transient ModConfig instance;
	/** Set when the file could not be read, so it is left alone for the player to fix by hand. */
	private transient boolean savingBlocked;

	public static ModConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	public void save() {
		Path file = path();
		if (savingBlocked) {
			MSBedWarsClient.LOG.warn("Not saving {} because it could not be read at startup", file);
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			MSBedWarsClient.LOG.error("Could not save {}", file, e);
		}
	}

	private static ModConfig load() {
		Path file = path();
		if (!Files.exists(file)) return new ModConfig();
		try {
			ModConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
			return config == null ? new ModConfig() : config;
		} catch (IOException | JsonParseException e) {
			MSBedWarsClient.LOG.error("Could not read {}, using defaults for this session", file, e);
			ModConfig config = new ModConfig();
			config.savingBlocked = true;
			return config;
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(MSBedWarsClient.MOD_ID + ".json");
	}
}
