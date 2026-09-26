package com.msbedwars.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.msbedwars.client.MSBedWarsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings saved in config/msbedwars.json. Every boolean here is a feature switch
 * and shows up in /msb toggle automatically.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch. Off means the mod does nothing at all. */
	public boolean enabled = true;
	/** Look up everyone in the match. */
	public boolean fetchGamePlayers = true;
	/** Read the party with /pl and look its members up first while queueing. */
	public boolean fetchParty = true;
	/** Hide the chat output of /pl when the mod sends it by itself. */
	public boolean hideAutoPartyList = true;

	public boolean hud = true;
	public boolean hudHeads = true;
	public boolean hudStars = true;
	public boolean hudFkdr = true;
	/** Most played mode's FKDR in brackets, when it differs enough from this mode's. */
	public boolean hudTopMode = true;
	public boolean hudHealth = true;
	/** Shows the HUD everywhere with made-up players, for checking the layout. Fetches nothing. */
	public boolean testMode = false;

	public float hudScale = 0.75f;
	/** How far apart the two FKDRs must be before the top mode is shown, 0.15 = 15 %. */
	public double topModeDifference = 0.15;

	private static transient ModConfig instance;
	/** Set when the file could not be read, so it is left alone for the player to fix by hand. */
	private transient boolean savingBlocked;

	public static ModConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	/** Names of all on/off switches, for the /msb command. */
	public static List<String> toggleNames() {
		List<String> names = new ArrayList<>();
		for (Field field : ModConfig.class.getFields()) {
			if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
				names.add(field.getName());
			}
		}
		return names;
	}

	public boolean isOn(String toggle) {
		try {
			return ModConfig.class.getField(toggle).getBoolean(this);
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("Unknown setting " + toggle, e);
		}
	}

	/** Flips a switch and saves. @return the new value */
	public boolean flip(String toggle) {
		try {
			Field field = ModConfig.class.getField(toggle);
			boolean next = !field.getBoolean(this);
			field.setBoolean(this, next);
			save();
			return next;
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("Unknown setting " + toggle, e);
		}
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
