package com.msbedwars.client.lobby;

import com.msbedwars.client.Safe;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.data.PlayerRecord;
import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out from the sidebar where we are in Hypixel Bed Wars, keeps the match roster
 * up to date from the tab list, and asks for stats of each new player once.
 */
public final class LobbyTracker {
	private static final int CHECK_EVERY_TICKS = 10;
	// In-game sidebar rows look like "R Red: ✔" or "B Blue: 3"
	private static final Pattern TEAM_ROW = Pattern.compile("^[RBGYAWPS] [A-Za-z]+: ");
	// Bed Wars lobby sidebar row with our own stars: "Level: 21✫"
	private static final Pattern LEVEL_ROW = Pattern.compile("^Level: (\\d{1,5})");

	/** @param ownStars our Bed Wars stars from the lobby's "Level: 21✫" row, null elsewhere */
	private record SidebarReading(BedwarsPhase phase, BedwarsMode mode, Integer ownStars) {
	}

	private final StatsService stats;
	private final PlayerDatabase database;
	private final MatchRoster roster = new MatchRoster();
	private BedwarsPhase phase = BedwarsPhase.NONE;
	/** Read from the waiting room's "Mode:" row. Null until known. */
	private BedwarsMode sidebarMode;
	private Supplier<List<String>> partyMembers = List::of;
	private int ticks;

	public LobbyTracker(StatsService stats, PlayerDatabase database) {
		this.stats = stats;
		this.database = database;
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> Safe.run("match tracking", () -> onTick(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			phase = BedwarsPhase.NONE;
			endMatch();
		});
	}

	public BedwarsPhase phase() {
		return phase;
	}

	public MatchRoster roster() {
		return roster;
	}

	/** Mode of the current match, OVERALL when it cannot be told. */
	public BedwarsMode mode() {
		if (sidebarMode != null && sidebarMode != BedwarsMode.OVERALL) return sidebarMode;
		return modeFromTeamSizes();
	}

	public static boolean onHypixel(Minecraft client) {
		ServerData server = client.getCurrentServer();
		return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel.net");
	}

	private void onTick(Minecraft client) {
		if (++ticks < CHECK_EVERY_TICKS) return;
		ticks = 0;

		ModConfig config = ModConfig.get();
		// Test mode shows made-up players, so the real tracking and fetching stay off
		if (!config.enabled || config.testMode || client.level == null || client.getConnection() == null || !onHypixel(client)) {
			phase = BedwarsPhase.NONE;
			return;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		SidebarReading reading = readSidebar(scoreboard);
		if (reading.ownStars() != null && client.player != null) {
			database.recordStars(client.player.getGameProfile().name(), reading.ownStars(), System.currentTimeMillis());
		}
		if (!reading.phase().inMatch()) {
			// The roster survives short gaps like the world switch at game start
			phase = reading.phase();
			return;
		}

		List<String> tabNames = new ArrayList<>();
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			// Hypixel NPCs and fake tab rows use version 2 UUIDs
			if (info.getProfile().id().version() == 2) continue;
			tabNames.add(info.getProfile().name());
		}

		if (startsNewMatch(reading.phase(), tabNames)) endMatch();
		// The waiting room's tab list scrambles everyone but us and the party, and sometimes
		// leaves party members out, so there the party from /pl comes first; the tab list is
		// only used when we know no party
		List<String> players = reading.phase() == BedwarsPhase.PREGAME ? waitingRoomPlayers(client, tabNames) : tabNames;
		phase = reading.phase();
		if (reading.mode() != null) sidebarMode = reading.mode();

		long now = System.currentTimeMillis();
		boolean fetch = config.fetchGamePlayers;
		for (String name : players) {
			if (!roster.contains(name)) {
				int matchesBefore = database.get(name).map(PlayerRecord::matchesTogether).orElse(0);
				database.recordMatch(name, now);
				roster.add(name, matchesBefore);
				if (fetch) stats.request(name);
			} else if (fetch && needsRetry(name)) {
				stats.request(name);
			}
			// In the waiting room the scoreboard team is the rank color, not a Bed Wars team
			PlayerTeam team = phase == BedwarsPhase.INGAME ? scoreboard.getPlayersTeam(name) : null;
			if (team != null && team.getColor() != ChatFormatting.RESET) {
				roster.setTeam(name, team.getColor());
			}
		}
		if (phase == BedwarsPhase.INGAME && client.player != null) {
			matchNickedPartyMember(client.player.getGameProfile().name(), tabNames);
		}
	}

	/**
	 * A party member playing under a nick is missing from the tab list under their real name.
	 * If exactly one party member is missing and exactly one teammate is nicked, they are the
	 * same person, so the nick gets the real player's stats. With two or more it stays a guess.
	 */
	private void matchNickedPartyMember(String self, List<String> tabNames) {
		ChatFormatting ourTeam = roster.get(self).map(MatchRoster.Member::team).orElse(null);
		if (ourTeam == null) return;
		List<String> missing = partyMembers.get().stream()
				.filter(member -> !member.equalsIgnoreCase(self))
				.filter(member -> tabNames.stream().noneMatch(member::equalsIgnoreCase))
				.toList();
		List<String> nickedTeammates = roster.members().stream()
				.filter(member -> member.team() == ourTeam && !member.name().equalsIgnoreCase(self))
				.map(MatchRoster.Member::name)
				.filter(name -> stats.get(name).orElse(null) instanceof StatsLookup.Nicked)
				.toList();
		if (missing.size() == 1 && nickedTeammates.size() == 1) {
			roster.setRealName(nickedTeammates.getFirst(), missing.getFirst());
			stats.requestFirst(missing.getFirst());
		}
	}

	// Us plus the party from /pl, or the real names in the tab list when we know no party
	private List<String> waitingRoomPlayers(Minecraft client, List<String> tabNames) {
		List<String> party = partyMembers.get();
		List<String> players = new ArrayList<>(party.isEmpty() ? tabNames : party);
		if (client.player != null) {
			String self = client.player.getGameProfile().name();
			if (players.stream().noneMatch(self::equalsIgnoreCase)) players.addFirst(self);
		}
		return players;
	}

	/** Where the current party comes from, set once the party tracker exists. */
	public void setPartySource(Supplier<List<String>> partyMembers) {
		this.partyMembers = partyMembers;
	}

	private boolean startsNewMatch(BedwarsPhase next, List<String> tabNames) {
		// A fresh waiting room is always a new match
		if (next == BedwarsPhase.PREGAME && phase != BedwarsPhase.PREGAME) return true;
		// Landing straight in a game (e.g. /rejoin) is the same match only if we know someone there
		return next == BedwarsPhase.INGAME && !phase.inMatch()
				&& !roster.isEmpty() && !roster.containsAny(tabNames);
	}

	private void endMatch() {
		roster.clear();
		sidebarMode = null;
		stats.cancelPending();
	}

	// Found and nicked results are kept for the whole match. Only failed or dropped lookups are asked again.
	private boolean needsRetry(String name) {
		return stats.get(name).map(lookup -> lookup instanceof StatsLookup.Failed).orElse(true);
	}

	// Used when we joined mid-game and never saw the waiting room's "Mode:" row
	private BedwarsMode modeFromTeamSizes() {
		Map<ChatFormatting, Integer> sizes = new HashMap<>();
		for (MatchRoster.Member member : roster.members()) {
			if (member.team() != null) sizes.merge(member.team(), 1, Integer::sum);
		}
		if (sizes.size() <= 2) return BedwarsMode.OVERALL; // 4v4 is not listed per mode, or no teams yet
		int biggest = sizes.values().stream().max(Integer::compare).orElse(0);
		return switch (biggest) {
			case 1 -> BedwarsMode.SOLO;
			case 2 -> BedwarsMode.DOUBLES;
			case 3 -> BedwarsMode.THREES;
			case 4 -> BedwarsMode.FOURS;
			default -> BedwarsMode.OVERALL;
		};
	}

	private static SidebarReading readSidebar(Scoreboard scoreboard) {
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null || !plain(sidebar.getDisplayName().getString()).contains("BED WARS")) {
			return new SidebarReading(BedwarsPhase.NONE, null, null);
		}

		boolean sawMap = false;
		Integer ownStars = null;
		BedwarsMode mode = null;
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			String row = plain(PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
			if (TEAM_ROW.matcher(row).find()) return new SidebarReading(BedwarsPhase.INGAME, null, null);
			if (row.startsWith("Map:")) sawMap = true;
			if (row.startsWith("Mode:")) mode = BedwarsMode.fromSidebar(row.substring(5));
			Matcher level = LEVEL_ROW.matcher(row);
			if (level.find()) ownStars = Integer.parseInt(level.group(1));
		}
		return sawMap ? new SidebarReading(BedwarsPhase.PREGAME, mode, null) : new SidebarReading(BedwarsPhase.LOBBY, null, ownStars);
	}

	// Hypixel splits sidebar rows with made-up codes like "§u" that Minecraft's own
	// stripFormatting keeps ("Map: Aquarium§u"), so every "§" plus the next character goes
	private static String plain(String text) {
		return text == null ? "" : text.replaceAll("§.", "").trim();
	}
}
