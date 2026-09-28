package com.msbedwars.client.display;

import com.msbedwars.client.Safe;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.lobby.BedwarsPhase;
import com.msbedwars.client.lobby.LobbyTracker;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's Bed Wars sidebar, redrawn shorter. Replaces the vanilla sidebar only in the Bed
 * Wars lobby, waiting room and game; everywhere else, or if anything goes wrong, the vanilla
 * one is drawn. Rows the mod does not know are kept as they are.
 *
 * <pre>
 * lobby:   Tokens, Slumber Tickets, Total Kills, Total Wins  ->  "288K 107W 2.43 FKDR"
 *                                                                "205k Tokens · 39/99 Tickets"
 * queue:   Map + Mode -> "Aquarium · 4v4v4v4", Version dropped
 * game:    Kills, Final Kills, Beds Broken -> "2K 3F 1B", eliminated teams dimmed
 * all:     date gets the time before the server ID, the web address and double blanks go,
 *          and in the lobby Level and Progress sit together
 * </pre>
 */
public final class CleanSidebar implements HudElement {
	private static final int MAX_ROWS = 15;
	private static final int ROW = 9;
	private static final Pattern DATE_ROW = Pattern.compile("^(\\d\\d/\\d\\d/\\d\\d)\\s*(\\S*)$");
	private static final Pattern NUMBER = Pattern.compile("([\\d,]+)");
	private static final Pattern TEAM_ROW = Pattern.compile("^[RBGYAWPS] [A-Za-z]+: ");
	// Same order as vanilla: highest score on top, ties by name
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	private record Row(Component text, String plain) {
		boolean blank() {
			return plain.isEmpty();
		}
	}

	private final HudElement vanilla;
	private final LobbyTracker lobby;
	private final StatsService stats;

	public CleanSidebar(HudElement vanilla, LobbyTracker lobby, StatsService stats) {
		this.vanilla = vanilla;
		this.lobby = lobby;
		this.stats = stats;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		boolean drawn = Safe.check("clean sidebar", () -> drawClean(graphics), false);
		if (!drawn) vanilla.extractRenderState(graphics, delta);
	}

	// Returns false when the vanilla sidebar should be drawn instead
	private boolean drawClean(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		ModConfig config = ModConfig.get();
		BedwarsPhase phase = lobby.phase();
		if (!config.enabled || !config.sidebar || !phase.active() || client.level == null || client.player == null) return false;

		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = visibleSidebar(scoreboard, client);
		if (objective == null) return false;

		List<Component> lines = rebuild(phase, readRows(scoreboard, objective), config, client);
		graphics.nextStratum();
		draw(graphics, client, objective.getDisplayName(), lines);
		return true;
	}

	// The objective vanilla would show: our team's sidebar slot first, then the normal one
	private static Objective visibleSidebar(Scoreboard scoreboard, Minecraft client) {
		PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null && scoreboard.getDisplayObjective(slot) != null) return scoreboard.getDisplayObjective(slot);
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private static List<Row> readRows(Scoreboard scoreboard, Objective objective) {
		List<Row> rows = new ArrayList<>();
		scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(ORDER)
				.limit(MAX_ROWS)
				.forEach(entry -> {
					PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
					Component text = PlayerTeam.formatNameForTeam(team, entry.ownerName());
					rows.add(new Row(text, plain(text.getString())));
				});
		return rows;
	}

	private List<Component> rebuild(BedwarsPhase phase, List<Row> rows, ModConfig config, Minecraft client) {
		Map<String, String> values = new HashMap<>();
		for (Row row : rows) {
			int colon = row.plain().indexOf(": ");
			if (colon > 0) values.put(row.plain().substring(0, colon), row.plain().substring(colon + 2).trim());
		}
		int suffix = config.accentColor & 0xFFFFFF;

		List<Component> out = new ArrayList<>();
		boolean merged = false;
		String previous = "";
		for (Row row : rows) {
			String p = row.plain();
			String before = previous;
			previous = p;
			if (row.blank()) {
				// Level and Progress belong together in the lobby, Hypixel splits them with a blank
				if (phase != BedwarsPhase.LOBBY || !before.startsWith("Level:")) out.add(Component.empty());
				continue;
			}
			if (p.startsWith("www.hypixel.net")) continue;
			Matcher date = DATE_ROW.matcher(p);
			if (date.matches()) {
				out.add(dateLine(date.group(1), date.group(2), config.clock24h));
				continue;
			}
			switch (phase) {
				case LOBBY -> {
					if (p.startsWith("Tokens:") || p.startsWith("Slumber Tickets:")
							|| p.startsWith("Total Kills:") || p.startsWith("Total Wins:")) {
						if (!merged) {
							out.add(lobbyStats(values, suffix, client));
							Component currency = lobbyCurrency(values, suffix);
							if (currency != null) out.add(currency);
							merged = true;
						}
						continue;
					}
				}
				case PREGAME -> {
					if (p.startsWith("Mode:") || p.startsWith("Version:")) continue;
					if (p.startsWith("Map:")) {
						out.add(mapAndMode(values));
						continue;
					}
				}
				case INGAME -> {
					if (p.startsWith("Kills:") || p.startsWith("Final Kills:") || p.startsWith("Beds Broken:")) {
						if (!merged) {
							out.add(killsFinalsBeds(values, suffix));
							merged = true;
						}
						continue;
					}
					if (TEAM_ROW.matcher(p).find() && (p.contains("✗") || p.contains("✘"))) {
						out.add(Component.literal(p).withStyle(ChatFormatting.DARK_GRAY));
						continue;
					}
				}
				default -> {
				}
			}
			out.add(row.text());
		}
		return tidyBlanks(out);
	}

	// "09/26/26  01:36  m193BM": date and time gray, server ID dark gray like Hypixel's
	private static Component dateLine(String date, String serverId, boolean clock24h) {
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern(clock24h ? "HH:mm" : "h:mm a", Locale.ROOT));
		MutableComponent line = Component.literal(date + "  " + time).withStyle(ChatFormatting.GRAY);
		if (!serverId.isEmpty()) line.append(Component.literal("  " + serverId).withStyle(ChatFormatting.DARK_GRAY));
		return line;
	}

	// "288K 107W 2.43 FKDR": kills white, wins green, FKDR colored by value, letters in the suffix color
	private Component lobbyStats(Map<String, String> values, int suffix, Minecraft client) {
		MutableComponent line = Component.empty();
		String kills = firstNumber(values.get("Total Kills"));
		String wins = firstNumber(values.get("Total Wins"));
		if (kills != null) line.append(colored(kills, 0xFFFFFF)).append(colored("K ", suffix));
		if (wins != null) line.append(colored(wins, 0x55FF55)).append(colored("W ", suffix));

		StatsLookup own = stats.get(client.player.getGameProfile().name()).orElse(null);
		if (own instanceof StatsLookup.Found found) {
			double fkdr = found.stats().overall().fkdr();
			line.append(colored(String.format(Locale.ROOT, "%.2f", fkdr), StatColors.fkdr(fkdr) & 0xFFFFFF));
		} else {
			line.append(colored("?", StatColors.SEPARATOR & 0xFFFFFF));
		}
		return line.append(colored(" FKDR", suffix));
	}

	// "205k Tokens · 39/99 Tickets", or whichever half Hypixel sent
	private static Component lobbyCurrency(Map<String, String> values, int suffix) {
		String tokens = firstNumber(values.get("Tokens"));
		String tickets = values.get("Slumber Tickets");
		if (tokens == null && tickets == null) return null;
		MutableComponent line = Component.empty();
		if (tokens != null) {
			line.append(colored(compact(Long.parseLong(tokens.replace(",", ""))), 0x00AA00)).append(colored(" Tokens", suffix));
		}
		if (tickets != null) {
			if (tokens != null) line.append(colored(" · ", 0x555555));
			int slash = tickets.indexOf('/');
			if (slash > 0) {
				line.append(colored(tickets.substring(0, slash), 0x55FFFF)).append(colored(tickets.substring(slash), 0xAAAAAA));
			} else {
				line.append(colored(tickets, 0x55FFFF));
			}
			line.append(colored(" Tickets", suffix));
		}
		return line;
	}

	// "Aquarium · 4v4v4v4"
	private static Component mapAndMode(Map<String, String> values) {
		MutableComponent line = colored(values.getOrDefault("Map", "?"), 0x55FF55);
		String mode = values.get("Mode");
		if (mode != null) line.append(colored(" · ", 0x555555)).append(colored(mode, 0x55FF55));
		return line;
	}

	// "2K 3F 1B"
	private static Component killsFinalsBeds(Map<String, String> values, int suffix) {
		MutableComponent line = Component.empty();
		String[][] parts = {{"Kills", "K"}, {"Final Kills", "F"}, {"Beds Broken", "B"}};
		boolean first = true;
		for (String[] part : parts) {
			String number = firstNumber(values.get(part[0]));
			if (number == null) continue;
			if (!first) line.append(Component.literal(" "));
			line.append(colored(number, 0x55FF55)).append(colored(part[1], suffix));
			first = false;
		}
		return line;
	}

	/** 950 -> "950", 9,540 -> "9.5k", 205,455 -> "205k", 1,250,000 -> "1.3M". */
	static String compact(long n) {
		if (n < 1_000) return Long.toString(n);
		if (n < 10_000) return trimZero(n / 1_000.0) + "k";
		if (n < 1_000_000) return (n / 1_000) + "k";
		if (n < 10_000_000) return trimZero(n / 1_000_000.0) + "M";
		return (n / 1_000_000) + "M";
	}

	private static String trimZero(double value) {
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	private static String firstNumber(String value) {
		if (value == null) return null;
		Matcher number = NUMBER.matcher(value);
		return number.find() ? number.group(1) : null;
	}

	// No blank line at the top or bottom, and never two in a row
	private static List<Component> tidyBlanks(List<Component> lines) {
		List<Component> out = new ArrayList<>();
		for (Component line : lines) {
			boolean blank = line.getString().isBlank();
			if (blank && (out.isEmpty() || out.getLast().getString().isBlank())) continue;
			out.add(line);
		}
		while (!out.isEmpty() && out.getLast().getString().isBlank()) out.removeLast();
		return out;
	}

	private static MutableComponent colored(String text, int rgb) {
		return Component.literal(text).withStyle(style -> style.withColor(rgb));
	}

	// Hypixel pads rows with made-up codes like "§u" to keep them unique
	private static String plain(String text) {
		return text == null ? "" : text.replaceAll("§.", "").trim();
	}

	// Same placement and shading as the vanilla sidebar
	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, Component title, List<Component> lines) {
		Font font = client.font;
		int titleWidth = font.width(title);
		int width = titleWidth;
		for (Component line : lines) width = Math.max(width, font.width(line));

		int height = lines.size() * ROW;
		int bottom = graphics.guiHeight() / 2 + height / 3;
		int left = graphics.guiWidth() - width - 3;
		int right = graphics.guiWidth() - 3 + 2;
		int top = bottom - height;
		int rowShade = client.options.getBackgroundColor(0.3f);
		int titleShade = client.options.getBackgroundColor(0.4f);

		graphics.fill(left - 2, top - ROW - 1, right, top - 1, titleShade);
		graphics.fill(left - 2, top - 1, right, bottom, rowShade);
		graphics.text(font, title, left + width / 2 - titleWidth / 2, top - ROW, -1, false);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), left, top + i * ROW, -1, false);
		}
	}
}
